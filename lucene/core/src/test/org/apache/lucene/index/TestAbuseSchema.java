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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document2;
import org.apache.lucene.document.LowSchemaField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

/** Holds test cases that make schema changes only "allowed" by the low schema. */

public class TestAbuseSchema extends LuceneTestCase {

  // LUCENE-1010
  public void testNoTermVectorAfterTermVectorMerge() throws IOException {
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig());
    List<LowSchemaField> document = new ArrayList<>();
    LowSchemaField field = new LowSchemaField("tvtest", "a b c", IndexOptions.DOCS, false);
    field.enableTermVectors(false, false, false);
    document.add(field);
    iw.addDocument(document);
    iw.commit();

    document = new ArrayList<>();
    document.add(new LowSchemaField("tvtest", "a b c", IndexOptions.DOCS, false));
    iw.addDocument(document);
    // Make first segment
    iw.commit();

    iw.forceMerge(1);

    document = new ArrayList<>();
    document.add(field);
    iw.addDocument(document);
    // Make 2nd segment
    iw.commit();
    iw.forceMerge(1);

    iw.close();
    dir.close();
  }

  /** 
   * In a single doc, for the same field, mix the term vectors up 
   */
  public void testInconsistentTermVectorOptions() throws IOException {

    LowSchemaField f1, f2;

    // no vectors + vectors
    f1 = new LowSchemaField("field", "value1", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f2 = new LowSchemaField("field", "value2", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f2.enableTermVectors(false, false, false);
    doTestMixup(f1, f2);
    
    // vectors + vectors with pos
    f1 = new LowSchemaField("field", "value1", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f1.enableTermVectors(false, false, false);
    f2 = new LowSchemaField("field", "value2", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f2.enableTermVectors(true, false, false);
    doTestMixup(f1, f2);
    
    // vectors + vectors with off
    f1 = new LowSchemaField("field", "value1", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f1.enableTermVectors(false, false, false);
    f2 = new LowSchemaField("field", "value2", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f2.enableTermVectors(false, true, false);
    doTestMixup(f1, f2);
    
    // vectors with pos + vectors with pos + off
    f1 = new LowSchemaField("field", "value1", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f1.enableTermVectors(true, false, false);
    f2 = new LowSchemaField("field", "value2", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f2.enableTermVectors(true, true, false);
    doTestMixup(f1, f2);

    // vectors with pos + vectors with pos + pay
    f1 = new LowSchemaField("field", "value1", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f1.enableTermVectors(true, false, false);
    f2 = new LowSchemaField("field", "value2", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    f2.enableTermVectors(true, false, true);
    doTestMixup(f1, f2);
  }
  
  private void doTestMixup(LowSchemaField f1, LowSchemaField f2) throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    
    // add 3 good docs
    for (int i = 0; i < 3; i++) {
      Document2 doc = iw.newDocument();
      doc.addAtom("id", Integer.toString(i));
      iw.addDocument(doc);
    }

    // add broken doc
    List<LowSchemaField> doc = new ArrayList<>();
    doc.add(f1);
    doc.add(f2);
    
    // ensure broken doc hits exception
    try {
      iw.addDocument(doc);
      fail("didn't hit expected exception");
    } catch (IllegalArgumentException iae) {
      assertNotNull(iae.getMessage());
      assertTrue(iae.getMessage().startsWith("all instances of a given field name must have the same term vectors settings"));
    }
    
    // ensure good docs are still ok
    IndexReader ir = iw.getReader();
    assertEquals(3, ir.numDocs());
    
    ir.close();
    iw.close();
    dir.close();
  }

  // LUCENE-5611: don't abort segment when term vector settings are wrong
  public void testNoAbortOnBadTVSettings() throws Exception {
    Directory dir = newDirectory();
    // Don't use RandomIndexWriter because we want to be sure both docs go to 1 seg:
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    IndexWriter iw = new IndexWriter(dir, iwc);

    List<LowSchemaField> doc = new ArrayList<>();
    iw.addDocument(doc);
    LowSchemaField field = new LowSchemaField("field", "value", IndexOptions.NONE, false);
    field.enableTermVectors(false, false, false);
    doc.add(field);
    try {
      iw.addDocument(doc);
      fail("should have hit exc");
    } catch (IllegalArgumentException iae) {
      // expected
    }
    IndexReader r = DirectoryReader.open(iw, true);

    // Make sure the exc didn't lose our first document:
    assertEquals(1, r.numDocs());
    iw.close();
    r.close();
    dir.close();
  }

  public void testPostingsOffsetsWithUnindexedFields() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter riw = newRandomIndexWriter(dir);
    for (int i = 0; i < 100; i++) {
      // ensure at least one doc is indexed with offsets
      LowSchemaField field;
      if (i < 99 && random().nextInt(2) == 0) {
        // stored only
        field = new LowSchemaField("foo", "boo!", IndexOptions.NONE, false);
      } else {
        field = new LowSchemaField("foo", "boo!", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS, true);
        if (random().nextBoolean()) {
          // store some term vectors for the checkindex cross-check
          field.enableTermVectors(random().nextBoolean(), random().nextBoolean(), false);
        }
      }
      riw.addDocument(Collections.singletonList(field));
    }
    CompositeReader ir = riw.getReader();
    LeafReader slow = SlowCompositeReaderWrapper.wrap(ir);
    FieldInfos fis = slow.getFieldInfos();
    assertEquals(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS, fis.fieldInfo("foo").getIndexOptions());
    slow.close();
    ir.close();
    riw.close();
    dir.close();
  }
  
  /**
   * Tests various combinations of omitNorms=true/false, the field not existing at all,
   * ensuring that only omitNorms is 'viral'.
   * Internally checks that MultiNorms.norms() is consistent (returns the same bytes)
   * as the fully merged equivalent.
   */
  public void testOmitNormsCombos() throws IOException {
    // indexed with norms
    LowSchemaField norms = new LowSchemaField("foo", "a", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);

    // indexed without norms
    LowSchemaField noNorms = new LowSchemaField("foo", "a", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    noNorms.disableNorms();

    // not indexed, but stored
    LowSchemaField noIndex = new LowSchemaField("foo", "a", IndexOptions.NONE, false);

    // not indexed but stored, omitNorms is set
    LowSchemaField noNormsNoIndex = new LowSchemaField("foo", "a", IndexOptions.NONE, false);
    noNormsNoIndex.disableNorms();

    // not indexed nor stored (doesnt exist at all, we index a different field instead)
    LowSchemaField emptyNorms = new LowSchemaField("bar", "a", IndexOptions.NONE, false);
    
    assertNotNull(getNorms("foo", norms, norms));
    assertNull(getNorms("foo", norms, noNorms));
    assertNotNull(getNorms("foo", norms, noIndex));
    assertNotNull(getNorms("foo", norms, noNormsNoIndex));
    assertNotNull(getNorms("foo", norms, emptyNorms));
    assertNull(getNorms("foo", noNorms, noNorms));
    assertNull(getNorms("foo", noNorms, noIndex));
    assertNull(getNorms("foo", noNorms, noNormsNoIndex));
    assertNull(getNorms("foo", noNorms, emptyNorms));
    assertNull(getNorms("foo", noIndex, noIndex));
    assertNull(getNorms("foo", noIndex, noNormsNoIndex));
    assertNull(getNorms("foo", noIndex, emptyNorms));
    assertNull(getNorms("foo", noNormsNoIndex, noNormsNoIndex));
    assertNull(getNorms("foo", noNormsNoIndex, emptyNorms));
    assertNull(getNorms("foo", emptyNorms, emptyNorms));
  }

  /**
   * Indexes at least 1 document with f1, and at least 1 document with f2.
   * returns the norms for "field".
   */
  NumericDocValues getNorms(String field, LowSchemaField f1, LowSchemaField f2) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig(new MockAnalyzer(random()))
                              .setMergePolicy(newLogMergePolicy());
    RandomIndexWriter riw = new RandomIndexWriter(random(), dir, iwc);
    
    // add f1
    riw.addDocument(Collections.singletonList(f1));
    
    // add f2
    riw.addDocument(Collections.singletonList(f2));
    
    // add a mix of f1's and f2's
    int numExtraDocs = TestUtil.nextInt(random(), 1, 1000);
    for (int i = 0; i < numExtraDocs; i++) {
      riw.addDocument(Collections.singletonList(random().nextBoolean() ? f1 : f2));
    }

    IndexReader ir1 = riw.getReader();
    // todo: generalize
    NumericDocValues norms1 = MultiDocValues.getNormValues(ir1, field);
    
    // fully merge and validate MultiNorms against single segment.
    riw.forceMerge(1);
    DirectoryReader ir2 = riw.getReader();
    NumericDocValues norms2 = getOnlySegmentReader(ir2).getNormValues(field);

    if (norms1 == null) {
      assertNull(norms2);
    } else {
      for(int docID=0;docID<ir1.maxDoc();docID++) {
        assertEquals(norms1.get(docID), norms2.get(docID));
      }
    }
    ir1.close();
    ir2.close();
    riw.close();
    dir.close();
    return norms1;
  }

  public void testSameFieldNameForPostingAndDocValue() throws Exception {
    // LUCENE-5192: FieldInfos.Builder neglected to update
    // globalFieldNumbers.docValuesType map if the field existed, resulting in
    // potentially adding the same field with different DV types.
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(new MockAnalyzer(random()));
    IndexWriter writer = new IndexWriter(dir, conf);
    List<LowSchemaField> doc = new ArrayList<>();

    LowSchemaField field = new LowSchemaField("f", "mock-value", IndexOptions.DOCS, false);
    field.disableNorms();
    field.doNotStore();
    doc.add(field);

    field = new LowSchemaField("f", 5, IndexOptions.NONE, false);
    field.setDocValuesType(DocValuesType.NUMERIC);
    doc.add(field);
    writer.addDocument(doc);
    writer.commit();
    
    doc = new ArrayList<>();
    field = new LowSchemaField("f", new BytesRef("mock"), IndexOptions.NONE, false);
    field.setDocValuesType(DocValuesType.BINARY);
    doc.add(field);

    try {
      writer.addDocument(doc);
      fail("should not have succeeded to add a field with different DV type than what already exists");
    } catch (IllegalArgumentException e) {
      writer.rollback();
    }
    
    dir.close();
  }

  // LUCENE-6049
  public void testExcIndexingDocBeforeDocValues() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    IndexWriter w = new IndexWriter(dir, iwc);
    List<LowSchemaField> doc = new ArrayList<>();
    LowSchemaField field = new LowSchemaField("test", "value", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
    field.setDocValuesType(DocValuesType.SORTED);
    field.doNotStore();
    field.setTokenStream(new TokenStream() {
        @Override
        public boolean incrementToken() {
          throw new RuntimeException("no");
        }
      });
    doc.add(field);
    try {
      w.addDocument(doc);
      fail("did not hit exception");
    } catch (RuntimeException re) {
      // expected
    }
    w.addDocument(w.newDocument());
    w.close();
    dir.close();
  }


  public void testSameFieldNumbersAcrossSegments() throws Exception {
    for (int i = 0; i < 2; i++) {
      Directory dir = newDirectory();
      IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random()))
                                                   .setMergePolicy(NoMergePolicy.INSTANCE));

      List<LowSchemaField> d1 = new ArrayList<>();
      d1.add(new LowSchemaField("f1", "first field", IndexOptions.DOCS, false));
      d1.add(new LowSchemaField("f2", "second field", IndexOptions.DOCS, false));
      writer.addDocument(d1);

      if (i == 1) {
        writer.close();
        writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random()))
                                         .setMergePolicy(NoMergePolicy.INSTANCE));
      } else {
        writer.commit();
      }

      List<LowSchemaField> d2 = new ArrayList<>();
      d2.add(new LowSchemaField("f2", "second field", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true));
      LowSchemaField field = new LowSchemaField("f1", "first field", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
      field.enableTermVectors(false, false, false);
      d2.add(field);
      d2.add(new LowSchemaField("f3", "third field", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true));
      d2.add(new LowSchemaField("f4", "fourth field", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true));
      writer.addDocument(d2);

      writer.close();

      SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
      assertEquals(2, sis.size());

      FieldInfos fis1 = IndexWriter.readFieldInfos(sis.info(0));
      FieldInfos fis2 = IndexWriter.readFieldInfos(sis.info(1));

      assertEquals("f1", fis1.fieldInfo(0).name);
      assertEquals("f2", fis1.fieldInfo(1).name);
      assertEquals("f1", fis2.fieldInfo(0).name);
      assertEquals("f2", fis2.fieldInfo(1).name);
      assertEquals("f3", fis2.fieldInfo(2).name);
      assertEquals("f4", fis2.fieldInfo(3).name);

      writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())));
      writer.forceMerge(1);
      writer.close();

      sis = SegmentInfos.readLatestCommit(dir);
      assertEquals(1, sis.size());

      FieldInfos fis3 = IndexWriter.readFieldInfos(sis.info(0));

      assertEquals("f1", fis3.fieldInfo(0).name);
      assertEquals("f2", fis3.fieldInfo(1).name);
      assertEquals("f3", fis3.fieldInfo(2).name);
      assertEquals("f4", fis3.fieldInfo(3).name);


      dir.close();
    }
  }

  public void testEnablingNorms() throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random()))
                                          .setMaxBufferedDocs(10));
    // Enable norms for only 1 doc, pre flush
    for(int j=0;j<10;j++) {
      List<LowSchemaField> doc = new ArrayList<>();
      LowSchemaField f;
      if (j != 8) {
        f = new LowSchemaField("field", "aaa", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
        f.disableNorms();
      } else {
        f = new LowSchemaField("field", "aaa", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
        f.doNotStore();
      }
      doc.add(f);
      writer.addDocument(doc);
    }
    writer.close();

    Term searchTerm = new Term("field", "aaa");

    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    ScoreDoc[] hits = searcher.search(new TermQuery(searchTerm), null, 1000).scoreDocs;
    assertEquals(10, hits.length);
    reader.close();

    writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random()))
                             .setOpenMode(IndexWriterConfig.OpenMode.CREATE).setMaxBufferedDocs(10));
    // Enable norms for only 1 doc, post flush
    for(int j=0;j<27;j++) {
      List<LowSchemaField> doc = new ArrayList<>();
      LowSchemaField f;
      if (j != 26) {
        f = new LowSchemaField("field", "aaa", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
        f.disableNorms();
      } else {
        f = new LowSchemaField("field", "aaa", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
        f.doNotStore();
      }
      doc.add(f);
      writer.addDocument(doc);
    }
    writer.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
    hits = searcher.search(new TermQuery(searchTerm), null, 1000).scoreDocs;
    assertEquals(27, hits.length);
    reader.close();

    reader = DirectoryReader.open(dir);
    reader.close();

    dir.close();
  }

  public void testVariableSchema() throws Exception {
    Directory dir = newDirectory();
    for(int i=0;i<20;i++) {
      if (VERBOSE) {
        System.out.println("TEST: iter=" + i);
      }
      IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random()))
                                                  .setMaxBufferedDocs(2)
                                                  .setMergePolicy(newLogMergePolicy()));
      //LogMergePolicy lmp = (LogMergePolicy) writer.getConfig().getMergePolicy();
      //lmp.setMergeFactor(2);
      //lmp.setNoCFSRatio(0.0);
      List<LowSchemaField> doc = new ArrayList<>();
      String contents = "aa bb cc dd ee ff gg hh ii jj kk";

      if (i == 7) {
        // Add empty docs here
        LowSchemaField field = new LowSchemaField("content3", "", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
        field.doNotStore();
        doc.add(field);
      } else {
        if (i%2 == 0) {
          doc.add(new LowSchemaField("content4", contents, IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true));
          doc.add(new LowSchemaField("content5", "", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true));
        } else {
          LowSchemaField field = new LowSchemaField("content5", "", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
          field.doNotStore();
          doc.add(field);
        }
        LowSchemaField field = new LowSchemaField("content1", contents, IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true);
        field.doNotStore();
        doc.add(field);
        doc.add(new LowSchemaField("content3", "", IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, true));
      }

      for(int j=0;j<4;j++) {
        writer.addDocument(doc);
      }

      writer.close();

      if (0 == i % 4) {
        writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())));
        //LogMergePolicy lmp2 = (LogMergePolicy) writer.getConfig().getMergePolicy();
        //lmp2.setNoCFSRatio(0.0);
        writer.forceMerge(1);
        writer.close();
      }
    }
    dir.close();
  }

}
