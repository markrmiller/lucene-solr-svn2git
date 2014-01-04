package org.apache.lucene.facet.sortedset;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetTestCase;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util._TestUtil;

public class TestSortedSetDocValuesFacets extends FacetTestCase {

  // NOTE: TestDrillSideways.testRandom also sometimes
  // randomly uses SortedSetDV

  public void testBasic() throws Exception {
    assumeTrue("Test requires SortedSetDV support", defaultCodecSupportsSortedSet());
    Directory dir = newDirectory();

    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("a", true);
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo"));
    doc.add(new SortedSetDocValuesFacetField("a", "bar"));
    doc.add(new SortedSetDocValuesFacetField("a", "zoo"));
    doc.add(new SortedSetDocValuesFacetField("b", "baz"));
    writer.addDocument(config.build(doc));
    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo"));
    writer.addDocument(config.build(doc));

    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());

    // Per-top-reader state:
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(searcher.getIndexReader());
    
    FacetsCollector c = new FacetsCollector();

    searcher.search(new MatchAllDocsQuery(), c);

    SortedSetDocValuesFacetCounts facets = new SortedSetDocValuesFacetCounts(state, c);

    assertEquals("dim=a path=[] value=4 childCount=3\n  foo (2)\n  bar (1)\n  zoo (1)\n", facets.getTopChildren(10, "a").toString());
    assertEquals("dim=b path=[] value=1 childCount=1\n  baz (1)\n", facets.getTopChildren(10, "b").toString());

    // DrillDown:
    DrillDownQuery q = new DrillDownQuery(config);
    q.add("a", "foo");
    q.add("b", "baz");
    TopDocs hits = searcher.search(q, 1);
    assertEquals(1, hits.totalHits);

    IOUtils.close(writer, searcher.getIndexReader(), dir);
  }

  // LUCENE-5090
  @SuppressWarnings("unused")
  public void testStaleState() throws Exception {
    assumeTrue("Test requires SortedSetDV support", defaultCodecSupportsSortedSet());
    Directory dir = newDirectory();

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    FacetsConfig config = new FacetsConfig();

    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo"));
    writer.addDocument(config.build(doc));

    IndexReader r = writer.getReader();
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(r);

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "bar"));
    writer.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "baz"));
    writer.addDocument(config.build(doc));

    IndexSearcher searcher = newSearcher(writer.getReader());

    FacetsCollector c = new FacetsCollector();

    searcher.search(new MatchAllDocsQuery(), c);

    try {
      new SortedSetDocValuesFacetCounts(state, c);
      fail("did not hit expected exception");
    } catch (IllegalStateException ise) {
      // expected
    }

    r.close();
    writer.close();
    searcher.getIndexReader().close();
    dir.close();
  }

  // LUCENE-5333
  public void testSparseFacets() throws Exception {
    assumeTrue("Test requires SortedSetDV support", defaultCodecSupportsSortedSet());
    Directory dir = newDirectory();

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    FacetsConfig config = new FacetsConfig();

    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo1"));
    writer.addDocument(config.build(doc));

    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo2"));
    doc.add(new SortedSetDocValuesFacetField("b", "bar1"));
    writer.addDocument(config.build(doc));

    if (random().nextBoolean()) {
      writer.commit();
    }

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo3"));
    doc.add(new SortedSetDocValuesFacetField("b", "bar2"));
    doc.add(new SortedSetDocValuesFacetField("c", "baz1"));
    writer.addDocument(config.build(doc));

    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());
    writer.close();

    // Per-top-reader state:
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(searcher.getIndexReader());

    FacetsCollector c = new FacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    
    SortedSetDocValuesFacetCounts facets = new SortedSetDocValuesFacetCounts(state, c);

    // Ask for top 10 labels for any dims that have counts:
    List<FacetResult> results = facets.getAllDims(10);

    assertEquals(3, results.size());
    assertEquals("dim=a path=[] value=3 childCount=3\n  foo1 (1)\n  foo2 (1)\n  foo3 (1)\n", results.get(0).toString());
    assertEquals("dim=b path=[] value=2 childCount=2\n  bar1 (1)\n  bar2 (1)\n", results.get(1).toString());
    assertEquals("dim=c path=[] value=1 childCount=1\n  baz1 (1)\n", results.get(2).toString());

    searcher.getIndexReader().close();
    dir.close();
  }

  public void testSomeSegmentsMissing() throws Exception {
    assumeTrue("Test requires SortedSetDV support", defaultCodecSupportsSortedSet());
    Directory dir = newDirectory();

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    FacetsConfig config = new FacetsConfig();

    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo1"));
    writer.addDocument(config.build(doc));
    writer.commit();

    doc = new Document();
    writer.addDocument(config.build(doc));
    writer.commit();

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo2"));
    writer.addDocument(config.build(doc));
    writer.commit();

    // NRT open
    IndexSearcher searcher = newSearcher(writer.getReader());
    writer.close();

    // Per-top-reader state:
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(searcher.getIndexReader());

    FacetsCollector c = new FacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    
    SortedSetDocValuesFacetCounts facets = new SortedSetDocValuesFacetCounts(state, c);

    // Ask for top 10 labels for any dims that have counts:
    assertEquals("dim=a path=[] value=2 childCount=2\n  foo1 (1)\n  foo2 (1)\n", facets.getTopChildren(10, "a").toString());

    searcher.getIndexReader().close();
    dir.close();
  }

  public void testSlowCompositeReaderWrapper() throws Exception {
    assumeTrue("Test requires SortedSetDV support", defaultCodecSupportsSortedSet());
    Directory dir = newDirectory();

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    FacetsConfig config = new FacetsConfig();

    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo1"));
    writer.addDocument(config.build(doc));

    writer.commit();

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("a", "foo2"));
    writer.addDocument(config.build(doc));

    // NRT open
    IndexSearcher searcher = new IndexSearcher(SlowCompositeReaderWrapper.wrap(writer.getReader()));

    // Per-top-reader state:
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(searcher.getIndexReader());

    FacetsCollector c = new FacetsCollector();
    searcher.search(new MatchAllDocsQuery(), c);    
    Facets facets = new SortedSetDocValuesFacetCounts(state, c);

    // Ask for top 10 labels for any dims that have counts:
    assertEquals("dim=a path=[] value=2 childCount=2\n  foo1 (1)\n  foo2 (1)\n", facets.getTopChildren(10, "a").toString());

    IOUtils.close(writer, searcher.getIndexReader(), dir);
  }


  public void testRandom() throws Exception {
    assumeTrue("Test requires SortedSetDV support", defaultCodecSupportsSortedSet());
    String[] tokens = getRandomTokens(10);
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();

    RandomIndexWriter w = new RandomIndexWriter(random(), indexDir);
    FacetsConfig config = new FacetsConfig();
    int numDocs = atLeast(1000);
    int numDims = _TestUtil.nextInt(random(), 1, 7);
    List<TestDoc> testDocs = getRandomDocs(tokens, numDocs, numDims);
    for(TestDoc testDoc : testDocs) {
      Document doc = new Document();
      doc.add(newStringField("content", testDoc.content, Field.Store.NO));
      for(int j=0;j<numDims;j++) {
        if (testDoc.dims[j] != null) {
          doc.add(new SortedSetDocValuesFacetField("dim" + j, testDoc.dims[j]));
        }
      }
      w.addDocument(config.build(doc));
    }

    // NRT open
    IndexSearcher searcher = newSearcher(w.getReader());
    
    // Per-top-reader state:
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(searcher.getIndexReader());

    int iters = atLeast(100);
    for(int iter=0;iter<iters;iter++) {
      String searchToken = tokens[random().nextInt(tokens.length)];
      if (VERBOSE) {
        System.out.println("\nTEST: iter content=" + searchToken);
      }
      FacetsCollector fc = new FacetsCollector();
      FacetsCollector.search(searcher, new TermQuery(new Term("content", searchToken)), 10, fc);
      Facets facets = new SortedSetDocValuesFacetCounts(state, fc);

      // Slow, yet hopefully bug-free, faceting:
      @SuppressWarnings({"rawtypes","unchecked"}) Map<String,Integer>[] expectedCounts = new HashMap[numDims];
      for(int i=0;i<numDims;i++) {
        expectedCounts[i] = new HashMap<String,Integer>();
      }

      for(TestDoc doc : testDocs) {
        if (doc.content.equals(searchToken)) {
          for(int j=0;j<numDims;j++) {
            if (doc.dims[j] != null) {
              Integer v = expectedCounts[j].get(doc.dims[j]);
              if (v == null) {
                expectedCounts[j].put(doc.dims[j], 1);
              } else {
                expectedCounts[j].put(doc.dims[j], v.intValue() + 1);
              }
            }
          }
        }
      }

      List<FacetResult> expected = new ArrayList<FacetResult>();
      for(int i=0;i<numDims;i++) {
        List<LabelAndValue> labelValues = new ArrayList<LabelAndValue>();
        int totCount = 0;
        for(Map.Entry<String,Integer> ent : expectedCounts[i].entrySet()) {
          labelValues.add(new LabelAndValue(ent.getKey(), ent.getValue()));
          totCount += ent.getValue();
        }
        sortLabelValues(labelValues);
        if (totCount > 0) {
          expected.add(new FacetResult("dim" + i, new String[0], totCount, labelValues.toArray(new LabelAndValue[labelValues.size()]), labelValues.size()));
        }
      }

      // Sort by highest value, tie break by value:
      sortFacetResults(expected);

      List<FacetResult> actual = facets.getAllDims(10);

      // Messy: fixup ties
      //sortTies(actual);

      assertEquals(expected, actual);
    }

    IOUtils.close(w, searcher.getIndexReader(), indexDir, taxoDir);
  }
}
