package org.apache.lucene.codecs;

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

import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

/**
 * Specifies an API for classes that can write out {@link SegmentInfo} data.
 * @lucene.experimental
 */

public abstract class SegmentInfoWriter {
  /** Sole constructor. (For invocation by subclass 
   *  constructors, typically implicit.) */
  protected SegmentInfoWriter() {
  }

  /**
   * Write {@link SegmentInfo} data. 
   * @throws IOException If an I/O error occurs
   */
  public abstract void write(Directory dir, SegmentInfo info, IOContext ioContext) throws IOException;
}
