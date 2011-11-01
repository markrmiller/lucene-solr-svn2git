package org.apache.lucene.index.codecs.spi;

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

import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.codecs.PostingsFormat;

import org.apache.lucene.index.codecs.lucene3x.Lucene3xCodec;
import org.apache.lucene.index.codecs.lucene40.Lucene40Codec;

import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsBaseFormat;
import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsFormat;
import org.apache.lucene.index.codecs.memory.MemoryPostingsFormat;
import org.apache.lucene.index.codecs.pulsing.PulsingPostingsFormat;
import org.apache.lucene.index.codecs.simpletext.SimpleTextPostingsFormat;

public final class CoreCodecProvider extends SimpleCodecProvider {

  public CoreCodecProvider() {
    super(
      Arrays.<Codec>asList(
        new Lucene40Codec(), 
        new Lucene3xCodec()
      ),
      Arrays.<PostingsFormat>asList(
        new Lucene40PostingsFormat(),
        new PulsingPostingsFormat(new Lucene40PostingsBaseFormat(), 1),
        new SimpleTextPostingsFormat(),
        new MemoryPostingsFormat()
      )
    );
  }

}
