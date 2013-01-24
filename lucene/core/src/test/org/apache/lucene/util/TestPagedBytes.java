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

package org.apache.lucene.util;

import java.io.IOException;
import java.util.*;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.junit.Ignore;

// nocommit: clean up these tests (eg. not to use IndexINput/Output)
public class TestPagedBytes extends LuceneTestCase {

  /*
  public void testDataInputOutput() throws Exception {
    Random random = random();
    for(int iter=0;iter<5*RANDOM_MULTIPLIER;iter++) {
      final int blockBits = _TestUtil.nextInt(random, 1, 20);
      final int blockSize = 1 << blockBits;
      final PagedBytes p = new PagedBytes(blockBits);
      final DataOutput out = p.getDataOutput();
      final int numBytes = _TestUtil.nextInt(random(), 2, 10000000);

      final byte[] answer = new byte[numBytes];
      random().nextBytes(answer);
      int written = 0;
      while(written < numBytes) {
        if (random().nextInt(10) == 7) {
          out.writeByte(answer[written++]);
        } else {
          int chunk = Math.min(random().nextInt(1000), numBytes - written);
          out.writeBytes(answer, written, chunk);
          written += chunk;
        }
      }

      final PagedBytes.Reader reader = p.freeze(random.nextBoolean());

      final DataInput in = p.getDataInput();

      final byte[] verify = new byte[numBytes];
      int read = 0;
      while(read < numBytes) {
        if (random().nextInt(10) == 7) {
          verify[read++] = in.readByte();
        } else {
          int chunk = Math.min(random().nextInt(1000), numBytes - read);
          in.readBytes(verify, read, chunk);
          read += chunk;
        }
      }
      assertTrue(Arrays.equals(answer, verify));

      final BytesRef slice = new BytesRef();
      for(int iter2=0;iter2<100;iter2++) {
        final int pos = random.nextInt(numBytes-1);
        final int len = random.nextInt(Math.min(blockSize+1, numBytes - pos));
        reader.fillSlice(slice, pos, len);
        for(int byteUpto=0;byteUpto<len;byteUpto++) {
          assertEquals(answer[pos + byteUpto], slice.bytes[slice.offset + byteUpto]);
        }
      }
    }
  }

  @Ignore // memory hole
  public void testOverflow() throws IOException {
    final int blockBits = _TestUtil.nextInt(random(), 14, 28);
    final int blockSize = 1 << blockBits;
    byte[] arr = new byte[_TestUtil.nextInt(random(), blockSize / 2, blockSize * 2)];
    for (int i = 0; i < arr.length; ++i) {
      arr[i] = (byte) i;
    }
    final long numBytes = (1L << 31) + _TestUtil.nextInt(random(), 1, blockSize * 3);
    final PagedBytes p = new PagedBytes(blockBits);
    final PagedBytesDataOutput out = p.getDataOutput();
    for (long i = 0; i < numBytes; ) {
      assertEquals(i, out.getPosition());
      final int len = (int) Math.min(arr.length, numBytes - i);
      out.writeBytes(arr, len);
      i += len;
    }
    assertEquals(numBytes, out.getPosition());
    p.freeze(random().nextBoolean());
    final PagedBytesDataInput in = p.getDataInput();

    for (long offset : new long[] {0L, Integer.MAX_VALUE, numBytes - 1,
        _TestUtil.nextLong(random(), 1, numBytes - 2)}) {
      in.setPosition(offset);
      assertEquals(offset, in.getPosition());
      assertEquals(arr[(int) (offset % arr.length)], in.readByte());
      assertEquals(offset+1, in.getPosition());
    }
  } */
}
