package org.apache.lucene.facet;

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
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.DoubleDocValues;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;

public class TestTaxonomyFacetSumValueSource extends FacetTestCase {

  public void testBasic() throws Exception {

    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();

    // Writes facet ords to a separate directory from the
    // main index:
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    FacetsConfig config = new FacetsConfig(taxoWriter);

    // Reused across documents, to add the necessary facet
    // fields:
    Document doc = new Document();
    doc.add(new IntField("num", 10, Field.Store.NO));
    doc.add(new FacetField("Author", "Bob"));
    writer.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new IntField("num", 20, Field.Store.NO));
    doc.add(new FacetField("Author", "Lisa"));
    writer.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new IntField("num", 30, Field.Store.NO));
    doc.add(new FacetField("Author", "Lisa"));
    writer.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new IntField("num", 40, Field.Store.NO));
    doc.add(new FacetField("Author", "Susan"));
    writer.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new IntField("num", 45, Field.Store.NO));
    doc.add(new FacetField("Author", "Frank"));
    writer.addDocument(config.build(doc));

    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();

    // Aggregate the facet counts:
    FacetsCollector c = new FacetsCollector();

    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query and one of the
    // Facets.search utility methods:
    searcher.search(new MatchAllDocsQuery(), c);

    TaxonomyFacetSumValueSource facets = new TaxonomyFacetSumValueSource(taxoReader, new FacetsConfig(), c, new IntFieldSource("num"));

    // Retrieve & verify results:
    assertEquals("value=145.0 childCount=4\n  Lisa (50.0)\n  Frank (45.0)\n  Susan (40.0)\n  Bob (10.0)\n", facets.getTopChildren(10, "Author").toString());

    taxoReader.close();
    searcher.getIndexReader().close();
    dir.close();
    taxoDir.close();
  }

  // LUCENE-5333
  public void testSparseFacets() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();

    // Writes facet ords to a separate directory from the
    // main index:
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    FacetsConfig config = new FacetsConfig(taxoWriter);

    Document doc = new Document();
    doc.add(new IntField("num", 10, Field.Store.NO));
    doc.add(new FacetField("a", "foo1"));
    writer.addDocument(config.build(doc));

    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new IntField("num", 20, Field.Store.NO));
    doc.add(new FacetField("a", "foo2"));
    doc.add(new FacetField("b", "bar1"));
    writer.addDocument(config.build(doc));

    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new IntField("num", 30, Field.Store.NO));
    doc.add(new FacetField("a", "foo3"));
    doc.add(new FacetField("b", "bar2"));
    doc.add(new FacetField("c", "baz1"));
    writer.addDocument(config.build(doc));

    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();

    FacetsCollector c = new FacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    

    TaxonomyFacetSumValueSource facets = new TaxonomyFacetSumValueSource(taxoReader, new FacetsConfig(), c, new IntFieldSource("num"));

    // Ask for top 10 labels for any dims that have counts:
    List<FacetResult> results = facets.getAllDims(10);

    assertEquals(3, results.size());
    assertEquals("value=60.0 childCount=3\n  foo3 (30.0)\n  foo2 (20.0)\n  foo1 (10.0)\n", results.get(0).toString());
    assertEquals("value=50.0 childCount=2\n  bar2 (30.0)\n  bar1 (20.0)\n", results.get(1).toString());
    assertEquals("value=30.0 childCount=1\n  baz1 (30.0)\n", results.get(2).toString());

    IOUtils.close(searcher.getIndexReader(), taxoReader, dir, taxoDir);
  }

  public void testWrongIndexFieldName() throws Exception {

    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();

    // Writes facet ords to a separate directory from the
    // main index:
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);

    FacetsConfig config = new FacetsConfig(taxoWriter);
    config.setIndexFieldName("a", "$facets2");

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(new IntField("num", 10, Field.Store.NO));
    doc.add(new FacetField("a", "foo1"));
    writer.addDocument(config.build(doc));

    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();

    FacetsCollector c = new FacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    

    TaxonomyFacetSumValueSource facets = new TaxonomyFacetSumValueSource(taxoReader, config, c, new IntFieldSource("num"));

    // Ask for top 10 labels for any dims that have counts:
    List<FacetResult> results = facets.getAllDims(10);
    assertTrue(results.isEmpty());

    try {
      facets.getSpecificValue("a");
      fail("should have hit exc");
    } catch (IllegalArgumentException iae) {
      // expected
    }

    try {
      facets.getTopChildren(10, "a");
      fail("should have hit exc");
    } catch (IllegalArgumentException iae) {
      // expected
    }

    IOUtils.close(searcher.getIndexReader(), taxoReader, dir, taxoDir);
  }

  public void testSumScoreAggregator() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();

    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));

    FacetsConfig config = new FacetsConfig(taxoWriter);

    for(int i = atLeast(30); i > 0; --i) {
      Document doc = new Document();
      if (random().nextBoolean()) { // don't match all documents
        doc.add(new StringField("f", "v", Field.Store.NO));
      }
      doc.add(new FacetField("dim", "a"));
      iw.addDocument(config.build(doc));
    }
    
    DirectoryReader r = DirectoryReader.open(iw, true);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    
    FacetsCollector fc = new FacetsCollector(true);
    ConstantScoreQuery csq = new ConstantScoreQuery(new MatchAllDocsQuery());
    csq.setBoost(2.0f);
    
    TopDocs td = Facets.search(newSearcher(r), csq, 10, fc);

    Facets facets = new TaxonomyFacetSumValueSource(taxoReader, config, fc, new TaxonomyFacetSumValueSource.ScoreValueSource());
    
    int expected = (int) (td.getMaxScore() * td.totalHits);
    assertEquals(expected, facets.getSpecificValue("dim", "a").intValue());
    
    IOUtils.close(iw, taxoWriter, taxoReader, taxoDir, r, indexDir);
  }
  
  public void testNoScore() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();

    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    FacetsConfig config = new FacetsConfig(taxoWriter);
    for (int i = 0; i < 4; i++) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("price", (i+1)));
      doc.add(new FacetField("a", Integer.toString(i % 2)));
      iw.addDocument(config.build(doc));
    }
    
    DirectoryReader r = DirectoryReader.open(iw, true);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);

    FacetsCollector sfc = new FacetsCollector();
    newSearcher(r).search(new MatchAllDocsQuery(), sfc);
    Facets facets = new TaxonomyFacetSumValueSource(taxoReader, config, sfc, new LongFieldSource("price"));
    assertEquals("value=10.0 childCount=2\n  1 (6.0)\n  0 (4.0)\n", facets.getTopChildren(10, "a").toString());
    
    IOUtils.close(taxoWriter, iw, taxoReader, taxoDir, r, indexDir);
  }

  public void testWithScore() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();

    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));

    FacetsConfig config = new FacetsConfig(taxoWriter);
    for (int i = 0; i < 4; i++) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("price", (i+1)));
      doc.add(new FacetField("a", Integer.toString(i % 2)));
      iw.addDocument(config.build(doc));
    }
    
    DirectoryReader r = DirectoryReader.open(iw, true);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);

    ValueSource valueSource = new ValueSource() {
      @Override
      public FunctionValues getValues(@SuppressWarnings("rawtypes") Map context, AtomicReaderContext readerContext) throws IOException {
        final Scorer scorer = (Scorer) context.get("scorer");
        assert scorer != null;
        return new DoubleDocValues(this) {
          @Override
          public double doubleVal(int document) {
            try {
              return scorer.score();
            } catch (IOException exception) {
              throw new RuntimeException(exception);
            }
          }
        };
      }

      @Override public boolean equals(Object o) { return o == this; }
      @Override public int hashCode() { return System.identityHashCode(this); }
      @Override public String description() { return "score()"; }
    };
    
    FacetsCollector fc = new FacetsCollector(true);
    TopScoreDocCollector tsdc = TopScoreDocCollector.create(10, true);
    // score documents by their 'price' field - makes asserting the correct counts for the categories easier
    Query q = new FunctionQuery(new LongFieldSource("price"));
    Facets.search(newSearcher(r), q, 10, fc);
    Facets facets = new TaxonomyFacetSumValueSource(taxoReader, config, fc, valueSource);
    
    assertEquals("value=10.0 childCount=2\n  1 (6.0)\n  0 (4.0)\n", facets.getTopChildren(10, "a").toString());
    
    IOUtils.close(taxoWriter, iw, taxoReader, taxoDir, r, indexDir);
  }

  public void testRollupValues() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();

    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    FacetsConfig config = new FacetsConfig(taxoWriter);
    config.setHierarchical("a", true);
    //config.setRequireDimCount("a", true);
    
    for (int i = 0; i < 4; i++) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("price", (i+1)));
      doc.add(new FacetField("a", Integer.toString(i % 2), "1"));
      iw.addDocument(config.build(doc));
    }
    
    DirectoryReader r = DirectoryReader.open(iw, true);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);

    ValueSource valueSource = new LongFieldSource("price");
    FacetsCollector sfc = new FacetsCollector();
    newSearcher(r).search(new MatchAllDocsQuery(), sfc);
    Facets facets = new TaxonomyFacetSumValueSource(taxoReader, config, sfc, valueSource);
    
    assertEquals("value=10.0 childCount=2\n  1 (6.0)\n  0 (4.0)\n", facets.getTopChildren(10, "a").toString());
    
    IOUtils.close(taxoWriter, iw, taxoReader, taxoDir, r, indexDir);
  }

  public void testCountAndSumScore() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    FacetsConfig config = new FacetsConfig(taxoWriter);
    config.setIndexFieldName("b", "$b");
    
    for(int i = atLeast(30); i > 0; --i) {
      Document doc = new Document();
      doc.add(new StringField("f", "v", Field.Store.NO));
      doc.add(new FacetField("a", "1"));
      doc.add(new FacetField("b", "1"));
      iw.addDocument(config.build(doc));
    }
    
    DirectoryReader r = DirectoryReader.open(iw, true);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    
    FacetsCollector fc = new FacetsCollector(true);
    TopDocs hits = Facets.search(newSearcher(r), new MatchAllDocsQuery(), 10, fc);
    
    Facets facets1 = getTaxonomyFacetCounts(taxoReader, config, fc);
    Facets facets2 = new TaxonomyFacetSumValueSource(new DocValuesOrdinalsReader("$b"), taxoReader, config, fc, new TaxonomyFacetSumValueSource.ScoreValueSource());

    assertEquals(r.maxDoc(), facets1.getTopChildren(10, "a").value.intValue());
    double expected = hits.getMaxScore() * r.numDocs();
    assertEquals(r.maxDoc(), facets2.getTopChildren(10, "b").value.doubleValue(), 1E-10);
    IOUtils.close(taxoWriter, iw, taxoReader, taxoDir, r, indexDir);
  }

  // nocommit in the sparse case test that we are really
  // sorting by the correct dim count
}
