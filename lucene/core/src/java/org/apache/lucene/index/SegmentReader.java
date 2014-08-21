package org.apache.lucene.index;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.codecs.FieldInfosFormat;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.TermVectorsReader;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.store.CompoundFileDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.CloseableThreadLocal;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * IndexReader implementation over a single segment. 
 * <p>
 * Instances pointing to the same segment (but with different deletes, etc)
 * may share the same core data.
 * @lucene.experimental
 */
public final class SegmentReader extends AtomicReader implements Accountable {

  private static final long BASE_RAM_BYTES_USED =
        RamUsageEstimator.shallowSizeOfInstance(SegmentReader.class)
      + RamUsageEstimator.shallowSizeOfInstance(SegmentDocValues.class);
        
  private final SegmentCommitInfo si;
  private final Bits liveDocs;

  // Normally set to si.docCount - si.delDocCount, unless we
  // were created as an NRT reader from IW, in which case IW
  // tells us the docCount:
  private final int numDocs;

  final SegmentCoreReaders core;
  final SegmentDocValues segDocValues;
  
  final CloseableThreadLocal<Map<String,Object>> docValuesLocal = new CloseableThreadLocal<Map<String,Object>>() {
    @Override
    protected Map<String,Object> initialValue() {
      return new HashMap<>();
    }
  };

  final CloseableThreadLocal<Map<String,Bits>> docsWithFieldLocal = new CloseableThreadLocal<Map<String,Bits>>() {
    @Override
    protected Map<String,Bits> initialValue() {
      return new HashMap<>();
    }
  };

  final DocValuesProducer docValuesProducer;
  final FieldInfos fieldInfos;
  
  /**
   * Constructs a new SegmentReader with a new core.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  // TODO: why is this public?
  public SegmentReader(SegmentCommitInfo si, int termInfosIndexDivisor, IOContext context) throws IOException {
    this.si = si;
    // TODO if the segment uses CFS, we may open the CFS file twice: once for
    // reading the FieldInfos (if they are not gen'd) and second time by
    // SegmentCoreReaders. We can open the CFS here and pass to SCR, but then it
    // results in less readable code (resource not closed where it was opened).
    // Best if we could somehow read FieldInfos in SCR but not keep it there, but
    // constructors don't allow returning two things...
    fieldInfos = readFieldInfos(si);
    core = new SegmentCoreReaders(this, si.info.dir, si, context, termInfosIndexDivisor);
    segDocValues = new SegmentDocValues();
    
    boolean success = false;
    final Codec codec = si.info.getCodec();
    try {
      if (si.hasDeletions()) {
        // NOTE: the bitvector is stored using the regular directory, not cfs
        liveDocs = codec.liveDocsFormat().readLiveDocs(directory(), si, IOContext.READONCE);
      } else {
        assert si.getDelCount() == 0;
        liveDocs = null;
      }
      numDocs = si.info.getDocCount() - si.getDelCount();

      if (fieldInfos.hasDocValues()) {
        docValuesProducer = initDocValuesProducer(codec);
      } else {
        docValuesProducer = null;
      }

      success = true;
    } finally {
      // With lock-less commits, it's entirely possible (and
      // fine) to hit a FileNotFound exception above.  In
      // this case, we want to explicitly close any subset
      // of things that were opened so that we don't have to
      // wait for a GC to do so.
      if (!success) {
        doClose();
      }
    }
  }

  /** Create new SegmentReader sharing core from a previous
   *  SegmentReader and loading new live docs from a new
   *  deletes file.  Used by openIfChanged. */
  SegmentReader(SegmentCommitInfo si, SegmentReader sr) throws IOException {
    this(si, sr,
         si.info.getCodec().liveDocsFormat().readLiveDocs(si.info.dir, si, IOContext.READONCE),
         si.info.getDocCount() - si.getDelCount());
  }

  /** Create new SegmentReader sharing core from a previous
   *  SegmentReader and using the provided in-memory
   *  liveDocs.  Used by IndexWriter to provide a new NRT
   *  reader */
  SegmentReader(SegmentCommitInfo si, SegmentReader sr, Bits liveDocs, int numDocs) throws IOException {
    this.si = si;
    this.liveDocs = liveDocs;
    this.numDocs = numDocs;
    this.core = sr.core;
    core.incRef();
    this.segDocValues = sr.segDocValues;
    
//    System.out.println("[" + Thread.currentThread().getName() + "] SR.init: sharing reader: " + sr + " for gens=" + sr.genDVProducers.keySet());
    
    // increment refCount of DocValuesProducers that are used by this reader
    boolean success = false;
    try {
      final Codec codec = si.info.getCodec();
      if (si.getFieldInfosGen() == -1) {
        fieldInfos = sr.fieldInfos;
      } else {
        fieldInfos = readFieldInfos(si);
      }
      
      if (fieldInfos.hasDocValues()) {
        docValuesProducer = initDocValuesProducer(codec);
      } else {
        docValuesProducer = null;
      }
      success = true;
    } finally {
      if (!success) {
        doClose();
      }
    }
  }

  // initialize the per-field DocValuesProducer
  private DocValuesProducer initDocValuesProducer(Codec codec) throws IOException {
    final Directory dir = core.cfsReader != null ? core.cfsReader : si.info.dir;
    final DocValuesFormat dvFormat = codec.docValuesFormat();

    int termsIndexDivisor = getTermInfosIndexDivisor();
    if (!si.hasFieldUpdates()) {
      // simple case, no DocValues updates
      return segDocValues.getDocValuesProducer(-1L, si, IOContext.READ, dir, dvFormat, fieldInfos, termsIndexDivisor);
    } else {
      return new SegmentDocValuesProducer(si, dir, fieldInfos, segDocValues, dvFormat, termsIndexDivisor);
    }
  }
  
  /**
   * Reads the most recent {@link FieldInfos} of the given segment info.
   * 
   * @lucene.internal
   */
  static FieldInfos readFieldInfos(SegmentCommitInfo info) throws IOException {
    final Directory dir;
    final boolean closeDir;
    if (info.getFieldInfosGen() == -1 && info.info.getUseCompoundFile()) {
      // no fieldInfos gen and segment uses a compound file
      dir = new CompoundFileDirectory(info.info.dir,
          IndexFileNames.segmentFileName(info.info.name, "", IndexFileNames.COMPOUND_FILE_EXTENSION),
          IOContext.READONCE,
          false);
      closeDir = true;
    } else {
      // gen'd FIS are read outside CFS, or the segment doesn't use a compound file
      dir = info.info.dir;
      closeDir = false;
    }
    
    try {
      final String segmentSuffix = info.getFieldInfosGen() == -1 ? "" : Long.toString(info.getFieldInfosGen(), Character.MAX_RADIX);
      Codec codec = info.info.getCodec();
      FieldInfosFormat fisFormat = codec.fieldInfosFormat();
      return fisFormat.getFieldInfosReader().read(dir, info.info.name, segmentSuffix, IOContext.READONCE);
    } finally {
      if (closeDir) {
        dir.close();
      }
    }
  }
  
  @Override
  public Bits getLiveDocs() {
    ensureOpen();
    return liveDocs;
  }

  @Override
  protected void doClose() throws IOException {
    //System.out.println("SR.close seg=" + si);
    try {
      core.decRef();
    } finally {
      try {
        IOUtils.close(docValuesLocal, docsWithFieldLocal);
      } finally {
        if (docValuesProducer instanceof SegmentDocValuesProducer) {
          segDocValues.decRef(((SegmentDocValuesProducer)docValuesProducer).dvGens);
        } else if (docValuesProducer != null) {
          segDocValues.decRef(Collections.singletonList(-1L));
        }
      }
    }
  }

  @Override
  public FieldInfos getFieldInfos() {
    ensureOpen();
    return fieldInfos;
  }
  
  @Override
  public void document(int docID, StoredFieldVisitor visitor) throws IOException {
    checkBounds(docID);
    getFieldsReader().visitDocument(docID, visitor);
  }

  @Override
  public Fields fields() {
    ensureOpen();
    return core.fields;
  }

  @Override
  public int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)
    return numDocs;
  }

  @Override
  public int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return si.info.getDocCount();
  }

  /** Expert: retrieve thread-private {@link
   *  TermVectorsReader}
   *  @lucene.internal */
  public TermVectorsReader getTermVectorsReader() {
    ensureOpen();
    return core.termVectorsLocal.get();
  }

  /** Expert: retrieve thread-private {@link
   *  StoredFieldsReader}
   *  @lucene.internal */
  public StoredFieldsReader getFieldsReader() {
    ensureOpen();
    return core.fieldsReaderLocal.get();
  }
  
  /** Expert: retrieve underlying NormsProducer
   *  @lucene.internal */
  public NormsProducer getNormsReader() {
    ensureOpen();
    return core.normsProducer;
  }
  
  /** Expert: retrieve underlying DocValuesProducer
   *  @lucene.internal */
  public DocValuesProducer getDocValuesReader() {
    ensureOpen();
    return docValuesProducer;
  }

  @Override
  public Fields getTermVectors(int docID) throws IOException {
    TermVectorsReader termVectorsReader = getTermVectorsReader();
    if (termVectorsReader == null) {
      return null;
    }
    checkBounds(docID);
    return termVectorsReader.get(docID);
  }
  
  private void checkBounds(int docID) {
    if (docID < 0 || docID >= maxDoc()) {       
      throw new IndexOutOfBoundsException("docID must be >= 0 and < maxDoc=" + maxDoc() + " (got docID=" + docID + ")");
    }
  }

  @Override
  public String toString() {
    // SegmentInfo.toString takes dir and number of
    // *pending* deletions; so we reverse compute that here:
    return si.toString(si.info.dir, si.info.getDocCount() - numDocs - si.getDelCount());
  }
  
  /**
   * Return the name of the segment this reader is reading.
   */
  public String getSegmentName() {
    return si.info.name;
  }
  
  /**
   * Return the SegmentInfoPerCommit of the segment this reader is reading.
   */
  public SegmentCommitInfo getSegmentInfo() {
    return si;
  }

  /** Returns the directory this index resides in. */
  public Directory directory() {
    // Don't ensureOpen here -- in certain cases, when a
    // cloned/reopened reader needs to commit, it may call
    // this method on the closed original reader
    return si.info.dir;
  }

  // This is necessary so that cloned SegmentReaders (which
  // share the underlying postings data) will map to the
  // same entry in the FieldCache.  See LUCENE-1579.
  @Override
  public Object getCoreCacheKey() {
    // NOTE: if this ever changes, be sure to fix
    // SegmentCoreReader.notifyCoreClosedListeners to match!
    // Today it passes "this" as its coreCacheKey:
    return core;
  }

  @Override
  public Object getCombinedCoreAndDeletesKey() {
    return this;
  }

  /** Returns term infos index divisor originally passed to
   *  {@link #SegmentReader(SegmentCommitInfo, int, IOContext)}. */
  public int getTermInfosIndexDivisor() {
    return core.termsIndexDivisor;
  }

  // returns the FieldInfo that corresponds to the given field and type, or
  // null if the field does not exist, or not indexed as the requested
  // DovDocValuesType.
  private FieldInfo getDVField(String field, DocValuesType type) {
    FieldInfo fi = fieldInfos.fieldInfo(field);
    if (fi == null) {
      // Field does not exist
      return null;
    }
    if (fi.getDocValuesType() == null) {
      // Field was not indexed with doc values
      return null;
    }
    if (fi.getDocValuesType() != type) {
      // Field DocValues are different than requested type
      return null;
    }

    return fi;
  }
  
  @Override
  public NumericDocValues getNumericDocValues(String field) throws IOException {
    ensureOpen();
    Map<String,Object> dvFields = docValuesLocal.get();

    Object previous = dvFields.get(field);
    if (previous != null && previous instanceof NumericDocValues) {
      return (NumericDocValues) previous;
    } else {
      FieldInfo fi = getDVField(field, DocValuesType.NUMERIC);
      if (fi == null) {
        return null;
      }
      NumericDocValues dv = docValuesProducer.getNumeric(fi);
      dvFields.put(field, dv);
      return dv;
    }
  }

  @Override
  public Bits getDocsWithField(String field) throws IOException {
    ensureOpen();
    Map<String,Bits> dvFields = docsWithFieldLocal.get();

    Bits previous = dvFields.get(field);
    if (previous != null) {
      return previous;
    } else {
      FieldInfo fi = fieldInfos.fieldInfo(field);
      if (fi == null) {
        // Field does not exist
        return null;
      }
      if (fi.getDocValuesType() == null) {
        // Field was not indexed with doc values
        return null;
      }
      Bits dv = docValuesProducer.getDocsWithField(fi);
      dvFields.put(field, dv);
      return dv;
    }
  }

  @Override
  public BinaryDocValues getBinaryDocValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getDVField(field, DocValuesType.BINARY);
    if (fi == null) {
      return null;
    }

    Map<String,Object> dvFields = docValuesLocal.get();

    BinaryDocValues dvs = (BinaryDocValues) dvFields.get(field);
    if (dvs == null) {
      dvs = docValuesProducer.getBinary(fi);
      dvFields.put(field, dvs);
    }

    return dvs;
  }

  @Override
  public SortedDocValues getSortedDocValues(String field) throws IOException {
    ensureOpen();
    Map<String,Object> dvFields = docValuesLocal.get();
    
    Object previous = dvFields.get(field);
    if (previous != null && previous instanceof SortedDocValues) {
      return (SortedDocValues) previous;
    } else {
      FieldInfo fi = getDVField(field, DocValuesType.SORTED);
      if (fi == null) {
        return null;
      }
      SortedDocValues dv = docValuesProducer.getSorted(fi);
      dvFields.put(field, dv);
      return dv;
    }
  }
  
  @Override
  public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
    ensureOpen();
    Map<String,Object> dvFields = docValuesLocal.get();

    Object previous = dvFields.get(field);
    if (previous != null && previous instanceof SortedNumericDocValues) {
      return (SortedNumericDocValues) previous;
    } else {
      FieldInfo fi = getDVField(field, DocValuesType.SORTED_NUMERIC);
      if (fi == null) {
        return null;
      }
      SortedNumericDocValues dv = docValuesProducer.getSortedNumeric(fi);
      dvFields.put(field, dv);
      return dv;
    }
  }

  @Override
  public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
    ensureOpen();
    Map<String,Object> dvFields = docValuesLocal.get();
    
    Object previous = dvFields.get(field);
    if (previous != null && previous instanceof SortedSetDocValues) {
      return (SortedSetDocValues) previous;
    } else {
      FieldInfo fi = getDVField(field, DocValuesType.SORTED_SET);
      if (fi == null) {
        return null;
      }
      SortedSetDocValues dv = docValuesProducer.getSortedSet(fi);
      dvFields.put(field, dv);
      return dv;
    }
  }

  @Override
  public NumericDocValues getNormValues(String field) throws IOException {
    ensureOpen();
    return core.getNormValues(fieldInfos, field);
  }
  
  @Override
  public void addCoreClosedListener(CoreClosedListener listener) {
    ensureOpen();
    core.addCoreClosedListener(listener);
  }
  
  @Override
  public void removeCoreClosedListener(CoreClosedListener listener) {
    ensureOpen();
    core.removeCoreClosedListener(listener);
  }
  
  @Override
  public long ramBytesUsed() {
    ensureOpen();
    long ramBytesUsed = BASE_RAM_BYTES_USED;
    if (docValuesProducer != null) {
      ramBytesUsed += docValuesProducer.ramBytesUsed();
    }
    if (core != null) {
      ramBytesUsed += core.ramBytesUsed();
    }
    return ramBytesUsed;
  }
  
  @Override
  public void checkIntegrity() throws IOException {
    ensureOpen();
    
    // stored fields
    getFieldsReader().checkIntegrity();
    
    // term vectors
    TermVectorsReader termVectorsReader = getTermVectorsReader();
    if (termVectorsReader != null) {
      termVectorsReader.checkIntegrity();
    }
    
    // terms/postings
    if (core.fields != null) {
      core.fields.checkIntegrity();
    }
    
    // norms
    if (core.normsProducer != null) {
      core.normsProducer.checkIntegrity();
    }
    
    // docvalues
    if (docValuesProducer != null) {
      docValuesProducer.checkIntegrity();
    }
  }
}
