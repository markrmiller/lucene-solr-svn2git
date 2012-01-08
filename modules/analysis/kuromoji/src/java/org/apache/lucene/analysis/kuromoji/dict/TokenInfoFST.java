package org.apache.lucene.analysis.kuromoji.dict;

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

import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FST.Arc;

public class TokenInfoFST {
  private final FST<Long> fst;

  // depending upon fasterButMoreRam, we cache root arcs for either 
  // kana (0x3040-0x30FF) or kana + han (0x3040-0x9FFF)
  // false: 191 arcs
  // true:  28,607 arcs (costs ~1.5MB)
  private final int cacheCeiling;
  private final FST.Arc<Long> rootCache[];
  
  public final Long NO_OUTPUT;

  public TokenInfoFST(FST<Long> fst, boolean fasterButMoreRam) throws IOException {
    this.fst = fst;
    this.cacheCeiling = fasterButMoreRam ? 0x9FFF : 0x30FF;
    NO_OUTPUT = fst.outputs.getNoOutput();
    rootCache = cacheRootArcs();
  }
  
  @SuppressWarnings("unchecked")
  private FST.Arc<Long>[] cacheRootArcs() throws IOException {
    FST.Arc<Long> rootCache[] = new FST.Arc[1+(cacheCeiling-0x3040)];
    FST.Arc<Long> firstArc = new FST.Arc<Long>();
    fst.getFirstArc(firstArc);
    FST.Arc<Long> arc = new FST.Arc<Long>();
    // TODO: jump to 3040, readNextRealArc to ceiling? (just be careful we don't add bugs)
    for (int i = 0; i < rootCache.length; i++) {
      if (fst.findTargetArc(0x3040 + i, firstArc, arc) != null) {
        rootCache[i] = new FST.Arc<Long>().copyFrom(arc);
      }
    }
    return rootCache;
  }
  
  public FST.Arc<Long> findTargetArc(int ch, FST.Arc<Long> follow, FST.Arc<Long> arc, boolean useCache) throws IOException {
    if (useCache && ch >= 0x3040 && ch <= cacheCeiling) {
      assert ch != FST.END_LABEL;
      final Arc<Long> result = rootCache[ch - 0x3040];
      if (result == null) {
        return null;
      } else {
        arc.copyFrom(result);
        return arc;
      }
    } else {
      return fst.findTargetArc(ch, follow, arc);
    }
  }
  
  public Arc<Long> getFirstArc(FST.Arc<Long> arc) {
    return fst.getFirstArc(arc);
  }
  
  public Long addOutput(Long prefix, Long output) {
    return fst.outputs.add(prefix, output);
  }
}
