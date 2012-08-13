package org.apache.lucene.search.positions;

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
import java.util.Arrays;

import org.apache.lucene.search.Scorer;

/**
 * 
 * @lucene.experimental
 */
// nocommit - javadoc
public final class BlockIntervalIterator extends IntervalIterator {
  private final IntervalIterator[] iterators;

  private static final Interval INFINITE_INTERVAL = new Interval(
      Integer.MIN_VALUE, Integer.MIN_VALUE, -1, -1);
  private final Interval[] intervals;
  private final Interval interval = new Interval(
      Integer.MIN_VALUE, Integer.MIN_VALUE, -1, -1);
  private final int[] gaps;

  private final int lastIter;

  public BlockIntervalIterator(boolean collectPositions, IntervalIterator other) {
    this(other, collectPositions, defaultGaps(other.subs(true).length));
  }

  public BlockIntervalIterator(IntervalIterator other, boolean collectPositions, int[] gaps) {
    super(other.getScorer(), collectPositions);
    assert other.subs(true) != null;
    iterators = other.subs(true);
    assert iterators.length > 1;
    intervals = new Interval[iterators.length];
    lastIter = iterators.length - 1;
    this.gaps = gaps;
  }

  public BlockIntervalIterator(Scorer scorer, boolean collectPositions, Scorer... subScorers)
      throws IOException {
    this(scorer, collectPositions, defaultGaps(subScorers.length), subScorers);
  }

  private static int[] defaultGaps(int num) {
    int[] gaps = new int[num];
    Arrays.fill(gaps, 1);
    return gaps;
  }

  public BlockIntervalIterator(Scorer scorer,  boolean collectPositions, int[] gaps, Scorer... subScorers)
      throws IOException {
    super(scorer, collectPositions);
    assert subScorers.length > 1;
    iterators = new IntervalIterator[subScorers.length];
    intervals = new Interval[subScorers.length];
    for (int i = 0; i < subScorers.length; i++) {
      iterators[i] = subScorers[i].positions(collectPositions);
      assert iterators[i] != null;
    }
    lastIter = iterators.length - 1;
    this.gaps = gaps;
  }

  public BlockIntervalIterator(Scorer scorer, int[] gaps, boolean collectPositions, IntervalIterator... iterators) {
    super(scorer, collectPositions);
    assert iterators.length > 1;
    this.iterators = iterators;
    intervals = new Interval[iterators.length];
    lastIter = iterators.length - 1;
    this.gaps = gaps;
  }

  public BlockIntervalIterator(Scorer scorer, boolean collectPositions, IntervalIterator... iterators) {
    this(scorer, defaultGaps(iterators.length), collectPositions, iterators);
  }

  @Override
  public Interval next() throws IOException {
    if ((intervals[0] = iterators[0].next()) == null) {
      return null;
    }
    int offset = 0;
    for (int i = 1; i < iterators.length;) {
      final int gap = gaps[i];
      while (intervals[i].begin + gap <= intervals[i - 1].end) {
        if ((intervals[i] = iterators[i].next()) == null) {
          return null;
        }
      }
      offset += gap;
      if (intervals[i].begin == intervals[i - 1].end + gaps[i]) {
        i++;
        if (i < iterators.length && intervals[i] == INFINITE_INTERVAL) {
          // advance only if really necessary
          iterators[i].scorerAdvanced(docID());
          assert iterators[i].docID() == docID();
        }
      } else {
        do {
          if ((intervals[0] = iterators[0].next()) == null) {
            return null;
          }
        } while (intervals[0].begin < intervals[i].end - offset);

        i = 1;
      }
    }
    interval.begin = intervals[0].begin;
    interval.end = intervals[lastIter].end;
    interval.offsetBegin = intervals[0].offsetBegin;
    interval.offsetEnd = intervals[lastIter].offsetEnd;
    return interval;
  }

  @Override
  public IntervalIterator[] subs(boolean inOrder) {
    return iterators;
  }

  @Override
  public void collect(IntervalCollector collector) {
    assert collectPositions;
    collector.collectComposite(scorer, interval, docID());
    for (IntervalIterator iter : iterators) {
      iter.collect(collector);
    }
  }

  @Override
  public int scorerAdvanced(int docId) throws IOException {
    iterators[0].scorerAdvanced(docId);
    assert iterators[0].docID() == docId;
    iterators[1].scorerAdvanced(docId);
    assert iterators[1].docID() == docId;
    Arrays.fill(intervals, INFINITE_INTERVAL);
    return docId;
  }

  @Override
  public int matchDistance() {
    return intervals[lastIter].begin - intervals[0].end;
  }
}
