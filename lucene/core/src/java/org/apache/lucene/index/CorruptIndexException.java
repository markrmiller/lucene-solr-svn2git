package org.apache.lucene.index;

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

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.store.DataInput;

/**
 * This exception is thrown when Lucene detects
 * an inconsistency in the index.
 */
public class CorruptIndexException extends IOException {
  /** Create exception with a message only */
  public CorruptIndexException(String message, DataInput input) {
    this(message, input, null);
  }
  
  /** Create exception with message and root cause. */
  public CorruptIndexException(String message, DataInput input, Throwable cause) {
    this(message, Objects.toString(input), cause);
  }
  
  /** Create exception with a message only */
  public CorruptIndexException(String message, String resourceDescription) {
    this(message, resourceDescription, null);
  }
  
  /** Create exception with message and root cause. */
  public CorruptIndexException(String message, String resourceDescription, Throwable cause) {
    super(message + " (resource=" + resourceDescription + ")", cause);
  }
}
