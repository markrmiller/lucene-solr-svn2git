package org.apache.lucene.codecs.compressing;

import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.lucene42.Lucene42DocValuesFormat;
import org.apache.lucene.codecs.lucene42.Lucene42NormsFormat;
import org.apache.lucene.util.packed.PackedInts;

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

/** CompressionCodec that uses {@link CompressionMode#FAST} */
public class FastCompressingCodec extends CompressingCodec {

  /** Constructor that allows to configure the chunk size. */
  public FastCompressingCodec(int chunkSize, boolean withSegmentSuffix) {
    super("FastCompressingStoredFields", 
          withSegmentSuffix ? "FastCompressingStoredFields" : "",
          CompressionMode.FAST, chunkSize);
  }

  /** Default constructor. */
  public FastCompressingCodec() {
    this(1 << 14, false);
  }

  @Override
  public NormsFormat normsFormat() {
    return new Lucene42NormsFormat(PackedInts.FAST);
  }

  @Override
  public DocValuesFormat docValuesFormat() {
    return new Lucene42DocValuesFormat(PackedInts.FAST);
  }
}
