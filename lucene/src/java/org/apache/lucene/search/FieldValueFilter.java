package org.apache.lucene.search;

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
import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldCacheRangeFilter.FieldCacheDocIdSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Bits.MatchAllBits;
import org.apache.lucene.util.Bits.MatchNoBits;

/**
 * A {@link Filter} that accepts all documents that have one or more values in a
 * given field. This {@link Filter} request {@link Bits} from the
 * {@link FieldCache} and build the bits if not present.
 */
public class FieldValueFilter extends Filter {
  private final String field;
  private final boolean negate;

  /**
   * Creates a new {@link FieldValueFilter}
   * 
   * @param field
   *          the field to filter
   */
  public FieldValueFilter(String field) {
    this(field, false);
  }

  /**
   * Creates a new {@link FieldValueFilter}
   * 
   * @param field
   *          the field to filter
   * @param negate
   *          iff <code>true</code> all documents with no value in the given
   *          field are accepted.
   * 
   */
  public FieldValueFilter(String field, boolean negate) {
    this.field = field;
    this.negate = negate;
  }

  @Override
  public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
    final Bits docsWithField = FieldCache.DEFAULT.getDocsWithField(
        reader, field);
    if (negate) {
      if (docsWithField instanceof MatchAllBits) {
        return null;
      }
      final int maxDoc = reader.maxDoc();
      return new FieldCacheDocIdSet(reader, true) {
        @Override
        final boolean matchDoc(int doc) {
          if (doc >= maxDoc) {
            // TODO: this makes no sense we should check this on the caller level
            throw new ArrayIndexOutOfBoundsException("doc: "+doc + " maxDoc: " + maxDoc);
          }
          return !docsWithField.get(doc);
        }
      };
    } else {
      if (docsWithField instanceof MatchNoBits) {
        return null;
      }
      if (docsWithField instanceof DocIdSet) {
        // UweSays: this is always the case for our current impl - but who knows
        // :-)
        /*
         *  TODO this could deliver delete docs but FCDID seems broken too if
         *  this filter is not used with another query
         */
        return (DocIdSet) docsWithField;
      }
      final int maxDoc = reader.maxDoc();
      return new FieldCacheDocIdSet(reader, true) {
        @Override
        final boolean matchDoc(int doc) {
          if (doc >= maxDoc) {
            // TODO: this makes no sense we should check this on the caller level
            throw new ArrayIndexOutOfBoundsException("doc: "+doc + " maxDoc: " + maxDoc);
          }
          return docsWithField.get(doc);
        }
      };
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + (negate ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FieldValueFilter other = (FieldValueFilter) obj;
    if (field == null) {
      if (other.field != null)
        return false;
    } else if (!field.equals(other.field))
      return false;
    if (negate != other.negate)
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "NoFieldValueFilter [field=" + field + ", negate=" + negate + "]";
  }

}
