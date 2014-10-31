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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.index.BaseFieldInfoFormatTestCase;
import org.apache.lucene.index.DocValuesType;

/** Test Lucene 4.2 FieldInfos Format */
public class TestLucene42FieldInfoFormat extends BaseFieldInfoFormatTestCase {
  private final Codec codec = new Lucene42RWCodec();

  @Override
  protected Codec getCodec() {
    return codec;
  }

  // we only support these four dv types
  @Override
  @Deprecated
  protected DocValuesType[] getDocValuesTypes() {
    return new DocValuesType[] {
      DocValuesType.BINARY,
      DocValuesType.NUMERIC,
      DocValuesType.SORTED,
      DocValuesType.SORTED_SET
    };
  }
}
