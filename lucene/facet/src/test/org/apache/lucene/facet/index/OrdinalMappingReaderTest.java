package org.apache.lucene.facet.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.junit.Test;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.LuceneTestCase.SuppressCodecs;
import org.apache.lucene.facet.example.merge.TaxonomyMergeUtils;
import org.apache.lucene.facet.search.FacetsCollector;
import org.apache.lucene.facet.search.params.CountFacetRequest;
import org.apache.lucene.facet.search.params.FacetSearchParams;
import org.apache.lucene.facet.search.results.FacetResult;
import org.apache.lucene.facet.search.results.FacetResultNode;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;

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

@SuppressCodecs("Lucene3x")
public class OrdinalMappingReaderTest extends LuceneTestCase {
  
  private static final int NUM_DOCS = 100;
  
  @Test
  public void testTaxonomyMergeUtils() throws Exception {
    Directory dir = newDirectory();
    Directory taxDir = newDirectory();    
    buildIndexWithFacets(dir, taxDir, true);
    
    Directory dir1 = newDirectory();
    Directory taxDir1 = newDirectory();
    buildIndexWithFacets(dir1, taxDir1, false);
    
    TaxonomyMergeUtils.merge(dir, taxDir, dir1, taxDir1);
    
    verifyResults(dir1, taxDir1);
    dir1.close();
    taxDir1.close();
    dir.close();
    taxDir.close();
  }

  private void verifyResults(Directory dir, Directory taxDir) throws IOException {
    DirectoryReader reader1 = DirectoryReader.open(dir);
    DirectoryTaxonomyReader taxReader = new DirectoryTaxonomyReader(taxDir);
    IndexSearcher searcher = newSearcher(reader1);
    FacetSearchParams fsp = new FacetSearchParams(new CountFacetRequest(new CategoryPath("tag"), NUM_DOCS));
    FacetsCollector collector = new FacetsCollector(fsp, reader1, taxReader);
    searcher.search(new MatchAllDocsQuery(), collector);
    FacetResult result = collector.getFacetResults().get(0);
    FacetResultNode node = result.getFacetResultNode();
    for (FacetResultNode facet: node.subResults) {
      int weight = (int)facet.value;
      int label = Integer.parseInt(facet.label.components[1]);
      //System.out.println(label + ": " + weight);
      if (VERBOSE) {
        System.out.println(label + ": " + weight);
      }
      assertEquals(NUM_DOCS ,weight);
    }
    reader1.close();
    taxReader.close();
  }

  private void buildIndexWithFacets(Directory dir, Directory taxDir, boolean asc) throws IOException {
    IndexWriterConfig config = newIndexWriterConfig(TEST_VERSION_CURRENT, 
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, config);
    
    DirectoryTaxonomyWriter taxonomyWriter = new DirectoryTaxonomyWriter(taxDir);
    for (int i = 1; i <= NUM_DOCS; i++) {
      Document doc = new Document();
      List<CategoryPath> categoryPaths = new ArrayList<CategoryPath>(i + 1);
      for (int j = i; j <= NUM_DOCS; j++) {
        int facetValue = asc? j: NUM_DOCS - j;
        categoryPaths.add(new CategoryPath("tag", Integer.toString(facetValue)));
      }
      FacetFields facetFields = new FacetFields(taxonomyWriter);
      facetFields.addFields(doc, categoryPaths);
      writer.addDocument(doc);
    }    
    taxonomyWriter.close();
    writer.close();
  }  

}
