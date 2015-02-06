package org.apache.lucene.search.join;

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

import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.BytesRef;

class FakeScorer extends Scorer {

  float score;
  int doc = -1;
  int freq = 1;

  FakeScorer() {
    super(null);
  }

  @Override
  public int docID() {
    return doc;
  }

  @Override
  public int nextDoc() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int advance(int target) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long cost() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int freq() throws IOException {
    return freq;
  }

  @Override
  public int nextPosition() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int startOffset() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int endOffset() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public BytesRef getPayload() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public float score() throws IOException {
    return score;
  }
}
