package org.apache.lucene.bkdtree3d;

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

import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.DocIdSetBuilder;
import org.apache.lucene.util.RamUsageEstimator;

/** Handles intersection of a shape with a BKD tree previously written with {@link BKD3DTreeWriter}.
 *
 * @lucene.experimental */

final class BKD3DTreeReader implements Accountable {
  final private int[] splitValues; 
  final private int leafNodeOffset;
  final private long[] leafBlockFPs;
  final int maxDoc;
  final IndexInput in;
  final int globalMinX;
  final int globalMaxX;
  final int globalMinY;
  final int globalMaxY;
  final int globalMinZ;
  final int globalMaxZ;

  enum Relation {INSIDE, CROSSES, OUTSIDE};

  interface ValueFilter {
    boolean accept(int docID);
    Relation compare(int cellXMin, int cellXMax, int cellYMin, int cellYMax, int cellZMin, int cellZMax);
  }

  public BKD3DTreeReader(IndexInput in, int maxDoc) throws IOException {

    // nocommit is this really worth it?
    globalMinX = in.readInt();
    globalMaxX = in.readInt();
    globalMinY = in.readInt();
    globalMaxY = in.readInt();
    globalMinZ = in.readInt();
    globalMaxZ = in.readInt();

    // Read index:
    int numLeaves = in.readVInt();
    leafNodeOffset = numLeaves;

    // Tree is fully balanced binary tree, so number of nodes = numLeaves-1, except our nodeIDs are 1-based (splitValues[0] is unused):
    splitValues = new int[numLeaves];
    for(int i=0;i<numLeaves;i++) {
      splitValues[i] = in.readInt();
    }
    leafBlockFPs = new long[numLeaves];
    for(int i=0;i<numLeaves;i++) {
      leafBlockFPs[i] = in.readVLong();
    }

    this.maxDoc = maxDoc;
    this.in = in;
  }

  private static final class QueryState {
    final IndexInput in;
    byte[] scratch = new byte[16];
    final ByteArrayDataInput scratchReader = new ByteArrayDataInput(scratch);
    final DocIdSetBuilder docs;
    final int xMin;
    final int xMax;
    final int yMin;
    final int yMax;
    final int zMin;
    final int zMax;
    final ValueFilter valueFilter;

    public QueryState(IndexInput in, int maxDoc,
                      int xMin, int xMax,
                      int yMin, int yMax,
                      int zMin, int zMax,
                      ValueFilter valueFilter) {
      this.in = in;
      this.docs = new DocIdSetBuilder(maxDoc);
      this.xMin = xMin;
      this.xMax = xMax;
      this.yMin = yMin;
      this.yMax = yMax;
      this.zMin = zMin;
      this.zMax = zMax;
      this.valueFilter = valueFilter;
    }
  }

  public DocIdSet intersect(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax, ValueFilter filter) throws IOException {

    QueryState state = new QueryState(in.clone(), maxDoc,
                                      xMin, xMax,
                                      yMin, yMax,
                                      zMin, zMax,
                                      filter);

    int hitCount = intersect(state, 1,
                             globalMinX, globalMaxX,
                             globalMinY, globalMaxY,
                             globalMinZ, globalMaxZ);

    // NOTE: hitCount is an over-estimate in the multi-valued case:
    return state.docs.build(hitCount);
  }

  /** Fast path: this is called when the query rect fully encompasses all cells under this node. */
  private int addAll(QueryState state, int nodeID) throws IOException {
    //System.out.println("  addAll nodeID=" + nodeID + " leafNodeOffset=" + leafNodeOffset);

    if (nodeID >= leafNodeOffset) {

      /*
      System.out.println("A: " + BKDTreeWriter.decodeLat(cellLatMinEnc)
                         + " " + BKDTreeWriter.decodeLat(cellLatMaxEnc)
                         + " " + BKDTreeWriter.decodeLon(cellLonMinEnc)
                         + " " + BKDTreeWriter.decodeLon(cellLonMaxEnc));
      */

      // Leaf node
      long fp = leafBlockFPs[nodeID-leafNodeOffset];
      //System.out.println("    leaf fp=" + fp);
      state.in.seek(fp);
      
      //System.out.println("    seek to leafFP=" + fp);
      // How many points are stored in this leaf cell:
      int count = state.in.readVInt();
      //System.out.println("    count=" + count);
      state.docs.grow(count);
      for(int i=0;i<count;i++) {
        int docID = state.in.readInt();
        state.docs.add(docID);
      }

      return count;
    } else {
      int count = addAll(state, 2*nodeID);
      count += addAll(state, 2*nodeID+1);
      return count;
    }
  }

  private int intersect(QueryState state,
                        int nodeID,
                        int cellXMin, int cellXMax,
                        int cellYMin, int cellYMax,
                        int cellZMin, int cellZMax)
    throws IOException {

    //System.out.println("BKD3D.intersect nodeID=" + nodeID + " cellX=" + cellXMin + " TO " + cellXMax + ", cellY=" + cellYMin + " TO " + cellYMax + ", cellZ=" + cellZMin + " TO " + cellZMax);

    if (cellXMin >= state.xMin ||
        cellXMax <= state.xMax ||
        cellYMin >= state.yMin ||
        cellYMin >= state.yMin ||
        cellZMin <= state.zMin ||
        cellZMax <= state.zMax) {

      Relation r = state.valueFilter.compare(cellXMin, cellXMax,
                                             cellYMin, cellYMax,
                                             cellZMin, cellZMax);
      //System.out.println("  relation: " + r);

      if (r == Relation.OUTSIDE) {
        // This cell is fully outside of the query shape: stop recursing
        return 0;
      } else if (r == Relation.INSIDE) {
        // This cell is fully inside of the query shape: recursively add all points in this cell without filtering
        return addAll(state, nodeID);
      } else {
        // The cell crosses the shape boundary, so we fall through and do full filtering
      }
    }

    //System.out.println("\nintersect node=" + nodeID + " vs " + leafNodeOffset);

    if (nodeID >= leafNodeOffset) {
      //System.out.println("  leaf");
      // Leaf node; scan and filter all points in this block:
      //System.out.println("    intersect leaf nodeID=" + nodeID + " vs leafNodeOffset=" + leafNodeOffset + " fp=" + leafBlockFPs[nodeID-leafNodeOffset]);
      int hitCount = 0;

      long fp = leafBlockFPs[nodeID-leafNodeOffset];

      /*
      System.out.println("I: " + BKDTreeWriter.decodeLat(cellLatMinEnc)
                         + " " + BKDTreeWriter.decodeLat(cellLatMaxEnc)
                         + " " + BKDTreeWriter.decodeLon(cellLonMinEnc)
                         + " " + BKDTreeWriter.decodeLon(cellLonMaxEnc));
      */

      state.in.seek(fp);

      // How many points are stored in this leaf cell:
      int count = state.in.readVInt();

      state.docs.grow(count);
      //System.out.println("  count=" + count);
      for(int i=0;i<count;i++) {
        int docID = state.in.readInt();
        //System.out.println("  check docID=" + docID);
        if (state.valueFilter.accept(docID)) {
          state.docs.add(docID);
          hitCount++;
        }
      }

      return hitCount;

    } else {

      //System.out.println("  non-leaf");

      int splitDim = BKD3DTreeWriter.getSplitDim(cellXMin, cellXMax,
                                                 cellYMin, cellYMax,
                                                 cellZMin, cellZMax);

      int splitValue = splitValues[nodeID];

      int count = 0;

      if (splitDim == 0) {

        //System.out.println("  split on lat=" + splitValue);

        // Inner node split on x:

        // Left node:
        if (state.xMin <= splitValue) {
          //System.out.println("  recurse left");
          count += intersect(state,
                             2*nodeID,
                             cellXMin, splitValue,
                             cellYMin, cellYMax,
                             cellZMin, cellZMax);
        }

        // Right node:
        if (state.xMax >= splitValue) {
          //System.out.println("  recurse right");
          count += intersect(state,
                             2*nodeID+1,
                             splitValue, cellXMax,
                             cellYMin, cellYMax,
                             cellZMin, cellZMax);
        }

      } else if (splitDim == 1) {
        // Inner node split on y:

        // System.out.println("  split on lon=" + splitValue);

        // Left node:
        if (state.yMin <= splitValue) {
          // System.out.println("  recurse left");
          count += intersect(state,
                             2*nodeID,
                             cellXMin, cellXMax,
                             cellYMin, splitValue,
                             cellZMin, cellZMax);
        }

        // Right node:
        if (state.yMax >= splitValue) {
          // System.out.println("  recurse right");
          count += intersect(state,
                             2*nodeID+1,
                             cellXMin, cellXMax,
                             splitValue, cellYMax,
                             cellZMin, cellZMax);
        }
      } else {
        // Inner node split on z:

        // System.out.println("  split on lon=" + splitValue);

        // Left node:
        if (state.zMin <= splitValue) {
          // System.out.println("  recurse left");
          count += intersect(state,
                             2*nodeID,
                             cellXMin, cellXMax,
                             cellYMin, cellYMax,
                             cellZMin, splitValue);
        }

        // Right node:
        if (state.zMax >= splitValue) {
          // System.out.println("  recurse right");
          count += intersect(state,
                             2*nodeID+1,
                             cellXMin, cellXMax,
                             cellYMin, cellYMax,
                             splitValue, cellZMax);
        }
      }

      return count;
    }
  }

  @Override
  public long ramBytesUsed() {
    return splitValues.length * RamUsageEstimator.NUM_BYTES_INT + 
      leafBlockFPs.length * RamUsageEstimator.NUM_BYTES_LONG;
  }
}
