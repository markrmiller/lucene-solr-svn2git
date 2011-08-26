package org.apache.lucene.search.similarities;

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

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Tests against all the similarities we have
 */
public class TestSimilarity2 extends LuceneTestCase {
  List<SimilarityProvider> simProviders;
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    simProviders = new ArrayList<SimilarityProvider>();
    simProviders.add(new BasicSimilarityProvider(new DefaultSimilarity()));
    simProviders.add(new BasicSimilarityProvider(new BM25Similarity()));
    // TODO: not great that we dup this all with TestSimilarityBase
    for (BasicModel basicModel : TestSimilarityBase.BASIC_MODELS) {
      for (AfterEffect afterEffect : TestSimilarityBase.AFTER_EFFECTS) {
        for (Normalization normalization : TestSimilarityBase.NORMALIZATIONS) {
          simProviders.add(new BasicSimilarityProvider(new DFRSimilarity(basicModel, afterEffect, normalization)));
        }
      }
    }
    for (Distribution distribution : TestSimilarityBase.DISTRIBUTIONS) {
      for (Lambda lambda : TestSimilarityBase.LAMBDAS) {
        for (Normalization normalization : TestSimilarityBase.NORMALIZATIONS) {
          simProviders.add(new BasicSimilarityProvider(new IBSimilarity(distribution, lambda, normalization)));
        }
      }
    }
    simProviders.add(new BasicSimilarityProvider(new LMDirichletSimilarity()));
    simProviders.add(new BasicSimilarityProvider(new LMJelinekMercerSimilarity(0.1f)));
    simProviders.add(new BasicSimilarityProvider(new LMJelinekMercerSimilarity(0.7f)));
  }
  
  /** because of stupid things like querynorm, its possible we computeStats on a field that doesnt exist at all
   *  test this against a totally empty index, to make sure sims handle it
   */
  public void testEmptyIndex() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random, dir);
    IndexReader ir = iw.getReader();
    iw.close();
    IndexSearcher is = newSearcher(ir);
    
    for (SimilarityProvider simProvider : simProviders) {
      is.setSimilarityProvider(simProvider);
      assertEquals(0, is.search(new TermQuery(new Term("foo", "bar")), 10).totalHits);
    }
    is.close();
    ir.close();
    dir.close();
  }
  
  /** similar to the above, but ORs the query with a real field */
  public void testEmptyField() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    doc.add(newField("foo", "bar", Field.Index.ANALYZED));
    iw.addDocument(doc);
    IndexReader ir = iw.getReader();
    iw.close();
    IndexSearcher is = newSearcher(ir);
    
    for (SimilarityProvider simProvider : simProviders) {
      is.setSimilarityProvider(simProvider);
      BooleanQuery query = new BooleanQuery(true);
      query.add(new TermQuery(new Term("foo", "bar")), BooleanClause.Occur.SHOULD);
      query.add(new TermQuery(new Term("bar", "baz")), BooleanClause.Occur.SHOULD);
      assertEquals(1, is.search(query, 10).totalHits);
    }
    is.close();
    ir.close();
    dir.close();
  }
  
  /** similar to the above, however the field exists, but we query with a term that doesnt exist too */
  public void testEmptyTerm() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    doc.add(newField("foo", "bar", Field.Index.ANALYZED));
    iw.addDocument(doc);
    IndexReader ir = iw.getReader();
    iw.close();
    IndexSearcher is = newSearcher(ir);
    
    for (SimilarityProvider simProvider : simProviders) {
      is.setSimilarityProvider(simProvider);
      BooleanQuery query = new BooleanQuery(true);
      query.add(new TermQuery(new Term("foo", "bar")), BooleanClause.Occur.SHOULD);
      query.add(new TermQuery(new Term("foo", "baz")), BooleanClause.Occur.SHOULD);
      assertEquals(1, is.search(query, 10).totalHits);
    }
    is.close();
    ir.close();
    dir.close();
  }
  
  /** make sure we can retrieve when norms are disabled */
  public void testNoNorms() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    doc.add(newField("foo", "bar", Field.Index.ANALYZED_NO_NORMS));
    iw.addDocument(doc);
    IndexReader ir = iw.getReader();
    iw.close();
    IndexSearcher is = newSearcher(ir);
    
    for (SimilarityProvider simProvider : simProviders) {
      is.setSimilarityProvider(simProvider);
      BooleanQuery query = new BooleanQuery(true);
      query.add(new TermQuery(new Term("foo", "bar")), BooleanClause.Occur.SHOULD);
      assertEquals(1, is.search(query, 10).totalHits);
    }
    is.close();
    ir.close();
    dir.close();
  }
  
  /** make sure all sims work if TF is omitted */
  public void testOmitTF() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    Field f = newField("foo", "bar", Field.Index.ANALYZED);
    f.setIndexOptions(IndexOptions.DOCS_ONLY);
    doc.add(f);
    iw.addDocument(doc);
    IndexReader ir = iw.getReader();
    iw.close();
    IndexSearcher is = newSearcher(ir);
    
    for (SimilarityProvider simProvider : simProviders) {
      is.setSimilarityProvider(simProvider);
      BooleanQuery query = new BooleanQuery(true);
      query.add(new TermQuery(new Term("foo", "bar")), BooleanClause.Occur.SHOULD);
      assertEquals(1, is.search(query, 10).totalHits);
    }
    is.close();
    ir.close();
    dir.close();
  }
  
  /** make sure all sims work if TF and norms is omitted */
  public void testOmitTFAndNorms() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    Field f = newField("foo", "bar", Field.Index.ANALYZED_NO_NORMS);
    f.setIndexOptions(IndexOptions.DOCS_ONLY);
    doc.add(f);
    iw.addDocument(doc);
    IndexReader ir = iw.getReader();
    iw.close();
    IndexSearcher is = newSearcher(ir);
    
    for (SimilarityProvider simProvider : simProviders) {
      is.setSimilarityProvider(simProvider);
      BooleanQuery query = new BooleanQuery(true);
      query.add(new TermQuery(new Term("foo", "bar")), BooleanClause.Occur.SHOULD);
      assertEquals(1, is.search(query, 10).totalHits);
    }
    is.close();
    ir.close();
    dir.close();
  }
}
