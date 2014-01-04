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

import java.util.List;
import java.util.Locale;

import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.util.LuceneTestCase.SuppressCodecs;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

@SuppressCodecs("Lucene3x")
public class TestExpressionAggregationFacetsExample extends LuceneTestCase {

  @Test
  public void testSimple() throws Exception {
    FacetResult result = new ExpressionAggregationFacetsExample().runSearch();
    assertEquals("dim=A path=[] value=3.9681187 childCount=2\n  B (2.236068)\n  C (1.7320508)\n", result.toString());
  }
}
