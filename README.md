# Fork of Apache Tika project

Apache Tika(TM) is a toolkit for detecting and extracting metadata and structured text content from various documents using existing parser libraries.

Apache Tika, Tika, Apache, the Apache feather logo, and the Apache Tika project logo are trademarks of The Apache Software Foundation.

# Why this version ?

For a Knowledge mining project, my team were looking to have a consistent representation of embedded images in XHTML output.
To give you an example, the embedded images links for PowerPoint were missing while the images links for PDF were there.

This version is trying to harmonize the way embedded images are showing up in the XHTML in a nutshell.

Once stabilized our plan is to propose our changes to the Apache Tika community.

Main contact : [puthurr@gmail.com](mailto:puthurr@gmail.com)

This project contains all Tika projects modules.

## Tika Modules for building this version

- tika-core
- tika-eval
- tika-langdetect
- tika-parent
- tika-parsers
- tika-serialization
- tika-server
- tika-translate
- tika-xmp

# Tika Parsers

## Embedded Resources Naming consistency for Office and PDF

Extracting the embedded images of any document is a great feature. We implement a consistent images numbering format to identify quickly which page or slide a specific was referenced.

Format **image-(source)-(absolute image number).extension**

```java
    // Define how to name an embeddded resource
public final String EMBEDDED_RESOURCE_NAMING_FORMAT = "%05d-%05d";

// Define how to name an embeddded image
public final String EMBEDDED_IMAGE_NAMING_FORMAT = "image-"+EMBEDDED_RESOURCE_NAMING_FORMAT;
```

The final resource name for images would be

- image-00001-00001.png => first image of the document located on page/slide 1
- image-00004-00006.png => sixth image of the document located on page/slide 4

## XHTML Tags

- PDF Page tags contains the page id.
  ```<div class="page" id="2">```
- PPTX : added a slide div with slide id and title (when available)
  ```<div class="slide" id="1">```
  ```<div class="slide" id="2" title="Oil Production">```
- PPT/PPTX slide-notes div renamed to slide-notes-content for consistency
  ```<div class="slide-notes-content">```

## Embedded Images XHTML tags

Embedded representation in the XHTML for Office and PDF documents was diverse.
Images are now represented with an image tag containing extra information like the size or type.
The fact to have images in W3C img tag allow us to work towards a standardized document preview.

**For PDF**
- Original
```html
<img src="embedded:image0.jpg" alt="image0.jpg"/>
```
- Our version
```html
<img src="image-00001-00001.jpg" alt="image-00001-00001.jpg" class="embedded" id="image-00000-00001" contenttype="image/jpeg" size="775380" witdh="1491" height="2109"/>
```
**For PowerPoint**
- Original
```html
<div class="embedded" id="slide6_rId3" />
```
- Our version
```html
<img class="embedded" id="slide6_rId3" contenttype="image/jpeg" src="image-00006-00006.jpeg" alt="image6.jpeg" title="Picture 58" witdh="271" height="280" size="26789"/>
```

Some img attributes aren't HTML compliant we know. This above output is close to provide an HTML preview of any document.

**Benefits**: we can scan big images, specific type of images, size in bytes or dimensions.

## PDF Parser new configuration(s)

The new PDF parser configuration are all related to Image extraction thus they will take effects on calling the unpack endpoint.
It means they will also requires the **extractInlineImages** option to be set to **true** as well.

The below options goal is to validate if a PDF page is better off rendered as an image or not. The benefit of rendering a page as image is to:

1. Workaround fragmented/striped images in PDF.
2. Capture graphical elements.
3. Reduce the effect of the various scanning techniques.

#### New options

- **allPagesAsImages** : this instructs to convert any PDF page as image.
- **singlePagePDFAsImage** : this instructs to convert a single page PDF to an image.
- **stripedImagesHandling** : this instructs to convert a PDF page into an image based on the number of Contents Streams or images in a page. Some PDF writers tend to stripe an image into multiple contents streams (Array)
- **stripedImagesThreshold** : minimum number of contents streams to convert the page into an image. Default is 5 content streams or images.
- **graphicsToImage** : a page with graphics objets could be better represented with an image.
- **graphicsToImageThreshold** : minimum number of graphics objects to convert the page into an image.
- **jB2Images** : flag to convert any PDF page into image given that there is a minimum of one JB2 image in that page.
- **jB2ImagesThreshold** : minimum number of JB2 images found in a page to convert it to an image. Default is 1.

To leverage those features add the corresponding headers prefixed by **X-Tika-PDF**.

```--header "X-Tika-PDFextractInlineImages:true" --header "X-Tika-PDFsinglePagePDFAsImage:true"```

An image originating from the above processing options i.e. singlePagePDFAsImage, the resulting image name will hold a -99999 suffix

```image-<page/slide number>-99999.png```

#### Changing the image resulting format

The extension of the resulting is taken from OcrImageFormatName which default to png. To change the extension
```--header "X-Tika-PDFOcrImageFormatName:jpg"```

### Office Parser new configuration(s)

- **IncludeSlideShowEmbeddedResources** : for PPT Office documents, by default, images are extracted at the slideshow level. Setting this flag to false extracts the images at the slide level.

## Tika Server

All are specific changes are bundled in the **tika-server-puthurr** module.

### Additional libraries
The tika server includes PDFBOX [additional components](https://pdfbox.apache.org/2.0/dependencies.html#optional-components). See tika-server pom.xml for actual dependencies.

#### Azure Blob Storage support in tika-server unpack 
The unpack feature produces an archive response which you can expand and process.
For documents containing a lot of high-res images, the **unpack** will hit some limitations like OOM.
To avoid hitting those potential limitations, support cloud storage and big-size documents, I implemented an azure-unpack resource to write any embedded resource directly into an Azure Storage container and directory.

**Benefits** : no archive client expansion, network bandwidth reduced, handles documents with a lot of high-res images and more.

#### Azure Blob Storage support in tika-server new conversion endpoints 
Our version created a **converter** endpoint to convert 
- all PDF as images storing them in Azure blob storage
- all PPT/PPTX slides as images storing them in Azure blob storage

See [tika-server](/tika-server) for more details on how to use that feature.

TIKA: Repository Structure
---------------------------

- branch_1x => latest GA release 1.27 - This branch is not holding the mentioned enhancements. 
- main => 2.x code based. Has all the mentioned enhancements.

Apache Tika  <https://tika.apache.org/>
=======================================

[![license](https://img.shields.io/github/license/apache/tika.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0)
[![Jenkins](https://img.shields.io/jenkins/s/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk8.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk8/)
[![Jenkins tests](https://img.shields.io/jenkins/t/https/ci-builds.apache.org/job/Tika/job/tika-main-jdk8.svg?maxAge=3600)](https://ci-builds.apache.org/job/Tika/job/tika-main-jdk8/lastBuild/testReport/)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.tika/tika.svg?maxAge=86400)](http://search.maven.org/#search|ga|1|g%3A%22org.apache.tika%22)

Apache Tika(TM) is a toolkit for detecting and extracting metadata and structured text content from various documents using existing parser libraries.

Tika is a project of the [Apache Software Foundation](https://www.apache.org).

Apache Tika, Tika, Apache, the Apache feather logo, and the Apache Tika project logo are trademarks of The Apache Software Foundation.

Getting Started
===============
Pre-built binaries of Apache Tika standalone applications are available
from https://tika.apache.org/download.html . Pre-built binaries of all the
Tika jars can be fetched from Maven Central or your favourite Maven mirror.

**Tika 1.X reached End of Life (EOL) on September 30, 2022.**  

Tika is based on **Java 8** and uses the [Maven 3](https://maven.apache.org) build system. 
**N.B.** [Docker](https://www.docker.com/products/personal) is used for tests in tika-integration-tests.
As of Tika 2.5.1, if Docker is not installed, those tests are skipped.  Docker is required for a successful
build on earlier 2.x versions.

To build Tika from source, use the following command in the main directory:

    mvn clean install


The build consists of a number of components, including a standalone runnable jar that you can use to try out Tika features. You can run it like this:

    java -jar tika-app/target/tika-app-*.jar --help


To build a specific project (for example, tika-server-standard):

    mvn clean install -am -pl :tika-server-standard

If the ossindex-maven-plugin is causing the build to fail because a dependency
has now been discovered to have a vulnerability:

    mvn clean install -Dossindex.skip


Maven Dependencies
==================

Apache Tika provides *Bill of Material* (BOM) artifact to align Tika module versions and simplify version management. 
To avoid convergence errors in your own project, import this
bom or Tika's parent pom.xml in your dependencey management section.

If you use Apache Maven:

```xml
<project>
  <dependencyManagement>
    <dependencies>
      <dependency>
       <groupId>org.apache.tika</groupId>
       <artifactId>tika-bom</artifactId>
       <version>2.x.y</version>
       <type>pom</type>
       <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-parsers-standard-package</artifactId>
      <!-- version not required since BOM included -->
    </dependency>
  </dependencies>
</project>
```

For Gradle:

```kotlin
dependencies {
  implementation(platform("org.apache.tika:tika-bom:2.x.y"))

  // version not required since bom (platform in Gradle terms)
  implementation("org.apache.tika:tika-parsers-standard-package")
}
```

Migrating to 2.x
================
The initial 2.x release notes are available in the [archives](https://archive.apache.org/dist/tika/2.0.0/CHANGES-2.0.0.txt).

See our [wiki](https://cwiki.apache.org/confluence/display/TIKA/Migrating+to+Tika+2.0.0) for the latest.

Contributing via Github
=======================
See the [pull request template](https://github.com/apache/tika/blob/main/.github/pull_request_template.md).

## Thanks to all the people who have contributed

[![contributors](https://contributors-img.web.app/image?repo=apache/tika)](https://github.com/apache/tika/graphs/contributors)

Building from a Specific Tag
============================
Let's assume that you want to build the 2.5.0 tag:
```
0. Download and install hub.github.com
1. git clone https://github.com/apache/tika.git 
2. cd tika
3. git checkout 2.5.0
4. mvn clean install
```

If a new vulnerability has been discovered between the date of the 
tag and the date you are building the tag, you may need to build with:

```
4. mvn clean install -Dossindex.skip
```

If a local test is not working in your environment, please notify
 the project at dev@tika.apache.org. As an immediate workaround, 
 you can turn off individual tests with e.g.: 

```
4. mvn clean install -Dossindex.skip -Dtest=\!UnpackerResourceTest#testPDFImages
```

License (see also LICENSE.txt)
==============================

Collective work: Copyright 2011 The Apache Software Foundation.

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.

Apache Tika includes a number of subcomponents with separate copyright notices and license terms. Your use of these subcomponents is subject to the terms and conditions of the licenses listed in the LICENSE.txt file.

Export Control
==============

This distribution includes cryptographic software.  The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.  BEFORE using any encryption software, please  check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to  see if this is permitted.  See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.  The form and manner of this Apache Software Foundation distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

Apache Tika uses the Bouncy Castle generic encryption libraries for extracting text content and metadata from encrypted PDF files.  See <http://www.bouncycastle.org/> for more details on Bouncy Castle.  

Mailing Lists
=============

Discussion about Tika takes place on the following mailing lists:

* user@tika.apache.org    - About using Tika
* dev@tika.apache.org     - About developing Tika

Notification on all code changes are sent to the following mailing list:

* commits@tika.apache.org

The mailing lists are open to anyone and publicly archived.

You can subscribe the mailing lists by sending a message to 
[LIST]-subscribe@tika.apache.org (for example user-subscribe@...).  
To unsubscribe, send a message to [LIST]-unsubscribe@tika.apache.org.  
For more instructions, send a message to [LIST]-help@tika.apache.org.

Issue Tracker
=============

If you encounter errors in Tika or want to suggest an improvement or a new feature,
 please visit the [Tika issue tracker](https://issues.apache.org/jira/browse/TIKA). 
 There you can also find the latest information on known issues and 
 recent bug fixes and enhancements.

Build Issues
============

*TODO*

* Need to install jce

* If you find any other issues while building, please email the dev@tika.apache.org
  list.
