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
import java.util.Arrays;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.index.IndexReader.ReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.ReaderUtil;

public class TestDocsAndPositions extends LuceneTestCase {
  private String fieldName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    fieldName = "field" + random.nextInt();
  }

  /**
   * Simple testcase for {@link DocsAndPositionsEnum}
   */
  public void testPositionsSimple() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random, directory,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    for (int i = 0; i < 39; i++) {
      Document doc = new Document();
      FieldType customType = new FieldType(TextField.TYPE_UNSTORED);
      customType.setOmitNorms(true);
      doc.add(newField(fieldName, "1 2 3 4 5 6 7 8 9 10 "
          + "1 2 3 4 5 6 7 8 9 10 " + "1 2 3 4 5 6 7 8 9 10 "
          + "1 2 3 4 5 6 7 8 9 10", customType));
      writer.addDocument(doc);
    }
    IndexReader reader = writer.getReader();
    writer.close();

    int num = atLeast(13);
    for (int i = 0; i < num; i++) {
      BytesRef bytes = new BytesRef("1");
      ReaderContext topReaderContext = reader.getTopReaderContext();
      AtomicReaderContext[] leaves = ReaderUtil.leaves(topReaderContext);
      for (AtomicReaderContext atomicReaderContext : leaves) {
        DocsAndPositionsEnum docsAndPosEnum = getDocsAndPositions(
            atomicReaderContext.reader, bytes, null);
        assertNotNull(docsAndPosEnum);
        if (atomicReaderContext.reader.maxDoc() == 0) {
          continue;
        }
        final int advance = docsAndPosEnum.advance(random.nextInt(atomicReaderContext.reader.maxDoc()));
        do {
          String msg = "Advanced to: " + advance + " current doc: "
              + docsAndPosEnum.docID(); // TODO: + " usePayloads: " + usePayload;
          assertEquals(msg, 4, docsAndPosEnum.freq());
          assertEquals(msg, 0, docsAndPosEnum.nextPosition());
          assertEquals(msg, 4, docsAndPosEnum.freq());
          assertEquals(msg, 10, docsAndPosEnum.nextPosition());
          assertEquals(msg, 4, docsAndPosEnum.freq());
          assertEquals(msg, 20, docsAndPosEnum.nextPosition());
          assertEquals(msg, 4, docsAndPosEnum.freq());
          assertEquals(msg, 30, docsAndPosEnum.nextPosition());
        } while (docsAndPosEnum.nextDoc() != DocsAndPositionsEnum.NO_MORE_DOCS);
      }
    }
    reader.close();
    directory.close();
  }

  public DocsAndPositionsEnum getDocsAndPositions(IndexReader reader,
      BytesRef bytes, Bits liveDocs) throws IOException {
      return reader.termPositionsEnum(null, fieldName, bytes);
  }

  public DocsEnum getDocsEnum(IndexReader reader, BytesRef bytes,
      boolean freqs, Bits liveDocs) throws IOException {
    int randInt = random.nextInt(10);
    if (randInt == 0) { // once in a while throw in a positions enum
      return getDocsAndPositions(reader, bytes, liveDocs);
    } else {
      return reader.termDocsEnum(liveDocs, fieldName, bytes);
    } 
  }

  /**
   * this test indexes random numbers within a range into a field and checks
   * their occurrences by searching for a number from that range selected at
   * random. All positions for that number are saved up front and compared to
   * the enums positions.
   */
  public void testRandomPositions() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random, dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMergePolicy(newLogMergePolicy()));
    int numDocs = atLeast(47);
    int max = 1051;
    int term = random.nextInt(max);
    Integer[][] positionsInDoc = new Integer[numDocs][];
    FieldType customType = new FieldType(TextField.TYPE_UNSTORED);
    customType.setOmitNorms(true);
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      ArrayList<Integer> positions = new ArrayList<Integer>();
      StringBuilder builder = new StringBuilder();
      int num = atLeast(131);
      for (int j = 0; j < num; j++) {
        int nextInt = random.nextInt(max);
        builder.append(nextInt).append(" ");
        if (nextInt == term) {
          positions.add(Integer.valueOf(j));
        }
      }
      if (positions.size() == 0) {
        builder.append(term);
        positions.add(num);
      }
      doc.add(newField(fieldName, builder.toString(), customType));
      positionsInDoc[i] = positions.toArray(new Integer[0]);
      writer.addDocument(doc);
    }

    IndexReader reader = writer.getReader();
    writer.close();

    int num = atLeast(13);
    for (int i = 0; i < num; i++) {
      BytesRef bytes = new BytesRef("" + term);
      ReaderContext topReaderContext = reader.getTopReaderContext();
      AtomicReaderContext[] leaves = ReaderUtil.leaves(topReaderContext);
      for (AtomicReaderContext atomicReaderContext : leaves) {
        DocsAndPositionsEnum docsAndPosEnum = getDocsAndPositions(
            atomicReaderContext.reader, bytes, null);
        assertNotNull(docsAndPosEnum);
        int initDoc = 0;
        int maxDoc = atomicReaderContext.reader.maxDoc();
        // initially advance or do next doc
        if (random.nextBoolean()) {
          initDoc = docsAndPosEnum.nextDoc();
        } else {
          initDoc = docsAndPosEnum.advance(random.nextInt(maxDoc));
        }
        // now run through the scorer and check if all positions are there...
        do {
          int docID = docsAndPosEnum.docID();
          if (docID == DocsAndPositionsEnum.NO_MORE_DOCS) {
            break;
          }
          Integer[] pos = positionsInDoc[atomicReaderContext.docBase + docID];
          assertEquals(pos.length, docsAndPosEnum.freq());
          // number of positions read should be random - don't read all of them
          // allways
          final int howMany = random.nextInt(20) == 0 ? pos.length
              - random.nextInt(pos.length) : pos.length;
          for (int j = 0; j < howMany; j++) {
            assertEquals("iteration: " + i + " initDoc: " + initDoc + " doc: "
                + docID + " base: " + atomicReaderContext.docBase
                + " positions: " + Arrays.toString(pos) /* TODO: + " usePayloads: "
                + usePayload*/, pos[j].intValue(), docsAndPosEnum.nextPosition());
          }

          if (random.nextInt(10) == 0) { // once is a while advance
            docsAndPosEnum
                .advance(docID + 1 + random.nextInt((maxDoc - docID)));
          }

        } while (docsAndPosEnum.nextDoc() != DocsAndPositionsEnum.NO_MORE_DOCS);
      }

    }
    reader.close();
    dir.close();
  }

  public void testRandomDocs() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random, dir,
                                                     newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMergePolicy(newLogMergePolicy()));
    int numDocs = atLeast(49);
    int max = 15678;
    int term = random.nextInt(max);
    int[] freqInDoc = new int[numDocs];
    FieldType customType = new FieldType(TextField.TYPE_UNSTORED);
    customType.setOmitNorms(true);
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      StringBuilder builder = new StringBuilder();
      for (int j = 0; j < 199; j++) {
        int nextInt = random.nextInt(max);
        builder.append(nextInt).append(' ');
        if (nextInt == term) {
          freqInDoc[i]++;
        }
      }
      doc.add(newField(fieldName, builder.toString(), customType));
      writer.addDocument(doc);
    }

    IndexReader reader = writer.getReader();
    writer.close();

    int num = atLeast(13);
    for (int i = 0; i < num; i++) {
      BytesRef bytes = new BytesRef("" + term);
      ReaderContext topReaderContext = reader.getTopReaderContext();
      AtomicReaderContext[] leaves = ReaderUtil.leaves(topReaderContext);
      for (AtomicReaderContext context : leaves) {
        int maxDoc = context.reader.maxDoc();
        DocsEnum docsAndPosEnum = getDocsEnum(context.reader, bytes, true, null);
        if (findNext(freqInDoc, context.docBase, context.docBase + maxDoc) == Integer.MAX_VALUE) {
          assertNull(docsAndPosEnum);
          continue;
        }
        assertNotNull(docsAndPosEnum);
        docsAndPosEnum.nextDoc();
        for (int j = 0; j < maxDoc; j++) {
          if (freqInDoc[context.docBase + j] != 0) {
            assertEquals(j, docsAndPosEnum.docID());
            assertEquals(docsAndPosEnum.freq(), freqInDoc[context.docBase +j]);
            if (i % 2 == 0 && random.nextInt(10) == 0) {
              int next = findNext(freqInDoc, context.docBase+j+1, context.docBase + maxDoc) - context.docBase;
              int advancedTo = docsAndPosEnum.advance(next);
              if (next >= maxDoc) {
                assertEquals(DocsEnum.NO_MORE_DOCS, advancedTo);
              } else {
                assertTrue("advanced to: " +advancedTo + " but should be <= " + next, next >= advancedTo);  
              }
            } else {
              docsAndPosEnum.nextDoc();
            }
          } 
        }
        assertEquals("docBase: " + context.docBase + " maxDoc: " + maxDoc + " " + docsAndPosEnum.getClass(), DocsEnum.NO_MORE_DOCS, docsAndPosEnum.docID());
      }
      
    }

    reader.close();
    dir.close();
  }
  
  private static int findNext(int[] docs, int pos, int max) {
    for (int i = pos; i < max; i++) {
      if( docs[i] != 0) {
        return i;
      }
    }
    return Integer.MAX_VALUE;
  }

  /**
   * tests retrieval of positions for terms that have a large number of
   * occurrences to force test of buffer refill during positions iteration.
   */
  public void testLargeNumberOfPositions() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random, dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    int howMany = 1000;
    FieldType customType = new FieldType(TextField.TYPE_UNSTORED);
    customType.setOmitNorms(true);
    for (int i = 0; i < 39; i++) {
      Document doc = new Document();
      StringBuilder builder = new StringBuilder();
      for (int j = 0; j < howMany; j++) {
        if (j % 2 == 0) {
          builder.append("even ");
        } else {
          builder.append("odd ");
        }
      }
      doc.add(newField(fieldName, builder.toString(), customType));
      writer.addDocument(doc);
    }

    // now do searches
    IndexReader reader = writer.getReader();
    writer.close();

    int num = atLeast(13);
    for (int i = 0; i < num; i++) {
      BytesRef bytes = new BytesRef("even");

      ReaderContext topReaderContext = reader.getTopReaderContext();
      AtomicReaderContext[] leaves = ReaderUtil.leaves(topReaderContext);
      for (AtomicReaderContext atomicReaderContext : leaves) {
        DocsAndPositionsEnum docsAndPosEnum = getDocsAndPositions(
            atomicReaderContext.reader, bytes, null);
        assertNotNull(docsAndPosEnum);

        int initDoc = 0;
        int maxDoc = atomicReaderContext.reader.maxDoc();
        // initially advance or do next doc
        if (random.nextBoolean()) {
          initDoc = docsAndPosEnum.nextDoc();
        } else {
          initDoc = docsAndPosEnum.advance(random.nextInt(maxDoc));
        }
        String msg = "Iteration: " + i + " initDoc: " + initDoc; // TODO: + " payloads: " + usePayload;
        assertEquals(howMany / 2, docsAndPosEnum.freq());
        for (int j = 0; j < howMany; j += 2) {
          assertEquals("position missmatch index: " + j + " with freq: "
              + docsAndPosEnum.freq() + " -- " + msg, j,
              docsAndPosEnum.nextPosition());
        }
      }
    }
    reader.close();
    dir.close();
  }
  
  public void testDocsEnumStart() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    doc.add(newField("foo", "bar", StringField.TYPE_UNSTORED));
    writer.addDocument(doc);
    IndexReader reader = writer.getReader();
    IndexReader r = getOnlySegmentReader(reader);
    DocsEnum disi = r.termDocsEnum(null, "foo", new BytesRef("bar"));
    int docid = disi.docID();
    assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
    assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    
    // now reuse and check again
    TermsEnum te = r.terms("foo").iterator(null);
    assertTrue(te.seekExact(new BytesRef("bar"), true));
    disi = te.docs(null, disi);
    docid = disi.docID();
    assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
    assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    writer.close();
    r.close();
    dir.close();
  }
  
  public void testDocsAndPositionsEnumStart() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    doc.add(newField("foo", "bar", TextField.TYPE_UNSTORED));
    writer.addDocument(doc);
    IndexReader reader = writer.getReader();
    IndexReader r = getOnlySegmentReader(reader);
    DocsAndPositionsEnum disi = r.termPositionsEnum(null, "foo", new BytesRef("bar"));
    int docid = disi.docID();
    assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
    assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    
    // now reuse and check again
    TermsEnum te = r.terms("foo").iterator(null);
    assertTrue(te.seekExact(new BytesRef("bar"), true));
    disi = te.docsAndPositions(null, disi);
    docid = disi.docID();
    assertTrue(docid == -1 || docid == DocIdSetIterator.NO_MORE_DOCS);
    assertTrue(disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    writer.close();
    r.close();
    dir.close();
  }
}
