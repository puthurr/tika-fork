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

import java.io.InputStream;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.server.core.HTMLHelper;
import org.apache.tika.server.core.resource.TikaServerResource;

@Path("/azure/status")
public class AzureStatusResource extends AbstractAzureResource implements TikaServerResource {

    private static final Logger LOG = LoggerFactory.getLogger(AzureStatusResource.class);

    private final HTMLHelper html = new HTMLHelper();

    @GET
    @Produces({"text/html"})
    public String status(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception
    {
        LOG.info("Apache Azure Status");

        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Apache Azure Status");

        h.append("<div>");
        try
        {
            h.append("<h2>Headers</h2>");
            h.append("<ul>");
            // Get the headers
            MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();

            String containerName = this.GetContainer(headers);
            if ( containerName != null )
            {
                h.append("<li>Container : " + containerName + "</li>");
            }
            else {
                h.append("<li>No container found in headers.</li>");
            }

            String containerDirectory = this.GetContainerDirectory(headers);
            if ( containerDirectory != null )
            {
                h.append("<li>Container Directory : " + containerDirectory + "</li>");
            }
            else {
                h.append("<li>No container directory found in headers.</li>");
            }

            h.append("</ul>");

            this.AcquireBlobServiceClient();

            if ( this.blobServiceClient == null )
            {
                h.append("<p>");
                h.append("<strong>Blob Service Client is null...</strong>");
                h.append(connectStr);
                h.append("</p>");
            }
            else
            {
                h.append("<p>Account Url <strong>" + this.blobServiceClient.getAccountUrl() + "</strong></p>");
                h.append("<p>Account Name <strong>" + this.blobServiceClient.getAccountName() + "</strong></p>");
            }

            /* Create a new container client */
            BlobContainerClient containerClient = null;

            try
            {
                containerClient = this.AcquireBlobContainerClient(containerName);
                h.append("<p> Successfully Acquired Container Client " + containerClient.getBlobContainerName());
                h.append("</p>");

            } catch (BlobStorageException ex) {
                h.append("<p>");
                h.append(ex.getMessage());
                h.append("</p>");
            }
        }
        catch (Exception e)
        {
            h.append("<p>");
            h.append(e.getMessage());
            h.append("</p>");
        }
        h.append("</div>");

        h.append("<div>");
        h.append("<h2>Environment Variables</h2>");
        h.append("<ul>");
        Map<String, String> env = System.getenv();
        for (String envName : env.keySet())
        {
            if (envName.contains(AZURE_STORAGE_CONNECTION_STRING) || envName.contains("KEY"))
            {
                h.append("<li>" + envName + " : secured </li>");
            }
            else
            {
                h.append("<li>" + envName + " : " + env.get(envName) + "</li>");
            }
        }
        h.append("</ul>");
        h.append("</div>");


        html.generateFooter(h);
        return h.toString();
    }
}
