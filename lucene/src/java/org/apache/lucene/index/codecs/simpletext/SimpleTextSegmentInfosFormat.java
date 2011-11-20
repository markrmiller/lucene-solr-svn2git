package org.apache.lucene.index.codecs.simpletext;

/**
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

import org.apache.lucene.index.codecs.SegmentInfosFormat;
import org.apache.lucene.index.codecs.SegmentInfosReader;
import org.apache.lucene.index.codecs.SegmentInfosWriter;

/**
 * plain text segments file format.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SimpleTextSegmentInfosFormat extends SegmentInfosFormat {
  private final SegmentInfosReader reader = new SimpleTextSegmentInfosReader();
  private final SegmentInfosWriter writer = new SimpleTextSegmentInfosWriter();
  
  @Override
  public SegmentInfosReader getSegmentInfosReader() {
    return reader;
  }

  @Override
  public SegmentInfosWriter getSegmentInfosWriter() {
    return writer;
  }
}
