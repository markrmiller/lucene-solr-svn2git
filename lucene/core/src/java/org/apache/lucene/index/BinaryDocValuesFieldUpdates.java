package org.apache.lucene.index;

import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.index.DocValuesUpdate.BinaryDocValuesUpdate;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.InPlaceMergeSorter;
import org.apache.lucene.util.packed.PackedInts;
import org.apache.lucene.util.packed.PagedGrowableWriter;
import org.apache.lucene.util.packed.PagedMutable;

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

/**
 * A {@link DocValuesFieldUpdates} which holds updates of documents, of a single
 * {@link BinaryDocValuesField}.
 * 
 * @lucene.experimental
 */
class BinaryDocValuesFieldUpdates extends DocValuesFieldUpdates {
  
  final static class Iterator extends DocValuesFieldUpdates.Iterator {
    private final PagedGrowableWriter offsets;
    private final int size;
    private final PagedGrowableWriter lengths;
    private final PagedMutable docs;
    private final FixedBitSet docsWithField;
    private long idx = 0; // long so we don't overflow if size == Integer.MAX_VALUE
    private int doc = -1;
    private final BytesRef value;
    private int offset, length;
    
    Iterator(int size, PagedGrowableWriter offsets, PagedGrowableWriter lengths, 
        PagedMutable docs, BytesRef values, FixedBitSet docsWithField) {
      this.offsets = offsets;
      this.size = size;
      this.lengths = lengths;
      this.docs = docs;
      this.docsWithField = docsWithField;
      value = values.clone();
    }
    
    @Override
    BytesRef value() {
      if (offset == -1) {
        return null;
      } else {
        value.offset = offset;
        value.length = length;
        return value;
      }
    }
    
    @Override
    int nextDoc() {
      if (idx >= size) {
        offset = -1;
        return doc = DocIdSetIterator.NO_MORE_DOCS;
      }
      doc = (int) docs.get(idx);
      ++idx;
      while (idx < size && docs.get(idx) == doc) {
        ++idx;
      }
      // idx points to the "next" element
      long prevIdx = idx - 1;
      if (!docsWithField.get((int) prevIdx)) {
        offset = -1;
      } else {
        // cannot change 'value' here because nextDoc is called before the
        // value is used, and it's a waste to clone the BytesRef when we
        // obtain the value
        offset = (int) offsets.get(prevIdx);
        length = (int) lengths.get(prevIdx);
      }
      return doc;
    }
    
    @Override
    int doc() {
      return doc;
    }
    
    @Override
    void reset() {
      doc = -1;
      offset = -1;
      idx = 0;
    }
  }

  private FixedBitSet docsWithField;
  private PagedMutable docs;
  private PagedGrowableWriter offsets, lengths;
  private BytesRef values;
  private int size;
  private final int bitsPerValue;
  
  public BinaryDocValuesFieldUpdates(String field, int maxDoc) {
    super(field, Type.BINARY);
    docsWithField = new FixedBitSet(64);
    bitsPerValue = PackedInts.bitsRequired(maxDoc - 1);
    docs = new PagedMutable(1, PAGE_SIZE, bitsPerValue, PackedInts.COMPACT);
    offsets = new PagedGrowableWriter(1, PAGE_SIZE, 1, PackedInts.FAST);
    lengths = new PagedGrowableWriter(1, PAGE_SIZE, 1, PackedInts.FAST);
    values = new BytesRef(16); // start small
    size = 0;
  }
  
  @Override
  public void add(int doc, Object value) {
    // TODO: if the Sorter interface changes to take long indexes, we can remove that limitation
    if (size == Integer.MAX_VALUE) {
      throw new IllegalStateException("cannot support more than Integer.MAX_VALUE doc/value entries");
    }

    BytesRef val = (BytesRef) value;
    if (val == null) {
      val = BinaryDocValuesUpdate.MISSING;
    }
    
    // grow the structures to have room for more elements
    if (docs.size() == size) {
      docs = docs.grow(size + 1);
      offsets = offsets.grow(size + 1);
      lengths = lengths.grow(size + 1);
      docsWithField = FixedBitSet.ensureCapacity(docsWithField, (int) docs.size());
    }
    
    if (val != BinaryDocValuesUpdate.MISSING) {
      // only mark the document as having a value in that field if the value wasn't set to null (MISSING)
      docsWithField.set(size);
    }
    
    docs.set(size, doc);
    offsets.set(size, values.length);
    lengths.set(size, val.length);
    values.append(val);
    ++size;
  }

  @Override
  public Iterator iterator() {
    final PagedMutable docs = this.docs;
    final PagedGrowableWriter offsets = this.offsets;
    final PagedGrowableWriter lengths = this.lengths;
    final BytesRef values = this.values;
    final FixedBitSet docsWithField = this.docsWithField;
    new InPlaceMergeSorter() {
      @Override
      protected void swap(int i, int j) {
        long tmpDoc = docs.get(j);
        docs.set(j, docs.get(i));
        docs.set(i, tmpDoc);
        
        long tmpOffset = offsets.get(j);
        offsets.set(j, offsets.get(i));
        offsets.set(i, tmpOffset);

        long tmpLength = lengths.get(j);
        lengths.set(j, lengths.get(i));
        lengths.set(i, tmpLength);
        
        boolean tmpBool = docsWithField.get(j);
        if (docsWithField.get(i)) {
          docsWithField.set(j);
        } else {
          docsWithField.clear(j);
        }
        if (tmpBool) {
          docsWithField.set(i);
        } else {
          docsWithField.clear(i);
        }
      }
      
      @Override
      protected int compare(int i, int j) {
        int x = (int) docs.get(i);
        int y = (int) docs.get(j);
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
      }
    }.sort(0, size);
    
    return new Iterator(size, offsets, lengths, docs, values, docsWithField);
  }

  @Override
  public void merge(DocValuesFieldUpdates other) {
    BinaryDocValuesFieldUpdates otherUpdates = (BinaryDocValuesFieldUpdates) other;
    int newSize = size  + otherUpdates.size;
    if (newSize > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "cannot support more than Integer.MAX_VALUE doc/value entries; size="
              + size + " other.size=" + otherUpdates.size);
    }
    docs = docs.grow(newSize);
    offsets = offsets.grow(newSize);
    lengths = lengths.grow(newSize);
    docsWithField = FixedBitSet.ensureCapacity(docsWithField, (int) docs.size());
    for (int i = 0; i < otherUpdates.size; i++) {
      int doc = (int) otherUpdates.docs.get(i);
      if (otherUpdates.docsWithField.get(i)) {
        docsWithField.set(size);
      }
      docs.set(size, doc);
      offsets.set(size, values.length + otherUpdates.offsets.get(i)); // correct relative offset
      lengths.set(size, otherUpdates.lengths.get(i));
      ++size;
    }
    int newLen = values.length + otherUpdates.values.length;
    if (values.bytes.length < newLen) {
      values.bytes = ArrayUtil.grow(values.bytes, newLen);
    }
    System.arraycopy(otherUpdates.values.bytes, otherUpdates.values.offset, values.bytes, values.length, otherUpdates.values.length);
    values.length = newLen;
  }

  @Override
  public boolean any() {
    return size > 0;
  }

  @Override
  public long ramBytesPerDoc() {
    long bytesPerDoc = (long) Math.ceil((double) (bitsPerValue + 1 /* docsWithField */) / 8); // docs
    final int capacity = estimateCapacity(size);
    bytesPerDoc += (long) Math.ceil((double) offsets.ramBytesUsed() / capacity); // offsets
    bytesPerDoc += (long) Math.ceil((double) lengths.ramBytesUsed() / capacity); // lengths
    bytesPerDoc += (long) Math.ceil((double) values.length / size); // values
    return bytesPerDoc;
  }

}
