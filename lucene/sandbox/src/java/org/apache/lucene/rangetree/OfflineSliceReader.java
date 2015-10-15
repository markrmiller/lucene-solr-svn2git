package org.apache.lucene.rangetree;

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

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

final class OfflineSliceReader implements SliceReader {
  final IndexInput in;
  private long countLeft;
  private long value;
  private long ord;
  private int docID;

  OfflineSliceReader(Directory tempDir, String tempFileName, long start, long count) throws IOException {
    in = tempDir.openInput(tempFileName, IOContext.READONCE);
    in.seek(start * RangeTreeWriter.BYTES_PER_DOC);
    this.countLeft = count;
  }

  @Override
  public boolean next() throws IOException {
    if (countLeft == 0) {
      return false;
    }
    countLeft--;
    value = in.readLong();
    ord = in.readLong();
    docID = in.readInt();
    return true;
  }

  @Override
  public long value() {
    return value;
  }

  @Override
  public long ord() {
    return ord;
  }

  @Override
  public int docID() {
    return docID;
  }

  @Override
  public void close() throws IOException {
    in.close();
  }
}

