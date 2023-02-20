<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
# Apache Tika Server

https://cwiki.apache.org/confluence/display/TIKA/TikaJAXRS

Building tika-server skipping tests
-----------------------------------

    mvn clean install -am -pl :tika-server-standard -DskipTests
    mvn clean install -am -DskipTests

Running
-------
```
$ java -jar tika-server/target/tika-server.jar --help
   usage: tikaserver
    -?,--help           this help message
    -h,--host <arg>     host name (default = localhost)
    -l,--log <arg>      request URI log level ('debug' or 'info')
    -p,--port <arg>     listen port (default = 9998)
    -s,--includeStack   whether or not to return a stack trace
                        if there is an exception during 'parse'
```

Running via Docker
------------------
Assuming you have Docker installed, you can use a prebuilt image:

`docker run -d -p 9998:9998 apache/tika`

This will load Apache Tika Server and expose its interface on:

`http://localhost:9998`

You may also be interested in the https://github.com/apache/tika-docker project
which provides prebuilt Docker images.

Installing as a Service on Linux
-----------------------
To run as a service on Linux you need to run the `install_tika_service.sh` script.

Assuming you have the binary distribution like `tika-server-2.0.0-bin.tgz`,
then you can extract the install script via:

`tar xzf tika-server-2.0.0-bin.tgz --strip-components=2 tika-server-2.0.0-bin/bin/install_tika_service.sh`

and then run the installation process via:

`./install_tika_service.sh  ./tika-server-2.0.0-bin.tgz`


Usage
-----
Usage examples from command line with `curl` utility:

* Extract plain text:  
`curl -T price.xls http://localhost:9998/tika`

* Extract text with mime-type hint:  
`curl -v -H "Content-type: application/vnd.openxmlformats-officedocument.wordprocessingml.document" -T document.docx http://localhost:9998/tika`

* Get all document attachments as ZIP-file:  
`curl -v -T Doc1_ole.doc http://localhost:9998/unpacker > /var/tmp/x.zip`

* Extract metadata to CSV format:  
`curl -T price.xls http://localhost:9998/meta`

* Detect media type from CSV format using file extension hint:  
`curl -X PUT -H "Content-Disposition: attachment; filename=foo.csv" --upload-file foo.csv http://localhost:9998/detect/stream`


HTTP Return Codes
-----------------
`200` - Ok  
`204` - No content (for example when we are unpacking file without attachments)  
`415` - Unknown file type  
`422` - Unparsable document of known type (password protected documents and unsupported versions like Biff5 Excel)  
`500` - Internal error  

# Custom Apache Tika Server

## Additional dependencies added in Tika Server Standard POM 

```xml
    <!-- Support for jpeg2000 and jbig2 images format -->
    <!-- https://pdfbox.apache.org/2.0/dependencies.html#optional-components -->
    <dependency>
      <groupId>com.github.jai-imageio</groupId>
      <artifactId>jai-imageio-core</artifactId>
      <version>1.4.0</version>
    </dependency>

    <dependency>
      <groupId>com.github.jai-imageio</groupId>
      <artifactId>jai-imageio-jpeg2000</artifactId>
      <version>1.4.0</version>
      <exclusions>
        <exclusion>
          <groupId>com.github.jai-imageio</groupId>
          <artifactId>jai-imageio-core</artifactId>
        </exclusion>
      </exclusions>
    </dependency>

    <!-- Optional for you ; just to avoid the same error with JBIG2 images -->
    <dependency>
      <groupId>org.apache.pdfbox</groupId>
      <artifactId>jbig2-imageio</artifactId>
      <version>3.0.3</version>
    </dependency>

    <!-- https://mvnrepository.com/artifact/org.apache.xmlgraphics/batik-all -->
    <dependency>
      <groupId>org.apache.xmlgraphics</groupId>
      <artifactId>batik-all</artifactId>
      <version>1.14</version>
      <type>pom</type>
      <exclusions>
        <exclusion>
          <groupId>xml-apis</groupId>
          <artifactId>xml-apis</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
```

## Azure Blob Storage Support

### Context

Our version of Tika Server includes support for Azure Blob Storage for unpacking resources from documents. 

Tika's unpack implementation loads all resources im memory to serve a zip/tar archive in response. Documents with a lot of embedded images like scanned-pdf you will hit OOM. To workaround this and support writing embedded resources directly in an Azure Blob storage, we added an azure/unpack endpoint i.e. **http://localhost:9998/azure/unpack**

On top of the azure/unpack, we added a convert endpoint for converting PDF pages and PowerPoint slides into images.  

### Azure dedicated endpoints

- **/azure/status** : validate the connectivity to Azure Blob storage.
- **/azure/unpack** : Tika unpack feature 
- **/azure/convert/(pdf|ppt|pptx)** : convert PDF pages or PowerPoint slides into images stored in Azure Blob storage.

### Tika Server Standard POM added dependencies

Azure Storage SDK for Java is added POM Dependency
```xml
    <!-- https://mvnrepository.com/artifact/com.azure/azure-storage-blob -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.20.0</version>
    <exclusions>
        <exclusion>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.projectreactor.netty</groupId>
            <artifactId>reactor-netty-http</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.fasterxml.woodstox</groupId>
            <artifactId>woodstox-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Azure Global Settings

#### Azure Blob Storage Connection - Environment Variable
Azure Blob connection string is taken from the environment variable **AZURE_STORAGE_CONNECTION_STRING**.

Some documentation to help your knowledge on Azure Storage & Java
- https://docs.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java
- https://docs.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java#configure-your-storage-connection-string

Once you have a connection string bound to the environment where your Tika server runs, we need to indicate the container and path where we want our resources to be unpacked.

#### Azure Blob Target Container - Header
To specify which container is used to write all embedded resources to, send the header **X-TIKA-AZURE-CONTAINER** along with your azure-unpack query.

#### Azure Blob Target Container Directory - Header
To specify which container directory to write all embedded resources to, send the header **X-TIKA-AZURE-CONTAINER-DIRECTORY** along with your azure-unpack query.

#### Azure Blob Metadata - Header
Our implementation supports adding blob metadata to each embedded resource. To your azure-unpack request, any header with the prefix  **X-TIKA-AZURE-META-** will end up in the user-defined blob properties.

Refer to the official documentation for limitations
https://docs.microsoft.com/en-us/azure/storage/blobs/storage-blob-properties-metadata?tabs=dotnet

### Azure Unpacker Usage : **/azure/unpack**
 
- Example 1

`curl -T PDF-01NCMKWBOVUAAXWBGMCVHLD72RR376GDAX.pdf http://localhost:9998/azure/unpack --header "X-Tika-PDFextractInlineImages:true" --header "X-TIKA-AZURE-CONTAINER:tika2" --header "X-TIKA-AZURE-CONTAINER-DIRECTORY:test2" --header "X-TIKA-AZURE-META-property1:test12" --header "X-TIKA-AZURE-META-property3:test34"`

The above command will extract embedded resources to the Azure Blob container **tika2** , in the **test2** directory.
Each extracted resource will have 2 user-defined metadata property1:test12 & property3:test34

- Example 2

`curl -T 01NCMKWBOY74FZ5GAOOJBLJZ2MAHDINEK7.pptx http://localhost:9998/azure/unpack --header "X-TIKA-AZURE-CONTAINER:tika" --header "X-TIKA-AZURE-META-property1:test12" --header "X-TIKA-AZURE-META-property3:test34"`

The above command will extract embedded resources to the Azure Blob container **tika** in the root directory.
Each extracted resource will have 2 user-defined metadata property1:test12 & property3:test34

#### Implementation

All extra resources are located in the server/resource/azure directory.

Refer to the resource class **AzureUnpackerResource.class** for the core unpacking code.

### Azure Convert Usage : **/azure/convert/(pdf|ppt|pptx)**

The convert endpoint supports converting PDF pages into images, PowerPoint PPT & PPTX slides into images. All output images are stored in Azure storage.

Supported extensions : pdf, ppt, pptx. 

- Converting PPT slides to images

```
curl -T 01NCMKWBLPI3LCGRPGIBBLK5VFDFP5FPDE.ppt http://localhost:9998/azure/convert/ppt --header "Accept: text/plain" --header "X-TIKA-AZURE-CONTAINER:images" --header "X-TIKA-AZURE-CONTAINER-DIRECTORY:01NCMKWBLPI3LCGRPGIBBLK5VFDFP5FPDE.ppt" --header "X-TIKA-AZURE-META-property1:test12" --header "X-TIKA-AZURE-META-property3:test34"
```

The above command will extract embedded resources to the Azure Blob container **images** , in the **01NCMKWBLPI3LCGRPGIBBLK5VFDFP5FPDE.ppt** directory. Each extracted resource will have 2 user-defined metadata property1:test12 & property3:test34.

- Converting PPTX slides to images

```
curl -T 01NCMKWBOY74FZ5GAOOJBLJZ2MAHDINEK7.pptx http://localhost:9998/azure/convert/pptx --header "Accept: text/plain" --header "X-TIKA-AZURE-CONTAINER:images" --header "X-TIKA-AZURE-CONTAINER-DIRECTORY:01NCMKWBOY74FZ5GAOOJBLJZ2MAHDINEK7.pptx" --header "X-TIKA-AZURE-META-property1:test12" --header "X-TIKA-AZURE-META-property3:test34"
```

The above command will extract embedded resources to the Azure Blob container **images** in the **01NCMKWBOY74FZ5GAOOJBLJZ2MAHDINEK7.pptx** directory.
Each extracted resource will have 2 user-defined metadata property1:test12 & property3:test34

- Converting PDF pages to images

```
curl -T ARK–Invest_BigIdeas_2021.pdf http://localhost:9998/azure/convert/pdf --header "X-TIKA-AZURE-CONTAINER:images" --header "X-TIKA-AZURE-CONTAINER-DIRECTORY:testpdf" --header "X-TIKA-AZURE-META-property1:test12" --header "X-TIKA-AZURE-META-property3:test34"
```

The above command will extract embedded resources to the Azure Blob container **images** in the **testpdf** directory.
Each extracted resource will have 2 user-defined metadata property1:test12 & property3:test34

#### Implementation

All extra resources are located in the server/resource/azure directory.

Refer to the resource class **AzureConverterResource.class** for the core conversion code.
