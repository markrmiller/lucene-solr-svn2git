package org.apache.lucene.index.values;

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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.index.values.Bytes.BytesBaseSource;
import org.apache.lucene.index.values.Bytes.BytesReaderBase;
import org.apache.lucene.index.values.Bytes.BytesWriterBase;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.PagedBytes;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.packed.PackedInts;

// Variable length byte[] per document, no sharing

class VarStraightBytesImpl {

  static final String CODEC_NAME = "VarStraightBytes";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;

  static class Writer extends BytesWriterBase {
    private long address;
    // start at -1 if the first added value is > 0
    private int lastDocID = -1;
    private long[] docToAddress;

    public Writer(Directory dir, String id, AtomicLong bytesUsed)
        throws IOException {
      super(dir, id, CODEC_NAME, VERSION_CURRENT, false, false, null, bytesUsed);
      docToAddress = new long[1];
      bytesUsed.addAndGet(RamUsageEstimator.NUM_BYTES_INT);
    }

    public Writer(Directory dir, String id) throws IOException {
      this(dir, id, new AtomicLong());
    }

    // Fills up to but not including this docID
    private void fill(final int docID) {
      if (docID >= docToAddress.length) {
        int oldSize = docToAddress.length;
        docToAddress = ArrayUtil.grow(docToAddress, 1 + docID);
        bytesUsed.addAndGet((docToAddress.length - oldSize)
            * RamUsageEstimator.NUM_BYTES_INT);
      }
      for (int i = lastDocID + 1; i < docID; i++) {
        docToAddress[i] = address;
      }
      lastDocID = docID;
    }

    @Override
    synchronized public void add(int docID, BytesRef bytes) throws IOException {
      if (bytes.length == 0)
        return; // default
      if (datOut == null)
        initDataOut();
      fill(docID);
      docToAddress[docID] = address;
      datOut.writeBytes(bytes.bytes, bytes.offset, bytes.length);
      address += bytes.length;
    }

    @Override
    synchronized public void finish(int docCount) throws IOException {
      if (datOut == null) {
        return;
      }
      initIndexOut();
      fill(docCount);
      idxOut.writeVLong(address);
      final PackedInts.Writer w = PackedInts.getWriter(idxOut, docCount,
          PackedInts.bitsRequired(address));
      for (int i = 0; i < docCount; i++) {
        w.add(docToAddress[i]);
      }
      w.finish();
      bytesUsed.addAndGet(-(docToAddress.length)
          * RamUsageEstimator.NUM_BYTES_INT);
      docToAddress = null;
      super.finish(docCount);
    }

    public long ramBytesUsed() {
      return bytesUsed.get();
    }
  }

  public static class Reader extends BytesReaderBase {
    private final int maxDoc;

    Reader(Directory dir, String id, int maxDoc) throws IOException {
      super(dir, id, CODEC_NAME, VERSION_START, true);
      this.maxDoc = maxDoc;
    }

    @Override
    public Source load() throws IOException {
      return new Source(cloneData(), cloneIndex());
    }

    private class Source extends BytesBaseSource {
      private final PackedInts.Reader addresses;

      public Source(IndexInput datIn, IndexInput idxIn) throws IOException {
        super(datIn, idxIn, new PagedBytes(PAGED_BYTES_BITS), idxIn.readVLong()); 
        addresses = PackedInts.getReader(idxIn);
        missingValue.bytesValue = new BytesRef(0); // empty
      }

      @Override
      public BytesRef getBytes(int docID, BytesRef bytesRef) {
        final long address = addresses.get(docID);
        final int length = docID == maxDoc - 1 ? (int) (totalLengthInBytes - address)
            : (int) (addresses.get(1 + docID) - address);
        return data.fill(bytesRef, address, length);
      }

      @Override
      public int getValueCount() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Values type() {
        return Values.BYTES_VAR_STRAIGHT;
      }

      @Override
      protected int maxDoc() {
        return addresses.size();
      }
    }

    @Override
    public ValuesEnum getEnum(AttributeSource source) throws IOException {
      return new VarStraightBytesEnum(source, cloneData(), cloneIndex());
    }

    private class VarStraightBytesEnum extends ValuesEnum {
      private final PackedInts.Reader addresses;
      private final IndexInput datIn;
      private final IndexInput idxIn;
      private final long fp;
      private final int totBytes;
      private final BytesRef ref;
      private int pos = -1;

      protected VarStraightBytesEnum(AttributeSource source, IndexInput datIn,
          IndexInput idxIn) throws IOException {
        super(source, Values.BYTES_VAR_STRAIGHT);
        totBytes = idxIn.readVInt();
        fp = datIn.getFilePointer();
        addresses = PackedInts.getReader(idxIn);
        this.datIn = datIn;
        this.idxIn = idxIn;
        ref = attr.bytes();

      }

      @Override
      public void close() throws IOException {
        datIn.close();
        idxIn.close();
      }

      @Override
      public int advance(final int target) throws IOException {
        if (target >= maxDoc) {
          return pos = NO_MORE_DOCS;
        }
        final long addr = addresses.get(target);
        if (addr == totBytes) { // empty values at the end
          ref.length = 0;
          ref.offset = 0;
          return pos = target;
        }
        datIn.seek(fp + addr);
        final int size = (int) (target == maxDoc - 1 ? totBytes - addr
            : addresses.get(target + 1) - addr);
        if (ref.bytes.length < size)
          ref.grow(size);
        ref.length = size;
        datIn.readBytes(ref.bytes, 0, size);
        return pos = target;
      }

      @Override
      public int docID() {
        return pos;
      }

      @Override
      public int nextDoc() throws IOException {
        return advance(pos + 1);
      }
    }

    @Override
    public Values type() {
      return Values.BYTES_VAR_STRAIGHT;
    }
  }
}
