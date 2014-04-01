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

package org.apache.solr.schema;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.SortField;
import org.apache.solr.search.QParser;
import org.apache.lucene.index.GeneralField;
import org.apache.lucene.index.StorableField;
import org.apache.solr.util.BCDUtils;
import org.apache.solr.response.TextResponseWriter;

import java.io.IOException;
/**
 *
 */
public class BCDIntField extends PrimitiveFieldType {
  @Override
  public SortField getSortField(SchemaField field,boolean reverse) {
    return getStringSort(field,reverse);
  }

  @Override
  public ValueSource getValueSource(SchemaField field, QParser qparser) {
    throw new UnsupportedOperationException("ValueSource not implemented");
  }

  @Override
  public String toInternal(String val) {
    // TODO? make sure each character is a digit?
    return BCDUtils.base10toBase10kSortableInt(val);
  }

  @Override
  public String toExternal(StorableField f) {
    return indexedToReadable(f.stringValue());
  }
  
  // Note, this can't return type 'Integer' because BCDStrField and BCDLong extend it
  @Override
  public Object toObject(StorableField f) {
    return Integer.valueOf( toExternal(f) );
  }

  @Override
  public String indexedToReadable(String indexedForm) {
    return BCDUtils.base10kSortableIntToBase10(indexedForm);
  }

  @Override
  public void write(TextResponseWriter writer, String name, StorableField f) throws IOException {
    writer.writeInt(name,toExternal(f));
  }

  @Override
  public Object marshalSortValue(Object value) {
    return marshalStringSortValue(value);
  }

  @Override
  public Object unmarshalSortValue(Object value) {
    return unmarshalStringSortValue(value);
  }
}





