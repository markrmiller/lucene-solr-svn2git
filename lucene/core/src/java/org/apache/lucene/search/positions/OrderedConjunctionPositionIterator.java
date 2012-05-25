package org.apache.lucene.search.positions;

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

/**
 * @lucene.experimental
 */ // nocommit - javadoc
public final class OrderedConjunctionPositionIterator extends
    PositionIntervalIterator {

  private final PositionIntervalIterator[] iterators;
  private static final PositionInterval INFINITE_INTERVAL = new PositionInterval(
      Integer.MIN_VALUE, Integer.MIN_VALUE, -1, -1);
  private final PositionInterval[] intervals;
  private final int lastIter;
  private final PositionInterval interval = new PositionInterval(
      Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1);
  private int index = 1;

  public OrderedConjunctionPositionIterator(PositionIntervalIterator other) {
    super(other.scorer);
    assert other.subs(true) != null;
    iterators = other.subs(true);
    assert iterators.length > 1;
    intervals = new PositionInterval[iterators.length];
    lastIter = iterators.length - 1;
  }

  @Override
  public PositionInterval next() throws IOException {
    
    
    if(intervals[0] == null) {
      return null;
    }
      
    interval.begin = Integer.MAX_VALUE;
    interval.end = Integer.MAX_VALUE;
    interval.offsetBegin = -1;
    interval.offsetEnd = -1;
    int b = Integer.MAX_VALUE;
    while (true) {
      while (true) {
        final PositionInterval previous = intervals[index - 1];
        if (previous.end >= b) {
          return interval.begin == Integer.MAX_VALUE ? null : interval;
        }
        if (index == intervals.length || intervals[index].begin > previous.end) {
          break;
        }
        PositionInterval current = intervals[index];
        do {
          final PositionInterval next;
          if (current.end >= b || (next = iterators[index].next()) == null) {
            return interval.begin == Integer.MAX_VALUE ? null : interval;
          }
          current = intervals[index] = next;
        } while (current.begin <= previous.end);
        index++;
      }
      interval.begin = intervals[0].begin;
      interval.end = intervals[lastIter].end;
      interval.offsetBegin = intervals[0].offsetBegin;
      interval.offsetEnd = intervals[lastIter].offsetEnd;
      b = intervals[lastIter].begin;
      index = 1;
      intervals[0] = iterators[0].next();
      if (intervals[0] == null) {
        return interval.begin == Integer.MAX_VALUE ? null : interval;
      }
    }

  }

  @Override
  public PositionIntervalIterator[] subs(boolean inOrder) {
    return iterators;
  }

  @Override
  public void collect() {
    collector.collectComposite(scorer, interval, currentDoc);
    for (PositionIntervalIterator iter : iterators) {
      iter.collect();
    }
  }

  @Override
  public int advanceTo(int docId) throws IOException {
    if (docId == currentDoc) {
      return docId;
    }
    for (int i = 0; i < iterators.length; i++) {
      int advanceTo = iterators[i].advanceTo(docId);
      assert advanceTo == docId;
      intervals[i] = INFINITE_INTERVAL;
    }
    intervals[0] = iterators[0].next();
    index = 1;
    return currentDoc = docId;
  }

}
