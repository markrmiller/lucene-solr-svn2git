package org.apache.lucene.analysis.kuromoji.util;

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.lucene.analysis.kuromoji.dict.ConnectionCosts;

import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.CodecUtil;

public final class ConnectionCostsWriter {
  
  private final short[][] costs; // array is backward IDs first since get is called using the same backward ID consecutively. maybe doesn't matter.
  
  /**
   * Constructor for building. TODO: remove write access
   */
  public ConnectionCostsWriter(int forwardSize, int backwardSize) {
    this.costs = new short[backwardSize][forwardSize]; 
  }
  
  public void add(int forwardId, int backwardId, int cost) {
    this.costs[backwardId][forwardId] = (short)cost;
  }
  
  public void write(String baseDir) throws IOException {
    String filename = baseDir + File.separator +
      ConnectionCosts.class.getName().replace('.', File.separatorChar) + ConnectionCosts.FILENAME_SUFFIX;
    new File(filename).getParentFile().mkdirs();
    OutputStream os = new FileOutputStream(filename);
    try {
      os = new BufferedOutputStream(os);
      final DataOutput out = new OutputStreamDataOutput(os);
      CodecUtil.writeHeader(out, ConnectionCosts.HEADER, ConnectionCosts.VERSION);
      out.writeVInt(costs.length);
      for (short[] a : costs) {
        out.writeVInt(a.length);
        for (int i = 0; i < a.length; i++) {
          out.writeShort(a[i]);
        }
      }
    } finally {
      os.close();
    }
  }
  
}
