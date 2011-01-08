
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

package org.apache.lucene.misc;

import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.index.FieldInvertState;

/**
 * Test of the SweetSpotSimilarity
 */
public class SweetSpotSimilarityTest extends LuceneTestCase {

  public void testSweetSpotComputeNorm() {
  
    SweetSpotSimilarity ss = new SweetSpotSimilarity();
    ss.setLengthNormFactors(1,1,0.5f);

    Similarity d = new DefaultSimilarity();
    Similarity s = ss;


    // base case, should degrade
    final FieldInvertState invertState = new FieldInvertState();
    invertState.setBoost(1.0f);
    for (int i = 1; i < 1000; i++) {
      invertState.setLength(i);
      assertEquals("base case: i="+i,
                   d.computeNorm("foo", invertState),
                   s.computeNorm("foo", invertState),
                   0.0f);
    }

    // make a sweet spot
  
    ss.setLengthNormFactors(3,10,0.5f);
  
    for (int i = 3; i <=10; i++) {
      invertState.setLength(i);
      assertEquals("3,10: spot i="+i,
                   1.0f,
                   s.computeNorm("foo", invertState),
                   0.0f);
    }
  
    for (int i = 10; i < 1000; i++) {
      invertState.setLength(i-9);
      final float normD = d.computeNorm("foo", invertState);
      invertState.setLength(i);
      final float normS = s.computeNorm("foo", invertState);
      assertEquals("3,10: 10<x : i="+i,
                   normD,
                   normS,
                   0.0f);
    }


    // seperate sweet spot for certain fields

    ss.setLengthNormFactors("bar",8,13, 0.5f, false);
    ss.setLengthNormFactors("yak",6,9, 0.5f, false);

  
    for (int i = 3; i <=10; i++) {
      invertState.setLength(i);
      assertEquals("f: 3,10: spot i="+i,
                   1.0f,
                   s.computeNorm("foo", invertState),
                   0.0f);
    }
    for (int i = 10; i < 1000; i++) {
      invertState.setLength(i-9);
      final float normD = d.computeNorm("foo", invertState);
      invertState.setLength(i);
      final float normS = s.computeNorm("foo", invertState);
      assertEquals("f: 3,10: 10<x : i="+i,
                   normD,
                   normS,
                   0.0f);
    }
    for (int i = 8; i <=13; i++) {
      invertState.setLength(i);
      assertEquals("f: 8,13: spot i="+i,
                   1.0f,
                   s.computeNorm("bar", invertState),
                   0.0f);
    }
    for (int i = 6; i <=9; i++) {
      invertState.setLength(i);
      assertEquals("f: 6,9: spot i="+i,
                   1.0f,
                   s.computeNorm("yak", invertState),
                   0.0f);
    }
    for (int i = 13; i < 1000; i++) {
      invertState.setLength(i-12);
      final float normD = d.computeNorm("foo", invertState);
      invertState.setLength(i);
      final float normS = s.computeNorm("bar", invertState);
      assertEquals("f: 8,13: 13<x : i="+i,
                   normD,
                   normS,
                   0.0f);
    }
    for (int i = 9; i < 1000; i++) {
      invertState.setLength(i-8);
      final float normD = d.computeNorm("foo", invertState);
      invertState.setLength(i);
      final float normS = s.computeNorm("yak", invertState);
      assertEquals("f: 6,9: 9<x : i="+i,
                   normD,
                   normS,
                   0.0f);
    }


    // steepness

    ss.setLengthNormFactors("a",5,8,0.5f, false);
    ss.setLengthNormFactors("b",5,8,0.1f, false);

    for (int i = 9; i < 1000; i++) {
      invertState.setLength(i);
      final float normSS = ss.computeNorm("a", invertState);
      final float normS = s.computeNorm("b", invertState);
      assertTrue("s: i="+i+" : a="+normSS+
                 " < b="+normS,
                 normSS < normS);
    }

  }

  public void testSweetSpotTf() {
  
    SweetSpotSimilarity ss = new SweetSpotSimilarity();

    Similarity d = new DefaultSimilarity();
    Similarity s = ss;
    
    // tf equal

    ss.setBaselineTfFactors(0.0f, 0.0f);
  
    for (int i = 1; i < 1000; i++) {
      assertEquals("tf: i="+i,
                   d.tf(i), s.tf(i), 0.0f);
    }

    // tf higher
  
    ss.setBaselineTfFactors(1.0f, 0.0f);
  
    for (int i = 1; i < 1000; i++) {
      assertTrue("tf: i="+i+" : d="+d.tf(i)+
                 " < s="+s.tf(i),
                 d.tf(i) < s.tf(i));
    }

    // tf flat
  
    ss.setBaselineTfFactors(1.0f, 6.0f);
    for (int i = 1; i <=6; i++) {
      assertEquals("tf flat1: i="+i, 1.0f, s.tf(i), 0.0f);
    }
    ss.setBaselineTfFactors(2.0f, 6.0f);
    for (int i = 1; i <=6; i++) {
      assertEquals("tf flat2: i="+i, 2.0f, s.tf(i), 0.0f);
    }
    for (int i = 6; i <=1000; i++) {
      assertTrue("tf: i="+i+" : s="+s.tf(i)+
                 " < d="+d.tf(i),
                 s.tf(i) < d.tf(i));
    }

    // stupidity
    assertEquals("tf zero", 0.0f, s.tf(0), 0.0f);
  }

  public void testHyperbolicSweetSpot() {
  
    SweetSpotSimilarity ss = new SweetSpotSimilarity() {
        @Override
        public float tf(int freq) {
          return hyperbolicTf(freq);
        }
      };
    ss.setHyperbolicTfFactors(3.3f, 7.7f, Math.E, 5.0f);
    
    Similarity s = ss;

    for (int i = 1; i <=1000; i++) {
      assertTrue("MIN tf: i="+i+" : s="+s.tf(i),
                 3.3f <= s.tf(i));
      assertTrue("MAX tf: i="+i+" : s="+s.tf(i),
                 s.tf(i) <= 7.7f);
    }
    assertEquals("MID tf", 3.3f+(7.7f - 3.3f)/2.0f, s.tf(5), 0.00001f);
    
    // stupidity
    assertEquals("tf zero", 0.0f, s.tf(0), 0.0f);
    
  }

  
}

