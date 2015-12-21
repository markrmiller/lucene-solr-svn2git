package org.apache.lucene.bkdtree;

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
import java.util.Collections;

import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;

final class OfflineLatLonWriter implements LatLonWriter {

  final Directory tempDir;
  final byte[] scratchBytes = new byte[BKDTreeWriter.BYTES_PER_DOC];
  final ByteArrayDataOutput scratchBytesOutput = new ByteArrayDataOutput(scratchBytes);      
  final IndexOutput out;
  final long count;
  private long countWritten;
  private boolean closed;

  public OfflineLatLonWriter(Directory tempDir, String tempFileNamePrefix, long count) throws IOException {
    this.tempDir = tempDir;
    out = tempDir.createTempOutput(tempFileNamePrefix, "bkd", IOContext.DEFAULT);
    this.count = count;
  }
    
  @Override
  public void append(int latEnc, int lonEnc, long ord, int docID) throws IOException {
    out.writeInt(latEnc);
    out.writeInt(lonEnc);
    out.writeLong(ord);
    out.writeInt(docID);
    countWritten++;
  }

  @Override
  public LatLonReader getReader(long start) throws IOException {
    assert closed;
    return new OfflineLatLonReader(tempDir, out.getName(), start, count-start);
  }

  @Override
  public void close() throws IOException {
    closed = true;
    out.close();
    if (count != countWritten) {
      throw new IllegalStateException("wrote " + countWritten + " values, but expected " + count);
    }
  }

  @Override
  public void destroy() throws IOException {
    tempDir.deleteFiles(Collections.singleton(out.getName()));
  }

  @Override
  public String toString() {
    return "OfflineLatLonWriter(count=" + count + " tempFileName=" + out.getName() + ")";
  }
}

