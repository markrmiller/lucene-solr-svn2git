package org.apache.lucene.facet.simple;

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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.facet.FacetTestCase;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.facet.util.PrintTaxonomyStats;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util._TestUtil;

public class TestTaxonomyFacetCounts extends FacetTestCase {

  public void testBasic() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();

    // Writes facet ords to a separate directory from the
    // main index:
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);

    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("Publish Date");

    IndexWriter writer = new FacetIndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())), taxoWriter, config);

    Document doc = new Document();
    doc.add(new FacetField("Author", "Bob"));
    doc.add(new FacetField("Publish Date", "2010", "10", "15"));
    writer.addDocument(doc);

    doc = new Document();
    doc.add(new FacetField("Author", "Lisa"));
    doc.add(new FacetField("Publish Date", "2010", "10", "20"));
    writer.addDocument(doc);

    doc = new Document();
    doc.add(new FacetField("Author", "Lisa"));
    doc.add(new FacetField("Publish Date", "2012", "1", "1"));
    writer.addDocument(doc);

    doc = new Document();
    doc.add(new FacetField("Author", "Susan"));
    doc.add(new FacetField("Publish Date", "2012", "1", "7"));
    writer.addDocument(doc);

    doc = new Document();
    doc.add(new FacetField("Author", "Frank"));
    doc.add(new FacetField("Publish Date", "1999", "5", "5"));
    writer.addDocument(doc);

    // NRT open
    IndexSearcher searcher = newSearcher(DirectoryReader.open(writer, true));
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();

    // Aggregate the facet counts:
    SimpleFacetsCollector c = new SimpleFacetsCollector();

    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query, and use MultiCollector to
    // wrap collecting the "normal" hits and also facets:
    searcher.search(new MatchAllDocsQuery(), c);

    Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, c);

    // Retrieve & verify results:
    assertEquals("Publish Date (5)\n  2010 (2)\n  2012 (2)\n  1999 (1)\n", facets.getTopChildren(10, "Publish Date").toString());
    assertEquals("Author (5)\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n", facets.getTopChildren(10, "Author").toString());

    // Now user drills down on Publish Date/2010:
    SimpleDrillDownQuery q2 = new SimpleDrillDownQuery(config);
    q2.add("Publish Date", "2010");
    c = new SimpleFacetsCollector();
    searcher.search(q2, c);
    facets = new FastTaxonomyFacetCounts(taxoReader, config, c);
    assertEquals("Author (2)\n  Bob (1)\n  Lisa (1)\n", facets.getTopChildren(10, "Author").toString());

    assertEquals(1, facets.getSpecificValue("Author", "Lisa"));

    // Smoke test PrintTaxonomyStats:
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    PrintTaxonomyStats.printStats(taxoReader, new PrintStream(bos, false, "UTF-8"), true);
    String result = bos.toString("UTF-8");
    assertTrue(result.indexOf("/Author: 4 immediate children; 5 total categories") != -1);
    assertTrue(result.indexOf("/Publish Date: 3 immediate children; 12 total categories") != -1);
    // Make sure at least a few nodes of the tree came out:
    assertTrue(result.indexOf("  /1999") != -1);
    assertTrue(result.indexOf("  /2012") != -1);
    assertTrue(result.indexOf("      /20") != -1);

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

    IndexWriter writer = new FacetIndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())), taxoWriter, new FacetsConfig());

    Document doc = new Document();
    doc.add(new FacetField("a", "foo1"));
    writer.addDocument(doc);

    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new FacetField("a", "foo2"));
    doc.add(new FacetField("b", "bar1"));
    writer.addDocument(doc);

    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new FacetField("a", "foo3"));
    doc.add(new FacetField("b", "bar2"));
    doc.add(new FacetField("c", "baz1"));
    writer.addDocument(doc);

    // NRT open
    IndexSearcher searcher = newSearcher(DirectoryReader.open(writer, true));
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();

    SimpleFacetsCollector c = new SimpleFacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    

    Facets facets = getFacetCounts(taxoReader, new FacetsConfig(), c);

    // Ask for top 10 labels for any dims that have counts:
    List<SimpleFacetResult> results = facets.getAllDims(10);

    assertEquals(3, results.size());
    assertEquals("a (3)\n  foo1 (1)\n  foo2 (1)\n  foo3 (1)\n", results.get(0).toString());
    assertEquals("b (2)\n  bar1 (1)\n  bar2 (1)\n", results.get(1).toString());
    assertEquals("c (1)\n  baz1 (1)\n", results.get(2).toString());

    searcher.getIndexReader().close();
    taxoReader.close();
    taxoDir.close();
    dir.close();
  }

  public void testWrongIndexFieldName() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();

    // Writes facet ords to a separate directory from the
    // main index:
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);

    FacetsConfig config = new FacetsConfig();
    config.setIndexFieldName("a", "$facets2");
    IndexWriter writer = new FacetIndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())), taxoWriter, config);

    Document doc = new Document();
    doc.add(new FacetField("a", "foo1"));
    writer.addDocument(doc);

    // NRT open
    IndexSearcher searcher = newSearcher(DirectoryReader.open(writer, true));
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();

    SimpleFacetsCollector c = new SimpleFacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    

    // Uses default $facets field:
    Facets facets;
    if (random().nextBoolean()) {
      facets = new FastTaxonomyFacetCounts(taxoReader, config, c);
    } else {
      OrdinalsReader ordsReader = new DocValuesOrdinalsReader();
      if (random().nextBoolean()) {
        ordsReader = new CachedOrdinalsReader(ordsReader);
      }
      facets = new TaxonomyFacetCounts(ordsReader, taxoReader, config, c);
    }

    // Ask for top 10 labels for any dims that have counts:
    List<SimpleFacetResult> results = facets.getAllDims(10);
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

    searcher.getIndexReader().close();
    taxoReader.close();
    taxoDir.close();
    dir.close();
  }


  // nocommit in the sparse case test that we are really
  // sorting by the correct dim count

  public void testReallyNoNormsForDrillDown() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    iwc.setSimilarity(new PerFieldSimilarityWrapper() {
        final Similarity sim = new DefaultSimilarity();

        @Override
        public Similarity get(String name) {
          assertEquals("field", name);
          return sim;
        }
      });
    TaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);
    IndexWriter writer = new FacetIndexWriter(dir, iwc, taxoWriter, new FacetsConfig());

    Document doc = new Document();
    doc.add(newTextField("field", "text", Field.Store.NO));
    doc.add(new FacetField("a", "path"));
    writer.addDocument(doc);
    writer.close();
    taxoWriter.close();
    dir.close();
    taxoDir.close();
  }

  public void testMultiValuedHierarchy() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("a");
    config.setMultiValued("a");
    IndexWriter writer = new FacetIndexWriter(dir, iwc, taxoWriter, config);

    Document doc = new Document();
    doc.add(newTextField("field", "text", Field.Store.NO));
    doc.add(new FacetField("a", "path", "x"));
    doc.add(new FacetField("a", "path", "y"));
    writer.addDocument(doc);

    // NRT open
    IndexSearcher searcher = newSearcher(DirectoryReader.open(writer, true));
    writer.close();

    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();
    
    // Aggregate the facet counts:
    SimpleFacetsCollector c = new SimpleFacetsCollector();

    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query, and use MultiCollector to
    // wrap collecting the "normal" hits and also facets:
    searcher.search(new MatchAllDocsQuery(), c);
    Facets facets = getFacetCounts(taxoReader, config, c);
    SimpleFacetResult result = facets.getTopChildren(10, "a");
    assertEquals(1, result.labelValues.length);
    assertEquals(1, result.labelValues[0].value.intValue());

    searcher.getIndexReader().close();
    taxoReader.close();
    dir.close();
    taxoDir.close();
  }

  /*
  public void testLabelWithDelimiter() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);

    FacetFields facetFields = new FacetFields(taxoWriter);

    Document doc = new Document();
    doc.add(newTextField("field", "text", Field.Store.NO));
    BytesRef br = new BytesRef(new byte[] {(byte) 0xee, (byte) 0x92, (byte) 0xaa, (byte) 0xef, (byte) 0x9d, (byte) 0x89});
    facetFields.addFields(doc, Collections.singletonList(new CategoryPath("dim/" + br.utf8ToString(), '/')));
    try {
      writer.addDocument(doc);
    } catch (IllegalArgumentException iae) {
      // expected
    }
    writer.close();
    taxoWriter.close();
    dir.close();
    taxoDir.close();
  }
  
  // LUCENE-4583: make sure if we require > 32 KB for one
  // document, we don't hit exc when using Facet42DocValuesFormat
  public void testManyFacetsInOneDocument() throws Exception {
    assumeTrue("default Codec doesn't support huge BinaryDocValues", _TestUtil.fieldSupportsHugeBinaryDocValues(CategoryListParams.DEFAULT_FIELD));
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random()));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, iwc);
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE);
    
    FacetFields facetFields = new FacetFields(taxoWriter);
    
    int numLabels = _TestUtil.nextInt(random(), 40000, 100000);
    
    Document doc = new Document();
    doc.add(newTextField("field", "text", Field.Store.NO));
    List<CategoryPath> paths = new ArrayList<CategoryPath>();
    for (int i = 0; i < numLabels; i++) {
      paths.add(new CategoryPath("dim", "" + i));
    }
    facetFields.addFields(doc, paths);
    writer.addDocument(doc);
    
    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());
    writer.close();
    
    // NRT open
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    taxoWriter.close();
    
    FacetSearchParams fsp = new FacetSearchParams(new CountFacetRequest(new CategoryPath("dim"), Integer.MAX_VALUE));
    
    // Aggregate the facet counts:
    FacetsCollector c = FacetsCollector.create(fsp, searcher.getIndexReader(), taxoReader);
    
    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query, and use MultiCollector to
    // wrap collecting the "normal" hits and also facets:
    searcher.search(new MatchAllDocsQuery(), c);
    List<FacetResult> results = c.getFacetResults();
    assertEquals(1, results.size());
    FacetResultNode root = results.get(0).getFacetResultNode();
    assertEquals(numLabels, root.subResults.size());
    Set<String> allLabels = new HashSet<String>();
    for (FacetResultNode childNode : root.subResults) {
      assertEquals(2, childNode.label.length);
      allLabels.add(childNode.label.components[1]);
      assertEquals(1, (int) childNode.value);
    }
    assertEquals(numLabels, allLabels.size());
    
    IOUtils.close(searcher.getIndexReader(), taxoReader, dir, taxoDir);
  }
  */
}
