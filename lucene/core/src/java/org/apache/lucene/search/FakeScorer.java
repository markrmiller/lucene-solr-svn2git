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

import java.io.IOException;
import java.util.Collection;

/** Used by {@link BulkScorer}s that need to pass a {@link
 *  Scorer} to {@link LeafCollector#setScorer}. */
final class FakeScorer extends Scorer {
  float score;
  int doc = -1;
  int freq = 1;

  public FakeScorer() {
    super(null);
  }
    
  @Override
  public int advance(int target) {
    throw new UnsupportedOperationException("FakeScorer doesn't support advance(int)");
  }

  @Override
  public int docID() {
    return doc;
  }

  @Override
  public int freq() {
    return freq;
  }

  @Override
  public int nextPosition() throws IOException {
    throw new UnsupportedOperationException("FakeScorer doesn't support nextPosition()");
  }

  @Override
  public int nextDoc() {
    throw new UnsupportedOperationException("FakeScorer doesn't support nextDoc()");
  }
    
  @Override
  public float score() {
    return score;
  }

  @Override
  public long cost() {
    return 1;
  }

  @Override
  public Weight getWeight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<ChildScorer> getChildren() {
    throw new UnsupportedOperationException();
  }
}
