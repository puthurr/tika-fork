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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.ws.rs.core.MultivaluedMap;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.apache.commons.codec.binary.Base64;

import org.apache.tika.Tika;

public class AbstractAzureResource {
    // AZURE
    protected static final String AZURE_STORAGE_CONNECTION_STRING = "AZURE_STORAGE_CONNECTION_STRING";

    protected static final String AZURE_STORAGE_MI_ENABLED = "AZURE_STORAGE_MI_ENABLED";
    protected static final String AZURE_STORAGE_CLIENT_ID = "AZURE_STORAGE_CLIENT_ID";
    protected static final String AZURE_STORAGE_SERVICE_URI = "AZURE_STORAGE_SERVICE_URI";

    protected static final String AZURE_CONTAINER = "X-TIKA-AZURE-CONTAINER";
    protected static final String AZURE_CONTAINER_DIRECTORY = "X-TIKA-AZURE-CONTAINER-DIRECTORY";
    protected static final String AZURE_CONTAINER_DIRECTORY_BASE64ENCODED
            = "X-TIKA-AZURE-CONTAINER-DIRECTORY-BASE64ENCODED";
    protected static final String AZURE_METADATA_PREFIX = "X-TIKA-AZURE-META-";

    // Retrieve the connection string for use with the application. The storage
    // connection string is stored in an environment variable on the machine
    // running the application called AZURE_STORAGE_CONNECTION_STRING. If the environment variable
    // is created after the application is launched in a console or with
    // Visual Studio, the shell or application needs to be closed and reloaded
    // to take the environment variable into account.
    private static String connectStr = System.getenv(AZURE_STORAGE_CONNECTION_STRING);

    /* Create a new BlobServiceClient with a connection string */
    protected static BlobServiceClient blobServiceClient;

    // Upload data variables
    protected static final String OutputFormat = "png";
    protected static final String OutputContentType = "image/" + OutputFormat;
    protected BlobHttpHeaders sysproperties = new BlobHttpHeaders().setContentType(OutputContentType);
    protected Long blockSize = 10L * 1024L * 1024L; // 10 MB;
    protected ParallelTransferOptions parallelTransferOptions =
            new ParallelTransferOptions().setBlockSizeLong(blockSize).setMaxConcurrency(5);

    protected final Tika tikaDetector = new Tika();

    protected static void AcquireBlobServiceClient()
    {
        boolean isManagedIdentityEnabled = Boolean.parseBoolean(System.getenv(AZURE_STORAGE_MI_ENABLED) != null ?
                System.getenv(AZURE_STORAGE_MI_ENABLED) : "false");

        if (isManagedIdentityEnabled) {
            TokenCredential defaultCredential = new DefaultAzureCredentialBuilder().build();

            String clientId = System.getenv(AZURE_STORAGE_CLIENT_ID);
            if (clientId != null)
            {
                defaultCredential = new ManagedIdentityCredentialBuilder()
                        .clientId(clientId) // only required for user assigned
                        .build();
            }
            String serviceUri = System.getenv(AZURE_STORAGE_SERVICE_URI);
            blobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(serviceUri)
                    .credential(defaultCredential)
                    .buildClient();
        } else if (connectStr != null) {
            blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectStr)
                    .buildClient();
        }
    }

    protected BlobContainerClient AcquireBlobContainerClient(String containerName) throws BlobStorageException
    {
        BlobContainerClient containerClient = null;

        try {
            if ( blobServiceClient == null )
            {
                this.AcquireBlobServiceClient();
            }

            if ( blobServiceClient != null )
            {
                containerClient = blobServiceClient.createBlobContainer(containerName);
            }

        } catch (BlobStorageException ex) {
            // The container may already exist, so don't throw an error
            if (!ex.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                throw ex;
            }
            else
            {
                containerClient = blobServiceClient.getBlobContainerClient(containerName);
            }
        }
        return containerClient;
    }

    protected String GetContainer(MultivaluedMap<String, String> headers)
    {
        String container = null;
        if ( headers.containsKey(AZURE_CONTAINER) )
        {
            container = headers.getFirst(AZURE_CONTAINER);
        }
        return container;
    }

    protected String GetContainerDirectory(MultivaluedMap<String, String> headers)
    {
        String containerDirectory = null ;

        if ( headers.containsKey(AZURE_CONTAINER_DIRECTORY) )
        {
            containerDirectory = headers.getFirst(AZURE_CONTAINER_DIRECTORY);

            if ( headers.containsKey(AZURE_CONTAINER_DIRECTORY_BASE64ENCODED) ) {
                containerDirectory = new String(Base64.decodeBase64(
                        containerDirectory.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
            }
        }
        return containerDirectory;
    }

    protected void UploadImage(BlobContainerClient containerClient,String containerDirectory,
                               String imageName, byte[] data, Map<String, String> blobMetadata) {

        BlobClient blobClient = containerClient.getBlobClient(containerDirectory + "/" + imageName);

        BlobParallelUploadOptions options = new BlobParallelUploadOptions(new ByteArrayInputStream(data));
        options.setMetadata(blobMetadata);
        options.setParallelTransferOptions(parallelTransferOptions);
        options.setHeaders(sysproperties);
//            blobClient.uploadWithResponse(new ByteArrayInputStream(data), data.length, parallelTransferOptions,
//                    sysproperties, blobMetadata, null, null, null, null);
        blobClient.uploadWithResponse(options,null, null);

    }
}
