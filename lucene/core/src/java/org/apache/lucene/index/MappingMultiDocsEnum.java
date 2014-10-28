package org.apache.lucene.index;

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

import org.apache.lucene.index.MultiDocsEnum.EnumWithSlice;

import java.io.IOException;

/**
 * Exposes flex API, merged from flex API of sub-segments,
 * remapping docIDs (this is used for segment merging).
 *
 * @lucene.experimental
 */

final class MappingMultiDocsEnum extends DocsEnum {
  private MultiDocsEnum.EnumWithSlice[] subs;
  int numSubs;
  int upto;
  MergeState.DocMap currentMap;
  DocsEnum current;
  int currentBase;
  int doc;
  private final MergeState mergeState;
  MultiDocsEnum multiDocsEnum;

  /** Sole constructor. */
  public MappingMultiDocsEnum(MergeState mergeState) {
    this.mergeState = mergeState;
  }

  MappingMultiDocsEnum reset(MultiDocsEnum docsEnum) {
    this.numSubs = docsEnum.getNumSubs();
    this.subs = docsEnum.getSubs();
    this.multiDocsEnum = docsEnum;
    upto = -1;
    current = null;
    doc = -1;
    return this;
  }

  /** How many sub-readers we are merging.
   *  @see #getSubs */
  public int getNumSubs() {
    return numSubs;
  }

  /** Returns sub-readers we are merging. */
  public EnumWithSlice[] getSubs() {
    return subs;
  }

  @Override
  public int freq() throws IOException {
    return current.freq();
  }

  @Override
  public int docID() {
    return doc;
  }

  @Override
  public int advance(int target) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int nextDoc() throws IOException {
    while(true) {
      if (current == null) {
        if (upto == numSubs-1) {
          return this.doc = NO_MORE_DOCS;
        } else {
          upto++;
          final int reader = subs[upto].slice.readerIndex;
          current = subs[upto].docsEnum;
          currentBase = mergeState.docBase[reader];
          currentMap = mergeState.docMaps[reader];
          assert currentMap.maxDoc() == subs[upto].slice.length: "readerIndex=" + reader + " subs.len=" + subs.length + " len1=" + currentMap.maxDoc() + " vs " + subs[upto].slice.length;
        }
      }

      int doc = current.nextDoc();
      if (doc != NO_MORE_DOCS) {

        mergeState.checkAbortCount++;
        if (mergeState.checkAbortCount > 60000) {
          mergeState.checkAbort.work(mergeState.checkAbortCount/5.0);
          mergeState.checkAbortCount = 0;
        }

        // compact deletions
        doc = currentMap.get(doc);
        if (doc == -1) {
          continue;
        }
        return this.doc = currentBase + doc;
      } else {
        current = null;
      }
    }
  }
  
  @Override
  public long cost() {
    long cost = 0;
    for (EnumWithSlice enumWithSlice : subs) {
      cost += enumWithSlice.docsEnum.cost();
    }
    return cost;
  }
}

