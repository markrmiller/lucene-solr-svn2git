package org.apache.lucene.expressions;

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.FieldCache.DoubleParser;
import org.apache.lucene.search.FieldCache.FloatParser;
import org.apache.lucene.search.FieldCache.IntParser;
import org.apache.lucene.search.FieldCache.LongParser;
import org.apache.lucene.search.SortField;

/**
 * Simple class that binds expression variable names to {@link SortField}s.
 *
 * @lucene.experimental
 */
public final class SimpleBindings extends Bindings {
  final Map<String,Object> map = new HashMap<String,Object>();
  
  /** Creates a new empty Bindings */
  public SimpleBindings() {}
  
  /** 
   * Adds a SortField to the bindings.
   * <p>
   * This can be used to reference a DocValuesField, a field from
   * FieldCache, the document's score, etc. 
   */
  public void add(SortField sortField) {
    map.put(sortField.getField(), sortField);
  }
  
  /** 
   * Adds an Expression to the bindings.
   * <p>
   * This can be used to reference expressions from other expressions. 
   */
  public void add(String name, Expression expression) {
    map.put(name, expression);
  }
  
  @Override
  public ValueSource getValueSource(String name) {
    Object o = map.get(name);
    if (o == null) {
      throw new IllegalArgumentException("Invalid reference '" + name + "'");
    } else if (o instanceof Expression) {
      return ((Expression)o).getValueSource(this);
    }
    SortField field = (SortField) o;
    switch(field.getType()) {
      case INT:
        return new IntFieldSource(field.getField(), (IntParser) field.getParser());
      case LONG:
        return new LongFieldSource(field.getField(), (LongParser) field.getParser());
      case FLOAT:
        return new FloatFieldSource(field.getField(), (FloatParser) field.getParser());
      case DOUBLE:
        return new DoubleFieldSource(field.getField(), (DoubleParser) field.getParser());
      case SCORE:
        return new ScoreValueSource();
      default:
        throw new UnsupportedOperationException(); 
    }
  }

  @Override
  public Iterator<String> iterator() {
    return map.keySet().iterator();
  }
}
