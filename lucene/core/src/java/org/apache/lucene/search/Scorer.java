package org.apache.lucene.search;

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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.apache.lucene.index.DocsEnum;

/**
 * Expert: Common scoring functionality for different types of queries.
 *
 * <p>
 * A <code>Scorer</code> iterates over documents matching a
 * query in increasing order of doc Id.
 * </p>
 * <p>
 * Document scores are computed using a given <code>Similarity</code>
 * implementation.
 * </p>
 *
 * <p><b>NOTE</b>: The values Float.Nan,
 * Float.NEGATIVE_INFINITY and Float.POSITIVE_INFINITY are
 * not valid scores.  Certain collectors (eg {@link
 * TopScoreDocCollector}) will not properly collect hits
 * with these scores.
 */
public abstract class Scorer extends DocsEnum {
  /** the Scorer's parent Weight. in some cases this may be null */
  // TODO can we clean this up?
  protected final Weight weight;

  /**
   * Constructs a Scorer
   * @param weight The scorers <code>Weight</code>.
   */
  protected Scorer(Weight weight) {
    this.weight = weight;
  }

  /** Returns the score of the current document matching the query.
   * Initially invalid, until {@link #nextDoc()} or {@link #advance(int)}
   * is called the first time, or when called from within
   * {@link LeafCollector#collect}.
   */
  public abstract float score() throws IOException;

  /** Returns the score of the current interval spanned by this scorer.
   * Initially invalid, until {@link #nextPosition()} is called
   */
  public float intervalScore() throws IOException {
    return 1;
  }
  
  /** returns parent Weight
   * @lucene.experimental
   */
  public Weight getWeight() {
    return weight;
  }

  @Override
  public String toString() {
    try {
      return String.format("%d:%d(%d)->%d(%d)", docID(), startPosition(), startOffset(), endPosition(), endOffset());
    } catch (IOException e) {
      return String.format("Cannot retrieve position due to IOException");
    }
  }
  
  /** Returns child sub-scorers
   * @lucene.experimental */
  public Collection<ChildScorer> getChildren() {
    return Collections.emptyList();
  }
  
  /** A child Scorer and its relationship to its parent.
   * the meaning of the relationship depends upon the parent query. 
   * @lucene.experimental */
  public static class ChildScorer {
    /**
     * Child Scorer. (note this is typically a direct child, and may
     * itself also have children).
     */
    public final Scorer child;
    /**
     * An arbitrary string relating this scorer to the parent.
     */
    public final String relationship;
    
    /**
     * Creates a new ChildScorer node with the specified relationship.
     * <p>
     * The relationship can be any be any string that makes sense to 
     * the parent Scorer. 
     */
    public ChildScorer(Scorer child, String relationship) {
      this.child = child;
      this.relationship = relationship;
    }
  }
}
