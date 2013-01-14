package org.apache.lucene.codecs.lucene41;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.SimpleDVConsumer;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.packed.PackedInts;
import org.apache.lucene.util.packed.PackedInts.FormatAndBits;

/**
 * Writes numbers one of two ways:
 * 1. packed ints as deltas from minValue
 * 2. packed ints as ordinals to a table (if the number of values is small, e.g. <= 256)
 * 
 * the latter is typically much smaller with lucene's sims, as only some byte values are used,
 * but its often a nonlinear mapping, especially if you dont use crazy boosts.
 */
class Lucene41SimpleDocValuesConsumer extends SimpleDVConsumer {
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;
  
  final IndexOutput data, meta;
  final int maxDoc;
  
  Lucene41SimpleDocValuesConsumer(SegmentWriteState state, String dataCodec, String dataExtension, String metaCodec, String metaExtension) throws IOException {
    boolean success = false;
    try {
      String dataName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, dataExtension);
      data = state.directory.createOutput(dataName, state.context);
      CodecUtil.writeHeader(data, dataCodec, VERSION_CURRENT);
      String metaName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, metaExtension);
      meta = state.directory.createOutput(metaName, state.context);
      CodecUtil.writeHeader(meta, metaCodec, VERSION_CURRENT);
      maxDoc = state.segmentInfo.getDocCount();
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(this);
      }
    }
  }
  
  @Override
  public void addNumericField(FieldInfo field, Iterable<Number> values) throws IOException {
    meta.writeVInt(field.number);
    meta.writeLong(data.getFilePointer());
    long minValue = Long.MAX_VALUE;
    long maxValue = Long.MIN_VALUE;
    int count = 0;
    // TODO: more efficient?
    HashSet<Long> uniqueValues = new HashSet<Long>();
    for(Number nv : values) {
      long v = nv.longValue();
      minValue = Math.min(minValue, v);
      maxValue = Math.max(maxValue, v);
      count++;
      if (uniqueValues != null) {
        if (uniqueValues.add(v)) {
          if (uniqueValues.size() > 256) {
            uniqueValues = null;
          }
        }
      }
    }

    long delta = maxValue - minValue;
    final int bitsPerValue;
    if (delta < 0) {
      bitsPerValue = 64;
      meta.writeByte((byte)0); // delta-compressed
    } else if (uniqueValues != null && PackedInts.bitsRequired(uniqueValues.size()-1) < PackedInts.bitsRequired(delta)) {
      // smaller to tableize
      bitsPerValue = PackedInts.bitsRequired(uniqueValues.size()-1);
      minValue = 0; // we will write indexes into the table instead of values
      meta.writeByte((byte)1); // table-compressed
      Long[] decode = uniqueValues.toArray(new Long[uniqueValues.size()]);
      final HashMap<Long,Integer> encode = new HashMap<Long,Integer>();
      data.writeVInt(decode.length);
      for (int i = 0; i < decode.length; i++) {
        data.writeLong(decode[i]);
        encode.put(decode[i], i);
      }
      final Iterable<Number> original = values;
      values = new Iterable<Number>() {
        @Override
        public Iterator<Number> iterator() {
          final Iterator<Number> inner = original.iterator();
          return new Iterator<Number>() {
            @Override
            public boolean hasNext() {
              return inner.hasNext();
            }

            @Override
            public Number next() {
              return encode.get(inner.next());
            }

            @Override
            public void remove() { throw new UnsupportedOperationException(); }
          };
        }
      };
    } else {
      bitsPerValue = PackedInts.bitsRequired(delta);
      meta.writeByte((byte)0); // delta-compressed
    }

    data.writeLong(minValue);

    FormatAndBits formatAndBits = PackedInts.fastestFormatAndBits(count, bitsPerValue, PackedInts.COMPACT);   
    final PackedInts.Writer writer = PackedInts.getWriter(data, count, formatAndBits.bitsPerValue, 0);
    for(Number nv : values) {
      writer.add(nv.longValue() - minValue);
    }
    writer.finish();
  }
  
  @Override
  public void close() throws IOException {
    // nocommit: just write this to a RAMfile or something and flush it here, with #fields first.
    // this meta is a tiny file so this hurts nobody
    boolean success = false;
    try {
      if (meta != null) {
        meta.writeVInt(-1);
      }
      success = true;
    } finally {
      if (success) {
        IOUtils.close(data, meta);
      } else {
        IOUtils.closeWhileHandlingException(data, meta);
      }
    }
  }

  // nocommit: have SimpleDVConsumer extend SimpleNormsConsumer?
  @Override
  public void addBinaryField(FieldInfo field, Iterable<BytesRef> values) throws IOException {
    throw new AssertionError();
  }

  @Override
  public void addSortedField(FieldInfo field, Iterable<BytesRef> values, Iterable<Number> docToOrd) throws IOException {
    throw new AssertionError();
  }
  
  // nocommit: can/should we make override merge + make it smarter to pull the values 
  // directly from disk for fields that arent already loaded up in ram?
}
