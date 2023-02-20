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
import java.awt.DisplayMode;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.config.BaseParserConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.TikaServerResource;

@Path("/azure/convert")
public class AzureConverterResource extends AbstractAzureResource implements TikaServerResource {

    private static final Logger LOG = LoggerFactory.getLogger(AzureConverterResource.class);


    private static final int DPI = 300;
    private static final float DPI_SCALE = DPI / 72f;

    private final BaseParserConfig baseParserConfig = new BaseParserConfig();

    static {
        if (connectStr != null) {
            blobServiceClient = new BlobServiceClientBuilder().connectionString(connectStr).buildClient();
        }
    }

    @Path("/pptx")
    @PUT
    @Produces({"text/plain"})
    public String convertPPTX(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info)
            throws Exception {

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
        XMLSlideShow ppt = new XMLSlideShow(tikaInputStream);

        // get the dimension and size of the slide
        Dimension pgsize = ppt.getPageSize();
        List<XSLFSlide> pptSlides = ppt.getSlides();

        BufferedImage img = null;

        // Loop through the slides
        for (int i = 0; i < pptSlides.size(); i++) {

            int widthPx = (int) Math.max(Math.floor(pgsize.width * DPI_SCALE), 1);
            int heightPx = (int) Math.max(Math.floor(pgsize.height * DPI_SCALE), 1);

            img = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();
            graphics.setBackground(Color.WHITE);
            graphics.clearRect(0, 0, img.getWidth(), img.getHeight());
            graphics.scale(DPI_SCALE, DPI_SCALE);
            graphics.addRenderingHints(createDefaultRenderingHints(graphics));

            // draw the images
            pptSlides.get(i).draw(graphics);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIOUtil.writeImage(img, OutputFormat, out, DPI);

            byte[] data = out.toByteArray();

            String imageName = baseParserConfig.getResourceFilename("image",
                    i + 1, 99999, "." + OutputFormat);

            this.UploadImage(containerClient,containerDirectory,imageName,data,blobMetadata);

            out.close();
        }

        return ("PPTX successfully converted");
    }

    @Path("/ppt")
    @PUT
    @Produces({"text/plain"})
    public String convertPPT(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info)
            throws Exception {

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

        // Loop through the slides
        for (int i = 0; i < pptSlides.size(); i++) {

            int widthPx = (int) Math.max(Math.floor(pgsize.width * DPI_SCALE), 1);
            int heightPx = (int) Math.max(Math.floor(pgsize.height * DPI_SCALE), 1);

            img = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();
            graphics.setBackground(Color.WHITE);
            graphics.clearRect(0, 0, img.getWidth(), img.getHeight());
            graphics.scale(DPI_SCALE, DPI_SCALE);
            graphics.addRenderingHints(createDefaultRenderingHints(graphics));

            // draw the images
            pptSlides.get(i).draw(graphics);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIOUtil.writeImage(img, OutputFormat, out, DPI);

            byte[] data = out.toByteArray();

            String imageName = baseParserConfig.getResourceFilename("image",
                    i + 1, 99999, "." + OutputFormat);

            this.UploadImage(containerClient,containerDirectory,imageName,data,blobMetadata);

            out.close();
        }

        return ("PPT successfully converted");
    }


    @Path("/pdf")
    @PUT
    @Produces({"text/plain"})
    public String convertPDF(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info)
            throws Exception {

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

        // PDF
        PDDocument pdfDocument = null;

        try {

            pdfDocument = PDDocument.load(tikaInputStream);

            PDPageTree pages = pdfDocument.getPages();
            int totalPagesCount = pages.getCount();

            PDFRenderer renderer = new PDFRenderer(pdfDocument);
            PDFParserConfig config = new PDFParserConfig();

            for (int pageIndex = 0; pageIndex < totalPagesCount; pageIndex++) {
                PDPage page = pages.get(pageIndex);
                int dpi = config.getOcrDPI();

                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);

                String extension = config.getOcrImageFormatName();
                int imageNumber = 99999;

                String fileName = config.getImageFilename(pageIndex + 1, imageNumber, extension);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIOUtil.writeImage(image, config.getOcrImageFormatName(), out, dpi);

                byte[] data = out.toByteArray();

                this.UploadImage(containerClient,containerDirectory,fileName,data,blobMetadata);

                out.close();
            }
        } finally {
            if (pdfDocument != null) {
                pdfDocument.close();
            }
        }

        return ("PDF successfully converted");
    }

    // Utilities methods

    private boolean isBitonal(Graphics2D graphics) {
        GraphicsConfiguration deviceConfiguration = graphics.getDeviceConfiguration();
        if (deviceConfiguration == null) {
            return false;
        }
        GraphicsDevice device = deviceConfiguration.getDevice();
        if (device == null) {
            return false;
        }
        DisplayMode displayMode = device.getDisplayMode();
        if (displayMode == null) {
            return false;
        }
        return displayMode.getBitDepth() == 1;
    }

    private RenderingHints createDefaultRenderingHints(Graphics2D graphics) {
        RenderingHints r = new RenderingHints(null);
        r.put(RenderingHints.KEY_INTERPOLATION, isBitonal(graphics) ?
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR :
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        r.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        r.put(RenderingHints.KEY_ANTIALIASING, isBitonal(graphics) ?
                RenderingHints.VALUE_ANTIALIAS_OFF :
                RenderingHints.VALUE_ANTIALIAS_ON);
        return r;
    }

}
