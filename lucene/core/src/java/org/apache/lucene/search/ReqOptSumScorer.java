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
import java.util.ArrayList;
import java.util.Collection;

import org.apache.lucene.util.BytesRef;

/** A Scorer for queries with a required part and an optional part.
 * Delays skipTo() on the optional part until a score() is needed.
 * <br>
 * This <code>Scorer</code> implements {@link Scorer#advance(int)}.
 */
class ReqOptSumScorer extends Scorer {
  /** The scorers passed from the constructor.
   * These are set to null as soon as their next() or skipTo() returns false.
   */
  protected final Scorer reqScorer;
  protected Scorer optScorer;

  /** Construct a <code>ReqOptScorer</code>.
   * @param reqScorer The required scorer. This must match.
   * @param optScorer The optional scorer. This is used for scoring only.
   */
  public ReqOptSumScorer(
      Scorer reqScorer,
      Scorer optScorer)
  {
    super(reqScorer.weight);
    assert reqScorer != null;
    assert optScorer != null;
    this.reqScorer = reqScorer;
    this.optScorer = optScorer;
  }

  @Override
  public TwoPhaseDocIdSetIterator asTwoPhaseIterator() {
    return reqScorer.asTwoPhaseIterator();
  }

  @Override
  public int nextDoc() throws IOException {
    return reqScorer.nextDoc();
  }
  
  @Override
  public int advance(int target) throws IOException {
    return reqScorer.advance(target);
  }
  
  @Override
  public int docID() {
    return reqScorer.docID();
  }
  
  /** Returns the score of the current document matching the query.
   * Initially invalid, until {@link #nextDoc()} is called the first time.
   * @return The score of the required scorer, eventually increased by the score
   * of the optional scorer when it also matches the current document.
   */
  @Override
  public float score() throws IOException {
    // TODO: sum into a double and cast to float if we ever send required clauses to BS1
    int curDoc = reqScorer.docID();
    float reqScore = reqScorer.score();
    if (optScorer == null) {
      return reqScore;
    }
    
    int optScorerDoc = optScorer.docID();
    if (optScorerDoc < curDoc && (optScorerDoc = optScorer.advance(curDoc)) == NO_MORE_DOCS) {
      optScorer = null;
      return reqScore;
    }
    
    return optScorerDoc == curDoc ? reqScore + optScorer.score() : reqScore;
  }

  @Override
  public int freq() throws IOException {
    // we might have deferred advance()
    score();
    return (optScorer != null && optScorer.docID() == reqScorer.docID()) ? 2 : 1;
  }

  @Override
  public int nextPosition() throws IOException {
    return -1;
  }

  @Override
  public int startOffset() throws IOException {
    return -1;
  }

  @Override
  public int endOffset() throws IOException {
    return -1;
  }

  @Override
  public BytesRef getPayload() throws IOException {
    return null;
  }

  @Override
  public Collection<ChildScorer> getChildren() {
    ArrayList<ChildScorer> children = new ArrayList<>(2);
    children.add(new ChildScorer(reqScorer, "MUST"));
    children.add(new ChildScorer(optScorer, "SHOULD"));
    return children;
  }

  @Override
  public long cost() {
    return reqScorer.cost();
  }
}

