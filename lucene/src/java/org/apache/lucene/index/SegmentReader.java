package org.apache.lucene.index;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.store.Directory;
import org.apache.lucene.codecs.PerDocProducer;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.TermVectorsReader;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.search.FieldCache; // javadocs
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.BitVector;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.CloseableThreadLocal;

/**
 * @lucene.experimental
 */
public final class SegmentReader extends IndexReader {

  private final SegmentInfo si;
  private final ReaderContext readerContext = new AtomicReaderContext(this);
  private final CloseableThreadLocal<StoredFieldsReader> fieldsReaderLocal = new FieldsReaderLocal();
  private final CloseableThreadLocal<TermVectorsReader> termVectorsLocal = new CloseableThreadLocal<TermVectorsReader>();

  private final BitVector liveDocs;

  // Normally set to si.docCount - si.delDocCount, unless we
  // were created as an NRT reader from IW, in which case IW
  // tells us the docCount:
  private final int numDocs;

  private final SegmentCoreReaders core;

  /**
   * Sets the initial value 
   */
  private class FieldsReaderLocal extends CloseableThreadLocal<StoredFieldsReader> {
    @Override
    protected StoredFieldsReader initialValue() {
      return core.getFieldsReaderOrig().clone();
    }
  }
  
  /**
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public SegmentReader(SegmentInfo si, int termInfosIndexDivisor, IOContext context) throws IOException {
    this.si = si;
    boolean success = false;
    try {
      core = new SegmentCoreReaders(this, si.dir, si, context, termInfosIndexDivisor);
      if (si.hasDeletions()) {
        // NOTE: the bitvector is stored using the regular directory, not cfs
        liveDocs = new BitVector(directory(), si.getDelFileName(), new IOContext(IOContext.READ, true));
      } else {
        assert si.getDelCount() == 0;
        liveDocs = null;
      }
      numDocs = si.docCount - si.getDelCount();
      assert checkLiveCounts(false);
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

  // TODO: really these next 2 ctors could take
  // SegmentCoreReaders... that's all we do w/ the parent
  // SR:

  // Create new SegmentReader sharing core from a previous
  // SegmentReader and loading new live docs from a new
  // deletes file.  Used by openIfChanged.
  SegmentReader(SegmentInfo si, SegmentReader parent, IOContext context) throws IOException {
    assert si.dir == parent.getSegmentInfo().dir;
    this.si = si;

    // It's no longer possible to unDeleteAll, so, we can
    // only be created if we have deletions:
    assert si.hasDeletions();

    // ... but load our own deleted docs:
    liveDocs = new BitVector(si.dir, si.getDelFileName(), context);
    numDocs = si.docCount - si.getDelCount();
    assert checkLiveCounts(false);

    // We share core w/ parent:
    parent.core.incRef();
    core = parent.core;
  }

  // Create new SegmentReader sharing core from a previous
  // SegmentReader and using the provided in-memory
  // liveDocs.  Used by IndexWriter to provide a new NRT
  // reader:
  SegmentReader(SegmentReader parent, BitVector liveDocs, int numDocs) throws IOException {
    this.si = parent.si;
    parent.core.incRef();
    this.core = parent.core;

    assert liveDocs != null;
    this.liveDocs = liveDocs;

    this.numDocs = numDocs;

    assert checkLiveCounts(true);
  }

  @Override
  public Bits getLiveDocs() {
    ensureOpen();
    return liveDocs;
  }

  private boolean checkLiveCounts(boolean isNRT) throws IOException {
    if (liveDocs != null) {
      if (liveDocs.size() != si.docCount) {
        throw new CorruptIndexException("document count mismatch: deleted docs count " + liveDocs.size() + " vs segment doc count " + si.docCount + " segment=" + si.name);
      }

      final int recomputedCount = liveDocs.getRecomputedCount();
      // Verify BitVector is self consistent:
      assert liveDocs.count() == recomputedCount : "live count=" + liveDocs.count() + " vs recomputed count=" + recomputedCount;

      // Verify our docCount matches:
      assert numDocs == recomputedCount :
      "delete count mismatch: numDocs=" + numDocs + " vs BitVector=" + (si.docCount-recomputedCount);

      assert isNRT || si.docCount - si.getDelCount() == recomputedCount :
        "si.docCount=" + si.docCount + "si.getDelCount()=" + si.getDelCount() + " recomputedCount=" + recomputedCount;
    }
  
    return true;
  }

  /** @lucene.internal */
  public StoredFieldsReader getFieldsReader() {
    return fieldsReaderLocal.get();
  }
  
  @Override
  protected void doClose() throws IOException {
    //System.out.println("SR.close seg=" + si);
    termVectorsLocal.close();
    fieldsReaderLocal.close();
    if (core != null) {
      core.decRef();
    }
  }

  @Override
  public boolean hasDeletions() {
    // Don't call ensureOpen() here (it could affect performance)
    return liveDocs != null;
  }

  FieldInfos fieldInfos() {
    return core.fieldInfos;
  }

  public void document(int docID, StoredFieldVisitor visitor) throws CorruptIndexException, IOException {
    ensureOpen();
    if (docID < 0 || docID >= maxDoc()) {       
      throw new IllegalArgumentException("docID must be >= 0 and < maxDoc=" + maxDoc() + " (got docID=" + docID + ")");
    }
    getFieldsReader().visitDocument(docID, visitor);
  }

  @Override
  public Fields fields() throws IOException {
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
    return si.docCount;
  }

  /**
   * @see IndexReader#getFieldNames(org.apache.lucene.index.IndexReader.FieldOption)
   */
  @Override
  public Collection<String> getFieldNames(IndexReader.FieldOption fieldOption) {
    ensureOpen();

    Set<String> fieldSet = new HashSet<String>();
    for (FieldInfo fi : core.fieldInfos) {
      if (fieldOption == IndexReader.FieldOption.ALL) {
        fieldSet.add(fi.name);
      }
      else if (!fi.isIndexed && fieldOption == IndexReader.FieldOption.UNINDEXED) {
        fieldSet.add(fi.name);
      }
      else if (fi.indexOptions == IndexOptions.DOCS_ONLY && fieldOption == IndexReader.FieldOption.OMIT_TERM_FREQ_AND_POSITIONS) {
        fieldSet.add(fi.name);
      }
      else if (fi.indexOptions == IndexOptions.DOCS_AND_FREQS && fieldOption == IndexReader.FieldOption.OMIT_POSITIONS) {
        fieldSet.add(fi.name);
      }
      else if (fi.storePayloads && fieldOption == IndexReader.FieldOption.STORES_PAYLOADS) {
        fieldSet.add(fi.name);
      }
      else if (fi.isIndexed && fieldOption == IndexReader.FieldOption.INDEXED) {
        fieldSet.add(fi.name);
      }
      else if (fi.isIndexed && fi.storeTermVector == false && fieldOption == IndexReader.FieldOption.INDEXED_NO_TERMVECTOR) {
        fieldSet.add(fi.name);
      }
      else if (fi.storeTermVector == true &&
               fi.storePositionWithTermVector == false &&
               fi.storeOffsetWithTermVector == false &&
               fieldOption == IndexReader.FieldOption.TERMVECTOR) {
        fieldSet.add(fi.name);
      }
      else if (fi.isIndexed && fi.storeTermVector && fieldOption == IndexReader.FieldOption.INDEXED_WITH_TERMVECTOR) {
        fieldSet.add(fi.name);
      }
      else if (fi.storePositionWithTermVector && fi.storeOffsetWithTermVector == false && fieldOption == IndexReader.FieldOption.TERMVECTOR_WITH_POSITION) {
        fieldSet.add(fi.name);
      }
      else if (fi.storeOffsetWithTermVector && fi.storePositionWithTermVector == false && fieldOption == IndexReader.FieldOption.TERMVECTOR_WITH_OFFSET) {
        fieldSet.add(fi.name);
      }
      else if ((fi.storeOffsetWithTermVector && fi.storePositionWithTermVector) &&
                fieldOption == IndexReader.FieldOption.TERMVECTOR_WITH_POSITION_OFFSET) {
        fieldSet.add(fi.name);
      } 
      else if (fi.hasDocValues() && fieldOption == IndexReader.FieldOption.DOC_VALUES) {
        fieldSet.add(fi.name);
      }
    }
    return fieldSet;
  }

  @Override
  public boolean hasNorms(String field) {
    ensureOpen();
    FieldInfo fi = core.fieldInfos.fieldInfo(field);
    return fi != null && fi.isIndexed && !fi.omitNorms;
  }

  @Override
  public byte[] norms(String field) throws IOException {
    ensureOpen();
    return core.norms.norms(field);
  }

  /**
   * Create a clone from the initial TermVectorsReader and store it in the ThreadLocal.
   * @return TermVectorsReader
   * @lucene.internal
   */
  public TermVectorsReader getTermVectorsReader() {
    TermVectorsReader tvReader = termVectorsLocal.get();
    if (tvReader == null) {
      TermVectorsReader orig = core.getTermVectorsReaderOrig();
      if (orig == null) {
        return null;
      } else {
        tvReader = orig.clone();
      }
      termVectorsLocal.set(tvReader);
    }
    return tvReader;
  }

  /** Return a term frequency vector for the specified document and field. The
   *  vector returned contains term numbers and frequencies for all terms in
   *  the specified field of this document, if the field had storeTermVector
   *  flag set.  If the flag was not set, the method returns null.
   * @throws IOException
   */
  @Override
  public Fields getTermVectors(int docID) throws IOException {
    ensureOpen();
    TermVectorsReader termVectorsReader = getTermVectorsReader();
    if (termVectorsReader == null) {
      return null;
    }
    return termVectorsReader.get(docID);
  }

  @Override
  public String toString() {
    // SegmentInfo.toString takes dir and number of
    // *pending* deletions; so we reverse compute that here:
    return si.toString(core.dir, si.docCount - numDocs - si.getDelCount());
  }
  
  @Override
  public ReaderContext getTopReaderContext() {
    ensureOpen();
    return readerContext;
  }

  /**
   * Return the name of the segment this reader is reading.
   */
  public String getSegmentName() {
    return core.segment;
  }
  
  /**
   * Return the SegmentInfo of the segment this reader is reading.
   */
  SegmentInfo getSegmentInfo() {
    return si;
  }

  /** Returns the directory this index resides in. */
  @Override
  public Directory directory() {
    // Don't ensureOpen here -- in certain cases, when a
    // cloned/reopened reader needs to commit, it may call
    // this method on the closed original reader
    return core.dir;
  }

  // This is necessary so that cloned SegmentReaders (which
  // share the underlying postings data) will map to the
  // same entry in the FieldCache.  See LUCENE-1579.
  @Override
  public Object getCoreCacheKey() {
    return core;
  }

  @Override
  public Object getCombinedCoreAndDeletesKey() {
    return this;
  }
  
  @Override
  public int getTermInfosIndexDivisor() {
    return core.termsIndexDivisor;
  }
  
  @Override
  public DocValues docValues(String field) throws IOException {
    ensureOpen();
    final PerDocProducer perDoc = core.perDocProducer;
    if (perDoc == null) {
      return null;
    }
    return perDoc.docValues(field);
  }

  /**
   * Called when the shared core for this SegmentReader
   * is closed.
   * <p>
   * This listener is called only once all SegmentReaders 
   * sharing the same core are closed.  At this point it 
   * is safe for apps to evict this reader from any caches 
   * keyed on {@link #getCoreCacheKey}.  This is the same 
   * interface that {@link FieldCache} uses, internally, 
   * to evict entries.</p>
   * 
   * @lucene.experimental
   */
  public static interface CoreClosedListener {
    public void onClose(SegmentReader owner);
  }
  
  /** Expert: adds a CoreClosedListener to this reader's shared core */
  public void addCoreClosedListener(CoreClosedListener listener) {
    ensureOpen();
    core.addCoreClosedListener(listener);
  }
  
  /** Expert: removes a CoreClosedListener from this reader's shared core */
  public void removeCoreClosedListener(CoreClosedListener listener) {
    ensureOpen();
    core.removeCoreClosedListener(listener);
  }
}
