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

public class PDFStatistics {
    private int numberOfImages;
    private int numberOfGraphics;

    // Specific treatment of jb2 images
    private int numberOfJB2Images;

    protected int getNumberOfImages() {
        return numberOfImages;
    }

    protected int getNumberOfGraphics() {
        return numberOfGraphics;
    }

    protected int getNumberOfJB2Images() {
        return numberOfJB2Images;
    }

    protected void incrementImageCounter() {
        this.numberOfImages++;
    }

    protected void incrementImageCounter(int increment) {
        this.numberOfImages = this.numberOfImages + increment;
    }

    protected void incrementGraphicCounter() {
        this.numberOfGraphics++;
    }

    protected void incrementGraphicCounter(int increment) {
        this.numberOfGraphics = this.numberOfGraphics + increment;
    }

    protected void incrementJB2Counter() {
        this.numberOfJB2Images++;
    }

    protected void incrementJB2Counter(int increment) {
        this.numberOfJB2Images = this.numberOfJB2Images + increment;
    }
}

