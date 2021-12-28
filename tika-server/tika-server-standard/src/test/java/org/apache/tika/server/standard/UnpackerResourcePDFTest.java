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

package org.apache.tika.server.standard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;
import org.apache.tika.server.standard.config.PDFServerConfig;

public class UnpackerResourcePDFTest extends CXFTestBase {

    private static final String BASE_PATH = "/unpack";
    private static final String UNPACKER_PATH = BASE_PATH + "";

    private static final String PDF1 = "test-documents/pdf/pdf1.pdf";
    private static final String PDF2 = "test-documents/pdf/pdf2.pdf";
    private static final String PDF3 = "test-documents/pdf/pdf3-single-page.pdf";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class,
                new SingletonResourceProvider(new UnpackerResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
    }

    @Test
    public void testPDF1AllPagesAsImagesTrue() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "allPagesAsImages", "true")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF1));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertTrue((results.size() > 30));
        assertTrue(results.containsKey("image-00020-99999.png"));
        assertTrue(results.containsKey("image-00023-99999.png"));
        assertTrue(results.containsKey("image-00034-99999.png"));
    }

    @Test
    public void testPDF1AllPagesAsImagesFalse() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "allPagesAsImages", "false")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF1));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertTrue((results.size() < 30));
        assertTrue(results.containsKey("image-00031-00020.tif"));
        assertTrue(results.containsKey("image-00031-00023.tif"));
    }

    @Test
    public void testPDF1GraphicsToImageTrue() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "GraphicsToImage", "true")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF1));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertTrue((results.size() >= 25));
        assertTrue(results.containsKey("image-00004-99999.png"));
        assertTrue(results.containsKey("image-00029-99999.png"));
        assertTrue(results.containsKey("image-00032-99999.png"));
    }

    @Test
    public void testPDF1GraphicsToImageFalse() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "GraphicsToImage", "false")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF1));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertFalse((results.size() >= 25));
        assertTrue(results.containsKey("image-00011-00004.jpg"));
        assertTrue(results.containsKey("image-00031-00020.tif"));
        assertTrue(results.containsKey("image-00031-00023.tif"));
    }

    @Test
    public void testPDF2GraphicsToImageTrue() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "GraphicsToImage", "true")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF2));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        //All PDF pages are images...
        assertTrue((results.size() >= 48));
        assertTrue(results.containsKey("image-00001-99999.png"));
    }

    @Test
    public void testPDF2GraphicsToImageTrueThreshold() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "GraphicsToImage", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "GraphicsToImageThreshold", "25000000")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF2));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertTrue((results.size() >= 71));
        assertTrue(results.containsKey("image-00001-00001.jpg"));
        assertTrue(results.containsKey("image-00015-00010.jpg"));
    }

    @Test
    public void testPDF3SinglePagePDFTrue() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "SinglePagePDFAsImage", "true")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF3));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertTrue(results.containsKey("image-00001-99999.png"));
        assertFalse(results.containsKey("image-00001-00002.jpg"));
        assertFalse(results.containsKey("image-00001-00003.jpg"));
    }

    @Test
    public void testPDF3SinglePagePDFFalse() throws Exception {
        Response response = WebClient.create(endPoint + UNPACKER_PATH)
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "ExtractInlineImages", "true")
                .header(PDFServerConfig.X_TIKA_PDF_HEADER_PREFIX + "SinglePagePDFAsImage", "false")
                .accept("application/zip")
                .put(ClassLoader.getSystemResourceAsStream(PDF3));
        Map<String, String> results = readZipArchive((InputStream) response.getEntity());
        assertTrue(results.containsKey("image-00001-00001.png"));
        assertTrue(results.containsKey("image-00001-00002.jpg"));
        assertTrue(results.containsKey("image-00001-00003.jpg"));
    }
}
