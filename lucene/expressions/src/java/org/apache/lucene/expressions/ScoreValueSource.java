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

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;

import java.io.IOException;
import java.util.Map;

/**
 * A {@link ValueSource} which uses the {@link ScoreFunctionValues} passed through
 * the context map by {@link ExpressionComparator}.
 */
@SuppressWarnings({"rawtypes"})
class ScoreValueSource extends ValueSource {

  /**
   * <code>context</code> must contain a key "scorer" which is a {@link FunctionValues}.
   */
  @Override
  public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
    FunctionValues v = (FunctionValues) context.get("scorer");
    if (v == null) {
      throw new NullPointerException();
    }
    return v;
  }

  @Override
  public boolean equals(Object o) {
    return o == this;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String description() {
    return "ValueSource to expose scorer passed by ExpressionComparator";
  }
}
