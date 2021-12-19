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

import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MultivaluedMap;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import org.apache.commons.codec.binary.Base64;

public class AbstractAzureResource {
    // AZURE
    protected static final String AZURE_STORAGE_CONNECTION_STRING = "AZURE_STORAGE_CONNECTION_STRING";

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
    protected static String connectStr = System.getenv(AZURE_STORAGE_CONNECTION_STRING);

    /* Create a new BlobServiceClient with a connection string */
    protected static BlobServiceClient blobServiceClient;

    protected void AcquireBlobServiceClient()
    {
        if (connectStr != null) {
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

}
