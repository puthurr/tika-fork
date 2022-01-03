/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.server.standard.resource.azure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ParallelTransferOptions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.RichTextContentHandler;
import org.apache.tika.server.core.MetadataList;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.TikaServerResource;
import org.apache.tika.utils.ParserUtils;

@Path("/azure/unpack")
public class AzureUnpackerResource extends AbstractAzureResource implements TikaServerResource {
    // 100MB max size
    private static final long MAX_ATTACHMENT_BYTES = 100L * 1024L * 1024L;

    private static final Logger LOG = LoggerFactory.getLogger(AzureUnpackerResource.class);

    static {
        if (connectStr != null) {
            blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectStr)
                    .buildClient();
        }
    }

    @Path("/{id:(/.*)?}")
    @PUT
    @Produces({"application/json"})
    public MetadataList unpack(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception
    {
        return process(TikaResource.getInputStream(is, new Metadata(), httpHeaders), httpHeaders, info, false);
    }

    @Path("/all{id:(/.*)?}")
    @PUT
    @Produces({"application/json"})
    public MetadataList unpackAll(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception
    {
        return process(TikaResource.getInputStream(is, new Metadata(), httpHeaders), httpHeaders, info, true);
    }

    private MetadataList process(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info,
            boolean saveAll
    ) throws Exception
    {
        if ( blobServiceClient == null )
        {
            this.AcquireBlobServiceClient();

            if ( blobServiceClient == null)
            {
                throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
            }
        }

        List<Metadata> metadataList = new LinkedList<>();

        // Get the headers
        MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();

        String containerName = this.GetContainer(headers);
        String containerDirectory = this.GetContainerDirectory(headers);

        if (containerName == null) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        /* Create a new container client */
        BlobContainerClient containerClient = null;

        try {
            containerClient = this.AcquireBlobContainerClient(containerName);
        } catch (BlobStorageException ex) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        Metadata metadata = new Metadata();
        ParseContext pc = new ParseContext();

        Parser parser = TikaResource.createParser();
        if (parser instanceof DigestingParser) {
            //no need to digest for unwrapping
            parser = ((DigestingParser)parser).getWrappedParser();
        }
        TikaResource.fillParseContext(headers, metadata, pc);
        TikaResource.fillMetadata(parser, metadata, headers);
        TikaResource.logRequest(LOG, info.toString(), metadata);
        //even though we aren't currently parsing embedded documents,
        //we need to add this to allow for "inline" use of other parsers.
        pc.set(Parser.class, parser);
        ContentHandler ch;
        ByteArrayOutputStream text = new ByteArrayOutputStream();

        if (saveAll) {
            ch = new BodyContentHandler(new RichTextContentHandler(new OutputStreamWriter(text, UTF_8)));
        } else {
            ch = new DefaultHandler();
        }

        Map<String, String> files = new HashMap<>();
        MutableInt count = new MutableInt();
        // User-Defined Metadata we will add to the extracted item in Azure Blob.
        // Those would be common to all embedded resources, useful to refer back to the original document.
        Map<String, String> blobMetadata = new HashMap<>();
        // Populate the blobMetadata from headers. Use the prefix x-ms-meta-name:string-value
        for (String key : headers.keySet()) {
            if ( key.startsWith(AZURE_METADATA_PREFIX)) {
                blobMetadata.put(key.replaceAll(AZURE_METADATA_PREFIX,""),headers.getFirst(key));
            }
        }
        // Set the EmbeddedDocumentExtractor we need
        pc.set(EmbeddedDocumentExtractor.class, new AzureEmbeddedDocumentExtractor(
                count, files, metadataList, containerClient, containerDirectory, blobMetadata));
        // Parse
        TikaResource.parse(parser, LOG, info.getPath(), is, ch, metadata, pc);

        if (count.intValue() == 0 && !saveAll) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }

        if ( text != null ) {
            text.close();
        }

        // Ask for GC whenever the JVM wants to execute.
        System.gc();

        // Add the document metadata to index 0 and return
        metadataList.add(0, ParserUtils.cloneMetadata(metadata));
        // Wrap into a MetadataList object
        return new MetadataList(metadataList);
    }

    private class AzureEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        private final MutableInt count;
        private final Map<String, String> zout;
        private BlobContainerClient containerClient ;
        private Map<String, String> blobMetadata;
        private String containerDirectory;
        private List<Metadata> metadataList;

        AzureEmbeddedDocumentExtractor(MutableInt count, Map<String, String> zout,
                                       List<Metadata> metadataList,
                                       BlobContainerClient containerClient,
                                       String containerDirectory,
                                       Map<String, String> blobMetadata) {
            this.count = count;
            this.zout = zout;
            this.metadataList = metadataList;
            this.containerClient = containerClient;
            this.blobMetadata = blobMetadata;
            this.containerDirectory = containerDirectory;
        }

        public boolean shouldParseEmbedded(Metadata metadata)
        {
            return true;
        }

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b)
                throws SAXException, IOException
        {
//            ByteArrayOutputStream bos = new ByteArrayOutputStream();
//            BoundedInputStream bis = new BoundedInputStream(MAX_ATTACHMENT_BYTES, inputStream);
//            IOUtils.copy(bis, bos);
//            if (bis.hasHitBound()) {
//                throw new IOExceptionWithCause(
//                        new TikaMemoryLimitException(MAX_ATTACHMENT_BYTES+1, MAX_ATTACHMENT_BYTES));
//            }
//            byte[] data = bos.toByteArray();
            byte[] data = org.apache.commons.io.IOUtils.toByteArray(inputStream);

            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);

            if (name == null) {
                name = Integer.toString(count.intValue());
            }

            if (!name.contains(".") && contentType != null) {
                try {
                    String ext = TikaResource.getConfig().getMimeRepository().forName(contentType).getExtension();

                    if (ext != null) {
                        name += ext;
                    }
                } catch (MimeTypeException e) {
                    LOG.warn("Unexpected MimeTypeException", e);
                }
            }

            if ("application/vnd.openxmlformats-officedocument.oleObject".equals(contentType)) {
                POIFSFileSystem poifs = new POIFSFileSystem(new ByteArrayInputStream(data));
                OfficeParser.POIFSDocumentType type = OfficeParser.POIFSDocumentType.detectType(poifs);

                if (type == OfficeParser.POIFSDocumentType.OLE10_NATIVE) {
                    try {
                        Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(poifs);
                        if (ole.getDataSize() > 0) {
                            String label = ole.getLabel();

                            if (label.startsWith("ole-")) {
                                label = Integer.toString(count.intValue()) + '-' + label;
                            }

                            name = label;

                            data = ole.getDataBuffer();
                        }
                    } catch (Ole10NativeException ex) {
                        LOG.warn("Skipping invalid part", ex);
                    }
                } else {
                    name += '.' + type.getExtension();
                }
            }

            final String finalName = getFinalName(name, zout);

            this.metadataList.add(ParserUtils.cloneMetadata(metadata));

            if (data.length > 0) {
                /* Upload the file to the Azure container */
                BlobClient blobClient = containerClient.getBlobClient(containerDirectory + "/" + finalName);
                BlobHttpHeaders sysproperties = new BlobHttpHeaders();
                sysproperties.setContentType(contentType);

                Long blockSize = 10L * 1024L * 1024L; // 10 MB;
                ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions()
                        .setBlockSizeLong(blockSize).setMaxConcurrency(5);

                blobClient.uploadWithResponse(new ByteArrayInputStream(data), data.length,parallelTransferOptions,
                        sysproperties,blobMetadata,null,null,null,null );

                count.increment();
            } else {
                if (inputStream instanceof TikaInputStream) {
                    TikaInputStream tin = (TikaInputStream) inputStream;

                    if (tin.getOpenContainer() != null && tin.getOpenContainer() instanceof DirectoryEntry) {
                        POIFSFileSystem fs = new POIFSFileSystem();
                        copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
                        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
                        fs.writeFilesystem(bos2);
                        bos2.close();

                        //AZURE BLOB WRITE
                        BlobClient blobClient = containerClient
                                .getBlobClient(containerDirectory + "/" + finalName);
                        BlobHttpHeaders sysproperties = new BlobHttpHeaders();
                        sysproperties.setContentType(contentType);

                        Long blockSize = 10L * 1024L * 1024L; // 10 MB;
                        ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions()
                                .setBlockSizeLong(blockSize).setMaxConcurrency(5);

                        blobClient.uploadWithResponse(new ByteArrayInputStream(bos2.toByteArray()),
                                bos2.toByteArray().length,null,sysproperties,
                                blobMetadata,null,null,null,null );
                    }
                }
            }

            // Invoke a gc every 50 embedded objects.
            if ( (count.intValue() % 50) == 0 ) {
                System.gc();
            }
        }

        private String getFinalName(String name, Map<String, String> zout)
        {
            name = name.replaceAll("\u0000", " ");
            String normalizedName = FilenameUtils.normalize(name);

            if (normalizedName == null) {
                normalizedName = FilenameUtils.getName(name);
            }

            if (normalizedName == null) {
                normalizedName = count.toString();
            }
            //strip off initial C:/ or ~/ or /
            int prefixLength = FilenameUtils.getPrefixLength(normalizedName);
            if (prefixLength > -1) {
                normalizedName = normalizedName.substring(prefixLength);
            }
            if (zout.containsKey(normalizedName)) {
                return UUID.randomUUID().toString() + "-" + normalizedName;
            }
            return normalizedName;
        }

        protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir)
                throws IOException
        {
            for (Entry entry : sourceDir) {
                if (entry instanceof DirectoryEntry) {
                    // Need to recurse
                    DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                    copy((DirectoryEntry) entry, newDir);
                } else {
                    // Copy entry
                    try (InputStream contents = new DocumentInputStream((DocumentEntry) entry)) {
                        destDir.createDocument(entry.getName(), contents);
                    }
                }
            }
        }
    }
}
