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

package org.apache.solr.search.function;

import org.apache.lucene.common.mutable.MutableValue;
import org.apache.lucene.common.mutable.MutableValueLong;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.queries.function.DocValues;
import org.apache.lucene.queries.function.ValueSourceScorer;
import org.apache.lucene.util.Bits;
import org.apache.lucene.search.cache.LongValuesCreator;
import org.apache.lucene.search.cache.CachedArray.LongValues;


import java.io.IOException;
import java.util.Map;

/**
 * Obtains float field values from the {@link org.apache.lucene.search.FieldCache}
 * using <code>getFloats()</code>
 * and makes those values available as other numeric types, casting as needed.
 *
 *
 */

public class LongFieldSource extends NumericFieldCacheSource<LongValues> {

  public LongFieldSource(LongValuesCreator creator) {
    super(creator);
  }

  @Override
  public String description() {
    return "long(" + field + ')';
  }

  public long externalToLong(String extVal) {
    return Long.parseLong(extVal);
  }

  public Object longToObject(long val) {
    return val;
  }

  @Override
  public DocValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
    final LongValues vals = cache.getLongs(readerContext.reader, field, creator);
    final long[] arr = vals.values;
    final Bits valid = vals.valid;
    
    return new LongDocValues(this) {
      @Override
      public long longVal(int doc) {
        return arr[doc];
      }

      @Override
      public boolean exists(int doc) {
        return valid.get(doc);
      }

      @Override
      public Object objectVal(int doc) {
        return valid.get(doc) ? longToObject(arr[doc]) : null;
      }

      @Override
      public ValueSourceScorer getRangeScorer(IndexReader reader, String lowerVal, String upperVal, boolean includeLower, boolean includeUpper) {
        long lower,upper;

        // instead of using separate comparison functions, adjust the endpoints.

        if (lowerVal==null) {
          lower = Long.MIN_VALUE;
        } else {
          lower = externalToLong(lowerVal);
          if (!includeLower && lower < Long.MAX_VALUE) lower++;
        }

         if (upperVal==null) {
          upper = Long.MAX_VALUE;
        } else {
          upper = externalToLong(upperVal);
          if (!includeUpper && upper > Long.MIN_VALUE) upper--;
        }

        final long ll = lower;
        final long uu = upper;

        return new ValueSourceScorer(reader, this) {
          @Override
          public boolean matchesValue(int doc) {
            long val = arr[doc];
            // only check for deleted if it's the default value
            // if (val==0 && reader.isDeleted(doc)) return false;
            return val >= ll && val <= uu;
          }
        };
      }

      @Override
      public ValueFiller getValueFiller() {
        return new ValueFiller() {
          private final long[] longArr = arr;
          private final MutableValueLong mval = newMutableValueLong();

          @Override
          public MutableValue getValue() {
            return mval;
          }

          @Override
          public void fillValue(int doc) {
            mval.value = longArr[doc];
            mval.exists = valid.get(doc);
          }
        };
      }

    };
  }

  protected MutableValueLong newMutableValueLong() {
    return new MutableValueLong();  
  }

}
