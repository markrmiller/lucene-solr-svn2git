package org.apache.lucene.demo.facet;

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
import java.util.List;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.simple.Facets;
import org.apache.lucene.facet.simple.FacetsConfig;
import org.apache.lucene.facet.simple.SimpleDrillDownQuery;
import org.apache.lucene.facet.simple.SimpleFacetResult;
import org.apache.lucene.facet.simple.SimpleFacetsCollector;
import org.apache.lucene.facet.simple.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.simple.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.simple.SortedSetDocValuesReaderState;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/** Shows simple usage of faceted indexing and search,
 *  using {@link SortedSetDocValuesFacetFields} and {@link
 *  SortedSetDocValuesAccumulator}.  */

public class SimpleSortedSetFacetsExample {

  private final Directory indexDir = new RAMDirectory();

  /** Empty constructor */
  public SimpleSortedSetFacetsExample() {}
  
  private FacetsConfig getConfig() {
    return new FacetsConfig();
  }

  /** Build the example index. */
  private void index() throws IOException {
    IndexWriter indexWriter = new IndexWriter(indexDir, new IndexWriterConfig(FacetExamples.EXAMPLES_VER, 
        new WhitespaceAnalyzer(FacetExamples.EXAMPLES_VER)));
    FacetsConfig config = getConfig();
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("Author", "Bob"));
    doc.add(new SortedSetDocValuesFacetField("Publish Year", "2010"));
    indexWriter.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("Author", "Lisa"));
    doc.add(new SortedSetDocValuesFacetField("Publish Year", "2010"));
    indexWriter.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("Author", "Lisa"));
    doc.add(new SortedSetDocValuesFacetField("Publish Year", "2012"));
    indexWriter.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("Author", "Susan"));
    doc.add(new SortedSetDocValuesFacetField("Publish Year", "2012"));
    indexWriter.addDocument(config.build(doc));

    doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("Author", "Frank"));
    doc.add(new SortedSetDocValuesFacetField("Publish Year", "1999"));
    indexWriter.addDocument(config.build(doc));
    
    indexWriter.close();
  }

  /** User runs a query and counts facets. */
  private List<SimpleFacetResult> search() throws IOException {
    DirectoryReader indexReader = DirectoryReader.open(indexDir);
    IndexSearcher searcher = new IndexSearcher(indexReader);
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(indexReader);
    FacetsConfig config = getConfig();

    // Aggregatses the facet counts
    SimpleFacetsCollector sfc = new SimpleFacetsCollector();

    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query, and use MultiCollector to
    // wrap collecting the "normal" hits and also facets:
    searcher.search(new MatchAllDocsQuery(), sfc);

    // Retrieve results
    Facets facets = new SortedSetDocValuesFacetCounts(state, sfc);

    List<SimpleFacetResult> results = new ArrayList<SimpleFacetResult>();
    results.add(facets.getTopChildren(10, "Author"));
    results.add(facets.getTopChildren(10, "Publish Year"));
    indexReader.close();
    
    return results;
  }
  
  /** User drills down on 'Publish Year/2010'. */
  private SimpleFacetResult drillDown() throws IOException {
    DirectoryReader indexReader = DirectoryReader.open(indexDir);
    IndexSearcher searcher = new IndexSearcher(indexReader);
    SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(indexReader);
    FacetsConfig config = getConfig();

    // Now user drills down on Publish Year/2010:
    SimpleDrillDownQuery q = new SimpleDrillDownQuery(config);
    q.add("Publish Year", "2010");
    SimpleFacetsCollector sfc = new SimpleFacetsCollector();
    searcher.search(q, sfc);

    // Retrieve results
    Facets facets = new SortedSetDocValuesFacetCounts(state, sfc);
    SimpleFacetResult result = facets.getTopChildren(10, "Author");
    indexReader.close();
    
    return result;
  }

  /** Runs the search example. */
  public List<SimpleFacetResult> runSearch() throws IOException {
    index();
    return search();
  }
  
  /** Runs the drill-down example. */
  public SimpleFacetResult runDrillDown() throws IOException {
    index();
    return drillDown();
  }

  /** Runs the search and drill-down examples and prints the results. */
  public static void main(String[] args) throws Exception {
    System.out.println("Facet counting example:");
    System.out.println("-----------------------");
    SimpleSortedSetFacetsExample example = new SimpleSortedSetFacetsExample();
    List<SimpleFacetResult> results = example.runSearch();
    System.out.println("Author: " + results.get(0));
    System.out.println("Publish Year: " + results.get(0));

    System.out.println("\n");
    System.out.println("Facet drill-down example (Publish Year/2010):");
    System.out.println("---------------------------------------------");
    System.out.println("Author: " + example.runDrillDown());
  }
}
