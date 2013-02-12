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

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.search.intervals.ConjunctionIntervalIterator;
import org.apache.lucene.search.intervals.IntervalIterator;
import org.apache.lucene.search.intervals.TermIntervalIterator;
import org.apache.lucene.util.ArrayUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;


/** Scorer for conjunctions, sets of terms, all of which are required. */
class ConjunctionTermScorer extends Scorer {
  protected final float coord;
  protected int lastDoc = -1;
  protected final DocsAndFreqs[] docsAndFreqs;
  private final DocsAndFreqs lead;
  private DocsAndFreqs[] origDocsAndFreqs;
  final PositionQueue posQueue;

  ConjunctionTermScorer(Weight weight, float coord, DocsAndFreqs[] docsAndFreqs) {
    super(weight);
    this.coord = coord;
    this.docsAndFreqs = docsAndFreqs;
    this.origDocsAndFreqs = new DocsAndFreqs[docsAndFreqs.length];
    System.arraycopy(docsAndFreqs, 0,origDocsAndFreqs, 0, docsAndFreqs.length);
    // Sort the array the first time to allow the least frequent DocsEnum to
    // lead the matching.
    ArrayUtil.mergeSort(docsAndFreqs, new Comparator<DocsAndFreqs>() {
      @Override
      public int compare(DocsAndFreqs o1, DocsAndFreqs o2) {
        return o1.docFreq - o2.docFreq;
      }
    });
    posQueue = new PositionQueue(makeDocsEnumArray(docsAndFreqs));
    lead = docsAndFreqs[0]; // least frequent DocsEnum leads the intersection
  }

  private DocsEnum[] makeDocsEnumArray(DocsAndFreqs[] docsAndFreqs) {
    DocsEnum[] docsEnums = new DocsEnum[docsAndFreqs.length];
    for (int i = 0; i < docsAndFreqs.length; i++) {
      docsEnums[i] = docsAndFreqs[i].docs;
    }
    return docsEnums;
  }

  private int doNext(int doc) throws IOException {
    do {
      if (lead.doc == DocIdSetIterator.NO_MORE_DOCS) {
        return NO_MORE_DOCS;
      }
      advanceHead: do {
        for (int i = 1; i < docsAndFreqs.length; i++) {
          if (docsAndFreqs[i].doc < doc) {
            docsAndFreqs[i].doc = docsAndFreqs[i].docs.advance(doc);
          }
          if (docsAndFreqs[i].doc > doc) {
            // DocsEnum beyond the current doc - break and advance lead
            break advanceHead;
          }
        }
        // success - all DocsEnums are on the same doc
        posQueue.advanceTo(doc);
        return doc;
      } while (true);
      // advance head for next iteration
      doc = lead.doc = lead.docs.nextDoc();  
    } while (true);
  }

  @Override
  public int advance(int target) throws IOException {
    if (lead.doc >= target)
      return lead.doc;
    lead.doc = lead.docs.advance(target);
    return lastDoc = doNext(lead.doc);
  }

  @Override
  public int docID() {
    return lastDoc;
  }

  @Override
  public int nextDoc() throws IOException {
    lead.doc = lead.docs.nextDoc();
    return lastDoc = doNext(lead.doc);
  }

  @Override
  public float score() throws IOException {
    // TODO: sum into a double and cast to float if we ever send required clauses to BS1
    float sum = 0.0f;
    for (DocsAndFreqs docs : docsAndFreqs) {
      sum += docs.scorer.score();
    }
    return sum * coord;
  }
  
  @Override
  public int freq() throws IOException {
    return docsAndFreqs.length;
  }

  @Override
  public int nextPosition() throws IOException {
    return posQueue.nextPosition();
  }

  @Override
  public int startPosition() throws IOException {
    return posQueue.startPosition();
  }

  @Override
  public int endPosition() throws IOException {
    return posQueue.endPosition();
  }

  @Override
  public int startOffset() throws IOException {
    return posQueue.startOffset();
  }

  @Override
  public int endOffset() throws IOException {
    return posQueue.endOffset();
  }

  @Override
  public Collection<ChildScorer> getChildren() {
    ArrayList<ChildScorer> children = new ArrayList<ChildScorer>(docsAndFreqs.length);
    for (DocsAndFreqs docs : docsAndFreqs) {
      children.add(new ChildScorer(docs.scorer, "MUST"));
    }
    return children;
  }

  static final class DocsAndFreqs {

    final DocsEnum docs;
    final int docFreq;
    final TermScorer scorer;
    int doc = -1;

    DocsAndFreqs(TermScorer termScorer) {
      this(termScorer, termScorer.getDocsEnum(), termScorer.getDocFreq());
    }

    DocsAndFreqs(TermScorer scorer, DocsEnum docs, int docFreq) {
      this.docs = docs;
      this.docFreq = docFreq;
      this.scorer = scorer;
    }

  }

  @Override
  public IntervalIterator intervals(boolean collectIntervals) throws IOException {
    
    TermIntervalIterator[] positionIters = new TermIntervalIterator[origDocsAndFreqs.length];
    for (int i = 0; i < positionIters.length; i++) {
      DocsAndFreqs d = origDocsAndFreqs[i];
      positionIters[i] = new TermIntervalIterator(this, d.docs, false, collectIntervals);
    }
    return new ConjunctionIntervalIterator(this, collectIntervals, positionIters);
  }

}
