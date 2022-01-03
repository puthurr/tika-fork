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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.config.BaseParserConfig;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.TikaServerResource;

@Path("/azure/converter")
public class AzureConverterResource extends AbstractAzureResource implements TikaServerResource {

    private static final Logger LOG = LoggerFactory.getLogger(AzureConverterResource.class);

    private BaseParserConfig baseParserConfig = new BaseParserConfig();

    static {
        if (connectStr != null) {
            blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectStr)
                    .buildClient();
        }
    }

    @Path("/pptx")
    @PUT
    @Produces({"text/plain"})
    public String convertPPTX(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception {

        InputStream tikaInputStream = TikaResource.getInputStream(is, new Metadata(), httpHeaders);

        TikaResource.logRequest(LOG, info.toString(), new Metadata());

        // AZURE Support
        // Get the headers
        MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();

        // User-Defined Metadata we will add to the extracted item in Azure Blob.
        // Those would be common to all embedded resources, useful to refer back to the original document.
        Map<String, String> blobMetadata = new HashMap<>();
        // Populate the blobMetadata from headers. Use the prefix x-ms-meta-name:string-value
        for (String key : headers.keySet()) {
            if (key.startsWith(AZURE_METADATA_PREFIX)) {
                blobMetadata.put(key.replaceAll(AZURE_METADATA_PREFIX, ""), headers.getFirst(key));
            }
        }

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

        // Open SlideShow
        XMLSlideShow ppt
                = new XMLSlideShow(tikaInputStream);

        // get the dimension and size of the slide
        Dimension pgsize = ppt.getPageSize();
        List<XSLFSlide> pptSlides = ppt.getSlides();

        BufferedImage img = null;

        System.out.println(pptSlides.size());

        /* AZURE */

        BlobHttpHeaders sysproperties = new BlobHttpHeaders();
        sysproperties.setContentType("image/png");

        Long blockSize = 10L * 1024L * 1024L; // 10 MB;
        ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions()
                .setBlockSizeLong(blockSize).setMaxConcurrency(5);

        // Loop through the slides
        for (int i = 0; i < pptSlides.size(); i++) {
            img = new BufferedImage(
                    pgsize.width, pgsize.height,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();

            // clear area
//            graphics.setPaint(Color.white);
//            graphics.fill(new Rectangle2D.Float(
//                    0, 0, pgsize.width, pgsize.height));

            Color whiteTrans = new Color(1f,1f,1f,0f);
            graphics.setColor(whiteTrans);
            graphics.fillRect(0, 0, pgsize.width, pgsize.height);

            // draw the images
            pptSlides.get(i).draw(graphics);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            ppt.write(out);

            byte[] data = out.toByteArray();

            String imageName = baseParserConfig.getResourceFilename("image",i + 1,99999,".png");

            BlobClient blobClient = containerClient.getBlobClient(containerDirectory + "/" + imageName);
            blobClient.uploadWithResponse(new ByteArrayInputStream(data), data.length, parallelTransferOptions,
                    sysproperties, blobMetadata, null, null, null, null);

            out.close();
        }

        return ("PPTX successfully converted");
    }

    @Path("/ppt")
    @PUT
    @Produces({"text/plain"})
    public String convertPPT(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception {

        InputStream tikaInputStream = TikaResource.getInputStream(is, new Metadata(), httpHeaders);

        TikaResource.logRequest(LOG, info.toString(), new Metadata());

        // AZURE Support
        // Get the headers
        MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();

        // User-Defined Metadata we will add to the extracted item in Azure Blob.
        // Those would be common to all embedded resources, useful to refer back to the original document.
        Map<String, String> blobMetadata = new HashMap<>();
        // Populate the blobMetadata from headers. Use the prefix x-ms-meta-name:string-value
        for (String key : headers.keySet()) {
            if (key.startsWith(AZURE_METADATA_PREFIX)) {
                blobMetadata.put(key.replaceAll(AZURE_METADATA_PREFIX, ""), headers.getFirst(key));
            }
        }

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

        // PPT
        // Open SlideShow
        HSLFSlideShow ppt = new HSLFSlideShow(tikaInputStream);

        // get the dimension and size of the slide
        Dimension pgsize = ppt.getPageSize();

        List<HSLFSlide> pptSlides = ppt.getSlides();

        BufferedImage img = null;

        System.out.println(pptSlides.size());

        /* AZURE */

        BlobHttpHeaders sysproperties = new BlobHttpHeaders();
        sysproperties.setContentType("image/png");

        Long blockSize = 10L * 1024L * 1024L; // 10 MB;
        ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions()
                .setBlockSizeLong(blockSize).setMaxConcurrency(5);

        // Loop through the slides
        for (int i = 0; i < pptSlides.size(); i++) {
            img = new BufferedImage(
                    pgsize.width, pgsize.height,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();

            // clear area
//            graphics.setPaint(Color.white);
//            graphics.fill(new Rectangle2D.Float(
//                    0, 0, pgsize.width, pgsize.height));
            Color whiteTrans = new Color(1f,1f,1f,0f);
            graphics.setColor(whiteTrans);
            graphics.fillRect(0, 0, pgsize.width, pgsize.height);

            // draw the images
            pptSlides.get(i).draw(graphics);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            ppt.write(out);

            byte[] data = out.toByteArray();

            String imageName = baseParserConfig.getResourceFilename("image",i + 1,99999,".png");

            BlobClient blobClient = containerClient.getBlobClient(containerDirectory + "/" + imageName);
            blobClient.uploadWithResponse(new ByteArrayInputStream(data), data.length, parallelTransferOptions,
                    sysproperties, blobMetadata, null, null, null, null);

            out.close();
        }

        return ("PPT successfully converted");
    }
}
