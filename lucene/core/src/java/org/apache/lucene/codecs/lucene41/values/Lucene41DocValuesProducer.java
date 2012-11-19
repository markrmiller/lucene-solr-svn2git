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
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.codecs.SimpleDVProducer;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.store.CompoundFileDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.IOUtils;

// nocommit 
public class Lucene41DocValuesProducer extends SimpleDVProducer {
  
  private final CompoundFileDirectory cfs;
  private IOContext context;
  private final SegmentInfo info;
  private final Map<String,DocValuesFactory<NumericDocValues>> numeric = new HashMap<String,DocValuesFactory<NumericDocValues>>();
  private final Map<String,DocValuesFactory<BinaryDocValues>> binary = new HashMap<String,DocValuesFactory<BinaryDocValues>>();
  
  private final Map<String,DocValuesFactory<SortedDocValues>> sorted = new HashMap<String,DocValuesFactory<SortedDocValues>>();
  
  public Lucene41DocValuesProducer(Directory dir, SegmentInfo segmentInfo,
      IOContext context) throws IOException {
    this.cfs = new CompoundFileDirectory(dir, IndexFileNames.segmentFileName(
        segmentInfo.name, Lucene41DocValuesConsumer.DV_SEGMENT_SUFFIX,
        IndexFileNames.COMPOUND_FILE_EXTENSION), context, false);
    this.context = context;
    this.info = segmentInfo;
  }
  
  @Override
  public void close() throws IOException {
    try {
      List<Closeable> closeables = new ArrayList<Closeable>(numeric.values());
      closeables.addAll(binary.values());
      closeables.addAll(sorted.values());
      IOUtils.close(closeables);
    } finally {
      IOUtils.close(cfs);
    }
  }
  
  @Override
  public NumericDocValues getNumeric(FieldInfo field) throws IOException {
    //nocommit do we need to sync that?
    DocValuesFactory<NumericDocValues> docValuesFactory = numeric
        .get(field.name);
    if (docValuesFactory == null) {
      numeric.put(field.name,
          docValuesFactory = new Lucene41NumericDocValues.Factory(this.cfs,
              this.info, field, context));
    }
    return docValuesFactory.getDirect();
  }
  
  @Override
  public BinaryDocValues getBinary(FieldInfo field) throws IOException {
    //nocommit do we need to sync that?
    DocValuesFactory<BinaryDocValues> docValuesFactory = binary.get(field.name);
    if (docValuesFactory == null) {
      binary.put(field.name,
          docValuesFactory = new Lucene41BinaryDocValues.Factory(this.cfs,
              this.info, field, context));
    }
    return docValuesFactory.getDirect();
  }
  
  @Override
  public SortedDocValues getSorted(FieldInfo field) throws IOException {
    //nocommit do we need to sync that?
    DocValuesFactory<SortedDocValues> docValuesFactory = sorted.get(field.name);
    if (docValuesFactory == null) {
      sorted.put(field.name,
          docValuesFactory = new Lucene41SortedDocValues.Factory(this.cfs,
              this.info, field, context));
    }
    return docValuesFactory.getDirect();
  }
  
  public static abstract class DocValuesFactory<T> implements Closeable {
    
    public abstract T getDirect() throws IOException;
    
    public abstract T getInMemory() throws IOException;
  }
  
}
