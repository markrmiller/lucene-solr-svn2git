package org.apache.lucene.codecs.diskdv;

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
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.packed.BlockPackedReader;
import org.apache.lucene.util.packed.MonotonicBlockPackedReader;

class DiskDocValuesProducer extends DocValuesProducer {
  private final Map<Integer,NumericEntry> numerics;
  private final Map<Integer,NumericEntry> ords;
  private final Map<Integer,BinaryEntry> binaries;
  private final IndexInput data;
  
  DiskDocValuesProducer(SegmentReadState state) throws IOException {
    String metaName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, "ddvm");
    // read in the entries from the metadata file.
    IndexInput in = state.directory.openInput(metaName, state.context);
    boolean success = false;
    try {
      CodecUtil.checkHeader(in, DiskDocValuesFormat.METADATA_CODEC, 
                                DiskDocValuesFormat.VERSION_START,
                                DiskDocValuesFormat.VERSION_START);
      numerics = new HashMap<Integer,NumericEntry>();
      ords = new HashMap<Integer,NumericEntry>();
      binaries = new HashMap<Integer,BinaryEntry>();
      readFields(in, state.fieldInfos);
      success = true;
    } finally {
      if (success) {
        IOUtils.close(in);
      } else {
        IOUtils.closeWhileHandlingException(in);
      }
    }
    
    String dataName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, "ddvd");
    data = state.directory.openInput(dataName, state.context);
    CodecUtil.checkHeader(data, DiskDocValuesFormat.DATA_CODEC, 
                                DiskDocValuesFormat.VERSION_START,
                                DiskDocValuesFormat.VERSION_START);
  }
  
  private void readFields(IndexInput meta, FieldInfos infos) throws IOException {
    int fieldNumber = meta.readVInt();
    while (fieldNumber != -1) {
      DocValuesType type = infos.fieldInfo(fieldNumber).getDocValuesType();
      if (type == DocValuesType.NUMERIC) {
        numerics.put(fieldNumber, readNumericEntry(meta));
      } else if (type == DocValuesType.BINARY) {
        BinaryEntry b = readBinaryEntry(meta);
        binaries.put(fieldNumber, b);
      } else if (type == DocValuesType.SORTED) {
        BinaryEntry b = readBinaryEntry(meta);
        binaries.put(fieldNumber, b);
        if (meta.readVInt() != fieldNumber) {
          throw new CorruptIndexException("sorted entry for field: " + fieldNumber + " is corrupt");
        }
        NumericEntry n = readNumericEntry(meta);
        ords.put(fieldNumber, n);
      }
      fieldNumber = meta.readVInt();
    }
  }
  
  static NumericEntry readNumericEntry(IndexInput meta) throws IOException {
    NumericEntry entry = new NumericEntry();
    entry.packedIntsVersion = meta.readVInt();
    entry.offset = meta.readLong();
    entry.count = meta.readVInt();
    entry.blockSize = meta.readVInt();
    return entry;
  }
  
  static BinaryEntry readBinaryEntry(IndexInput meta) throws IOException {
    BinaryEntry entry = new BinaryEntry();
    entry.minLength = meta.readVInt();
    entry.maxLength = meta.readVInt();
    entry.count = meta.readVInt();
    entry.offset = meta.readLong();
    if (entry.minLength != entry.maxLength) {
      entry.addressesOffset = meta.readLong();
      entry.packedIntsVersion = meta.readVInt();
      entry.blockSize = meta.readVInt();
    }
    return entry;
  }

  @Override
  public NumericDocValues getNumeric(FieldInfo field) throws IOException {
    NumericEntry entry = numerics.get(field.number);
    return getNumeric(field, entry);
  }
  
  private NumericDocValues getNumeric(FieldInfo field, final NumericEntry entry) throws IOException {
    final IndexInput data = this.data.clone();
    data.seek(entry.offset);

    final BlockPackedReader reader = new BlockPackedReader(data, entry.packedIntsVersion, entry.blockSize, entry.count, true);
    return new NumericDocValues() {
      @Override
      public long get(int docID) {
        return reader.get(docID);
      }
    };
  }

  @Override
  public BinaryDocValues getBinary(FieldInfo field) throws IOException {
    BinaryEntry bytes = binaries.get(field.number);
    if (bytes.minLength == bytes.maxLength) {
      return getFixedBinary(field, bytes);
    } else {
      return getVariableBinary(field, bytes);
    }
  }
  
  private BinaryDocValues getFixedBinary(FieldInfo field, final BinaryEntry bytes) {
    final IndexInput data = this.data.clone();

    return new BinaryDocValues() {
      @Override
      public void get(int docID, BytesRef result) {
        long address = bytes.offset + docID * (long)bytes.maxLength;
        try {
          data.seek(address);
          // NOTE: we could have one buffer, but various consumers (e.g. FieldComparatorSource) 
          // assume "they" own the bytes after calling this!
          final byte[] buffer = new byte[bytes.maxLength];
          data.readBytes(buffer, 0, buffer.length);
          result.bytes = buffer;
          result.offset = 0;
          result.length = buffer.length;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
  
  private BinaryDocValues getVariableBinary(FieldInfo field, final BinaryEntry bytes) throws IOException {
    final IndexInput data = this.data.clone();
    data.seek(bytes.addressesOffset);

    final MonotonicBlockPackedReader addresses = new MonotonicBlockPackedReader(data, bytes.packedIntsVersion, bytes.blockSize, bytes.count, true);
    return new BinaryDocValues() {
      @Override
      public void get(int docID, BytesRef result) {
        long startAddress = bytes.offset + (docID == 0 ? 0 : + addresses.get(docID-1));
        long endAddress = bytes.offset + addresses.get(docID);
        int length = (int) (endAddress - startAddress);
        try {
          data.seek(startAddress);
          // NOTE: we could have one buffer, but various consumers (e.g. FieldComparatorSource) 
          // assume "they" own the bytes after calling this!
          final byte[] buffer = new byte[length];
          data.readBytes(buffer, 0, buffer.length);
          result.bytes = buffer;
          result.offset = 0;
          result.length = length;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @Override
  public SortedDocValues getSorted(FieldInfo field) throws IOException {
    final int valueCount = binaries.get(field.number).count;
    final BinaryDocValues binary = getBinary(field);
    final NumericDocValues ordinals = getNumeric(field, ords.get(field.number));
    return new SortedDocValues() {

      @Override
      public int getOrd(int docID) {
        return (int) ordinals.get(docID);
      }

      @Override
      public void lookupOrd(int ord, BytesRef result) {
        binary.get(ord, result);
      }

      @Override
      public int getValueCount() {
        return valueCount;
      }
    };
  }

  @Override
  public void close() throws IOException {
    data.close();
  }
  
  static class NumericEntry {
    long offset;

    int packedIntsVersion;
    int count;
    int blockSize;
  }
  
  static class BinaryEntry {
    long offset;

    int count;
    int minLength;
    int maxLength;
    long addressesOffset;
    int packedIntsVersion;
    int blockSize;
  }
}
