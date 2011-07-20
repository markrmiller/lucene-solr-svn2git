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
 * The smoothed power-law (SPL) distribution for the information-based framework
 * that is described in the original paper.
 * <p>Unlike for DFR, the natural logarithm is used, as
 * it is faster to compute and the original paper does not express any
 * preference to a specific base.</p>
 * @lucene.experimental
 */
public abstract class DistributionSPL extends Distribution {
  @Override
  public final float score(EasyStats stats, float tfn, float lambda) {
    return (float)-Math.log(
        (Math.pow(lambda, (tfn / (tfn + 1))) - lambda) / (1 - lambda));
  }
}
