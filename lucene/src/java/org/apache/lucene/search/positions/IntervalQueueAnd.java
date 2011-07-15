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

import org.apache.lucene.search.positions.PositionIntervalIterator.PositionInterval;
/**
 * 
 * @lucene.experimental
 */ // nocommit - javadoc/
final class IntervalQueueAnd extends IntervalQueue {

  int rightExtreme = Integer.MIN_VALUE;
  
  public IntervalQueueAnd(int size) {
    super(size);
  }

  public void reset () {
    super.reset();
    queueInterval.begin = Integer.MIN_VALUE;
    queueInterval.end = Integer.MIN_VALUE;
    rightExtreme = Integer.MIN_VALUE;
  }

  public void updateRightExtreme(PositionInterval interval) {
    rightExtreme = Math.max(rightExtreme, Math.max(interval.end, interval.end));
  }
  
  public boolean topContainsQueueInterval() {
    PositionInterval interval = top().interval;
    return interval.begin <= queueInterval.begin
        && queueInterval.end <= rightExtreme;
  }

  public void updateQueueInterval() {
    PositionInterval interval = top().interval;
    queueInterval.begin = interval.begin;
    queueInterval.end = rightExtreme;
  }
  
  @Override
  protected boolean lessThan(IntervalRef left, IntervalRef right) {
    final PositionInterval a = left.interval;
    final PositionInterval b = right.interval;
    return a.begin < b.begin || (a.begin == b.begin && a.end >= b.end);
  }
}
