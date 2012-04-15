package org.apache.lucene.queries;

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

import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.FunctionTestSetup;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.search.*;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 * Test CustomScoreQuery search.
 */
public class TestCustomScoreQuery extends FunctionTestSetup {

  @BeforeClass
  public static void beforeClass() throws Exception {
    createIndex(true);
  }

  /**
   * Test that CustomScoreQuery of Type.BYTE returns the expected scores.
   */
  @Test
  public void testCustomScoreByte() throws Exception {
    // INT field values are small enough to be parsed as byte
    doTestCustomScore(BYTE_VALUESOURCE, 1.0);
    doTestCustomScore(BYTE_VALUESOURCE, 2.0);
  }

  /**
   * Test that CustomScoreQuery of Type.SHORT returns the expected scores.
   */
  @Test
  public void testCustomScoreShort() throws Exception {
    // INT field values are small enough to be parsed as short
    doTestCustomScore(SHORT_VALUESOURCE, 1.0);
    doTestCustomScore(SHORT_VALUESOURCE, 3.0);
  }

  /**
   * Test that CustomScoreQuery of Type.INT returns the expected scores.
   */
  @Test
  public void testCustomScoreInt() throws Exception {
    doTestCustomScore(INT_VALUESOURCE, 1.0);
    doTestCustomScore(INT_VALUESOURCE, 4.0);
  }

  /**
   * Test that CustomScoreQuery of Type.FLOAT returns the expected scores.
   */
  @Test
  public void testCustomScoreFloat() throws Exception {
    // INT field can be parsed as float
    doTestCustomScore(INT_AS_FLOAT_VALUESOURCE, 1.0);
    doTestCustomScore(INT_AS_FLOAT_VALUESOURCE, 5.0);

    // same values, but in float format
    doTestCustomScore(FLOAT_VALUESOURCE, 1.0);
    doTestCustomScore(FLOAT_VALUESOURCE, 6.0);
  }

  // must have static class otherwise serialization tests fail
  private static class CustomAddQuery extends CustomScoreQuery {
    // constructor
    CustomAddQuery(Query q, FunctionQuery qValSrc) {
      super(q, qValSrc);
    }

    /*(non-Javadoc) @see org.apache.lucene.search.function.CustomScoreQuery#name() */
    @Override
    public String name() {
      return "customAdd";
    }
    
    @Override
    protected CustomScoreProvider getCustomScoreProvider(AtomicReaderContext context) {
      return new CustomScoreProvider(context) {
        @Override
        public float customScore(int doc, float subQueryScore, float valSrcScore) {
          return subQueryScore + valSrcScore;
        }

        @Override
        public Explanation customExplain(int doc, Explanation subQueryExpl, Explanation valSrcExpl) {
          float valSrcScore = valSrcExpl == null ? 0 : valSrcExpl.getValue();
          Explanation exp = new Explanation(valSrcScore + subQueryExpl.getValue(), "custom score: sum of:");
          exp.addDetail(subQueryExpl);
          if (valSrcExpl != null) {
            exp.addDetail(valSrcExpl);
          }
          return exp;
        }
      };
    }
  }

  // must have static class otherwise serialization tests fail
  private static class CustomMulAddQuery extends CustomScoreQuery {
    // constructor
    CustomMulAddQuery(Query q, FunctionQuery qValSrc1, FunctionQuery qValSrc2) {
      super(q, qValSrc1, qValSrc2);
    }

    /*(non-Javadoc) @see org.apache.lucene.search.function.CustomScoreQuery#name() */
    @Override
    public String name() {
      return "customMulAdd";
    }

    @Override
    protected CustomScoreProvider getCustomScoreProvider(AtomicReaderContext context) {
      return new CustomScoreProvider(context) {
        @Override
        public float customScore(int doc, float subQueryScore, float valSrcScores[]) {
          if (valSrcScores.length == 0) {
            return subQueryScore;
          }
          if (valSrcScores.length == 1) {
            return subQueryScore + valSrcScores[0];
            // confirm that skipping beyond the last doc, on the
            // previous reader, hits NO_MORE_DOCS
          }
          return (subQueryScore + valSrcScores[0]) * valSrcScores[1]; // we know there are two
        }

        @Override
        public Explanation customExplain(int doc, Explanation subQueryExpl, Explanation valSrcExpls[]) {
          if (valSrcExpls.length == 0) {
            return subQueryExpl;
          }
          Explanation exp = new Explanation(valSrcExpls[0].getValue() + subQueryExpl.getValue(), "sum of:");
          exp.addDetail(subQueryExpl);
          exp.addDetail(valSrcExpls[0]);
          if (valSrcExpls.length == 1) {
            exp.setDescription("CustomMulAdd, sum of:");
            return exp;
          }
          Explanation exp2 = new Explanation(valSrcExpls[1].getValue() * exp.getValue(), "custom score: product of:");
          exp2.addDetail(valSrcExpls[1]);
          exp2.addDetail(exp);
          return exp2;
        }
      };
    }
  }

  private final class CustomExternalQuery extends CustomScoreQuery {

    @Override
    protected CustomScoreProvider getCustomScoreProvider(AtomicReaderContext context) throws IOException {
      final int[] values = FieldCache.DEFAULT.getInts(context.reader(), INT_FIELD, false);
      return new CustomScoreProvider(context) {
        @Override
        public float customScore(int doc, float subScore, float valSrcScore) throws IOException {
          assertTrue(doc <= context.reader().maxDoc());
          return values[doc];
        }
      };
    }

    public CustomExternalQuery(Query q) {
      super(q);
    }
  }

  @Test
  public void testCustomExternalQuery() throws Exception {
    BooleanQuery q1 = new BooleanQuery();
    q1.add(new TermQuery(new Term(TEXT_FIELD, "first")), BooleanClause.Occur.SHOULD);
    q1.add(new TermQuery(new Term(TEXT_FIELD, "aid")), BooleanClause.Occur.SHOULD);
    q1.add(new TermQuery(new Term(TEXT_FIELD, "text")), BooleanClause.Occur.SHOULD);
    
    final Query q = new CustomExternalQuery(q1);
    log(q);

    IndexReader r = IndexReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);
    TopDocs hits = s.search(q, 1000);
    assertEquals(N_DOCS, hits.totalHits);
    for(int i=0;i<N_DOCS;i++) {
      final int doc = hits.scoreDocs[i].doc;
      final float score = hits.scoreDocs[i].score;
      assertEquals("doc=" + doc, (float) 1+(4*doc) % N_DOCS, score, 0.0001);
    }
    r.close();
  }
  
  @Test
  public void testRewrite() throws Exception {
    IndexReader r = IndexReader.open(dir);
    final IndexSearcher s = new IndexSearcher(r);

    Query q = new TermQuery(new Term(TEXT_FIELD, "first"));
    CustomScoreQuery original = new CustomScoreQuery(q);
    CustomScoreQuery rewritten = (CustomScoreQuery) original.rewrite(s.getIndexReader());
    assertTrue("rewritten query should be identical, as TermQuery does not rewrite", original == rewritten);
    assertTrue("no hits for query", s.search(rewritten,1).totalHits > 0);
    assertEquals(s.search(q,1).totalHits, s.search(rewritten,1).totalHits);

    q = new TermRangeQuery(TEXT_FIELD, null, null, true, true); // everything
    original = new CustomScoreQuery(q);
    rewritten = (CustomScoreQuery) original.rewrite(s.getIndexReader());
    assertTrue("rewritten query should not be identical, as TermRangeQuery rewrites", original != rewritten);
    assertTrue("no hits for query", s.search(rewritten,1).totalHits > 0);
    assertEquals(s.search(q,1).totalHits, s.search(original,1).totalHits);
    assertEquals(s.search(q,1).totalHits, s.search(rewritten,1).totalHits);
    
    r.close();
  }
  
  // Test that FieldScoreQuery returns docs with expected score.
  private void doTestCustomScore(ValueSource valueSource, double dboost) throws Exception {
    float boost = (float) dboost;
    FunctionQuery functionQuery = new FunctionQuery(valueSource);
    IndexReader r = IndexReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);

    // regular (boolean) query.
    BooleanQuery q1 = new BooleanQuery();
    q1.add(new TermQuery(new Term(TEXT_FIELD, "first")), BooleanClause.Occur.SHOULD);
    q1.add(new TermQuery(new Term(TEXT_FIELD, "aid")), BooleanClause.Occur.SHOULD);
    q1.add(new TermQuery(new Term(TEXT_FIELD, "text")), BooleanClause.Occur.SHOULD);
    log(q1);

    // custom query, that should score the same as q1.
    Query q2CustomNeutral = new CustomScoreQuery(q1);
    q2CustomNeutral.setBoost(boost);
    log(q2CustomNeutral);

    // custom query, that should (by default) multiply the scores of q1 by that of the field
    CustomScoreQuery q3CustomMul = new CustomScoreQuery(q1, functionQuery);
    q3CustomMul.setStrict(true);
    q3CustomMul.setBoost(boost);
    log(q3CustomMul);

    // custom query, that should add the scores of q1 to that of the field
    CustomScoreQuery q4CustomAdd = new CustomAddQuery(q1, functionQuery);
    q4CustomAdd.setStrict(true);
    q4CustomAdd.setBoost(boost);
    log(q4CustomAdd);

    // custom query, that multiplies and adds the field score to that of q1
    CustomScoreQuery q5CustomMulAdd = new CustomMulAddQuery(q1, functionQuery, functionQuery);
    q5CustomMulAdd.setStrict(true);
    q5CustomMulAdd.setBoost(boost);
    log(q5CustomMulAdd);

    // do al the searches 
    TopDocs td1 = s.search(q1, null, 1000);
    TopDocs td2CustomNeutral = s.search(q2CustomNeutral, null, 1000);
    TopDocs td3CustomMul = s.search(q3CustomMul, null, 1000);
    TopDocs td4CustomAdd = s.search(q4CustomAdd, null, 1000);
    TopDocs td5CustomMulAdd = s.search(q5CustomMulAdd, null, 1000);

    // put results in map so we can verify the scores although they have changed
    Map<Integer,Float> h1               = topDocsToMap(td1);
    Map<Integer,Float> h2CustomNeutral  = topDocsToMap(td2CustomNeutral);
    Map<Integer,Float> h3CustomMul      = topDocsToMap(td3CustomMul);
    Map<Integer,Float> h4CustomAdd      = topDocsToMap(td4CustomAdd);
    Map<Integer,Float> h5CustomMulAdd   = topDocsToMap(td5CustomMulAdd);
    
    verifyResults(boost, s, 
        h1, h2CustomNeutral, h3CustomMul, h4CustomAdd, h5CustomMulAdd,
        q1, q2CustomNeutral, q3CustomMul, q4CustomAdd, q5CustomMulAdd);
    r.close();
  }

  // verify results are as expected.
  private void verifyResults(float boost, IndexSearcher s, 
      Map<Integer,Float> h1, Map<Integer,Float> h2customNeutral, Map<Integer,Float> h3CustomMul, Map<Integer,Float> h4CustomAdd, Map<Integer,Float> h5CustomMulAdd,
      Query q1, Query q2, Query q3, Query q4, Query q5) throws Exception {
    
    // verify numbers of matches
    log("#hits = "+h1.size());
    assertEquals("queries should have same #hits",h1.size(),h2customNeutral.size());
    assertEquals("queries should have same #hits",h1.size(),h3CustomMul.size());
    assertEquals("queries should have same #hits",h1.size(),h4CustomAdd.size());
    assertEquals("queries should have same #hits",h1.size(),h5CustomMulAdd.size());

    QueryUtils.check(random(), q1, s, rarely());
    QueryUtils.check(random(), q2, s, rarely());
    QueryUtils.check(random(), q3, s, rarely());
    QueryUtils.check(random(), q4, s, rarely());
    QueryUtils.check(random(), q5, s, rarely());

    // verify scores ratios
    for (final Integer doc : h1.keySet()) {

      log("doc = "+doc);

      float fieldScore = expectedFieldScore(s.getIndexReader().document(doc).get(ID_FIELD));
      log("fieldScore = " + fieldScore);
      assertTrue("fieldScore should not be 0", fieldScore > 0);

      float score1 = h1.get(doc);
      logResult("score1=", s, q1, doc, score1);
      
      float score2 = h2customNeutral.get(doc);
      logResult("score2=", s, q2, doc, score2);
      assertEquals("same score (just boosted) for neutral", boost * score1, score2, TEST_SCORE_TOLERANCE_DELTA);

      float score3 = h3CustomMul.get(doc);
      logResult("score3=", s, q3, doc, score3);
      assertEquals("new score for custom mul", boost * fieldScore * score1, score3, TEST_SCORE_TOLERANCE_DELTA);
      
      float score4 = h4CustomAdd.get(doc);
      logResult("score4=", s, q4, doc, score4);
      assertEquals("new score for custom add", boost * (fieldScore + score1), score4, TEST_SCORE_TOLERANCE_DELTA);
      
      float score5 = h5CustomMulAdd.get(doc);
      logResult("score5=", s, q5, doc, score5);
      assertEquals("new score for custom mul add", boost * fieldScore * (score1 + fieldScore), score5, TEST_SCORE_TOLERANCE_DELTA);
    }
  }

  private void logResult(String msg, IndexSearcher s, Query q, int doc, float score1) throws IOException {
    log(msg+" "+score1);
    log("Explain by: "+q);
    log(s.explain(q,doc));
  }

  // since custom scoring modifies the order of docs, map results 
  // by doc ids so that we can later compare/verify them 
  private Map<Integer,Float> topDocsToMap(TopDocs td) {
    Map<Integer,Float> h = new HashMap<Integer,Float>();
    for (int i=0; i<td.totalHits; i++) {
      h.put(td.scoreDocs[i].doc, td.scoreDocs[i].score);
    }
    return h;
  }

}
