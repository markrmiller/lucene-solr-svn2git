package org.apache.lucene.index.codecs;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.FieldsEnum;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

public class DefaultTermVectorsReader extends TermVectorsReader {

  // NOTE: if you make a new format, it must be larger than
  // the current format

  // Changed strings to UTF8 with length-in-bytes not length-in-chars
  static final int FORMAT_UTF8_LENGTH_IN_BYTES = 4;

  // NOTE: always change this if you switch to a new format!
  // whenever you add a new format, make it 1 larger (positive version logic)!
  static final int FORMAT_CURRENT = FORMAT_UTF8_LENGTH_IN_BYTES;
  
  // when removing support for old versions, leave the last supported version here
  static final int FORMAT_MINIMUM = FORMAT_UTF8_LENGTH_IN_BYTES;

  //The size in bytes that the FORMAT_VERSION will take up at the beginning of each file 
  static final int FORMAT_SIZE = 4;

  static final byte STORE_POSITIONS_WITH_TERMVECTOR = 0x1;

  static final byte STORE_OFFSET_WITH_TERMVECTOR = 0x2;
  
  /** Extension of vectors fields file */
  // TODO: make pkg-private after we remove/refactor fileExists check in SI or drop 3.x support
  public static final String VECTORS_FIELDS_EXTENSION = "tvf";

  /** Extension of vectors documents file */
  // TODO: make pkg-private after we remove/refactor fileExists check in SI or drop 3.x support
  public static final String VECTORS_DOCUMENTS_EXTENSION = "tvd";

  /** Extension of vectors index file */
  // TODO: make pkg-private after we remove/refactor fileExists check in SI or drop 3.x support
  public static final String VECTORS_INDEX_EXTENSION = "tvx";

  private FieldInfos fieldInfos;

  private IndexInput tvx;
  private IndexInput tvd;
  private IndexInput tvf;
  private int size;
  private int numTotalDocs;

  // The docID offset where our docs begin in the index
  // file.  This will be 0 if we have our own private file.
  private int docStoreOffset;
  
  private final int format;

  // used by clone
  DefaultTermVectorsReader(FieldInfos fieldInfos, IndexInput tvx, IndexInput tvd, IndexInput tvf, int size, int numTotalDocs, int docStoreOffset, int format) {
    this.fieldInfos = fieldInfos;
    this.tvx = tvx;
    this.tvd = tvd;
    this.tvf = tvf;
    this.size = size;
    this.numTotalDocs = numTotalDocs;
    this.docStoreOffset = docStoreOffset;
    this.format = format;
  }
    
  public DefaultTermVectorsReader(Directory d, SegmentInfo si, FieldInfos fieldInfos, IOContext context)
    throws CorruptIndexException, IOException {
    final String segment = si.getDocStoreSegment();
    final int docStoreOffset = si.getDocStoreOffset();
    final int size = si.docCount;
    
    boolean success = false;

    try {
      String idxName = IndexFileNames.segmentFileName(segment, "", VECTORS_INDEX_EXTENSION);
      tvx = d.openInput(idxName, context);
      format = checkValidFormat(tvx, idxName);
      String fn = IndexFileNames.segmentFileName(segment, "", VECTORS_DOCUMENTS_EXTENSION);
      tvd = d.openInput(fn, context);
      final int tvdFormat = checkValidFormat(tvd, fn);
      fn = IndexFileNames.segmentFileName(segment, "", VECTORS_FIELDS_EXTENSION);
      tvf = d.openInput(fn, context);
      final int tvfFormat = checkValidFormat(tvf, fn);

      assert format == tvdFormat;
      assert format == tvfFormat;

      numTotalDocs = (int) (tvx.length() >> 4);

      if (-1 == docStoreOffset) {
        this.docStoreOffset = 0;
        this.size = numTotalDocs;
        assert size == 0 || numTotalDocs == size;
      } else {
        this.docStoreOffset = docStoreOffset;
        this.size = size;
        // Verify the file is long enough to hold all of our
        // docs
        assert numTotalDocs >= size + docStoreOffset: "numTotalDocs=" + numTotalDocs + " size=" + size + " docStoreOffset=" + docStoreOffset;
      }

      this.fieldInfos = fieldInfos;
      success = true;
    } finally {
      // With lock-less commits, it's entirely possible (and
      // fine) to hit a FileNotFound exception above. In
      // this case, we want to explicitly close any subset
      // of things that were opened so that we don't have to
      // wait for a GC to do so.
      if (!success) {
        close();
      }
    }
  }

  // Used for bulk copy when merging
  IndexInput getTvdStream() {
    return tvd;
  }

  // Used for bulk copy when merging
  IndexInput getTvfStream() {
    return tvf;
  }

  private void seekTvx(final int docNum) throws IOException {
    tvx.seek((docNum + docStoreOffset) * 16L + FORMAT_SIZE);
  }

  boolean canReadRawDocs() {
    // we can always read raw docs, unless the term vectors
    // didn't exist
    return format != 0;
  }

  /** Retrieve the length (in bytes) of the tvd and tvf
   *  entries for the next numDocs starting with
   *  startDocID.  This is used for bulk copying when
   *  merging segments, if the field numbers are
   *  congruent.  Once this returns, the tvf & tvd streams
   *  are seeked to the startDocID. */
  final void rawDocs(int[] tvdLengths, int[] tvfLengths, int startDocID, int numDocs) throws IOException {

    if (tvx == null) {
      Arrays.fill(tvdLengths, 0);
      Arrays.fill(tvfLengths, 0);
      return;
    }

    seekTvx(startDocID);

    long tvdPosition = tvx.readLong();
    tvd.seek(tvdPosition);

    long tvfPosition = tvx.readLong();
    tvf.seek(tvfPosition);

    long lastTvdPosition = tvdPosition;
    long lastTvfPosition = tvfPosition;

    int count = 0;
    while (count < numDocs) {
      final int docID = docStoreOffset + startDocID + count + 1;
      assert docID <= numTotalDocs;
      if (docID < numTotalDocs)  {
        tvdPosition = tvx.readLong();
        tvfPosition = tvx.readLong();
      } else {
        tvdPosition = tvd.length();
        tvfPosition = tvf.length();
        assert count == numDocs-1;
      }
      tvdLengths[count] = (int) (tvdPosition-lastTvdPosition);
      tvfLengths[count] = (int) (tvfPosition-lastTvfPosition);
      count++;
      lastTvdPosition = tvdPosition;
      lastTvfPosition = tvfPosition;
    }
  }

  private int checkValidFormat(IndexInput in, String fn) throws CorruptIndexException, IOException
  {
    int format = in.readInt();
    if (format < FORMAT_MINIMUM)
      throw new IndexFormatTooOldException(in, format, FORMAT_MINIMUM, FORMAT_CURRENT);
    if (format > FORMAT_CURRENT)
      throw new IndexFormatTooNewException(in, format, FORMAT_MINIMUM, FORMAT_CURRENT);
    return format;
  }

  public void close() throws IOException {
    IOUtils.close(tvx, tvd, tvf);
  }

  /**
   * 
   * @return The number of documents in the reader
   */
  int size() {
    return size;
  }

  private class TVFields extends Fields {
    // nocommit make hashmap so .terms(String) is O(1)
    private final int[] fieldNumbers;
    private final long[] fieldFPs;
    private final int docID;

    public TVFields(int docID) throws IOException {
      this.docID = docID;
      seekTvx(docID);
      tvd.seek(tvx.readLong());
      
      final int fieldCount = tvd.readVInt();
      assert fieldCount >= 0;
      if (fieldCount != 0) {
        fieldNumbers = new int[fieldCount];
        fieldFPs = new long[fieldCount];
        for(int fieldUpto=0;fieldUpto<fieldCount;fieldUpto++) {
          // nocommit i think this are already sorted
          // correctly during write...?
          fieldNumbers[fieldUpto] = tvd.readVInt();
        }

        long position = tvx.readLong();
        fieldFPs[0] = position;
        for(int fieldUpto=1;fieldUpto<fieldCount;fieldUpto++) {
          position += tvd.readVLong();
          fieldFPs[fieldUpto] = position;
        }
      } else {
        // nocommit: why do we write docs w/ 0 vectors!?
        // and... can we return null (Fields) in this case...?
        fieldNumbers = null;
        fieldFPs = null;
      }
    }
    
    @Override
    public FieldsEnum iterator() throws IOException {

      return new FieldsEnum() {
        private int fieldUpto;

        @Override
        public String next() throws IOException {
          if (fieldNumbers != null && fieldUpto < fieldNumbers.length) {
            return fieldInfos.fieldName(fieldNumbers[fieldUpto++]);
          } else {
            return null;
          }
        }

        @Override
        public TermsEnum terms() throws IOException {
          tvf.seek(fieldFPs[fieldUpto-1]);
          final int numTerms = tvf.readVInt();
          return new TVTermsEnum(docID, numTerms);
        }
      };
    }

    @Override
    public Terms terms(String field) throws IOException {
      final FieldInfo fieldInfo = fieldInfos.fieldInfo(field);
      if (fieldInfo == null) {
        // No such field
        return null;
      }

      for(int fieldUpto=0;fieldUpto<fieldNumbers.length;fieldUpto++) {
        if (fieldInfo.number == fieldNumbers[fieldUpto]) {
          return new TVTerms(docID, fieldFPs[fieldUpto]);
        }
      }

      // Field exists, but was not TVd for this doc
      return null;
    }

    @Override
    public int getUniqueFieldCount() {
      if (fieldNumbers == null) {
        return 0;
      } else {
        return fieldNumbers.length;
      }
    }
  }

  private class TVTerms extends Terms {
    private final int numTerms;
    private final int docID;

    public TVTerms(int docID, long tvfFP) throws IOException {
      this.docID = docID;
      tvf.seek(tvfFP);
      numTerms = tvf.readVInt();
    }

    @Override
    public TermsEnum iterator() throws IOException {
      return new TVTermsEnum(docID, numTerms);
    }

    @Override
    public long getUniqueTermCount() {
      return numTerms;
    }

    @Override
    public long getSumTotalTermFreq() {
      return -1;
    }

    @Override
    public long getSumDocFreq() {
      // Every term occurs in just one doc:
      return numTerms;
    }

    @Override
    public int getDocCount() {
      return 1;
    }

    @Override
    public Comparator<BytesRef> getComparator() {
      // TODO: really indexer hardwires
      // this...?  I guess codec could buffer and re-sort...
      return BytesRef.getUTF8SortedAsUnicodeComparator();
    }
  }

  private class TVTermsEnum extends TermsEnum {
    private final int numTerms;
    private final int docID;
    private int nextTerm;
    private int freq;
    private BytesRef lastTerm = new BytesRef();
    private BytesRef term = new BytesRef();
    private final boolean storePositions;
    private final boolean storeOffsets;
    private final long tvfFP;

    private int[] positions;
    private int[] startOffsets;
    private int[] endOffsets;

    // NOTE: tvf is pre-positioned by caller
    public TVTermsEnum(int docID, int numTerms) throws IOException {
      this.numTerms = numTerms;
      this.docID = docID;
    
      final byte bits = tvf.readByte();
      storePositions = (bits & STORE_POSITIONS_WITH_TERMVECTOR) != 0;
      storeOffsets = (bits & STORE_OFFSET_WITH_TERMVECTOR) != 0;
      
      tvfFP = tvf.getFilePointer();
    }

    // NOTE: slow!  (linear scan)
    @Override
    public SeekStatus seekCeil(BytesRef text, boolean useCache)
      throws IOException {
      if (nextTerm != 0 && text.compareTo(term) < 0) {
        nextTerm = 0;
        tvf.seek(tvfFP);
      }

      while (next() != null) {
        final int cmp = text.compareTo(term);
        if (cmp < 0) {
          return SeekStatus.NOT_FOUND;
        } else if (cmp == 0) {
          return SeekStatus.FOUND;
        }
      }

      return SeekStatus.END;
    }

    @Override
    public void seekExact(long ord) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BytesRef next() throws IOException {
      if (nextTerm >= numTerms) {
        return null;
      }
      term.copy(lastTerm);
      final int start = tvf.readVInt();
      final int deltaLen = tvf.readVInt();
      term.length = start + deltaLen;
      term.grow(term.length);
      tvf.readBytes(term.bytes, start, deltaLen);
      freq = tvf.readVInt();

      if (storePositions) {
        // TODO: we could maybe reuse last array, if we can
        // somehow be careful about consumer never using two
        // D&PEnums at once...
        positions = new int[freq];
        int pos = 0;
        for(int posUpto=0;posUpto<freq;posUpto++) {
          pos += tvf.readVInt();
          positions[posUpto] = pos;
        }
      }

      if (storeOffsets) {
        startOffsets = new int[freq];
        endOffsets = new int[freq];
        int offset = 0;
        for(int posUpto=0;posUpto<freq;posUpto++) {
          startOffsets[posUpto] = offset + tvf.readVInt();
          offset = endOffsets[posUpto] = startOffsets[posUpto] + tvf.readVInt();
        }
      }

      lastTerm.copy(term);
      nextTerm++;
      return term;
    }

    @Override
    public BytesRef term() {
      return term;
    }

    @Override
    public long ord() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int docFreq() {
      return 1;
    }

    @Override
    public long totalTermFreq() {
      return freq;
    }

    @Override
    public DocsEnum docs(Bits liveDocs, DocsEnum reuse) throws IOException {
      TVDocsEnum docsEnum;
      if (reuse != null && reuse instanceof TVDocsEnum) {
        docsEnum = (TVDocsEnum) reuse;
      } else {
        docsEnum = new TVDocsEnum();
      }
      docsEnum.reset(liveDocs, docID, freq);
      return docsEnum;
    }

    @Override
    public DocsAndPositionsEnum docsAndPositions(Bits liveDocs, DocsAndPositionsEnum reuse) throws IOException {
      if (!storePositions && !storeOffsets) {
        return null;
      }
      
      TVDocsAndPositionsEnum docsAndPositionsEnum;
      if (reuse != null) {
        docsAndPositionsEnum = (TVDocsAndPositionsEnum) reuse;
        if (docsAndPositionsEnum.canReuse(storeOffsets)) {
          docsAndPositionsEnum = (TVDocsAndPositionsEnum) reuse;
        } else {
          docsAndPositionsEnum = new TVDocsAndPositionsEnum(storeOffsets);
        }
      } else {
        docsAndPositionsEnum = new TVDocsAndPositionsEnum(storeOffsets);
      }
      docsAndPositionsEnum.reset(liveDocs, docID, positions, startOffsets, endOffsets);
      return docsAndPositionsEnum;
    }

    @Override
    public Comparator<BytesRef> getComparator() {
      // TODO: really indexer hardwires
      // this...?  I guess codec could buffer and re-sort...
      return BytesRef.getUTF8SortedAsUnicodeComparator();
    }
  }

  // nocommit not really useful?  you can get the freq from
  // .totalTF from the TermsEnum...
  private static class TVDocsEnum extends DocsEnum {
    private boolean didNext;
    private int docID;
    private int freq;
    private Bits liveDocs;

    @Override
    public int freq() {
      return freq;
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int nextDoc() {
      if (!didNext && (liveDocs == null || liveDocs.get(docID))) {
        didNext = true;
        return docID;
      } else {
        return NO_MORE_DOCS;
      }
    }

    @Override
    public int advance(int target) {
      if (!didNext && target <= docID) {
        return nextDoc();
      } else {
        return NO_MORE_DOCS;
      }
    }

    public void reset(Bits liveDocs, int docID, int freq) {
      this.liveDocs = liveDocs;
      this.docID = docID;
      this.freq = freq;
      didNext = false;
    }
  }

  private static class TVDocsAndPositionsEnum extends DocsAndPositionsEnum {
    private final OffsetAttribute offsetAtt;
    private boolean didNext;
    private int docID;
    private int nextPos;
    private Bits liveDocs;
    private int[] positions;
    private int[] startOffsets;
    private int[] endOffsets;

    public TVDocsAndPositionsEnum(boolean storeOffsets) {
      if (storeOffsets) {
        offsetAtt = attributes().addAttribute(OffsetAttribute.class);
      } else {
        offsetAtt = null;
      }
    }

    public boolean canReuse(boolean storeOffsets) {
      return storeOffsets == (offsetAtt != null);
    }

    @Override
    public int freq() {
      if (positions != null) {
        return positions.length;
      } else {
        assert startOffsets != null;
        return startOffsets.length;
      }
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int nextDoc() {
      if (!didNext && (liveDocs == null || liveDocs.get(docID))) {
        didNext = true;
        return docID;
      } else {
        return NO_MORE_DOCS;
      }
    }

    @Override
    public int advance(int target) {
      if (!didNext && target <= docID) {
        return nextDoc();
      } else {
        return NO_MORE_DOCS;
      }
    }

    public void reset(Bits liveDocs, int docID, int[] positions, int[] startOffsets, int[] endOffsets) {
      this.liveDocs = liveDocs;
      this.docID = docID;
      this.positions = positions;
      this.startOffsets = startOffsets;
      this.endOffsets = endOffsets;
      didNext = false;
      nextPos = 0;
    }

    @Override
    public BytesRef getPayload() {
      return null;
    }

    @Override
    public boolean hasPayload() {
      return false;
    }

    @Override
    public int nextPosition() {
      assert (positions != null && nextPos < positions.length) ||
        startOffsets != null && nextPos < startOffsets.length;

      if (startOffsets != null) {
        offsetAtt.setOffset(startOffsets[nextPos],
                            endOffsets[nextPos]);
      }
      if (positions != null) {
        return positions[nextPos++];
      } else {
        nextPos++;
        return -1;
      }
    }
  }

  @Override
  public Fields get(int docID) throws IOException {
    if (docID < 0 || docID >= numTotalDocs) {
      throw new IllegalArgumentException("doID=" + docID + " is out of bounds [0.." + (numTotalDocs-1) + "]");
    }
    if (tvx != null) {
      Fields fields = new TVFields(docID);
      if (fields.getUniqueFieldCount() == 0) {
        // nocommit writer should write in this case!?
        return null;
      } else {
        return fields;
      }
    } else {
      return null;
    }
  }

  @Override
  public TermVectorsReader clone() {
    IndexInput cloneTvx = null;
    IndexInput cloneTvd = null;
    IndexInput cloneTvf = null;

    // These are null when a TermVectorsReader was created
    // on a segment that did not have term vectors saved
    if (tvx != null && tvd != null && tvf != null) {
      cloneTvx = (IndexInput) tvx.clone();
      cloneTvd = (IndexInput) tvd.clone();
      cloneTvf = (IndexInput) tvf.clone();
    }
    
    return new DefaultTermVectorsReader(fieldInfos, cloneTvx, cloneTvd, cloneTvf, size, numTotalDocs, docStoreOffset, format);
  }
  
  public static void files(Directory dir, SegmentInfo info, Set<String> files) throws IOException {
    if (info.getHasVectors()) {
      if (info.getDocStoreOffset() != -1) {
        assert info.getDocStoreSegment() != null;
        if (!info.getDocStoreIsCompoundFile()) {
          files.add(IndexFileNames.segmentFileName(info.getDocStoreSegment(), "", VECTORS_INDEX_EXTENSION));
          files.add(IndexFileNames.segmentFileName(info.getDocStoreSegment(), "", VECTORS_FIELDS_EXTENSION));
          files.add(IndexFileNames.segmentFileName(info.getDocStoreSegment(), "", VECTORS_DOCUMENTS_EXTENSION));
        }
      } else {
        files.add(IndexFileNames.segmentFileName(info.name, "", VECTORS_INDEX_EXTENSION));
        files.add(IndexFileNames.segmentFileName(info.name, "", VECTORS_FIELDS_EXTENSION));
        files.add(IndexFileNames.segmentFileName(info.name, "", VECTORS_DOCUMENTS_EXTENSION));
      }
    }
  }
}

