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

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.SortField;

/**
 * A {@link ValueSource} which evaluates a {@link Expression} given the context of an {@link Bindings}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class ExpressionValueSource extends ValueSource {
  private final Bindings bindings;
  final Expression expression;

  public ExpressionValueSource(Bindings bindings, Expression expression) {
    if (bindings == null) throw new NullPointerException();
    if (expression == null) throw new NullPointerException();
    this.bindings = bindings;
    this.expression = expression;
  }

  /** <code>context</code> must contain a key <code>"valuesCache"</code> which is a <code>Map&lt;String,FunctionValues&gt;</code>. */
  @Override
  public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
    ValueSource source;
    Map<String, FunctionValues> valuesCache = (Map<String, FunctionValues>)context.get("valuesCache");
    if (valuesCache == null) {
      throw new NullPointerException();
    }
    FunctionValues[] externalValues = new FunctionValues[expression.externals.length];

    for (int i = 0; i < expression.externals.length; ++i) {
      String externalName = expression.externals[i];
      FunctionValues values = valuesCache.get(externalName);
      if (values == null) {
        source = bindings.getValueSource(externalName);
        values = source.getValues(context, readerContext);
        if (values == null) {
          throw new RuntimeException("Internal error. External (" + externalName + ") does not exist.");
        }
        valuesCache.put(externalName, values);
      }
      externalValues[i] = values;
    }

    return new ExpressionFunctionValues(expression, externalValues);
  }

  @Override
  public SortField getSortField(boolean reverse) {
    return new ExpressionSortField(expression.expression, this, reverse);
  }

  @Override
  public String description() {
    return "ExpressionValueSource(" + expression.expression + ")";
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
  
  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }
}
