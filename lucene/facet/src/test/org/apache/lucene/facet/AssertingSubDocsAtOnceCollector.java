package org.apache.lucene.facet;

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

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer.ChildScorer;
import org.apache.lucene.search.Scorer;

/** Verifies in collect() that all child subScorers are on
 *  the collected doc. */
class AssertingSubDocsAtOnceCollector extends Collector {

  // TODO: allow wrapping another Collector

  List<Scorer> allScorers;

  @Override
  public void setScorer(Scorer s) {
    // Gathers all scorers, including s and "under":
    allScorers = new ArrayList<>();
    allScorers.add(s);
    int upto = 0;
    while(upto < allScorers.size()) {
      s = allScorers.get(upto++);
      for (ChildScorer sub : s.getChildren()) {
        allScorers.add(sub.child);
      }
    }
  }

  @Override
  public void collect(int docID) {
    for(Scorer s : allScorers) {
      if (docID != s.docID()) {
        throw new IllegalStateException("subScorer=" + s + " has docID=" + s.docID() + " != collected docID=" + docID);
      }
    }
  }

  @Override
  public void setNextReader(AtomicReaderContext context) {
  }

  @Override
  public boolean acceptsDocsOutOfOrder() {
    return false;
  }
}
