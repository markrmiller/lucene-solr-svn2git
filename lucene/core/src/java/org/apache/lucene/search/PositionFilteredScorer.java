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

import org.apache.lucene.search.intervals.Interval;

import java.io.IOException;

public abstract class PositionFilteredScorer extends Scorer {

  protected final Scorer[] subScorers;
  protected final Scorer child;
  protected final Interval current = new Interval();
  protected int matchDistance;

  private boolean buffered;

  public PositionFilteredScorer(Scorer filteredScorer) {
    super(filteredScorer.getWeight());
    child = filteredScorer;
    subScorers = new Scorer[filteredScorer.getChildren().size()];
    int i = 0;
    for (ChildScorer subScorer : filteredScorer.getChildren()) {
      subScorers[i++] = subScorer.child;
    }
  }

  @Override
  public float score() throws IOException {
    return child.score();
  }

  @Override
  public int docID() {
    return child.docID();
  }

  @Override
  public int nextDoc() throws IOException {
    while (child.nextDoc() != NO_MORE_DOCS) {
      reset(child.docID());
      if (nextPosition() != NO_MORE_POSITIONS) {
        buffered = true;
        return child.docID();
      }
    }
    return NO_MORE_DOCS;
  }

  @Override
  public int advance(int target) throws IOException {
    if (child.advance(target) == NO_MORE_DOCS)
      return NO_MORE_DOCS;
    do {
      reset(child.docID());
      if (nextPosition() != NO_MORE_POSITIONS) {
        buffered = true;
        return child.docID();
      }
    } while (child.nextDoc() != NO_MORE_DOCS);
    return NO_MORE_DOCS;
  }

  @Override
  public int nextPosition() throws IOException {
    if (buffered) {
      buffered = false;
      return current.begin;
    }
    while (doNextPosition() != NO_MORE_POSITIONS) {
      if (passesFilter()) {
        return current.begin;
      }
    }
    return NO_MORE_POSITIONS;
  }

  protected abstract int doNextPosition() throws IOException;

  protected boolean passesFilter() {
    return true;
  }

  protected abstract void reset(int doc) throws IOException;

  public int getMatchDistance() {
    return matchDistance;
  }

  @Override
  public int startPosition() throws IOException {
    return current.begin;
  }

  @Override
  public int endPosition() throws IOException {
    return current.end;
  }

  @Override
  public int startOffset() throws IOException {
    return current.offsetBegin;
  }

  @Override
  public int endOffset() throws IOException {
    return current.offsetEnd;
  }

  // nocommit Payloads - need to add these to Interval?
}
