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

/**
 * Dirichlet Priors normalization
 * @lucene.experimental
 */
public class NormalizationH3 extends Normalization {
  private final float mu;
  
  public NormalizationH3() {
    this(800F);
  }
  
  public NormalizationH3(float mu) {
    this.mu = mu;
  }

  @Override
  public float tfn(BasicStats stats, float tf, float len) {
    return (tf + mu * (stats.getTotalTermFreq() / (float)stats.getNumberOfFieldTokens())) / (len + mu) * mu;
  }

  @Override
  public String toString() {
    return "3(" + mu + ")";
  }
}
