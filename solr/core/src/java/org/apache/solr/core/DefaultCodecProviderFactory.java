package org.apache.solr.core;

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

import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.codecs.CodecProvider;
import org.apache.lucene.index.codecs.CoreCodecProvider;
import org.apache.lucene.index.codecs.lucene40.Lucene40Codec;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;

/**
 * Default CodecProviderFactory implementation, extends Lucene's 
 * and returns postings format implementations according to the 
 * schema configuration.
 * @lucene.experimental
 */
public class DefaultCodecProviderFactory extends CodecProviderFactory {

  @Override
  public CodecProvider create(final IndexSchema schema) {
    return new CoreCodecProvider() {
      @Override
      public Codec getDefaultCodec() {
        return new Lucene40Codec() {
          @Override
          public String getPostingsFormatForField(String field) {
            final SchemaField fieldOrNull = schema.getFieldOrNull(field);
            if (fieldOrNull == null) {
              throw new IllegalArgumentException("no such field " + field);
            }
            String postingsFormatName = fieldOrNull.getType().getPostingsFormat();
            if (postingsFormatName != null) {
              return postingsFormatName;
            }
            return super.getPostingsFormatForField(field);
          }
        };
      }
    };
  }
}
