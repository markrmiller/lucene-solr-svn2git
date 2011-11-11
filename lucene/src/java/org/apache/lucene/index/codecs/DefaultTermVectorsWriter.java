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

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.MergePolicy.MergeAbortedException;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.StringHelper;

import java.io.IOException;

public final class DefaultTermVectorsWriter extends TermVectorsWriter {
  private final Directory directory;
  private final String segment;
  private IndexOutput tvx = null, tvd = null, tvf = null;

  public DefaultTermVectorsWriter(Directory directory, String segment, IOContext context) throws IOException {
    this.directory = directory;
    this.segment = segment;
    boolean success = false;
    try {
      // Open files for TermVector storage
      tvx = directory.createOutput(IndexFileNames.segmentFileName(segment, "", IndexFileNames.VECTORS_INDEX_EXTENSION), context);
      tvx.writeInt(DefaultTermVectorsReader.FORMAT_CURRENT);
      tvd = directory.createOutput(IndexFileNames.segmentFileName(segment, "", IndexFileNames.VECTORS_DOCUMENTS_EXTENSION), context);
      tvd.writeInt(DefaultTermVectorsReader.FORMAT_CURRENT);
      tvf = directory.createOutput(IndexFileNames.segmentFileName(segment, "", IndexFileNames.VECTORS_FIELDS_EXTENSION), context);
      tvf.writeInt(DefaultTermVectorsReader.FORMAT_CURRENT);
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(tvx, tvd, tvf);
      }
    }
  }

  /**
   * Add a complete document specified by all its term vectors. If document has no
   * term vectors, add value for tvx.
   *
   * @param vectors
   * @throws IOException
   */
  @Override
  public final void addAllDocVectors(TermFreqVector[] vectors, FieldInfos fieldInfos) throws IOException {

    tvx.writeLong(tvd.getFilePointer());
    tvx.writeLong(tvf.getFilePointer());

    if (vectors != null) {
      final int numFields = vectors.length;
      tvd.writeVInt(numFields);

      long[] fieldPointers = new long[numFields];

      for (int i=0; i<numFields; i++) {
        fieldPointers[i] = tvf.getFilePointer();

        final int fieldNumber = fieldInfos.fieldNumber(vectors[i].getField());

        // 1st pass: write field numbers to tvd
        tvd.writeVInt(fieldNumber);

        final int numTerms = vectors[i].size();
        tvf.writeVInt(numTerms);

        final TermPositionVector tpVector;

        final byte bits;
        final boolean storePositions;
        final boolean storeOffsets;

        if (vectors[i] instanceof TermPositionVector) {
          // May have positions & offsets
          tpVector = (TermPositionVector) vectors[i];
          storePositions = tpVector.size() > 0 && tpVector.getTermPositions(0) != null;
          storeOffsets = tpVector.size() > 0 && tpVector.getOffsets(0) != null;
          bits = (byte) ((storePositions ? DefaultTermVectorsReader.STORE_POSITIONS_WITH_TERMVECTOR : 0) +
                         (storeOffsets ? DefaultTermVectorsReader.STORE_OFFSET_WITH_TERMVECTOR : 0));
        } else {
          tpVector = null;
          bits = 0;
          storePositions = false;
          storeOffsets = false;
        }

        tvf.writeVInt(bits);

        final BytesRef[] terms = vectors[i].getTerms();
        final int[] freqs = vectors[i].getTermFrequencies();

        for (int j=0; j<numTerms; j++) {

          int start = j == 0 ? 0 : StringHelper.bytesDifference(terms[j-1].bytes,
                                                   terms[j-1].length,
                                                   terms[j].bytes,
                                                   terms[j].length);
          int length = terms[j].length - start;
          tvf.writeVInt(start);       // write shared prefix length
          tvf.writeVInt(length);        // write delta length
          tvf.writeBytes(terms[j].bytes, start, length);  // write delta bytes

          final int termFreq = freqs[j];

          tvf.writeVInt(termFreq);

          if (storePositions) {
            final int[] positions = tpVector.getTermPositions(j);
            if (positions == null)
              throw new IllegalStateException("Trying to write positions that are null!");
            assert positions.length == termFreq;

            // use delta encoding for positions
            int lastPosition = 0;
            for(int k=0;k<positions.length;k++) {
              final int position = positions[k];
              tvf.writeVInt(position-lastPosition);
              lastPosition = position;
            }
          }

          if (storeOffsets) {
            final TermVectorOffsetInfo[] offsets = tpVector.getOffsets(j);
            if (offsets == null)
              throw new IllegalStateException("Trying to write offsets that are null!");
            assert offsets.length == termFreq;

            // use delta encoding for offsets
            int lastEndOffset = 0;
            for(int k=0;k<offsets.length;k++) {
              final int startOffset = offsets[k].getStartOffset();
              final int endOffset = offsets[k].getEndOffset();
              tvf.writeVInt(startOffset-lastEndOffset);
              tvf.writeVInt(endOffset-startOffset);
              lastEndOffset = endOffset;
            }
          }
        }
      }

      // 2nd pass: write field pointers to tvd
      if (numFields > 1) {
        long lastFieldPointer = fieldPointers[0];
        for (int i=1; i<numFields; i++) {
          final long fieldPointer = fieldPointers[i];
          tvd.writeVLong(fieldPointer-lastFieldPointer);
          lastFieldPointer = fieldPointer;
        }
      }
    } else
      tvd.writeVInt(0);
  }
  
  @Override
  public void startDocument(int numVectorFields) throws IOException {
    this.numVectorFields = numVectorFields;
    tvx.writeLong(tvd.getFilePointer());
    tvx.writeLong(tvf.getFilePointer());
    tvd.writeVInt(numVectorFields);
    fieldCount = 0;
    fps = ArrayUtil.grow(fps, numVectorFields);
  }
  
  private long fps[] = new long[10]; // pointers to the tvf before writing each field 
  private int fieldCount = 0;        // number of fields we have written so far for this document
  private int numVectorFields = 0;   // total number of fields we will write for this document
  
  @Override
  public void startField(FieldInfo info, int numTerms, boolean positions, boolean offsets) throws IOException {
    lastTerm.length = 0;
    fps[fieldCount++] = tvf.getFilePointer();
    tvd.writeVInt(info.number);
    tvf.writeVInt(numTerms);
    byte bits = 0x0;
    if (positions)
      bits |= DefaultTermVectorsReader.STORE_POSITIONS_WITH_TERMVECTOR;
    if (offsets)
      bits |= DefaultTermVectorsReader.STORE_OFFSET_WITH_TERMVECTOR;
    tvf.writeByte(bits);
    
    assert fieldCount <= numVectorFields;
    if (fieldCount == numVectorFields) {
      // last field of the document
      // this is crazy because the file format is crazy!
      for (int i = 1; i < fieldCount; i++) {
        tvd.writeVLong(fps[i] - fps[i-1]);
      }
    }
  }
  
  private final BytesRef lastTerm = new BytesRef(10);

  @Override
  public void startTerm(BytesRef term, int freq) throws IOException {
    final int prefix = StringHelper.bytesDifference(lastTerm.bytes, lastTerm.offset, lastTerm.length, 
                                                    term.bytes, term.offset, term.length);
    final int suffix = term.length - prefix;
    tvf.writeVInt(prefix);
    tvf.writeVInt(suffix);
    tvf.writeBytes(term.bytes, term.offset + prefix, suffix);
    tvf.writeVInt(freq);
    lastTerm.copy(term);
    lastPosition = lastOffset = 0;
  }

  int lastPosition = 0;
  int lastOffset = 0;
  
  @Override
  public void addPosition(int position) throws IOException {
    tvf.writeVInt(position - lastPosition);
    lastPosition = position;
  }

  @Override
  public void addOffset(int startOffset, int endOffset) throws IOException {
    tvf.writeVInt(startOffset - lastOffset);
    tvf.writeVInt(endOffset - startOffset);
    lastOffset = startOffset;
  }

  @Override
  public void abort() {
    try {
      close();
    } catch (IOException ignored) {}
    
    try {
      directory.deleteFile(IndexFileNames.segmentFileName(segment, "", IndexFileNames.VECTORS_INDEX_EXTENSION));
    } catch (IOException ignored) {}
    
    try {
      directory.deleteFile(IndexFileNames.segmentFileName(segment, "", IndexFileNames.VECTORS_DOCUMENTS_EXTENSION));
    } catch (IOException ignored) {}
    
    try {
      directory.deleteFile(IndexFileNames.segmentFileName(segment, "", IndexFileNames.VECTORS_FIELDS_EXTENSION));
    } catch (IOException ignored) {}
  }

  /**
   * Do a bulk copy of numDocs documents from reader to our
   * streams.  This is used to expedite merging, if the
   * field numbers are congruent.
   */
  private void addRawDocuments(DefaultTermVectorsReader reader, int[] tvdLengths, int[] tvfLengths, int numDocs) throws IOException {
    long tvdPosition = tvd.getFilePointer();
    long tvfPosition = tvf.getFilePointer();
    long tvdStart = tvdPosition;
    long tvfStart = tvfPosition;
    for(int i=0;i<numDocs;i++) {
      tvx.writeLong(tvdPosition);
      tvdPosition += tvdLengths[i];
      tvx.writeLong(tvfPosition);
      tvfPosition += tvfLengths[i];
    }
    tvd.copyBytes(reader.getTvdStream(), tvdPosition-tvdStart);
    tvf.copyBytes(reader.getTvfStream(), tvfPosition-tvfStart);
    assert tvd.getFilePointer() == tvdPosition;
    assert tvf.getFilePointer() == tvfPosition;
  }

  @Override
  public final int merge(MergeState mergeState) throws IOException {
    // Used for bulk-reading raw bytes for term vectors
    int rawDocLengths[] = new int[MAX_RAW_MERGE_DOCS];
    int rawDocLengths2[] = new int[MAX_RAW_MERGE_DOCS];

    int idx = 0;
    int numDocs = 0;
    for (final MergeState.IndexReaderAndLiveDocs reader : mergeState.readers) {
      final SegmentReader matchingSegmentReader = mergeState.matchingSegmentReaders[idx++];
      DefaultTermVectorsReader matchingVectorsReader = null;
      if (matchingSegmentReader != null) {
        TermVectorsReader vectorsReader = matchingSegmentReader.getTermVectorsReader();

        if (vectorsReader != null && vectorsReader instanceof DefaultTermVectorsReader) {
          // If the TV* files are an older format then they cannot read raw docs:
          if (((DefaultTermVectorsReader)vectorsReader).canReadRawDocs()) {
            matchingVectorsReader = (DefaultTermVectorsReader) vectorsReader;
          }
        }
      }
      if (reader.liveDocs != null) {
        numDocs += copyVectorsWithDeletions(mergeState, matchingVectorsReader, reader, rawDocLengths, rawDocLengths2);
      } else {
        numDocs += copyVectorsNoDeletions(mergeState, matchingVectorsReader, reader, rawDocLengths, rawDocLengths2);
      }
    }
    finish(numDocs);
    return numDocs;
  }

  /** Maximum number of contiguous documents to bulk-copy
      when merging term vectors */
  private final static int MAX_RAW_MERGE_DOCS = 4192;

  private int copyVectorsWithDeletions(MergeState mergeState,
                                        final DefaultTermVectorsReader matchingVectorsReader,
                                        final MergeState.IndexReaderAndLiveDocs reader,
                                        int rawDocLengths[],
                                        int rawDocLengths2[])
          throws IOException, MergeAbortedException {
    final int maxDoc = reader.reader.maxDoc();
    final Bits liveDocs = reader.liveDocs;
    int totalNumDocs = 0;
    if (matchingVectorsReader != null) {
      // We can bulk-copy because the fieldInfos are "congruent"
      for (int docNum = 0; docNum < maxDoc;) {
        if (!liveDocs.get(docNum)) {
          // skip deleted docs
          ++docNum;
          continue;
        }
        // We can optimize this case (doing a bulk byte copy) since the field
        // numbers are identical
        int start = docNum, numDocs = 0;
        do {
          docNum++;
          numDocs++;
          if (docNum >= maxDoc) break;
          if (!liveDocs.get(docNum)) {
            docNum++;
            break;
          }
        } while(numDocs < MAX_RAW_MERGE_DOCS);
        
        matchingVectorsReader.rawDocs(rawDocLengths, rawDocLengths2, start, numDocs);
        addRawDocuments(matchingVectorsReader, rawDocLengths, rawDocLengths2, numDocs);
        totalNumDocs += numDocs;
        mergeState.checkAbort.work(300 * numDocs);
      }
    } else {
      for (int docNum = 0; docNum < maxDoc; docNum++) {
        if (!liveDocs.get(docNum)) {
          // skip deleted docs
          continue;
        }
        
        // NOTE: it's very important to first assign to vectors then pass it to
        // termVectorsWriter.addAllDocVectors; see LUCENE-1282
        TermFreqVector[] vectors = reader.reader.getTermFreqVectors(docNum);
        addAllDocVectors(vectors, mergeState.fieldInfos);
        totalNumDocs++;
        mergeState.checkAbort.work(300);
      }
    }
    return totalNumDocs;
  }
  
  private int copyVectorsNoDeletions(MergeState mergeState,
                                      final DefaultTermVectorsReader matchingVectorsReader,
                                      final MergeState.IndexReaderAndLiveDocs reader,
                                      int rawDocLengths[],
                                      int rawDocLengths2[])
          throws IOException, MergeAbortedException {
    final int maxDoc = reader.reader.maxDoc();
    if (matchingVectorsReader != null) {
      // We can bulk-copy because the fieldInfos are "congruent"
      int docCount = 0;
      while (docCount < maxDoc) {
        int len = Math.min(MAX_RAW_MERGE_DOCS, maxDoc - docCount);
        matchingVectorsReader.rawDocs(rawDocLengths, rawDocLengths2, docCount, len);
        addRawDocuments(matchingVectorsReader, rawDocLengths, rawDocLengths2, len);
        docCount += len;
        mergeState.checkAbort.work(300 * len);
      }
    } else {
      for (int docNum = 0; docNum < maxDoc; docNum++) {
        // NOTE: it's very important to first assign to vectors then pass it to
        // termVectorsWriter.addAllDocVectors; see LUCENE-1282
        TermFreqVector[] vectors = reader.reader.getTermFreqVectors(docNum);
        addAllDocVectors(vectors, mergeState.fieldInfos);
        mergeState.checkAbort.work(300);
      }
    }
    return maxDoc;
  }
  
  @Override
  public void finish(int numDocs) throws IOException {
    if (4+((long) numDocs)*16 != tvx.getFilePointer())
      // This is most likely a bug in Sun JRE 1.6.0_04/_05;
      // we detect that the bug has struck, here, and
      // throw an exception to prevent the corruption from
      // entering the index.  See LUCENE-1282 for
      // details.
      throw new RuntimeException("mergeVectors produced an invalid result: mergedDocs is " + numDocs + " but tvx size is " + tvx.getFilePointer() + " file=" + tvx.toString() + "; now aborting this merge to prevent index corruption");
  }

  /** Close all streams. */
  @Override
  public void close() throws IOException {
    // make an effort to close all streams we can but remember and re-throw
    // the first exception encountered in this process
    IOUtils.close(tvx, tvd, tvf);
    tvx = tvd = tvf = null;
  }
}
