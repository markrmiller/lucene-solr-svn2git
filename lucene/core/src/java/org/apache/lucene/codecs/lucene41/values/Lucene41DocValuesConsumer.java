package org.apache.lucene.codecs.lucene41.values;

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

import org.apache.lucene.codecs.SimpleDVConsumer;
import org.apache.lucene.codecs.SortedDocValuesConsumer;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.CompoundFileDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

public class Lucene41DocValuesConsumer extends SimpleDVConsumer {
  /**
   * Filename extension for index files
   */
  static final String INDEX_EXTENSION = "idx";
  
  static final String OFFSET_EXTENSION = "off";
  
  /**
   * Filename extension for data files.
   */
  static final String DATA_EXTENSION = "dat";
  
  static final String DV_SEGMENT_SUFFIX = "sdv"; // nocommit change to dv
  
  private final SegmentInfo info;
  private final Directory dir;
  private Directory cfs;
  private final IOContext context;
  private final String segmentSuffix;
  
  Lucene41DocValuesConsumer(SegmentWriteState state) throws IOException {
    this.dir = state.directory;
    this.info = state.segmentInfo;
    this.context = state.context;
    this.segmentSuffix = state.segmentSuffix;
  }
  
  private synchronized Directory getDirectory() throws IOException {
    if (cfs == null) {
      final String suffix;
      if (segmentSuffix.length() == 0) {
        suffix = Lucene41DocValuesConsumer.DV_SEGMENT_SUFFIX;
      } else {
        suffix = segmentSuffix + "_" + Lucene41DocValuesConsumer.DV_SEGMENT_SUFFIX;
      }
      String fileName = IndexFileNames.segmentFileName(info.name, 
                                                       suffix, 
                                                       IndexFileNames.COMPOUND_FILE_EXTENSION);
      cfs = new CompoundFileDirectory(dir, fileName, context, true);
    }
    return cfs;
  }
  
  @Override
  public void close() throws IOException {
    IOUtils.close(cfs);
  }
  
  @Override
  public void addNumericField(FieldInfo field, Iterable<Number> values) throws IOException {
    // ncommit
  }
  
  @Override
  public void addBinaryField(FieldInfo field, Iterable<BytesRef> values) {
    // ncommit
  }
  
  // nocommit: bogus to put segmentName in here. think about copySegmentAsIs!!!!!!
  static String getDocValuesFileName(SegmentInfo info, FieldInfo field, String extension) {
    return IndexFileNames.segmentFileName(info.name + "_"
        + field.number, DV_SEGMENT_SUFFIX, extension);
  }
  
  @Override
  public SortedDocValuesConsumer addSortedField(FieldInfo field,
      int valueCount, boolean fixedLength, int maxLength)
      throws IOException {
    String nameData = getDocValuesFileName(info, field, DATA_EXTENSION);
    String idxOut = getDocValuesFileName(info, field, INDEX_EXTENSION);
    String offOut = getDocValuesFileName(info, field, OFFSET_EXTENSION);
    boolean success = false;
    IndexOutput dataOut = null;
    IndexOutput indexOut = null;
    IndexOutput offsetOut = null;
    try {
      dataOut = getDirectory().createOutput(nameData, context);
      indexOut = getDirectory().createOutput(idxOut, context);
      if (fixedLength) {
        offsetOut = null;
      } else {
        offsetOut = getDirectory().createOutput(offOut, context);
      }
      Lucene41SortedDocValuesConsumer consumer = new Lucene41SortedDocValuesConsumer(
          dataOut, indexOut, offsetOut, valueCount, maxLength, this.info.getDocCount());
      success = true;
      return consumer;
    } finally {
      if (!success) {
        IOUtils.close(dataOut, indexOut);
      }
    }
  }
  
}
