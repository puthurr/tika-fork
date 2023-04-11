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
package org.apache.tika.parser.config;

import java.io.Serializable;
import java.util.Locale;

public class BaseParserConfig implements Serializable {

    // Define how to name an embeddded resource
    public final String EMBEDDED_RESOURCE_NAMING_FORMAT = "%05d-%05d";

    // Define how to name an embeddded image
    public final String EMBEDDED_IMAGE_NAMING_FORMAT = "image-" + EMBEDDED_RESOURCE_NAMING_FORMAT;

    /**
     * @param sourceNumber
     * @param imageNumber
     * @return
     */
    public String getImageName(int sourceNumber,int imageNumber)
    {
        return String.format(Locale.ROOT, EMBEDDED_IMAGE_NAMING_FORMAT, sourceNumber, imageNumber) ;
    }

    /**
     * @param sourceNumber
     * @param imageNumber
     * @param extension
     * @return
     */
    public String getImageFilename(int sourceNumber,int imageNumber,String extension)
    {
        if (extension.startsWith("."))
        {
            return String.format(Locale.ROOT, EMBEDDED_IMAGE_NAMING_FORMAT, sourceNumber, imageNumber) + extension;
        }
        else
        {
            return String.format(Locale.ROOT, EMBEDDED_IMAGE_NAMING_FORMAT, sourceNumber, imageNumber) +
                    "." + extension;
        }
    }

    /**
     * @param prefix
     * @param sourceNumber
     * @param resourceNumber
     * @param extension
     * @return
     */
    public String getResourceFilename(String prefix, int sourceNumber,int resourceNumber,String extension)
    {
        if (extension.startsWith("."))
        {
            return prefix + "-" +
                    String.format(Locale.ROOT, EMBEDDED_RESOURCE_NAMING_FORMAT, sourceNumber, resourceNumber) +
                    extension;
        }
        else
        {
            return prefix + "-" +
                    String.format(Locale.ROOT, EMBEDDED_RESOURCE_NAMING_FORMAT, sourceNumber, resourceNumber) +
                    "." + extension;
        }
    }
}
