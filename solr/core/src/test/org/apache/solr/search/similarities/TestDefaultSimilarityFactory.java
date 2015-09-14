package org.apache.solr.search.similarities;

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

import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.junit.BeforeClass;

/**
 * Tests {@link DefaultSimilarityFactory}
 */
public class TestDefaultSimilarityFactory extends BaseSimilarityTestCase {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig-basic.xml","schema-tfidf.xml");
  }
  
  /** default parameters */
  public void testDefaults() throws Exception {
    Similarity sim = getSimilarity("text");
    assertEquals(ClassicSimilarity.class, sim.getClass());
    assertEquals(true, ((ClassicSimilarity)sim).getDiscountOverlaps());
  }
  /** explicit params */
  public void testParams() throws Exception {
    Similarity sim = getSimilarity("text_overlap");
    assertEquals(ClassicSimilarity.class, sim.getClass());
    assertEquals(false, ((ClassicSimilarity)sim).getDiscountOverlaps());
  }

}
