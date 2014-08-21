package org.apache.lucene.codecs.lucene42;

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

import org.apache.lucene.codecs.NormsConsumer;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Read-write version of {@link Lucene42NormsFormat}
 */
public class Lucene42RWNormsFormat extends Lucene42NormsFormat {

  @Override
  public NormsConsumer normsConsumer(SegmentWriteState state) throws IOException {
    if (LuceneTestCase.OLD_FORMAT_IMPERSONATION_IS_ACTIVE) {
      return new Lucene42NormsConsumer(state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION, acceptableOverheadRatio);
    } else {
      return super.normsConsumer(state);
    }
  }
}
