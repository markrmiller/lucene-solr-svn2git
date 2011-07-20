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

import java.io.IOException;

import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.TermContext;

/**
 * Abstract superclass for language modeling Similarities. The following inner
 * types are introduced:
 * <ul>
 *   <li>{@link LMStats}, which defines a new statistic, the probability that
 *   the collection language model generates the current term;</li>
 *   <li>{@link CollectionModel}, which is a strategy interface for object that
 *   compute the collection language model {@code p(w|C)};</li>
 *   <li>{@link DefaultCollectionModel}, an implementation of the former, that
 *   computes the term probability as the number of occurrences of the term in the
 *   collection, divided by the total number of tokens.</li>
 * </ul> 
 * 
 * @lucene.experimental
 */
public abstract class LMSimilarity extends EasySimilarity {
  /** The collection model. */
  protected final CollectionModel collectionModel;
  
  /** Creates a new instance with the specified collection language model. */
  public LMSimilarity(CollectionModel collectionModel) {
    this.collectionModel = collectionModel;
  }
  
  /** Creates a new instance with the default collection language model. */
  public LMSimilarity() {
    this(new DefaultCollectionModel());
  }
  
  /**
   * Computes the collection probability of the current term in addition to the
   * usual statistics.
   */
  @Override
  public EasyStats computeStats(IndexSearcher searcher, String fieldName,
      float queryBoost, TermContext... termContexts) throws IOException {
    LMStats stats = new LMStats(queryBoost);
    fillEasyStats(stats, searcher, fieldName, termContexts);
    stats.setCollectionProbability(collectionModel.computeProbability(stats));
    return stats;
  }

  @Override
  protected void explain(Explanation expl, EasyStats stats, int doc,
      float freq, byte norm) {
    expl.addDetail(new Explanation(collectionModel.computeProbability(stats),
                                   "collection probability"));
  }

  /** Stores the collection distribution of the current term. */
  public static class LMStats extends EasyStats {
    /** The probability that the current term is generated by the collection. */
    private float collectionProbability;
    
    public LMStats(float queryBoost) {
      super(queryBoost);
    }
    
    /**
     * Returns the probability that the current term is generated by the
     * collection.
     */
    public final float getCollectionProbability() {
      return collectionProbability;
    }
    
    /**
     * Sets the probability that the current term is generated by the
     * collection.
     */
    public final void setCollectionProbability(float collectionProbability) {
      this.collectionProbability = collectionProbability;
    } 
  }
  
  /** A strategy for computing the collection language model. */
  public static interface CollectionModel {
    /**
     * Computes the probability {@code p(w|C)} according to the language model
     * strategy for the current term.
     */
    public float computeProbability(EasyStats stats);
  }
  
  /**
   * Models {@code p(w|C)} as the number of occurrences of the term in the
   * collection, divided by the total number of tokens.
   */
  public static class DefaultCollectionModel implements CollectionModel {
    @Override
    public float computeProbability(EasyStats stats) {
      return (float)stats.getTotalTermFreq() / stats.getSumTotalTermFreq();
    }
  }
}
