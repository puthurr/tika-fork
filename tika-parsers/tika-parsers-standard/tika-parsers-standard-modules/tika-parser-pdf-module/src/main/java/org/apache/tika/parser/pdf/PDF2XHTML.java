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
package org.apache.tika.parser.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.MissingImageReaderException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.pdfbox.util.Matrix;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.image.ImageGraphicsEngine;
import org.apache.tika.parser.pdf.image.ImageGraphicsEngineFactory;
import org.apache.tika.parser.pdf.statistics.PDFStatistics;
import org.apache.tika.sax.EmbeddedContentHandler;

/**
 * Utility class that overrides the {@link PDFTextStripper} functionality
 * to produce a semi-structured XHTML SAX events instead of a plain text
 * stream.
 */
class PDF2XHTML extends AbstractPDF2XHTML {


    /**
     * This keeps track of the pdf object ids for inline
     * images that have been processed.
     * If {@link PDFParserConfig#isExtractUniqueInlineImagesOnly()
     * is true, this will be checked before extracting an embedded image.
     * The integer keeps track of the inlineImageCounter for that image.
     * This integer is used to identify images in the markup.
     * <p>
     * This is used across the document.  To avoid infinite recursion
     * TIKA-1742, we're limiting the export to one image per page.
     */
    private Map<COSStream, Integer> processedInlineImages = new HashMap<>();

    // puthurr - Image number starting to 1 -
    private AtomicInteger inlineImageCounter = new AtomicInteger(1);

    PDF2XHTML(PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
              PDFParserConfig config) throws IOException {
        super(document, handler, context, metadata, config);
    }

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param document PDF document
     * @param handler  SAX content handler
     * @param metadata PDF metadata
     * @throws SAXException  if the content handler fails to process SAX events
     * @throws TikaException if there was an exception outside of per page processing
     */
    public static void process(PDDocument document, ContentHandler handler, ParseContext context,
                               Metadata metadata, PDFParserConfig config)
            throws SAXException, TikaException {
        PDF2XHTML pdf2XHTML = null;
        try {
            // Extract text using a dummy Writer as we override the
            // key methods to output to the given content
            // handler.
            if (config.isDetectAngles()) {
                pdf2XHTML =
                        new AngleDetectingPDF2XHTML(document, handler, context, metadata, config);
            } else {
                pdf2XHTML = new PDF2XHTML(document, handler, context, metadata, config);
            }
            config.configure(pdf2XHTML);

            pdf2XHTML.writeText(document, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
        if (pdf2XHTML.exceptions.size() > 0) {
            //throw the first
            throw new TikaException("Unable to extract PDF content", pdf2XHTML.exceptions.get(0));
        }
    }

    @Override
    public void processPage(PDPage page) throws IOException {
        try {
            super.processPage(page);
        } catch (IOException e) {
            handleCatchableIOE(e);
            endPage(page);
        }
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        try {
            writeParagraphEnd();
            try {
                extractImages(page);
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
            super.endPage(page);
        } catch (SAXException e) {
            throw new IOException("Unable to end a page", e);
        } catch (IOException e) {
            handleCatchableIOE(e);
        }
    }

    /**
     * PUTHURR : Method to convert a page into an image. Using PDFBOX renderImageWithDPI
     * Resulting image is added as an embedded object.
     *
     * @param page
     * @throws SAXException
     * @throws IOException
     */
    protected void processPageAsImage(PDPage page) throws SAXException, IOException {
        PDFRenderer renderer = new PDFRenderer(pdDocument);
        int dpi = config.getOcrDPI();
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
        Metadata imgMetadata = new Metadata();
        String extension = config.getOcrImageFormatName();
        extension = ImageGraphicsEngine.getSuffix(extension, imgMetadata);

        int imageNumber = 99999;
        // Increment the Image Number and put a new COSStream object in
//        int imageNumber = inlineImageCounter.getAndIncrement();
//        processedInlineImages.put(new COSStream(), imageNumber);

        String fileName = config.getImageFilename(pageIndex + 1, imageNumber, extension);

        imgMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        // Build the Image Tag Attributes
        AttributesImpl attr = buildImageAttributes(imageNumber, imgMetadata, fileName);

        imgMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.INLINE.toString());

        if (embeddedDocumentExtractor.shouldParseEmbedded(imgMetadata)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try {
                try {
                    ImageIOUtil.writeImage(image, config.getOcrImageFormatName(), buffer, dpi,
                            config.getOcrImageQuality());
                    // Get the size of the image from the OutputStream for consistency
                    attr.addAttribute("", "size", "size", "CDATA", String.valueOf(buffer.size()));
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                    return;
                }
                try (InputStream embeddedIs = TikaInputStream.get(buffer.toByteArray())) {
                    embeddedDocumentExtractor.parseEmbedded(
                            embeddedIs,
                            new EmbeddedContentHandler(xhtml),
                            imgMetadata, false);

                    adjustImageWidthHeightAttributes(attr, imgMetadata, image.getWidth(), image.getHeight());
                }
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
        }

        writeImageTag(attr);
    }


    /**
     * Extract Embedded/Inline Images if configured to do so.
     *
     * @param page
     * @throws SAXException
     * @throws IOException
     */
    private void extractImages(PDPage page) throws SAXException, IOException {
        if (config.isExtractInlineImages() == false) {
            return;
        }

        // Set a flag to convert the page to image or not.
        // Initialized with the config parameter allPagesAsImages.
        boolean convertPageToImage = config.isAllPagesAsImages();

        if (convertPageToImage) {
            processPageAsImage(page);
            metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "PDFAllPagesAsImages", String.valueOf(true));
            return;
        }

        if (config.isFirstPageAsCoverImage() && pageIndex == 0) {
            processPageAsImage(page);
            metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "PDFFirstPageAsCoverImage", String.valueOf(true));
            return;
        }

        // puthurr - striped-scanned images ( single image is split into multiple streams in the PDF)
        if (page.hasContents() && config.isStripedImagesHandling()) {
            // Count how many Streams we have.
            int counter = 0;
            for (Iterator<PDStream> it = page.getContentStreams(); it.hasNext(); ) {
                PDStream i = it.next();
                counter++;
            }
            // Case: striped-scanned images oftenly have an Array of Streams instead of a single COSStream
            if (counter > config.getStripedImagesThreshold()) {
                convertPageToImage = true;
            }
            metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "PDFContentStreamsCount", String.valueOf(counter));
        }

        // puthurr - Our project is focused on Image, A single page PDF is forced into an image covering all our base
        // in terms of graphics, weird PDF construction etc.
        if ((config.isSinglePagePDFAsImage() && getTotalPagesCount() == 1) || convertPageToImage) {
            processPageAsImage(page);
            metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "PDFSinglePagePDFAsImage", String.valueOf(true));
            return;
        }

        // Create a Graphics Engine
        ImageGraphicsEngineFactory factory = new ImageGraphicsEngineFactory();
        ImageGraphicsEngine engine = factory.newEngine(page,pageIndex + 1, embeddedDocumentExtractor, config,
                processedInlineImages, inlineImageCounter, xhtml, metadata, context);

        PDFStatistics stats = engine.runStatistics();

        // PUTHURR - many images on a single page could indicate a stripe issue (similar to the number of content
        // streams)
        if (config.isStripedImagesHandling()) {
            // Case: striped-scanned images oftenly have an Array of Streams instead of a single COSStream
            if (stats.getNumberOfImages() > config.getStripedImagesThreshold()) {
                processPageAsImage(page);
                metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "PDFStripedImagesCount",
                        String.valueOf(stats.getNumberOfImages()));
                return;
            }
        }

        // PUTHURR - when PDF page contains Graphics like curve, stroke etc. annotating any background image
        // they should be considered part of the extracted image.
        if (config.isGraphicsToImage()) {
            if (stats.getNumberOfGraphics() > config.getGraphicsToImageThreshold()) {
                // puthurr - not taking any risk of losing graphical annotation, we treat the entire page as a single
                // image
                processPageAsImage(page);
                metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "PDFGraphicsToImage",
                        String.valueOf(stats.getNumberOfGraphics()));
                return;
            }
        }

        // PUTHURR - when PDF page contains Graphics like curve, stroke etc. annotating any background image
        // they should be considered part of the extracted image.
        if (config.isJB2Images()) {
            if (stats.getNumberOfJB2Images() >= config.getJB2ImagesThreshold()) {
                processPageAsImage(page);
                metadata.add(TikaCoreProperties.TIKA_META_PREFIX + "JB2Images",
                        String.valueOf(stats.getNumberOfJB2Images()));
                return;
            }
        }

        // no presence of graphics on top of images, we can carry on as usual.
        Map<PDImage, Integer> extractedImages = engine.imagesExtractionRun();

        for (PDImage image : extractedImages.keySet()) {
            try {
                processExtractedImage(image, extractedImages.get(image));
            } catch (TikaException e) {
                e.printStackTrace();
            }
        }
        List<IOException> engineExceptions = engine.getExceptions();
        if (engineExceptions.size() > 0) {
            IOException first = engineExceptions.remove(0);
            String msg = first.getMessage();
            if (msg == null) {
                msg = "IOException, no message";
            }
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, msg);
            if (config.isCatchIntermediateIOExceptions()) {
                exceptions.addAll(engineExceptions);
            }
            throw first;
        }
    }

    private void processExtractedImage(PDImage pdImage, int imageNumber) throws IOException, TikaException,
            SAXException {
        //this is the metadata for this particular image
        Metadata imgMetadata = new Metadata();
        String suffix = ImageGraphicsEngine.getSuffix(pdImage, imgMetadata);
        String fileName = config.getImageFilename(pageIndex + 1, imageNumber, suffix);

        // Build the Image Tag Attributes
        AttributesImpl attr = buildImageAttributes(imageNumber, imgMetadata, fileName);

        imgMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        imgMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.INLINE.toString());

        if (embeddedDocumentExtractor.shouldParseEmbedded(imgMetadata)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try {
                //extract the metadata contained outside of the image
                if (pdImage instanceof PDImageXObject) {
                    PDMetadataExtractor.extract(((PDImageXObject) pdImage).getMetadata(),
                            imgMetadata, context);
                }
                try {
                    ImageGraphicsEngine.writeToBuffer(pdImage, suffix, ImageGraphicsEngine.useDirectJPEG, buffer);
                    // Get the size of the image from the OutputStream for consistency
                    attr.addAttribute("", "size", "size", "CDATA", String.valueOf(buffer.size()));
                } catch (MissingImageReaderException e) {
                    EmbeddedDocumentUtil.recordException(e, imgMetadata);
                    return;
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                    return;
                }
                // Parse Image metadata
                try (InputStream embeddedIs = TikaInputStream.get(buffer.toByteArray())) {
                    embeddedDocumentExtractor.parseEmbedded(
                            embeddedIs,
                            new EmbeddedContentHandler(xhtml),
                            imgMetadata, false);

                    adjustImageWidthHeightAttributes(attr, imgMetadata, pdImage.getWidth(), pdImage.getHeight());
                }
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
        }

        writeImageTag(attr);
    }

    private AttributesImpl buildImageAttributes(int imageNumber, Metadata imgMetadata, String fileName) {
        return buildImageAttributes(imageNumber, imgMetadata, fileName, 0, 0);
    }

    private AttributesImpl buildImageAttributes(int imageNumber, Metadata imgMetadata, String fileName, int width,
                                                int height) {
        // Output the img metadata and html
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute("", "src", "src", "CDATA", fileName);
        attr.addAttribute("", "alt", "alt", "CDATA", fileName);
        attr.addAttribute("", "class", "class", "CDATA", "embedded");
        //Adding extra attributes to the image tag for consistency
        try {
            attr.addAttribute("", "id", "id", "CDATA", config.getImageName(pageIndex + 1, imageNumber));
            attr.addAttribute("", "contenttype", "contenttype", "CDATA", imgMetadata.get(Metadata.CONTENT_TYPE));
            if (width > 0) {
                attr.addAttribute("", "width", "witdh", "CDATA", String.valueOf(width));
            }
            if (height > 0) {
                attr.addAttribute("", "height", "height", "CDATA", String.valueOf(height));
            }
        } catch (Exception e) {
            // Do Nothing
        }
        return attr;
    }

    private void adjustImageWidthHeightAttributes(AttributesImpl attr, Metadata imgMetadata, int iwidth, int iheight) {
        try {
            String width = imgMetadata.get(Metadata.IMAGE_WIDTH);
            if (width != null) {
                attr.addAttribute("", "width", "witdh", "CDATA", width);
            } else {
                attr.addAttribute("", "width", "witdh", "CDATA", String.valueOf(iwidth));
            }

            String height = imgMetadata.get(Metadata.IMAGE_LENGTH);
            if (height != null) {
                attr.addAttribute("", "height", "height", "CDATA", height);
            } else {
                attr.addAttribute("", "height", "height", "CDATA", String.valueOf(iheight));
            }
        } catch (Exception e) {
            // Do Nothing
        }
    }

    private void writeImageTag(AttributesImpl attributes) throws SAXException {
        xhtml.startElement("img", attributes);
        xhtml.endElement("img");
        xhtml.newline();
    }

    @Override
    protected void writeParagraphStart() throws IOException {
        super.writeParagraphStart();
        try {
            xhtml.startElement("p");
        } catch (SAXException e) {
            throw new IOException("Unable to start a paragraph", e);
        }
    }

    @Override
    protected void writeParagraphEnd() throws IOException {
        super.writeParagraphEnd();
        try {
            xhtml.endElement("p");
        } catch (SAXException e) {
            throw new IOException("Unable to end a paragraph", e);
        }
    }

    @Override
    protected void writeString(String text) throws IOException {
        try {
            xhtml.characters(text);
        } catch (SAXException e) {
            throw new IOException("Unable to write a string: " + text, e);
        }
    }

    @Override
    protected void writeCharacters(TextPosition text) throws IOException {
        try {
            xhtml.characters(text.getUnicode());
        } catch (SAXException e) {
            throw new IOException("Unable to write a character: " + text.getUnicode(), e);
        }
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        try {
            xhtml.characters(getWordSeparator());
        } catch (SAXException e) {
            throw new IOException("Unable to write a space character", e);
        }
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        try {
            xhtml.newline();
        } catch (SAXException e) {
            throw new IOException("Unable to write a newline character", e);
        }
    }

    private static class AngleDetectingPDF2XHTML extends PDF2XHTML {

        private AngleDetectingPDF2XHTML(PDDocument document, ContentHandler handler,
                                        ParseContext context, Metadata metadata,
                                        PDFParserConfig config) throws IOException {
            super(document, handler, context, metadata, config);
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            //no-op
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            //no-op
        }

        @Override
        public void processPage(PDPage page) throws IOException {
            try {
                super.startPage(page);
                detectAnglesAndProcessPage(page);
            } catch (IOException e) {
                handleCatchableIOE(e);
            } finally {
                super.endPage(page);
            }
        }

        private void detectAnglesAndProcessPage(PDPage page) throws IOException {
            //copied and pasted from https://issues.apache.org/jira/secure/attachment/12947452/ExtractAngledText.java
            //PDFBOX-4371
            AngleCollector angleCollector = new AngleCollector(); // alternatively, reset angles
            angleCollector.setStartPage(getCurrentPageNo());
            angleCollector.setEndPage(getCurrentPageNo());
            angleCollector.getText(document);

            int rotation = page.getRotation();
            page.setRotation(0);

            for (Integer angle : angleCollector.getAngles()) {
                if (angle == 0) {
                    try {
                        super.processPage(page);
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }
                } else {
                    // prepend a transformation
                    try (PDPageContentStream cs = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.PREPEND, false)) {
                        cs.transform(Matrix.getRotateInstance(-Math.toRadians(angle), 0, 0));
                    }

                    try {
                        super.processPage(page);
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }

                    // remove transformation
                    COSArray contents = (COSArray) page.getCOSObject().getItem(COSName.CONTENTS);
                    contents.remove(0);
                }
            }
            page.setRotation(rotation);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            Matrix m = text.getTextMatrix();
            m.concatenate(text.getFont().getFontMatrix());
            int angle = (int) Math.round(Math.toDegrees(Math.atan2(m.getShearY(), m.getScaleY())));
            if (angle == 0) {
                super.processTextPosition(text);
            }
        }
    }

    static class AngleCollector extends PDFTextStripper {
        Set<Integer> angles = new HashSet<>();

        /**
         * Instantiate a new PDFTextStripper object.
         *
         * @throws IOException If there is an error loading the properties.
         */
        AngleCollector() throws IOException {
        }

        public Set<Integer> getAngles() {
            return angles;
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            Matrix m = text.getTextMatrix();
            m.concatenate(text.getFont().getFontMatrix());
            int angle = (int) Math.round(Math.toDegrees(Math.atan2(m.getShearY(), m.getScaleY())));
            angle = (angle + 360) % 360;
            angles.add(angle);
        }
    }
}

