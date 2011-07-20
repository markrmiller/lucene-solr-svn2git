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

import static org.apache.lucene.search.similarities.EasySimilarity.log2;

/**
 * Limiting form of the Bose-Einstein model.
 * @lucene.experimental
 */
public class BasicModelBE extends BasicModel {
  @Override
  public final float score(EasyStats stats, float tfn) {
    long N = stats.getNumberOfDocuments();
    long F = stats.getTotalTermFreq();
    return (float)(-log2((N - 1) * Math.E)
        + f(N + F -1, N + F - tfn - 2) - f(F, F - tfn));
  }
  
  /** The <em>f</em> helper function defined for <em>B<sub>E</sub></em>. */
  private final double f(long n, float m) {
    return (m + 0.5) * log2((double)n / m) + (n - m) * log2(n);
  }
}
