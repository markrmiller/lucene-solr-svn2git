package org.apache.lucene.search;

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

import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.search.TermQuery.TermDocsEnumFactory;
import org.apache.lucene.search.positions.PositionIntervalIterator;
import org.apache.lucene.search.positions.PositionIntervalIterator.PositionInterval;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

/** Expert: A <code>Scorer</code> for documents matching a <code>Term</code>.
 */
final class TermScorer extends Scorer {
  private final DocsEnum docsEnum;
  private final Similarity.ExactSimScorer docScorer;
  private final TermDocsEnumFactory factory;
  
  /**
   * Construct a <code>TermScorer</code>.
   * 
   * @param weight
   *          The weight of the <code>Term</code> in the query.
   * @param td
   *          An iterator over the documents matching the <code>Term</code>.
   * @param docScorer
   *          The </code>Similarity.ExactSimScorer</code> implementation 
   *          to be used for score computations.
   */
  TermScorer(Weight weight, TermDocsEnumFactory factory, Similarity.ExactSimScorer docScorer) throws IOException {
    super(weight);
    this.docScorer = docScorer;
    this.docsEnum = factory.docsEnum();
    this.factory = factory;
  }

  @Override
  public int docID() {
    return docsEnum.docID();
  }

  @Override
  public float freq() throws IOException {
    return docsEnum.freq();
  }

  /**
   * Advances to the next document matching the query. <br>
   * 
   * @return the document matching the query or NO_MORE_DOCS if there are no more documents.
   */
  @Override
  public int nextDoc() throws IOException {
    return docsEnum.nextDoc();
  }
  
  @Override
  public float score() throws IOException {
    assert docID() != NO_MORE_DOCS;
    return docScorer.score(docsEnum.docID(), docsEnum.freq());  
  }

  /**
   * Advances to the first match beyond the current whose document number is
   * greater than or equal to a given target. <br>
   * The implementation uses {@link DocsEnum#advance(int)}.
   * 
   * @param target
   *          The target document number.
   * @return the matching document or NO_MORE_DOCS if none exist.
   */
  @Override
  public int advance(int target) throws IOException {
    return docsEnum.advance(target);
  }

  /** Returns a string representation of this <code>TermScorer</code>. */
  @Override
  public String toString() { return "scorer(" + weight + ")"; }
  
  @Override
  public PositionIntervalIterator positions() throws IOException {
    boolean doPayloads = false; // nocommit - we need to pass this info somehow
    boolean doOffsets = false;
    return new TermPositions(this, factory.docsAndPositionsEnum(doOffsets), false);
  }

 static final class TermPositions extends PositionIntervalIterator {
    private final PositionInterval interval;
    int positionsPending;
    private final DocsAndPositionsEnum docsAndPos;
    private int docID = -1;

    public TermPositions(Scorer scorer, DocsAndPositionsEnum docsAndPos, boolean doPayloads) {
      super(scorer);
      this.docsAndPos = docsAndPos;
      this.interval = doPayloads ? new PayloadPosInterval(docsAndPos, this)
          : new PositionInterval();
    }

    @Override
    public PositionInterval next() throws IOException {
      if (--positionsPending >= 0) {
        interval.begin = interval.end = docsAndPos.nextPosition();
        return interval;
      }
      positionsPending = 0;
      return null;
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public PositionIntervalIterator[] subs(boolean inOrder) {
      return EMPTY;
    }

    @Override
    public void collect() {
      collector.collectLeafPosition(scorer, interval, docID);
    }

    @Override
    public int advanceTo(int docId) throws IOException {
      int advance = docsAndPos.advance(docId);
      if (advance != NO_MORE_DOCS) {
        positionsPending = docsAndPos.freq();
      }
      interval.reset();
      return docID = docsAndPos.docID();
    }
    
    @Override
    public String toString() {
      return "TermPositions [interval=" + interval + ", positionsPending="
          + positionsPending + ", docID=" + docID + "]";
    }
  }

  private static final class PayloadPosInterval extends PositionInterval {
    private int pos = -1;
    private final DocsAndPositionsEnum payloads;
    private final TermPositions termPos;

    public PayloadPosInterval(DocsAndPositionsEnum payloads, TermPositions pos) {
      this.payloads = payloads;
      this.termPos = pos;
    }

    @Override
    public boolean payloadAvailable() {
      return payloads.hasPayload();
    }

    @Override
    public boolean nextPayload(BytesRef ref) throws IOException {
      if (pos == termPos.positionsPending) {
        return false;
      } else {
        pos = termPos.positionsPending;
        final BytesRef payload = payloads.getPayload();
        ref.bytes = payload.bytes;
        ref.length = payload.length;
        ref.offset = payload.offset;
        return true;
      }
    }

    @Override
    public void reset() {
      super.reset();
      pos = -1;
    }

  }
}
