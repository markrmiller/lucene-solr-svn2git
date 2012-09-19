package org.apache.lucene.util;

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

/** Represents int[], as a slice (offset + length) into an
 *  existing int[].  The {@link #ints} member should never be null; use
 *  {@link #EMPTY_INTS} if necessary.
 *
 *  @lucene.internal */
public final class IntsRef implements Comparable<IntsRef>, Cloneable {
  /** An empty integer array for convenience */
  public static final int[] EMPTY_INTS = new int[0];

  /** The contents of the IntsRef. Should never be {@code null}. */
  public int[] ints;
  /** Offset of first valid integer. */
  public int offset;
  /** Length of used ints. */
  public int length;

  /** Create a IntsRef with {@link #EMPTY_INTS} */
  public IntsRef() {
    ints = EMPTY_INTS;
  }

  /** 
   * Create a IntsRef pointing to a new array of size <code>capacity</code>.
   * Offset and length will both be zero.
   */
  public IntsRef(int capacity) {
    ints = new int[capacity];
  }

  /** This instance will directly reference ints w/o making a copy.
   * ints should not be null.
   */
  public IntsRef(int[] ints, int offset, int length) {
    assert ints != null;
    assert offset >= 0;
    assert length >= 0;
    assert ints.length >= offset + length;
    this.ints = ints;
    this.offset = offset;
    this.length = length;
  }

  @Override
  public IntsRef clone() {
    return new IntsRef(ints, offset, length);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 0;
    final int end = offset + length;
    for(int i = offset; i < end; i++) {
      result = prime * result + ints[i];
    }
    return result;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (other instanceof IntsRef) {
      return this.intsEquals((IntsRef) other);
    }
    return false;
  }

  public boolean intsEquals(IntsRef other) {
    if (length == other.length) {
      int otherUpto = other.offset;
      final int[] otherInts = other.ints;
      final int end = offset + length;
      for(int upto=offset;upto<end;upto++,otherUpto++) {
        if (ints[upto] != otherInts[otherUpto]) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /** Signed int order comparison */
  public int compareTo(IntsRef other) {
    if (this == other) return 0;

    final int[] aInts = this.ints;
    int aUpto = this.offset;
    final int[] bInts = other.ints;
    int bUpto = other.offset;

    final int aStop = aUpto + Math.min(this.length, other.length);

    while(aUpto < aStop) {
      int aInt = aInts[aUpto++];
      int bInt = bInts[bUpto++];
      if (aInt > bInt) {
        return 1;
      } else if (aInt < bInt) {
        return -1;
      }
    }

    // One is a prefix of the other, or, they are equal:
    return this.length - other.length;
  }

  public void copyInts(IntsRef other) {
    if (ints.length - offset < other.length) {
      ints = new int[other.length];
      offset = 0;
    }
    System.arraycopy(other.ints, other.offset, ints, offset, other.length);
    length = other.length;
  }

  /** 
   * Used to grow the reference array. 
   * 
   * In general this should not be used as it does not take the offset into account.
   * @lucene.internal */
  public void grow(int newLength) {
    assert offset == 0;
    if (ints.length < newLength) {
      ints = ArrayUtil.grow(ints, newLength);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    final int end = offset + length;
    for(int i=offset;i<end;i++) {
      if (i > offset) {
        sb.append(' ');
      }
      sb.append(Integer.toHexString(ints[i]));
    }
    sb.append(']');
    return sb.toString();
  }
  
  /**
   * Creates a new IntsRef that points to a copy of the ints from 
   * <code>other</code>
   * <p>
   * The returned IntsRef will have a length of other.length
   * and an offset of zero.
   */
  public static IntsRef deepCopyOf(IntsRef other) {
    IntsRef clone = new IntsRef();
    clone.copyInts(other);
    return clone;
  }
}
