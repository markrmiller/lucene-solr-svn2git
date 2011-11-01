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

import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.codecs.PostingsFormat;

import org.apache.lucene.index.codecs.mockintblock.MockFixedIntBlockPostingsFormat;
import org.apache.lucene.index.codecs.mockintblock.MockVariableIntBlockPostingsFormat;
import org.apache.lucene.index.codecs.mockrandom.MockRandomPostingsFormat;
import org.apache.lucene.index.codecs.mocksep.MockSepPostingsFormat;
import org.apache.lucene.index.codecs.preflexrw.PreFlexRWCodec;

public final class _TestCodecProvider extends SimpleCodecProvider {

  public _TestCodecProvider() {
    super(
      Arrays.<Codec>asList(
        new PreFlexRWCodec()
      ),
      Arrays.<PostingsFormat>asList(
        new MockRandomPostingsFormat(),
        new MockFixedIntBlockPostingsFormat(1),
        new MockVariableIntBlockPostingsFormat(1),
        new MockSepPostingsFormat()
      )
    );
  }

}
