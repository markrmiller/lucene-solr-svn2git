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

import static org.apache.lucene.index.SortedSetDocValues.NO_MORE_ORDS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.lucene42.Lucene42DocValuesFormat;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.TermsEnum.SeekStatus;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefHash;
import org.apache.lucene.util.TestUtil;

/**
 * Abstract class to do basic tests for a docvalues format.
 * NOTE: This test focuses on the docvalues impl, nothing else.
 * The [stretch] goal is for this test to be
 * so thorough in testing a new DocValuesFormat that if this
 * test passes, then all Lucene/Solr tests should also pass.  Ie,
 * if there is some bug in a given DocValuesFormat that this
 * test fails to catch then this test needs to be improved! */
public abstract class BaseDocValuesFormatTestCase extends BaseIndexFileFormatTestCase {

  @Override
  protected void addRandomFields(Document doc) {
    if (usually()) {
      doc.add(new NumericDocValuesField("ndv", random().nextInt(1 << 12)));
      doc.add(new BinaryDocValuesField("bdv", new BytesRef(TestUtil.randomSimpleString(random()))));
      doc.add(new SortedDocValuesField("sdv", new BytesRef(TestUtil.randomSimpleString(random(), 2))));
    }
    if (defaultCodecSupportsSortedSet()) {
      final int numValues = random().nextInt(5);
      for (int i = 0; i < numValues; ++i) {
        doc.add(new SortedSetDocValuesField("ssdv", new BytesRef(TestUtil.randomSimpleString(random(), 2))));
      }
    }
    if (defaultCodecSupportsSortedNumeric()) {
      final int numValues = random().nextInt(5);
      for (int i = 0; i < numValues; ++i) {
        doc.add(new SortedNumericDocValuesField("sndv", TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE)));
      }
    }
  }

  public void testOneNumber() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv", 5));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
    }

    ireader.close();
    directory.close();
  }

  public void testOneFloat() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new FloatDocValuesField("dv", 5.7f));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
      assertEquals(Float.floatToRawIntBits(5.7f), dv.get(hits.scoreDocs[i].doc));
    }

    ireader.close();
    directory.close();
  }
  
  public void testTwoNumbers() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 5));
    doc.add(new NumericDocValuesField("dv2", 17));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv1");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
      dv = ireader.leaves().get(0).reader().getNumericDocValues("dv2");
      assertEquals(17, dv.get(hits.scoreDocs[i].doc));
    }

    ireader.close();
    directory.close();
  }

  public void testTwoBinaryValues() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef(longTerm)));
    doc.add(new BinaryDocValuesField("dv2", new BytesRef(text)));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv1");
      BytesRef scratch = dv.get(hits.scoreDocs[i].doc);
      assertEquals(new BytesRef(longTerm), scratch);
      dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv2");
      scratch = dv.get(hits.scoreDocs[i].doc);
      assertEquals(new BytesRef(text), scratch);
    }

    ireader.close();
    directory.close();
  }

  public void testTwoFieldsMixed() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 5));
    doc.add(new BinaryDocValuesField("dv2", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv1");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv2 = ireader.leaves().get(0).reader().getBinaryDocValues("dv2");
      BytesRef scratch = dv2.get(hits.scoreDocs[i].doc);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testThreeFieldsMixed() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new SortedDocValuesField("dv1", new BytesRef("hello hello")));
    doc.add(new NumericDocValuesField("dv2", 5));
    doc.add(new BinaryDocValuesField("dv3", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv1");
      int ord = dv.getOrd(0);
      BytesRef scratch = dv.lookupOrd(ord);
      assertEquals(new BytesRef("hello hello"), scratch);
      NumericDocValues dv2 = ireader.leaves().get(0).reader().getNumericDocValues("dv2");
      assertEquals(5, dv2.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv3 = ireader.leaves().get(0).reader().getBinaryDocValues("dv3");
      scratch = dv3.get(hits.scoreDocs[i].doc);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testThreeFieldsMixed2() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef("hello world")));
    doc.add(new SortedDocValuesField("dv2", new BytesRef("hello hello")));
    doc.add(new NumericDocValuesField("dv3", 5));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv2");
      int ord = dv.getOrd(0);
      scratch = dv.lookupOrd(ord);
      assertEquals(new BytesRef("hello hello"), scratch);
      NumericDocValues dv2 = ireader.leaves().get(0).reader().getNumericDocValues("dv3");
      assertEquals(5, dv2.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv3 = ireader.leaves().get(0).reader().getBinaryDocValues("dv1");
      scratch = dv3.get(hits.scoreDocs[i].doc);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testTwoDocumentsNumeric() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("dv", 1));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new NumericDocValuesField("dv", 2));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    assertEquals(1, dv.get(0));
    assertEquals(2, dv.get(1));

    ireader.close();
    directory.close();
  }
  
  public void testTwoDocumentsMerged() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(newField("id", "0", StringField.TYPE_STORED));
    doc.add(new NumericDocValuesField("dv", -10));
    iwriter.addDocument(doc);
    iwriter.commit();
    doc = new Document();
    doc.add(newField("id", "1", StringField.TYPE_STORED));
    doc.add(new NumericDocValuesField("dv", 99));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    for(int i=0;i<2;i++) {
      Document doc2 = ireader.leaves().get(0).reader().document(i);
      long expected;
      if (doc2.get("id").equals("0")) {
        expected = -10;
      } else {
        expected = 99;
      }
      assertEquals(expected, dv.get(i));
    }

    ireader.close();
    directory.close();
  }

  public void testBigNumericRange() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("dv", Long.MIN_VALUE));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new NumericDocValuesField("dv", Long.MAX_VALUE));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    assertEquals(Long.MIN_VALUE, dv.get(0));
    assertEquals(Long.MAX_VALUE, dv.get(1));

    ireader.close();
    directory.close();
  }
  
  public void testBigNumericRange2() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("dv", -8841491950446638677L));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new NumericDocValuesField("dv", 9062230939892376225L));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    assertEquals(-8841491950446638677L, dv.get(0));
    assertEquals(9062230939892376225L, dv.get(1));

    ireader.close();
    directory.close();
  }
  
  public void testBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
      BytesRef scratch = dv.get(hits.scoreDocs[i].doc);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testBytesTwoDocumentsMerged() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(newField("id", "0", StringField.TYPE_STORED));
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    iwriter.commit();
    doc = new Document();
    doc.add(newField("id", "1", StringField.TYPE_STORED));
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello 2")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = new BytesRef();
    for(int i=0;i<2;i++) {
      Document doc2 = ireader.leaves().get(0).reader().document(i);
      String expected;
      if (doc2.get("id").equals("0")) {
        expected = "hello world 1";
      } else {
        expected = "hello 2";
      }
      scratch = dv.get(i);
      assertEquals(expected, scratch.utf8ToString());
    }

    ireader.close();
    directory.close();
  }

  public void testSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      Document hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
      scratch = dv.lookupOrd(dv.getOrd(hits.scoreDocs[i].doc));
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }

  public void testSortedBytesTwoDocuments() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = new BytesRef();
    scratch = dv.lookupOrd(dv.getOrd(0));
    assertEquals("hello world 1", scratch.utf8ToString());
    scratch = dv.lookupOrd(dv.getOrd(1));
    assertEquals("hello world 2", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testSortedBytesThreeDocuments() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    assertEquals(2, dv.getValueCount());
    assertEquals(0, dv.getOrd(0));
    BytesRef scratch = dv.lookupOrd(0);
    assertEquals("hello world 1", scratch.utf8ToString());
    assertEquals(1, dv.getOrd(1));
    scratch = dv.lookupOrd(1);
    assertEquals("hello world 2", scratch.utf8ToString());
    assertEquals(0, dv.getOrd(2));

    ireader.close();
    directory.close();
  }

  public void testSortedBytesTwoDocumentsMerged() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(newField("id", "0", StringField.TYPE_STORED));
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    iwriter.commit();
    doc = new Document();
    doc.add(newField("id", "1", StringField.TYPE_STORED));
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    assertEquals(2, dv.getValueCount()); // 2 ords
    BytesRef scratch = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello world 1"), scratch);
    scratch = dv.lookupOrd(1);
    assertEquals(new BytesRef("hello world 2"), scratch);
    for(int i=0;i<2;i++) {
      Document doc2 = ireader.leaves().get(0).reader().document(i);
      String expected;
      if (doc2.get("id").equals("0")) {
        expected = "hello world 1";
      } else {
        expected = "hello world 2";
      }
      scratch = dv.lookupOrd(dv.getOrd(i));
      assertEquals(expected, scratch.utf8ToString());
    }

    ireader.close();
    directory.close();
  }
  
  public void testSortedMergeAwayAllValues() throws IOException {
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    iwriter.addDocument(doc);    
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new SortedDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    iwriter.commit();
    iwriter.deleteDocuments(new Term("id", "1"));
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedDocValues dv = getOnlySegmentReader(ireader).getSortedDocValues("field");
    if (defaultCodecSupportsDocsWithField()) {
      assertEquals(-1, dv.getOrd(0));
      assertEquals(0, dv.getValueCount());
    } else {
      assertEquals(0, dv.getOrd(0));
      assertEquals(1, dv.getValueCount());
      BytesRef ref = dv.lookupOrd(0);
      assertEquals(new BytesRef(), ref);
    }
    
    ireader.close();
    directory.close();
  }

  public void testBytesWithNewline() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello\nworld\r1")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = dv.get(0);
    assertEquals(new BytesRef("hello\nworld\r1"), scratch);

    ireader.close();
    directory.close();
  }

  public void testMissingSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    // 2nd doc missing the DV field
    iwriter.addDocument(new Document());
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = dv.lookupOrd(dv.getOrd(0));
    assertEquals(new BytesRef("hello world 2"), scratch);
    if (defaultCodecSupportsDocsWithField()) {
      assertEquals(-1, dv.getOrd(1));
    }
    scratch = dv.get(1);
    assertEquals(new BytesRef(""), scratch);
    ireader.close();
    directory.close();
  }
  
  public void testSortedTermsEnum() throws IOException {
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new SortedDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    
    doc = new Document();
    doc.add(new SortedDocValuesField("field", new BytesRef("world")));
    iwriter.addDocument(doc);

    doc = new Document();
    doc.add(new SortedDocValuesField("field", new BytesRef("beer")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();

    SortedDocValues dv = getOnlySegmentReader(ireader).getSortedDocValues("field");
    assertEquals(3, dv.getValueCount());
    
    TermsEnum termsEnum = dv.termsEnum();
    
    // next()
    assertEquals("beer", termsEnum.next().utf8ToString());
    assertEquals(0, termsEnum.ord());
    assertEquals("hello", termsEnum.next().utf8ToString());
    assertEquals(1, termsEnum.ord());
    assertEquals("world", termsEnum.next().utf8ToString());
    assertEquals(2, termsEnum.ord());
    
    // seekCeil()
    assertEquals(SeekStatus.NOT_FOUND, termsEnum.seekCeil(new BytesRef("ha!")));
    assertEquals("hello", termsEnum.term().utf8ToString());
    assertEquals(1, termsEnum.ord());
    assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(new BytesRef("beer")));
    assertEquals("beer", termsEnum.term().utf8ToString());
    assertEquals(0, termsEnum.ord());
    assertEquals(SeekStatus.END, termsEnum.seekCeil(new BytesRef("zzz")));
    
    // seekExact()
    assertTrue(termsEnum.seekExact(new BytesRef("beer")));
    assertEquals("beer", termsEnum.term().utf8ToString());
    assertEquals(0, termsEnum.ord());
    assertTrue(termsEnum.seekExact(new BytesRef("hello")));
    assertEquals(Codec.getDefault().toString(), "hello", termsEnum.term().utf8ToString());
    assertEquals(1, termsEnum.ord());
    assertTrue(termsEnum.seekExact(new BytesRef("world")));
    assertEquals("world", termsEnum.term().utf8ToString());
    assertEquals(2, termsEnum.ord());
    assertFalse(termsEnum.seekExact(new BytesRef("bogus")));

    // seek(ord)
    termsEnum.seekExact(0);
    assertEquals("beer", termsEnum.term().utf8ToString());
    assertEquals(0, termsEnum.ord());
    termsEnum.seekExact(1);
    assertEquals("hello", termsEnum.term().utf8ToString());
    assertEquals(1, termsEnum.ord());
    termsEnum.seekExact(2);
    assertEquals("world", termsEnum.term().utf8ToString());
    assertEquals(2, termsEnum.ord());
    ireader.close();
    directory.close();
  }
  
  public void testEmptySortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    assertEquals(0, dv.getOrd(0));
    assertEquals(0, dv.getOrd(1));
    BytesRef scratch = dv.lookupOrd(dv.getOrd(0));
    assertEquals("", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testEmptyBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = dv.get(0);
    assertEquals("", scratch.utf8ToString());
    scratch = dv.get(1);
    assertEquals("", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testVeryLargeButLegalBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    byte bytes[] = new byte[32766];
    BytesRef b = new BytesRef(bytes);
    random().nextBytes(bytes);
    doc.add(new BinaryDocValuesField("dv", b));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = dv.get(0);
    assertEquals(new BytesRef(bytes), scratch);

    ireader.close();
    directory.close();
  }
  
  public void testVeryLargeButLegalSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    byte bytes[] = new byte[32766];
    BytesRef b = new BytesRef(bytes);
    random().nextBytes(bytes);
    doc.add(new SortedDocValuesField("dv", b));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = dv.get(0);
    assertEquals(new BytesRef(bytes), scratch);
    ireader.close();
    directory.close();
  }
  
  public void testCodecUsesOwnBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("boo!")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    byte mybytes[] = new byte[20];
    BytesRef scratch = dv.get(0);
    assertEquals("boo!", scratch.utf8ToString());
    assertFalse(scratch.bytes == mybytes);

    ireader.close();
    directory.close();
  }
  
  public void testCodecUsesOwnSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("boo!")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    byte mybytes[] = new byte[20];
    BytesRef scratch = dv.get(0);
    assertEquals("boo!", scratch.utf8ToString());
    assertFalse(scratch.bytes == mybytes);

    ireader.close();
    directory.close();
  }
  
  /*
   * Simple test case to show how to use the API
   */
  public void testDocValuesSimple() throws IOException {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    conf.setMergePolicy(newLogMergePolicy());
    IndexWriter writer = new IndexWriter(dir, conf);
    for (int i = 0; i < 5; i++) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("docId", i));
      doc.add(new TextField("docId", "" + i, Field.Store.NO));
      writer.addDocument(doc);
    }
    writer.commit();
    writer.forceMerge(1, true);

    writer.close(true);

    DirectoryReader reader = DirectoryReader.open(dir, 1);
    assertEquals(1, reader.leaves().size());
  
    IndexSearcher searcher = new IndexSearcher(reader);

    BooleanQuery query = new BooleanQuery();
    query.add(new TermQuery(new Term("docId", "0")), BooleanClause.Occur.SHOULD);
    query.add(new TermQuery(new Term("docId", "1")), BooleanClause.Occur.SHOULD);
    query.add(new TermQuery(new Term("docId", "2")), BooleanClause.Occur.SHOULD);
    query.add(new TermQuery(new Term("docId", "3")), BooleanClause.Occur.SHOULD);
    query.add(new TermQuery(new Term("docId", "4")), BooleanClause.Occur.SHOULD);

    TopDocs search = searcher.search(query, 10);
    assertEquals(5, search.totalHits);
    ScoreDoc[] scoreDocs = search.scoreDocs;
    NumericDocValues docValues = getOnlySegmentReader(reader).getNumericDocValues("docId");
    for (int i = 0; i < scoreDocs.length; i++) {
      assertEquals(i, scoreDocs[i].doc);
      assertEquals(i, docValues.get(scoreDocs[i].doc));
    }
    reader.close();
    dir.close();
  }
  
  public void testRandomSortedBytes() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig cfg = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    if (!defaultCodecSupportsDocsWithField()) {
      // if the codec doesnt support missing, we expect missing to be mapped to byte[]
      // by the impersonator, but we have to give it a chance to merge them to this
      cfg.setMergePolicy(newLogMergePolicy());
    }
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, cfg);
    int numDocs = atLeast(100);
    BytesRefHash hash = new BytesRefHash();
    Map<String, String> docToString = new HashMap<>();
    int maxLength = TestUtil.nextInt(random(), 1, 50);
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(newTextField("id", "" + i, Field.Store.YES));
      String string = TestUtil.randomRealisticUnicodeString(random(), 1, maxLength);
      BytesRef br = new BytesRef(string);
      doc.add(new SortedDocValuesField("field", br));
      hash.add(br);
      docToString.put("" + i, string);
      w.addDocument(doc);
    }
    if (rarely()) {
      w.commit();
    }
    int numDocsNoValue = atLeast(10);
    for (int i = 0; i < numDocsNoValue; i++) {
      Document doc = new Document();
      doc.add(newTextField("id", "noValue", Field.Store.YES));
      w.addDocument(doc);
    }
    if (!defaultCodecSupportsDocsWithField()) {
      BytesRef bytesRef = new BytesRef();
      hash.add(bytesRef); // add empty value for the gaps
    }
    if (rarely()) {
      w.commit();
    }
    if (!defaultCodecSupportsDocsWithField()) {
      // if the codec doesnt support missing, we expect missing to be mapped to byte[]
      // by the impersonator, but we have to give it a chance to merge them to this
      w.forceMerge(1);
    }
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      String id = "" + i + numDocs;
      doc.add(newTextField("id", id, Field.Store.YES));
      String string = TestUtil.randomRealisticUnicodeString(random(), 1, maxLength);
      BytesRef br = new BytesRef(string);
      hash.add(br);
      docToString.put(id, string);
      doc.add(new SortedDocValuesField("field", br));
      w.addDocument(doc);
    }
    w.commit();
    IndexReader reader = w.getReader();
    SortedDocValues docValues = MultiDocValues.getSortedValues(reader, "field");
    int[] sort = hash.sort(BytesRef.getUTF8SortedAsUnicodeComparator());
    BytesRef expected = new BytesRef();
    assertEquals(hash.size(), docValues.getValueCount());
    for (int i = 0; i < hash.size(); i++) {
      hash.get(sort[i], expected);
      final BytesRef actual = docValues.lookupOrd(i);
      assertEquals(expected.utf8ToString(), actual.utf8ToString());
      int ord = docValues.lookupTerm(expected);
      assertEquals(i, ord);
    }
    AtomicReader slowR = SlowCompositeReaderWrapper.wrap(reader);
    Set<Entry<String, String>> entrySet = docToString.entrySet();

    for (Entry<String, String> entry : entrySet) {
      // pk lookup
      DocsEnum termDocsEnum = slowR.termDocsEnum(new Term("id", entry.getKey()));
      int docId = termDocsEnum.nextDoc();
      expected = new BytesRef(entry.getValue());
      final BytesRef actual = docValues.get(docId);
      assertEquals(expected, actual);
    }

    reader.close();
    w.close();
    dir.close();
  }

  static abstract class LongProducer {
    abstract long next();
  }

  private void doTestNumericsVsStoredFields(final long minValue, final long maxValue) throws Exception {
    doTestNumericsVsStoredFields(new LongProducer() {
      @Override
      long next() {
        return TestUtil.nextLong(random(), minValue, maxValue);
      }
    });
  }

  private void doTestNumericsVsStoredFields(LongProducer longs) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Document doc = new Document();
    Field idField = new StringField("id", "", Field.Store.NO);
    Field storedField = newStringField("stored", "", Field.Store.YES);
    Field dvField = new NumericDocValuesField("dv", 0);
    doc.add(idField);
    doc.add(storedField);
    doc.add(dvField);
    
    // index some docs
    int numDocs = atLeast(300);
    // numDocs should be always > 256 so that in case of a codec that optimizes
    // for numbers of values <= 256, all storage layouts are tested
    assert numDocs > 256;
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      long value = longs.next();
      storedField.setStringValue(Long.toString(value));
      dvField.setLongValue(value);
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }

    // merge some segments and ensure that at least one of them has more than
    // 256 values
    writer.forceMerge(numDocs / 256);

    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      NumericDocValues docValues = r.getNumericDocValues("dv");
      for (int i = 0; i < r.maxDoc(); i++) {
        long storedValue = Long.parseLong(r.document(i).get("stored"));
        assertEquals(storedValue, docValues.get(i));
      }
    }
    ir.close();
    dir.close();
  }
  
  private void doTestMissingVsFieldCache(final long minValue, final long maxValue) throws Exception {
    doTestMissingVsFieldCache(new LongProducer() {
      @Override
      long next() {
        return TestUtil.nextLong(random(), minValue, maxValue);
      }
    });
  }
  
  private void doTestMissingVsFieldCache(LongProducer longs) throws Exception {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Field idField = new StringField("id", "", Field.Store.NO);
    Field indexedField = newStringField("indexed", "", Field.Store.NO);
    Field dvField = new NumericDocValuesField("dv", 0);

    
    // index some docs
    int numDocs = atLeast(300);
    // numDocs should be always > 256 so that in case of a codec that optimizes
    // for numbers of values <= 256, all storage layouts are tested
    assert numDocs > 256;
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      long value = longs.next();
      indexedField.setStringValue(Long.toString(value));
      dvField.setLongValue(value);
      Document doc = new Document();
      doc.add(idField);
      // 1/4 of the time we neglect to add the fields
      if (random().nextInt(4) > 0) {
        doc.add(indexedField);
        doc.add(dvField);
      }
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }

    // merge some segments and ensure that at least one of them has more than
    // 256 values
    writer.forceMerge(numDocs / 256);

    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      Bits expected = FieldCache.DEFAULT.getDocsWithField(r, "indexed");
      Bits actual = FieldCache.DEFAULT.getDocsWithField(r, "dv");
      assertEquals(expected, actual);
    }
    ir.close();
    dir.close();
  }
  
  private void doTestSortedNumericsVsStoredFields(LongProducer counts, LongProducer values) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    
    // index some docs
    int numDocs = atLeast(300);
    // numDocs should be always > 256 so that in case of a codec that optimizes
    // for numbers of values <= 256, all storage layouts are tested
    assert numDocs > 256;
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      
      int valueCount = (int) counts.next();
      long valueArray[] = new long[valueCount];
      for (int j = 0; j < valueCount; j++) {
        long value = values.next();
        valueArray[j] = value;
        doc.add(new SortedNumericDocValuesField("dv", value));
      }
      Arrays.sort(valueArray);
      for (int j = 0; j < valueCount; j++) {
        doc.add(new StoredField("stored", Long.toString(valueArray[j])));
      }
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }

    // merge some segments and ensure that at least one of them has more than
    // 256 values
    writer.forceMerge(numDocs / 256);

    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      SortedNumericDocValues docValues = DocValues.getSortedNumeric(r, "dv");
      for (int i = 0; i < r.maxDoc(); i++) {
        String expected[] = r.document(i).getValues("stored");
        docValues.setDocument(i);
        String actual[] = new String[docValues.count()];
        for (int j = 0; j < actual.length; j++) {
          actual[j] = Long.toString(docValues.valueAt(j));
        }
        assertArrayEquals(expected, actual);
      }
    }
    ir.close();
    dir.close();
  }
  
  public void testBooleanNumericsVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestNumericsVsStoredFields(0, 1);
    }
  }
  
  public void testByteNumericsVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestNumericsVsStoredFields(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
  }
  
  public void testByteMissingVsFieldCache() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestMissingVsFieldCache(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
  }
  
  public void testShortNumericsVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestNumericsVsStoredFields(Short.MIN_VALUE, Short.MAX_VALUE);
    }
  }
  
  public void testShortMissingVsFieldCache() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestMissingVsFieldCache(Short.MIN_VALUE, Short.MAX_VALUE);
    }
  }
  
  public void testIntNumericsVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestNumericsVsStoredFields(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
  }
  
  public void testIntMissingVsFieldCache() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestMissingVsFieldCache(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
  }
  
  public void testLongNumericsVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestNumericsVsStoredFields(Long.MIN_VALUE, Long.MAX_VALUE);
    }
  }
  
  public void testLongMissingVsFieldCache() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestMissingVsFieldCache(Long.MIN_VALUE, Long.MAX_VALUE);
    }
  }
  
  private void doTestBinaryVsStoredFields(int minLength, int maxLength) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Document doc = new Document();
    Field idField = new StringField("id", "", Field.Store.NO);
    Field storedField = new StoredField("stored", new byte[0]);
    Field dvField = new BinaryDocValuesField("dv", new BytesRef());
    doc.add(idField);
    doc.add(storedField);
    doc.add(dvField);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      final int length;
      if (minLength == maxLength) {
        length = minLength; // fixed length
      } else {
        length = TestUtil.nextInt(random(), minLength, maxLength);
      }
      byte buffer[] = new byte[length];
      random().nextBytes(buffer);
      storedField.setBytesValue(buffer);
      dvField.setBytesValue(buffer);
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      BinaryDocValues docValues = r.getBinaryDocValues("dv");
      for (int i = 0; i < r.maxDoc(); i++) {
        BytesRef binaryValue = r.document(i).getBinaryValue("stored");
        BytesRef scratch = docValues.get(i);
        assertEquals(binaryValue, scratch);
      }
    }
    ir.close();
    dir.close();
  }
  
  public void testBinaryFixedLengthVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      int fixedLength = TestUtil.nextInt(random(), 0, 10);
      doTestBinaryVsStoredFields(fixedLength, fixedLength);
    }
  }
  
  public void testBinaryVariableLengthVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestBinaryVsStoredFields(0, 10);
    }
  }
  
  private void doTestSortedVsStoredFields(int minLength, int maxLength) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Document doc = new Document();
    Field idField = new StringField("id", "", Field.Store.NO);
    Field storedField = new StoredField("stored", new byte[0]);
    Field dvField = new SortedDocValuesField("dv", new BytesRef());
    doc.add(idField);
    doc.add(storedField);
    doc.add(dvField);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      final int length;
      if (minLength == maxLength) {
        length = minLength; // fixed length
      } else {
        length = TestUtil.nextInt(random(), minLength, maxLength);
      }
      byte buffer[] = new byte[length];
      random().nextBytes(buffer);
      storedField.setBytesValue(buffer);
      dvField.setBytesValue(buffer);
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      BinaryDocValues docValues = r.getSortedDocValues("dv");
      for (int i = 0; i < r.maxDoc(); i++) {
        BytesRef binaryValue = r.document(i).getBinaryValue("stored");
        BytesRef scratch = docValues.get(i);
        assertEquals(binaryValue, scratch);
      }
    }
    ir.close();
    dir.close();
  }
  
  private void doTestSortedVsFieldCache(int minLength, int maxLength) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Document doc = new Document();
    Field idField = new StringField("id", "", Field.Store.NO);
    Field indexedField = new StringField("indexed", "", Field.Store.NO);
    Field dvField = new SortedDocValuesField("dv", new BytesRef());
    doc.add(idField);
    doc.add(indexedField);
    doc.add(dvField);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      final int length;
      if (minLength == maxLength) {
        length = minLength; // fixed length
      } else {
        length = TestUtil.nextInt(random(), minLength, maxLength);
      }
      String value = TestUtil.randomSimpleString(random(), length);
      indexedField.setStringValue(value);
      dvField.setBytesValue(new BytesRef(value));
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      SortedDocValues expected = FieldCache.DEFAULT.getTermsIndex(r, "indexed");
      SortedDocValues actual = r.getSortedDocValues("dv");
      assertEquals(r.maxDoc(), expected, actual);
    }
    ir.close();
    dir.close();
  }
  
  public void testSortedFixedLengthVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      int fixedLength = TestUtil.nextInt(random(), 1, 10);
      doTestSortedVsStoredFields(fixedLength, fixedLength);
    }
  }
  
  public void testSortedFixedLengthVsFieldCache() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      int fixedLength = TestUtil.nextInt(random(), 1, 10);
      doTestSortedVsFieldCache(fixedLength, fixedLength);
    }
  }
  
  public void testSortedVariableLengthVsFieldCache() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedVsFieldCache(1, 10);
    }
  }
  
  public void testSortedVariableLengthVsStoredFields() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedVsStoredFields(1, 10);
    }
  }
  
  public void testSortedSetOneValue() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    
    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);

    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoFields() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    doc.add(new SortedSetDocValuesField("field2", new BytesRef("world")));
    iwriter.addDocument(doc);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    
    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field2");

    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("world"), bytes);
    
    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoDocumentsMerged() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
  
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    iwriter.commit();
    
    doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("world")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();

    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(2, dv.getValueCount());
    
    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    dv.setDocument(1);
    assertEquals(1, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    bytes = dv.lookupOrd(1);
    assertEquals(new BytesRef("world"), bytes);   

    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoValues() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("world")));
    iwriter.addDocument(doc);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    
    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(1, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    bytes = dv.lookupOrd(1);
    assertEquals(new BytesRef("world"), bytes);

    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoValuesUnordered() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("world")));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    
    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(1, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    bytes = dv.lookupOrd(1);
    assertEquals(new BytesRef("world"), bytes);

    ireader.close();
    directory.close();
  }
  
  public void testSortedSetThreeValuesTwoDocs() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("world")));
    iwriter.addDocument(doc);
    iwriter.commit();
    
    doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("beer")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();

    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(3, dv.getValueCount());
    
    dv.setDocument(0);
    assertEquals(1, dv.nextOrd());
    assertEquals(2, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    dv.setDocument(1);
    assertEquals(0, dv.nextOrd());
    assertEquals(1, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("beer"), bytes);
    
    bytes = dv.lookupOrd(1);
    assertEquals(new BytesRef("hello"), bytes);
    
    bytes = dv.lookupOrd(2);
    assertEquals(new BytesRef("world"), bytes);

    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoDocumentsLastMissing() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    
    doc = new Document();
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(1, dv.getValueCount());
    
    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoDocumentsLastMissingMerge() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    iwriter.commit();
    
    doc = new Document();
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
   
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(1, dv.getValueCount());

    dv.setDocument(0);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoDocumentsFirstMissing() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    iwriter.addDocument(doc);
    
    doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    
    iwriter.forceMerge(1);
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(1, dv.getValueCount());

    dv.setDocument(1);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTwoDocumentsFirstMissingMerge() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    iwriter.addDocument(doc);
    iwriter.commit();
    
    doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(1, dv.getValueCount());

    dv.setDocument(1);
    assertEquals(0, dv.nextOrd());
    assertEquals(NO_MORE_ORDS, dv.nextOrd());
    
    BytesRef bytes = dv.lookupOrd(0);
    assertEquals(new BytesRef("hello"), bytes);
    
    ireader.close();
    directory.close();
  }
  
  public void testSortedSetMergeAwayAllValues() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    iwriter.addDocument(doc);    
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    iwriter.addDocument(doc);
    iwriter.commit();
    iwriter.deleteDocuments(new Term("id", "1"));
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(0, dv.getValueCount());
    
    ireader.close();
    directory.close();
  }
  
  public void testSortedSetTermsEnum() throws IOException {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new SortedSetDocValuesField("field", new BytesRef("hello")));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("world")));
    doc.add(new SortedSetDocValuesField("field", new BytesRef("beer")));
    iwriter.addDocument(doc);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();

    SortedSetDocValues dv = getOnlySegmentReader(ireader).getSortedSetDocValues("field");
    assertEquals(3, dv.getValueCount());
    
    TermsEnum termsEnum = dv.termsEnum();
    
    // next()
    assertEquals("beer", termsEnum.next().utf8ToString());
    assertEquals(0, termsEnum.ord());
    assertEquals("hello", termsEnum.next().utf8ToString());
    assertEquals(1, termsEnum.ord());
    assertEquals("world", termsEnum.next().utf8ToString());
    assertEquals(2, termsEnum.ord());
    
    // seekCeil()
    assertEquals(SeekStatus.NOT_FOUND, termsEnum.seekCeil(new BytesRef("ha!")));
    assertEquals("hello", termsEnum.term().utf8ToString());
    assertEquals(1, termsEnum.ord());
    assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(new BytesRef("beer")));
    assertEquals("beer", termsEnum.term().utf8ToString());
    assertEquals(0, termsEnum.ord());
    assertEquals(SeekStatus.END, termsEnum.seekCeil(new BytesRef("zzz")));
    
    // seekExact()
    assertTrue(termsEnum.seekExact(new BytesRef("beer")));
    assertEquals("beer", termsEnum.term().utf8ToString());
    assertEquals(0, termsEnum.ord());
    assertTrue(termsEnum.seekExact(new BytesRef("hello")));
    assertEquals("hello", termsEnum.term().utf8ToString());
    assertEquals(1, termsEnum.ord());
    assertTrue(termsEnum.seekExact(new BytesRef("world")));
    assertEquals("world", termsEnum.term().utf8ToString());
    assertEquals(2, termsEnum.ord());
    assertFalse(termsEnum.seekExact(new BytesRef("bogus")));

    // seek(ord)
    termsEnum.seekExact(0);
    assertEquals("beer", termsEnum.term().utf8ToString());
    assertEquals(0, termsEnum.ord());
    termsEnum.seekExact(1);
    assertEquals("hello", termsEnum.term().utf8ToString());
    assertEquals(1, termsEnum.ord());
    termsEnum.seekExact(2);
    assertEquals("world", termsEnum.term().utf8ToString());
    assertEquals(2, termsEnum.ord());
    ireader.close();
    directory.close();
  }

  private void doTestSortedSetVsStoredFields(int minLength, int maxLength, int maxValuesPerDoc) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      Field idField = new StringField("id", Integer.toString(i), Field.Store.NO);
      doc.add(idField);
      final int length;
      if (minLength == maxLength) {
        length = minLength; // fixed length
      } else {
        length = TestUtil.nextInt(random(), minLength, maxLength);
      }
      int numValues = TestUtil.nextInt(random(), 0, maxValuesPerDoc);
      // create a random set of strings
      Set<String> values = new TreeSet<>();
      for (int v = 0; v < numValues; v++) {
        values.add(TestUtil.randomSimpleString(random(), length));
      }
      
      // add ordered to the stored field
      for (String v : values) {
        doc.add(new StoredField("stored", v));
      }

      // add in any order to the dv field
      ArrayList<String> unordered = new ArrayList<>(values);
      Collections.shuffle(unordered, random());
      for (String v : unordered) {
        doc.add(new SortedSetDocValuesField("dv", new BytesRef(v)));
      }

      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    writer.close();
    
    // compare
    DirectoryReader ir = DirectoryReader.open(dir);
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      SortedSetDocValues docValues = r.getSortedSetDocValues("dv");
      for (int i = 0; i < r.maxDoc(); i++) {
        String stringValues[] = r.document(i).getValues("stored");
        if (docValues != null) {
          docValues.setDocument(i);
        }
        for (int j = 0; j < stringValues.length; j++) {
          assert docValues != null;
          long ord = docValues.nextOrd();
          assert ord != NO_MORE_ORDS;
          BytesRef scratch = docValues.lookupOrd(ord);
          assertEquals(stringValues[j], scratch.utf8ToString());
        }
        assert docValues == null || docValues.nextOrd() == NO_MORE_ORDS;
      }
    }
    ir.close();
    dir.close();
  }
  
  public void testSortedSetFixedLengthVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      int fixedLength = TestUtil.nextInt(random(), 1, 10);
      doTestSortedSetVsStoredFields(fixedLength, fixedLength, 16);
    }
  }
  
  public void testSortedNumericsSingleValuedVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedNumericsVsStoredFields(
          new LongProducer() {
            @Override
            long next() {
              return 1;
            }
          },
          new LongProducer() {
            @Override
            long next() {
              return TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE);
            }
          }
      );
    }
  }
  
  public void testSortedNumericsSingleValuedMissingVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedNumericsVsStoredFields(
          new LongProducer() {
            @Override
            long next() {
              return random().nextBoolean() ? 0 : 1;
            }
          },
          new LongProducer() {
            @Override
            long next() {
              return TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE);
            }
          }
      );
    }
  }
  
  public void testSortedNumericsMultipleValuesVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedNumericsVsStoredFields(
          new LongProducer() {
            @Override
            long next() {
              return TestUtil.nextLong(random(), 0, 50);
            }
          },
          new LongProducer() {
            @Override
            long next() {
              return TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE);
            }
          }
      );
    }
  }
  
  public void testSortedSetVariableLengthVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedSetVsStoredFields(1, 10, 16);
    }
  }

  public void testSortedSetFixedLengthSingleValuedVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      int fixedLength = TestUtil.nextInt(random(), 1, 10);
      doTestSortedSetVsStoredFields(fixedLength, fixedLength, 1);
    }
  }
  
  public void testSortedSetVariableLengthSingleValuedVsStoredFields() throws Exception {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedSetVsStoredFields(1, 10, 1);
    }
  }

  private void assertEquals(Bits expected, Bits actual) throws Exception {
    assertEquals(expected.length(), actual.length());
    for (int i = 0; i < expected.length(); i++) {
      assertEquals(expected.get(i), actual.get(i));
    }
  }
  
  private void assertEquals(int maxDoc, SortedDocValues expected, SortedDocValues actual) throws Exception {
    assertEquals(maxDoc, new SingletonSortedSetDocValues(expected), new SingletonSortedSetDocValues(actual));
  }
  
  private void assertEquals(int maxDoc, SortedSetDocValues expected, SortedSetDocValues actual) throws Exception {
    // can be null for the segment if no docs actually had any SortedDocValues
    // in this case FC.getDocTermsOrds returns EMPTY
    if (actual == null) {
      assertEquals(expected.getValueCount(), 0);
      return;
    }
    assertEquals(expected.getValueCount(), actual.getValueCount());
    // compare ord lists
    for (int i = 0; i < maxDoc; i++) {
      expected.setDocument(i);
      actual.setDocument(i);
      long expectedOrd;
      while ((expectedOrd = expected.nextOrd()) != NO_MORE_ORDS) {
        assertEquals(expectedOrd, actual.nextOrd());
      }
      assertEquals(NO_MORE_ORDS, actual.nextOrd());
    }
    
    // compare ord dictionary
    BytesRef expectedBytes = new BytesRef();
    BytesRef actualBytes = new BytesRef();
    for (long i = 0; i < expected.getValueCount(); i++) {
      expected.lookupTerm(expectedBytes);
      actual.lookupTerm(actualBytes);
      assertEquals(expectedBytes, actualBytes);
    }
    
    // compare termsenum
    assertEquals(expected.getValueCount(), expected.termsEnum(), actual.termsEnum());
  }
  
  private void assertEquals(long numOrds, TermsEnum expected, TermsEnum actual) throws Exception {
    BytesRef ref;
    
    // sequential next() through all terms
    while ((ref = expected.next()) != null) {
      assertEquals(ref, actual.next());
      assertEquals(expected.ord(), actual.ord());
      assertEquals(expected.term(), actual.term());
    }
    assertNull(actual.next());
    
    // sequential seekExact(ord) through all terms
    for (long i = 0; i < numOrds; i++) {
      expected.seekExact(i);
      actual.seekExact(i);
      assertEquals(expected.ord(), actual.ord());
      assertEquals(expected.term(), actual.term());
    }
    
    // sequential seekExact(BytesRef) through all terms
    for (long i = 0; i < numOrds; i++) {
      expected.seekExact(i);
      assertTrue(actual.seekExact(expected.term()));
      assertEquals(expected.ord(), actual.ord());
      assertEquals(expected.term(), actual.term());
    }
    
    // sequential seekCeil(BytesRef) through all terms
    for (long i = 0; i < numOrds; i++) {
      expected.seekExact(i);
      assertEquals(SeekStatus.FOUND, actual.seekCeil(expected.term()));
      assertEquals(expected.ord(), actual.ord());
      assertEquals(expected.term(), actual.term());
    }
    
    // random seekExact(ord)
    for (long i = 0; i < numOrds; i++) {
      long randomOrd = TestUtil.nextLong(random(), 0, numOrds - 1);
      expected.seekExact(randomOrd);
      actual.seekExact(randomOrd);
      assertEquals(expected.ord(), actual.ord());
      assertEquals(expected.term(), actual.term());
    }
    
    // random seekExact(BytesRef)
    for (long i = 0; i < numOrds; i++) {
      long randomOrd = TestUtil.nextLong(random(), 0, numOrds - 1);
      expected.seekExact(randomOrd);
      actual.seekExact(expected.term());
      assertEquals(expected.ord(), actual.ord());
      assertEquals(expected.term(), actual.term());
    }
    
    // random seekCeil(BytesRef)
    for (long i = 0; i < numOrds; i++) {
      BytesRef target = new BytesRef(TestUtil.randomUnicodeString(random()));
      SeekStatus expectedStatus = expected.seekCeil(target);
      assertEquals(expectedStatus, actual.seekCeil(target));
      if (expectedStatus != SeekStatus.END) {
        assertEquals(expected.ord(), actual.ord());
        assertEquals(expected.term(), actual.term());
      }
    }
  }
  
  private void doTestSortedSetVsUninvertedField(int minLength, int maxLength) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      Field idField = new StringField("id", Integer.toString(i), Field.Store.NO);
      doc.add(idField);
      final int length;
      if (minLength == maxLength) {
        length = minLength; // fixed length
      } else {
        length = TestUtil.nextInt(random(), minLength, maxLength);
      }
      int numValues = random().nextInt(17);
      // create a random list of strings
      List<String> values = new ArrayList<>();
      for (int v = 0; v < numValues; v++) {
        values.add(TestUtil.randomSimpleString(random(), length));
      }
      
      // add in any order to the indexed field
      ArrayList<String> unordered = new ArrayList<>(values);
      Collections.shuffle(unordered, random());
      for (String v : values) {
        doc.add(newStringField("indexed", v, Field.Store.NO));
      }

      // add in any order to the dv field
      ArrayList<String> unordered2 = new ArrayList<>(values);
      Collections.shuffle(unordered2, random());
      for (String v : unordered2) {
        doc.add(new SortedSetDocValuesField("dv", new BytesRef(v)));
      }

      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    
    // compare per-segment
    DirectoryReader ir = writer.getReader();
    for (AtomicReaderContext context : ir.leaves()) {
      AtomicReader r = context.reader();
      SortedSetDocValues expected = FieldCache.DEFAULT.getDocTermOrds(r, "indexed");
      SortedSetDocValues actual = r.getSortedSetDocValues("dv");
      assertEquals(r.maxDoc(), expected, actual);
    }
    ir.close();
    
    writer.forceMerge(1);
    
    // now compare again after the merge
    ir = writer.getReader();
    AtomicReader ar = getOnlySegmentReader(ir);
    SortedSetDocValues expected = FieldCache.DEFAULT.getDocTermOrds(ar, "indexed");
    SortedSetDocValues actual = ar.getSortedSetDocValues("dv");
    assertEquals(ir.maxDoc(), expected, actual);
    ir.close();
    
    writer.close();
    dir.close();
  }
  
  public void testSortedSetFixedLengthVsUninvertedField() throws Exception {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      int fixedLength = TestUtil.nextInt(random(), 1, 10);
      doTestSortedSetVsUninvertedField(fixedLength, fixedLength);
    }
  }
  
  public void testSortedSetVariableLengthVsUninvertedField() throws Exception {
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      doTestSortedSetVsUninvertedField(1, 10);
    }
  }

  public void testGCDCompression() throws Exception {
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      final long min = - (((long) random().nextInt(1 << 30)) << 32);
      final long mul = random().nextInt() & 0xFFFFFFFFL;
      final LongProducer longs = new LongProducer() {
        @Override
        long next() {
          return min + mul * random().nextInt(1 << 20);
        }
      };
      doTestNumericsVsStoredFields(longs);
    }
  }

  public void testZeros() throws Exception {
    doTestNumericsVsStoredFields(0, 0);
  }

  public void testZeroOrMin() throws Exception {
    // try to make GCD compression fail if the format did not anticipate that
    // the GCD of 0 and MIN_VALUE is negative
    int numIterations = atLeast(1);
    for (int i = 0; i < numIterations; i++) {
      final LongProducer longs = new LongProducer() {
        @Override
        long next() {
          return random().nextBoolean() ? 0 : Long.MIN_VALUE;
        }
      };
      doTestNumericsVsStoredFields(longs);
    }
  }
  
  public void testTwoNumbersOneMissing() throws IOException {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 0));
    iw.addDocument(doc);
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.YES));
    iw.addDocument(doc);
    iw.forceMerge(1);
    iw.close();
    
    IndexReader ir = DirectoryReader.open(directory);
    assertEquals(1, ir.leaves().size());
    AtomicReader ar = ir.leaves().get(0).reader();
    NumericDocValues dv = ar.getNumericDocValues("dv1");
    assertEquals(0, dv.get(0));
    assertEquals(0, dv.get(1));
    Bits docsWithField = ar.getDocsWithField("dv1");
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));
    ir.close();
    directory.close();
  }
  
  public void testTwoNumbersOneMissingWithMerging() throws IOException {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 0));
    iw.addDocument(doc);
    iw.commit();
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.YES));
    iw.addDocument(doc);
    iw.forceMerge(1);
    iw.close();
    
    IndexReader ir = DirectoryReader.open(directory);
    assertEquals(1, ir.leaves().size());
    AtomicReader ar = ir.leaves().get(0).reader();
    NumericDocValues dv = ar.getNumericDocValues("dv1");
    assertEquals(0, dv.get(0));
    assertEquals(0, dv.get(1));
    Bits docsWithField = ar.getDocsWithField("dv1");
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));
    ir.close();
    directory.close();
  }
  
  public void testThreeNumbersOneMissingWithMerging() throws IOException {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 0));
    iw.addDocument(doc);
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.YES));
    iw.addDocument(doc);
    iw.commit();
    doc = new Document();
    doc.add(new StringField("id", "2", Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 5));
    iw.addDocument(doc);
    iw.forceMerge(1);
    iw.close();
    
    IndexReader ir = DirectoryReader.open(directory);
    assertEquals(1, ir.leaves().size());
    AtomicReader ar = ir.leaves().get(0).reader();
    NumericDocValues dv = ar.getNumericDocValues("dv1");
    assertEquals(0, dv.get(0));
    assertEquals(0, dv.get(1));
    assertEquals(5, dv.get(2));
    Bits docsWithField = ar.getDocsWithField("dv1");
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));
    assertTrue(docsWithField.get(2));
    ir.close();
    directory.close();
  }
  
  public void testTwoBytesOneMissing() throws IOException {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef()));
    iw.addDocument(doc);
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.YES));
    iw.addDocument(doc);
    iw.forceMerge(1);
    iw.close();
    
    IndexReader ir = DirectoryReader.open(directory);
    assertEquals(1, ir.leaves().size());
    AtomicReader ar = ir.leaves().get(0).reader();
    BinaryDocValues dv = ar.getBinaryDocValues("dv1");
    BytesRef ref = dv.get(0);
    assertEquals(new BytesRef(), ref);
    ref = dv.get(1);
    assertEquals(new BytesRef(), ref);
    Bits docsWithField = ar.getDocsWithField("dv1");
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));
    ir.close();
    directory.close();
  }
  
  public void testTwoBytesOneMissingWithMerging() throws IOException {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef()));
    iw.addDocument(doc);
    iw.commit();
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.YES));
    iw.addDocument(doc);
    iw.forceMerge(1);
    iw.close();
    
    IndexReader ir = DirectoryReader.open(directory);
    assertEquals(1, ir.leaves().size());
    AtomicReader ar = ir.leaves().get(0).reader();
    BinaryDocValues dv = ar.getBinaryDocValues("dv1");
    BytesRef ref = dv.get(0);
    assertEquals(new BytesRef(), ref);
    ref = dv.get(1);
    assertEquals(new BytesRef(), ref);
    Bits docsWithField = ar.getDocsWithField("dv1");
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));
    ir.close();
    directory.close();
  }
  
  public void testThreeBytesOneMissingWithMerging() throws IOException {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    Directory directory = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null);
    conf.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory, conf);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef()));
    iw.addDocument(doc);
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.YES));
    iw.addDocument(doc);
    iw.commit();
    doc = new Document();
    doc.add(new StringField("id", "2", Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef("boo")));
    iw.addDocument(doc);
    iw.forceMerge(1);
    iw.close();
    
    IndexReader ir = DirectoryReader.open(directory);
    assertEquals(1, ir.leaves().size());
    AtomicReader ar = ir.leaves().get(0).reader();
    BinaryDocValues dv = ar.getBinaryDocValues("dv1");
    BytesRef ref = dv.get(0);
    assertEquals(new BytesRef(), ref);
    ref = dv.get(1);
    assertEquals(new BytesRef(), ref);
    ref = dv.get(2);
    assertEquals(new BytesRef("boo"), ref);
    Bits docsWithField = ar.getDocsWithField("dv1");
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));
    assertTrue(docsWithField.get(2));
    ir.close();
    directory.close();
  }

  // LUCENE-4853
  public void testHugeBinaryValues() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    // FSDirectory because SimpleText will consume gobbs of
    // space when storing big binary values:
    Directory d = newFSDirectory(createTempDir("hugeBinaryValues"));
    boolean doFixed = random().nextBoolean();
    int numDocs;
    int fixedLength = 0;
    if (doFixed) {
      // Sometimes make all values fixed length since some
      // codecs have different code paths for this:
      numDocs = TestUtil.nextInt(random(), 10, 20);
      fixedLength = TestUtil.nextInt(random(), 65537, 256 * 1024);
    } else {
      numDocs = TestUtil.nextInt(random(), 100, 200);
    }
    IndexWriter w = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    List<byte[]> docBytes = new ArrayList<>();
    long totalBytes = 0;
    for(int docID=0;docID<numDocs;docID++) {
      // we don't use RandomIndexWriter because it might add
      // more docvalues than we expect !!!!

      // Must be > 64KB in size to ensure more than 2 pages in
      // PagedBytes would be needed:
      int numBytes;
      if (doFixed) {
        numBytes = fixedLength;
      } else if (docID == 0 || random().nextInt(5) == 3) {
        numBytes = TestUtil.nextInt(random(), 65537, 3 * 1024 * 1024);
      } else {
        numBytes = TestUtil.nextInt(random(), 1, 1024 * 1024);
      }
      totalBytes += numBytes;
      if (totalBytes > 5 * 1024*1024) {
        break;
      }
      byte[] bytes = new byte[numBytes];
      random().nextBytes(bytes);
      docBytes.add(bytes);
      Document doc = new Document();      
      BytesRef b = new BytesRef(bytes);
      b.length = bytes.length;
      doc.add(new BinaryDocValuesField("field", b));
      doc.add(new StringField("id", ""+docID, Field.Store.YES));
      try {
        w.addDocument(doc);
      } catch (IllegalArgumentException iae) {
        if (iae.getMessage().indexOf("is too large") == -1) {
          throw iae;
        } else {
          // OK: some codecs can't handle binary DV > 32K
          assertFalse(codecAcceptsHugeBinaryValues("field"));
          w.rollback();
          d.close();
          return;
        }
      }
    }
    
    DirectoryReader r;
    try {
      r = w.getReader();
    } catch (IllegalArgumentException iae) {
      if (iae.getMessage().indexOf("is too large") == -1) {
        throw iae;
      } else {
        assertFalse(codecAcceptsHugeBinaryValues("field"));

        // OK: some codecs can't handle binary DV > 32K
        w.rollback();
        d.close();
        return;
      }
    }
    w.close();

    AtomicReader ar = SlowCompositeReaderWrapper.wrap(r);

    BinaryDocValues s = FieldCache.DEFAULT.getTerms(ar, "field", false);
    for(int docID=0;docID<docBytes.size();docID++) {
      Document doc = ar.document(docID);
      BytesRef bytes = s.get(docID);
      byte[] expected = docBytes.get(Integer.parseInt(doc.get("id")));
      assertEquals(expected.length, bytes.length);
      assertEquals(new BytesRef(expected), bytes);
    }

    assertTrue(codecAcceptsHugeBinaryValues("field"));

    ar.close();
    d.close();
  }

  // TODO: get this out of here and into the deprecated codecs (4.0, 4.2)
  public void testHugeBinaryValueLimit() throws Exception {
    // We only test DVFormats that have a limit
    assumeFalse("test requires codec with limits on max binary field length", codecAcceptsHugeBinaryValues("field"));
    Analyzer analyzer = new MockAnalyzer(random());
    // FSDirectory because SimpleText will consume gobbs of
    // space when storing big binary values:
    Directory d = newFSDirectory(createTempDir("hugeBinaryValues"));
    boolean doFixed = random().nextBoolean();
    int numDocs;
    int fixedLength = 0;
    if (doFixed) {
      // Sometimes make all values fixed length since some
      // codecs have different code paths for this:
      numDocs = TestUtil.nextInt(random(), 10, 20);
      fixedLength = Lucene42DocValuesFormat.MAX_BINARY_FIELD_LENGTH;
    } else {
      numDocs = TestUtil.nextInt(random(), 100, 200);
    }
    IndexWriter w = new IndexWriter(d, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    List<byte[]> docBytes = new ArrayList<>();
    long totalBytes = 0;
    for(int docID=0;docID<numDocs;docID++) {
      // we don't use RandomIndexWriter because it might add
      // more docvalues than we expect !!!!

      // Must be > 64KB in size to ensure more than 2 pages in
      // PagedBytes would be needed:
      int numBytes;
      if (doFixed) {
        numBytes = fixedLength;
      } else if (docID == 0 || random().nextInt(5) == 3) {
        numBytes = Lucene42DocValuesFormat.MAX_BINARY_FIELD_LENGTH;
      } else {
        numBytes = TestUtil.nextInt(random(), 1, Lucene42DocValuesFormat.MAX_BINARY_FIELD_LENGTH);
      }
      totalBytes += numBytes;
      if (totalBytes > 5 * 1024*1024) {
        break;
      }
      byte[] bytes = new byte[numBytes];
      random().nextBytes(bytes);
      docBytes.add(bytes);
      Document doc = new Document();      
      BytesRef b = new BytesRef(bytes);
      b.length = bytes.length;
      doc.add(new BinaryDocValuesField("field", b));
      doc.add(new StringField("id", ""+docID, Field.Store.YES));
      w.addDocument(doc);
    }
    
    DirectoryReader r = w.getReader();
    w.close();

    AtomicReader ar = SlowCompositeReaderWrapper.wrap(r);

    BinaryDocValues s = FieldCache.DEFAULT.getTerms(ar, "field", false);
    for(int docID=0;docID<docBytes.size();docID++) {
      Document doc = ar.document(docID);
      BytesRef bytes = s.get(docID);
      byte[] expected = docBytes.get(Integer.parseInt(doc.get("id")));
      assertEquals(expected.length, bytes.length);
      assertEquals(new BytesRef(expected), bytes);
    }

    ar.close();
    d.close();
  }
  
  /** Tests dv against stored fields with threads (binary/numeric/sorted, no missing) */
  public void testThreads() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Document doc = new Document();
    Field idField = new StringField("id", "", Field.Store.NO);
    Field storedBinField = new StoredField("storedBin", new byte[0]);
    Field dvBinField = new BinaryDocValuesField("dvBin", new BytesRef());
    Field dvSortedField = new SortedDocValuesField("dvSorted", new BytesRef());
    Field storedNumericField = new StoredField("storedNum", "");
    Field dvNumericField = new NumericDocValuesField("dvNum", 0);
    doc.add(idField);
    doc.add(storedBinField);
    doc.add(dvBinField);
    doc.add(dvSortedField);
    doc.add(storedNumericField);
    doc.add(dvNumericField);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      int length = TestUtil.nextInt(random(), 0, 8);
      byte buffer[] = new byte[length];
      random().nextBytes(buffer);
      storedBinField.setBytesValue(buffer);
      dvBinField.setBytesValue(buffer);
      dvSortedField.setBytesValue(buffer);
      long numericValue = random().nextLong();
      storedNumericField.setStringValue(Long.toString(numericValue));
      dvNumericField.setLongValue(numericValue);
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    writer.close();
    
    // compare
    final DirectoryReader ir = DirectoryReader.open(dir);
    int numThreads = TestUtil.nextInt(random(), 2, 7);
    Thread threads[] = new Thread[numThreads];
    final CountDownLatch startingGun = new CountDownLatch(1);
    
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread() {
        @Override
        public void run() {
          try {
            startingGun.await();
            for (AtomicReaderContext context : ir.leaves()) {
              AtomicReader r = context.reader();
              BinaryDocValues binaries = r.getBinaryDocValues("dvBin");
              SortedDocValues sorted = r.getSortedDocValues("dvSorted");
              NumericDocValues numerics = r.getNumericDocValues("dvNum");
              for (int j = 0; j < r.maxDoc(); j++) {
                BytesRef binaryValue = r.document(j).getBinaryValue("storedBin");
                BytesRef scratch = binaries.get(j);
                assertEquals(binaryValue, scratch);
                scratch = sorted.get(j);
                assertEquals(binaryValue, scratch);
                String expected = r.document(j).get("storedNum");
                assertEquals(Long.parseLong(expected), numerics.get(j));
              }
            }
            TestUtil.checkReader(ir);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      };
      threads[i].start();
    }
    startingGun.countDown();
    for (Thread t : threads) {
      t.join();
    }
    ir.close();
    dir.close();
  }
  
  /** Tests dv against stored fields with threads (all types + missing) */
  public void testThreads2() throws Exception {
    assumeTrue("Codec does not support getDocsWithField", defaultCodecSupportsDocsWithField());
    assumeTrue("Codec does not support SORTED_SET", defaultCodecSupportsSortedSet());
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, conf);
    Field idField = new StringField("id", "", Field.Store.NO);
    Field storedBinField = new StoredField("storedBin", new byte[0]);
    Field dvBinField = new BinaryDocValuesField("dvBin", new BytesRef());
    Field dvSortedField = new SortedDocValuesField("dvSorted", new BytesRef());
    Field storedNumericField = new StoredField("storedNum", "");
    Field dvNumericField = new NumericDocValuesField("dvNum", 0);
    
    // index some docs
    int numDocs = atLeast(300);
    for (int i = 0; i < numDocs; i++) {
      idField.setStringValue(Integer.toString(i));
      int length = TestUtil.nextInt(random(), 0, 8);
      byte buffer[] = new byte[length];
      random().nextBytes(buffer);
      storedBinField.setBytesValue(buffer);
      dvBinField.setBytesValue(buffer);
      dvSortedField.setBytesValue(buffer);
      long numericValue = random().nextLong();
      storedNumericField.setStringValue(Long.toString(numericValue));
      dvNumericField.setLongValue(numericValue);
      Document doc = new Document();
      doc.add(idField);
      if (random().nextInt(4) > 0) {
        doc.add(storedBinField);
        doc.add(dvBinField);
        doc.add(dvSortedField);
      }
      if (random().nextInt(4) > 0) {
        doc.add(storedNumericField);
        doc.add(dvNumericField);
      }
      int numSortedSetFields = random().nextInt(3);
      Set<String> values = new TreeSet<>();
      for (int j = 0; j < numSortedSetFields; j++) {
        values.add(TestUtil.randomSimpleString(random()));
      }
      for (String v : values) {
        doc.add(new SortedSetDocValuesField("dvSortedSet", new BytesRef(v)));
        doc.add(new StoredField("storedSortedSet", v));
      }
      int numSortedNumericFields = random().nextInt(3);
      Set<Long> numValues = new TreeSet<>();
      for (int j = 0; j < numSortedNumericFields; j++) {
        numValues.add(TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE));
      }
      for (Long l : numValues) {
        doc.add(new SortedNumericDocValuesField("dvSortedNumeric", l));
        doc.add(new StoredField("storedSortedNumeric", Long.toString(l)));
      }
      writer.addDocument(doc);
      if (random().nextInt(31) == 0) {
        writer.commit();
      }
    }
    
    // delete some docs
    int numDeletions = random().nextInt(numDocs/10);
    for (int i = 0; i < numDeletions; i++) {
      int id = random().nextInt(numDocs);
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
    writer.close();
    
    // compare
    final DirectoryReader ir = DirectoryReader.open(dir);
    int numThreads = TestUtil.nextInt(random(), 2, 7);
    Thread threads[] = new Thread[numThreads];
    final CountDownLatch startingGun = new CountDownLatch(1);
    
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread() {
        @Override
        public void run() {
          try {
            startingGun.await();
            for (AtomicReaderContext context : ir.leaves()) {
              AtomicReader r = context.reader();
              BinaryDocValues binaries = r.getBinaryDocValues("dvBin");
              Bits binaryBits = r.getDocsWithField("dvBin");
              SortedDocValues sorted = r.getSortedDocValues("dvSorted");
              Bits sortedBits = r.getDocsWithField("dvSorted");
              NumericDocValues numerics = r.getNumericDocValues("dvNum");
              Bits numericBits = r.getDocsWithField("dvNum");
              SortedSetDocValues sortedSet = r.getSortedSetDocValues("dvSortedSet");
              Bits sortedSetBits = r.getDocsWithField("dvSortedSet");
              SortedNumericDocValues sortedNumeric = r.getSortedNumericDocValues("dvSortedNumeric");
              Bits sortedNumericBits = r.getDocsWithField("dvSortedNumeric");
              for (int j = 0; j < r.maxDoc(); j++) {
                BytesRef binaryValue = r.document(j).getBinaryValue("storedBin");
                if (binaryValue != null) {
                  if (binaries != null) {
                    BytesRef scratch = binaries.get(j);
                    assertEquals(binaryValue, scratch);
                    scratch = sorted.get(j);
                    assertEquals(binaryValue, scratch);
                    assertTrue(binaryBits.get(j));
                    assertTrue(sortedBits.get(j));
                  }
                } else if (binaries != null) {
                  assertFalse(binaryBits.get(j));
                  assertFalse(sortedBits.get(j));
                  assertEquals(-1, sorted.getOrd(j));
                }
               
                String number = r.document(j).get("storedNum");
                if (number != null) {
                  if (numerics != null) {
                    assertEquals(Long.parseLong(number), numerics.get(j));
                  }
                } else if (numerics != null) {
                  assertFalse(numericBits.get(j));
                  assertEquals(0, numerics.get(j));
                }
                
                String values[] = r.document(j).getValues("storedSortedSet");
                if (values.length > 0) {
                  assertNotNull(sortedSet);
                  sortedSet.setDocument(j);
                  for (int k = 0; k < values.length; k++) {
                    long ord = sortedSet.nextOrd();
                    assertTrue(ord != SortedSetDocValues.NO_MORE_ORDS);
                    BytesRef value = sortedSet.lookupOrd(ord);
                    assertEquals(values[k], value.utf8ToString());
                  }
                  assertEquals(SortedSetDocValues.NO_MORE_ORDS, sortedSet.nextOrd());
                  assertTrue(sortedSetBits.get(j));
                } else if (sortedSet != null) {
                  sortedSet.setDocument(j);
                  assertEquals(SortedSetDocValues.NO_MORE_ORDS, sortedSet.nextOrd());
                  assertFalse(sortedSetBits.get(j));
                }
                
                String numValues[] = r.document(j).getValues("storedSortedNumeric");
                if (numValues.length > 0) {
                  assertNotNull(sortedNumeric);
                  sortedNumeric.setDocument(j);
                  assertEquals(numValues.length, sortedNumeric.count());
                  for (int k = 0; k < numValues.length; k++) {
                    long v = sortedNumeric.valueAt(k);
                    assertEquals(numValues[k], Long.toString(v));
                  }
                  assertTrue(sortedNumericBits.get(j));
                } else if (sortedNumeric != null) {
                  sortedNumeric.setDocument(j);
                  assertEquals(0, sortedNumeric.count());
                  assertFalse(sortedNumericBits.get(j));
                }
              }
            }
            TestUtil.checkReader(ir);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      };
      threads[i].start();
    }
    startingGun.countDown();
    for (Thread t : threads) {
      t.join();
    }
    ir.close();
    dir.close();
  }

  // LUCENE-5218
  public void testEmptyBinaryValueOnPageSizes() throws Exception {
    // Test larger and larger power-of-two sized values,
    // followed by empty string value:
    for(int i=0;i<20;i++) {
      if (i > 14 && codecAcceptsHugeBinaryValues("field") == false) {
        break;
      }
      Directory dir = newDirectory();
      RandomIndexWriter w = new RandomIndexWriter(random(), dir);
      BytesRef bytes = new BytesRef();
      bytes.bytes = new byte[1<<i];
      bytes.length = 1<<i;
      for(int j=0;j<4;j++) {
        Document doc = new Document();
        doc.add(new BinaryDocValuesField("field", bytes));
        w.addDocument(doc);
      }
      Document doc = new Document();
      doc.add(new StoredField("id", "5"));
      doc.add(new BinaryDocValuesField("field", new BytesRef()));
      w.addDocument(doc);
      IndexReader r = w.getReader();
      w.close();

      AtomicReader ar = SlowCompositeReaderWrapper.wrap(r);
      BinaryDocValues values = ar.getBinaryDocValues("field");
      for(int j=0;j<5;j++) {
        BytesRef result = values.get(0);
        assertTrue(result.length == 0 || result.length == 1<<i);
      }
      ar.close();
      dir.close();
    }
  }
  
  public void testOneSortedNumber() throws IOException {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField("dv", 5));
    writer.addDocument(doc);
    writer.close();
    
    // Now search the index:
    IndexReader reader = DirectoryReader.open(directory);
    assert reader.leaves().size() == 1;
    SortedNumericDocValues dv = reader.leaves().get(0).reader().getSortedNumericDocValues("dv");
    dv.setDocument(0);
    assertEquals(1, dv.count());
    assertEquals(5, dv.valueAt(0));

    reader.close();
    directory.close();
  }
  
  public void testOneSortedNumberOneMissing() throws IOException {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory directory = newDirectory();
    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(TEST_VERSION_CURRENT, null));
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField("dv", 5));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.close();
    
    // Now search the index:
    IndexReader reader = DirectoryReader.open(directory);
    assert reader.leaves().size() == 1;
    SortedNumericDocValues dv = reader.leaves().get(0).reader().getSortedNumericDocValues("dv");
    dv.setDocument(0);
    assertEquals(1, dv.count());
    assertEquals(5, dv.valueAt(0));
    dv.setDocument(1);
    assertEquals(0, dv.count());
    
    Bits docsWithField = reader.leaves().get(0).reader().getDocsWithField("dv");
    assertEquals(2, docsWithField.length());
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));

    reader.close();
    directory.close();
  }
  
  public void testTwoSortedNumber() throws IOException {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField("dv", 11));
    doc.add(new SortedNumericDocValuesField("dv", -5));
    writer.addDocument(doc);
    writer.close();
    
    // Now search the index:
    IndexReader reader = DirectoryReader.open(directory);
    assert reader.leaves().size() == 1;
    SortedNumericDocValues dv = reader.leaves().get(0).reader().getSortedNumericDocValues("dv");
    dv.setDocument(0);
    assertEquals(2, dv.count());
    assertEquals(-5, dv.valueAt(0));
    assertEquals(11, dv.valueAt(1));

    reader.close();
    directory.close();
  }
  
  public void testTwoSortedNumberOneMissing() throws IOException {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory directory = newDirectory();
    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(TEST_VERSION_CURRENT, null));
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField("dv", 11));
    doc.add(new SortedNumericDocValuesField("dv", -5));
    writer.addDocument(doc);
    writer.addDocument(new Document());
    writer.close();
    
    // Now search the index:
    IndexReader reader = DirectoryReader.open(directory);
    assert reader.leaves().size() == 1;
    SortedNumericDocValues dv = reader.leaves().get(0).reader().getSortedNumericDocValues("dv");
    dv.setDocument(0);
    assertEquals(2, dv.count());
    assertEquals(-5, dv.valueAt(0));
    assertEquals(11, dv.valueAt(1));
    dv.setDocument(1);
    assertEquals(0, dv.count());
    
    Bits docsWithField = reader.leaves().get(0).reader().getDocsWithField("dv");
    assertEquals(2, docsWithField.length());
    assertTrue(docsWithField.get(0));
    assertFalse(docsWithField.get(1));

    reader.close();
    directory.close();
  }
  
  public void testSortedNumberMerge() throws IOException {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory directory = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(TEST_VERSION_CURRENT, null);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter writer = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField("dv", 11));
    writer.addDocument(doc);
    writer.commit();
    doc = new Document();
    doc.add(new SortedNumericDocValuesField("dv", -5));
    writer.addDocument(doc);
    writer.forceMerge(1);
    writer.close();
    
    // Now search the index:
    IndexReader reader = DirectoryReader.open(directory);
    assert reader.leaves().size() == 1;
    SortedNumericDocValues dv = reader.leaves().get(0).reader().getSortedNumericDocValues("dv");
    dv.setDocument(0);
    assertEquals(1, dv.count());
    assertEquals(11, dv.valueAt(0));
    dv.setDocument(1);
    assertEquals(1, dv.count());
    assertEquals(-5, dv.valueAt(0));

    reader.close();
    directory.close();
  }
  
  public void testSortedNumberMergeAwayAllValues() throws IOException {
    assumeTrue("Codec does not support SORTED_NUMERIC", defaultCodecSupportsSortedNumeric());
    Directory directory = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwconfig = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwconfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iwriter = new RandomIndexWriter(random(), directory, iwconfig);
    
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    iwriter.addDocument(doc);    
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new SortedNumericDocValuesField("field", 5));
    iwriter.addDocument(doc);
    iwriter.commit();
    iwriter.deleteDocuments(new Term("id", "1"));
    iwriter.forceMerge(1);
    
    DirectoryReader ireader = iwriter.getReader();
    iwriter.close();
    
    SortedNumericDocValues dv = getOnlySegmentReader(ireader).getSortedNumericDocValues("field");
    dv.setDocument(0);
    assertEquals(0, dv.count());
    
    ireader.close();
    directory.close();
  }

  protected boolean codecAcceptsHugeBinaryValues(String field) {
    return true;
  }
}
