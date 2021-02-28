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
<<<<<<<< HEAD:tika-core/src/main/java/org/apache/tika/pipes/async/AsyncRuntimeException.java
package org.apache.tika.pipes.async;

/**
 * Fatal exception that means that something went seriously wrong.
 */
public class AsyncRuntimeException extends RuntimeException {
========
package org.apache.tika.transcribe;

import org.junit.Before;
>>>>>>>> 17f2d104d (fix for TIKA-94 contributed by phantuanminh: Rename package (Fix typo). Add simple test and test files):tika-transcribe/src/test/java/org/apache/tika/transcribe/AmazonTranscribeTest.java

    public AsyncRuntimeException(Throwable t) {
        super(t);
    }
}
