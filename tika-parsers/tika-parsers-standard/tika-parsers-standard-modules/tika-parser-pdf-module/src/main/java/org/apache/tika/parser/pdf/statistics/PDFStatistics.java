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
package org.apache.tika.parser.pdf.statistics;

public class PDFStatistics {
    private int numberOfImages;
    private int numberOfGraphics;

    // Specific treatment of jb2 images
    private int numberOfJB2Images;

    public int getNumberOfImages() {
        return numberOfImages;
    }

    public int getNumberOfGraphics() {
        return numberOfGraphics;
    }

    public int getNumberOfJB2Images() {
        return numberOfJB2Images;
    }

    public void incrementImageCounter() {
        this.numberOfImages++;
    }

    public void incrementImageCounter(int increment) {
        this.numberOfImages = this.numberOfImages + increment;
    }

    public void incrementGraphicCounter() {
        this.numberOfGraphics++;
    }

    public void incrementGraphicCounter(int increment) {
        this.numberOfGraphics = this.numberOfGraphics + increment;
    }

    public void incrementJB2Counter() {
        this.numberOfJB2Images++;
    }

    public void incrementJB2Counter(int increment) {
        this.numberOfJB2Images = this.numberOfJB2Images + increment;
    }
}

