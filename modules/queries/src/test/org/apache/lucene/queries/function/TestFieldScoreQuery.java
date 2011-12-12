package org.apache.lucene.queries.function;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ByteFieldSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.queries.function.valuesource.ShortFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryUtils;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test FieldScoreQuery search.
 * <p>
 * Tests here create an index with a few documents, each having
 * an int value indexed  field and a float value indexed field.
 * The values of these fields are later used for scoring.
 * <p>
 * The rank tests use Hits to verify that docs are ordered (by score) as expected.
 * <p>
 * The exact score tests use TopDocs top to verify the exact score.  
 */
public class TestFieldScoreQuery extends FunctionTestSetup {

  @BeforeClass
  public static void beforeClass() throws Exception {
    createIndex(true);
  }

  /** Test that FieldScoreQuery of Type.BYTE returns docs in expected order. */
  @Test
  public void testRankByte () throws Exception {
    // INT field values are small enough to be parsed as byte
    doTestRank(BYTE_VALUESOURCE);
  }

  /** Test that FieldScoreQuery of Type.SHORT returns docs in expected order. */
  @Test
  public void testRankShort () throws Exception {
    // INT field values are small enough to be parsed as short
    doTestRank(SHORT_VALUESOURCE);
  }

  /** Test that FieldScoreQuery of Type.INT returns docs in expected order. */
  @Test
  public void testRankInt () throws Exception {
    doTestRank(INT_VALUESOURCE);
  }

  /** Test that FieldScoreQuery of Type.FLOAT returns docs in expected order. */
  @Test
  public void testRankFloat () throws Exception {
    // INT field can be parsed as float
    doTestRank(INT_AS_FLOAT_VALUESOURCE);
    // same values, but in flot format
    doTestRank(FLOAT_VALUESOURCE);
  }

  // Test that FieldScoreQuery returns docs in expected order.
  private void doTestRank (ValueSource valueSource) throws Exception {
    FunctionQuery functionQuery = new FunctionQuery(valueSource);
    IndexReader r = IndexReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);
    log("test: "+ functionQuery);
    QueryUtils.check(random, functionQuery,s);
    ScoreDoc[] h = s.search(functionQuery, null, 1000).scoreDocs;
    assertEquals("All docs should be matched!",N_DOCS,h.length);
    String prevID = "ID"+(N_DOCS+1); // greater than all ids of docs in this test
    for (int i=0; i<h.length; i++) {
      String resID = s.doc(h[i].doc).get(ID_FIELD);
      log(i+".   score="+h[i].score+"  -  "+resID);
      log(s.explain(functionQuery,h[i].doc));
      assertTrue("res id "+resID+" should be < prev res id "+prevID, resID.compareTo(prevID)<0);
      prevID = resID;
    }
    r.close();
  }

  /** Test that FieldScoreQuery of Type.BYTE returns the expected scores. */
  @Test
  public void testExactScoreByte () throws Exception {
    // INT field values are small enough to be parsed as byte
    doTestExactScore(BYTE_VALUESOURCE);
  }

  /** Test that FieldScoreQuery of Type.SHORT returns the expected scores. */
  @Test
  public void testExactScoreShort () throws  Exception {
    // INT field values are small enough to be parsed as short
    doTestExactScore(SHORT_VALUESOURCE);
  }

  /** Test that FieldScoreQuery of Type.INT returns the expected scores. */
  @Test
  public void testExactScoreInt () throws  Exception {
    doTestExactScore(INT_VALUESOURCE);
  }

  /** Test that FieldScoreQuery of Type.FLOAT returns the expected scores. */
  @Test
  public void testExactScoreFloat () throws  Exception {
    // INT field can be parsed as float
    doTestExactScore(INT_AS_FLOAT_VALUESOURCE);
    // same values, but in flot format
    doTestExactScore(FLOAT_VALUESOURCE);
  }

  // Test that FieldScoreQuery returns docs with expected score.
  private void doTestExactScore (ValueSource valueSource) throws Exception {
    FunctionQuery functionQuery = new FunctionQuery(valueSource);
    IndexReader r = IndexReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);
    TopDocs td = s.search(functionQuery,null,1000);
    assertEquals("All docs should be matched!",N_DOCS,td.totalHits);
    ScoreDoc sd[] = td.scoreDocs;
    for (ScoreDoc aSd : sd) {
      float score = aSd.score;
      log(s.explain(functionQuery, aSd.doc));
      String id = s.getIndexReader().document(aSd.doc).get(ID_FIELD);
      float expectedScore = expectedFieldScore(id); // "ID7" --> 7.0
      assertEquals("score of " + id + " shuould be " + expectedScore + " != " + score, expectedScore, score, TEST_SCORE_TOLERANCE_DELTA);
    }
    r.close();
  }

}
