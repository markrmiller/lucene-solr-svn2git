package org.apache.lucene.search.posfilter;
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

import org.apache.lucene.index.DocsEnum;

/**
 * Represents a section of a document that matches a query
 */
class Interval implements Cloneable {

  /** The position of the start of this Interval */
  public int begin;

  /** The position of the end of this Interval */
  public int end;

  /** The offset of the start of this Interval */
  public int offsetBegin;

  /** The offset of the end of this Interval */
  public int offsetEnd;

  /** An interval that will always compare as less than any other interval */
  public static final Interval INFINITE_INTERVAL = new Interval();

   /**
   * Constructs a new Interval
   * @param begin the start position
   * @param end the end position
   * @param offsetBegin the start offset
   * @param offsetEnd the end offset
   */
  public Interval(int begin, int end, int offsetBegin, int offsetEnd) {
    this.begin = begin;
    this.end = end;
    this.offsetBegin = offsetBegin;
    this.offsetEnd = offsetEnd;
  }

  /**
   * Constructs a new Interval with no initial values.  This
   * will always compare as less than any other Interval.
   */
  public Interval() {
    this(Integer.MIN_VALUE, Integer.MIN_VALUE, -1, -1);
  }

  /**
   * Update to span the range defined by two other Intervals.
   * @param start the first Interval
   * @param end the second Interval
   */
  public void update(Interval start, Interval end) {
    this.begin = start.begin;
    this.offsetBegin = start.offsetBegin;
    this.end = end.end;
    this.offsetEnd = end.offsetEnd;
  }

  public void update(DocsEnum start, Interval end) throws IOException {
    this.begin = start.startPosition();
    this.offsetBegin = start.startOffset();
    this.end = end.end;
    this.offsetEnd = end.offsetEnd;
  }

  public void update(Interval start, DocsEnum end) throws IOException {
    this.begin = start.begin;
    this.offsetBegin = start.offsetBegin;
    this.end = end.endPosition();
    this.offsetEnd = end.endOffset();
  }

  /**
   * Compare with another Interval.
   * @param other the comparator
   * @return true if both start and end positions are less than
   *              the comparator.
   */
  public boolean lessThanExclusive(Interval other) {
    return begin < other.begin && end < other.end;
  }

  /**
   * Compare with another Interval
   * @param other the comparator
   * @return true if this Interval contains the comparator
   */
  public boolean contains(Interval other) {
    return begin <= other.begin && other.end <= end;
  }

  /**
   * Compare with another Interval to find overlaps
   * @param other the comparator
   * @return true if the two intervals overlap
   */
  public boolean overlaps(Interval other) {
    return this.contains(other) || other.contains(this);
  }

  /**
   * Set to a state that will always compare as less than any
   * other Interval.
   */
  public void reset() {
    offsetBegin = offsetEnd = -1;
    begin = end = Integer.MIN_VALUE;
  }

  /**
   * Set to a state that will always compare as more than any
   * other Interval.
   */
  public void setMaximum() {
    offsetBegin = offsetEnd = -1;
    begin = end = DocsEnum.NO_MORE_POSITIONS;
  }
  
  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(); // should not happen
    }
  }
  
  @Override
  public String toString() {
    return "Interval [begin=" + begin + "(" + offsetBegin + "), end="
        + end + "(" + offsetEnd + ")]";
  }

  public void update(DocsEnum docsEnum) throws IOException {
    offsetBegin = docsEnum.startOffset();
    offsetEnd = docsEnum.endOffset();
    begin = docsEnum.startPosition();
    end = docsEnum.endPosition();
  }

  public void update(Interval interval) {
    this.begin = interval.begin;
    this.end = interval.end;
    this.offsetBegin = interval.offsetBegin;
    this.offsetEnd = interval.offsetEnd;
  }

}