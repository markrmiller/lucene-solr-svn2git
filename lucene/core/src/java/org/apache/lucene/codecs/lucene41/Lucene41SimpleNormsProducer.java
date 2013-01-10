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
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.SimpleDVProducer;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.packed.PackedInts;

class Lucene41SimpleNormsProducer extends SimpleDVProducer {
  private final Map<Integer,NumericEntry> numerics;
  private final IndexInput data;
  
  // ram instances we have already loaded
  private final Map<Integer,NumericDocValues> ramInstances = 
      new HashMap<Integer,NumericDocValues>();
  
  Lucene41SimpleNormsProducer(SegmentReadState state) throws IOException {
    String metaName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, "nvm");
    // read in the entries from the metadata file.
    IndexInput in = state.directory.openInput(metaName, state.context);
    boolean success = false;
    try {
      CodecUtil.checkHeader(in, Lucene41SimpleNormsFormat.METADATA_CODEC, 
                                Lucene41SimpleNormsFormat.VERSION_START,
                                Lucene41SimpleNormsFormat.VERSION_START);
      numerics = new HashMap<Integer,NumericEntry>();
      readFields(in, state.fieldInfos);
      success = true;
    } finally {
      if (success) {
        IOUtils.close(in);
      } else {
        IOUtils.closeWhileHandlingException(in);
      }
    }
    
    String dataName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, "nvd");
    data = state.directory.openInput(dataName, state.context);
    CodecUtil.checkHeader(data, Lucene41SimpleNormsFormat.DATA_CODEC, 
                                Lucene41SimpleNormsFormat.VERSION_START,
                                Lucene41SimpleNormsFormat.VERSION_START);
  }
  
  private void readFields(IndexInput meta, FieldInfos infos) throws IOException {
    int fieldNumber = meta.readVInt();
    while (fieldNumber != -1) {
      NumericEntry entry = new NumericEntry();
      entry.offset = meta.readLong();
      entry.tableized = meta.readByte() != 0;
      numerics.put(fieldNumber, entry);
      fieldNumber = meta.readVInt();
    }
  }

  @Override
  public synchronized NumericDocValues getNumeric(FieldInfo field) throws IOException {
    NumericDocValues instance = ramInstances.get(field.number);
    if (instance == null) {
      instance = loadNumeric(field);
      ramInstances.put(field.number, instance);
    }
    return instance;
  }
  
  private NumericDocValues loadNumeric(FieldInfo field) throws IOException {
    NumericEntry entry = numerics.get(field.number);
    final IndexInput data = this.data.clone();
    data.seek(entry.offset);
    if (entry.tableized) {
      int size = data.readVInt();
      final long decode[] = new long[size];
      for (int i = 0; i < decode.length; i++) {
        decode[i] = data.readLong();
      }
      final long minValue = data.readLong();
      assert minValue == 0;
      PackedInts.Header header = PackedInts.readHeader(data);
      final PackedInts.Reader reader = PackedInts.getReaderNoHeader(data, header);
      return new NumericDocValues() {
        @Override
        public long get(int docID) {
          return decode[(int)reader.get(docID)];
        }
      };
    } else {
      final long minValue = data.readLong();
      PackedInts.Header header = PackedInts.readHeader(data);
      final PackedInts.Reader reader = PackedInts.getReaderNoHeader(data, header);
      return new NumericDocValues() {
        @Override
        public long get(int docID) {
          return minValue + reader.get(docID);
        }
      };
    }
  }

  @Override
  public BinaryDocValues getBinary(FieldInfo field) throws IOException {
    throw new AssertionError();
  }

  @Override
  public SortedDocValues getSorted(FieldInfo field) throws IOException {
    throw new AssertionError();
  }

  @Override
  public void close() throws IOException {
    data.close();
  }
  
  static class NumericEntry {
    long offset;
    boolean tableized;
  }

}
