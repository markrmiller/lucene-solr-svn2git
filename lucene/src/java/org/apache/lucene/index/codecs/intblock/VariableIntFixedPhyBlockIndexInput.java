package org.apache.lucene.index.codecs.intblock;

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

import org.apache.lucene.index.BulkPostingsEnum;
import org.apache.lucene.index.codecs.sep.IntIndexInput;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.IndexInput;

// TODO: much of this can be shared code w/ the variable case
// TODO: not specific to simple64, (e.g. can be used by simple9/simple16 at least)

/** Abstract base class that reads variable-size blocks of ints
 *  from an IndexInput that have a fixed physical size in bytes.  
 *  While this is a simple approach, a
 *  more performant approach would directly create an impl
 *  of IntIndexInput inside Directory.  Wrapping a generic
 *  IndexInput will likely cost performance.
 *
 * @lucene.experimental
 */
public abstract class VariableIntFixedPhyBlockIndexInput extends IntIndexInput {

  protected final IndexInput in;
  protected final int maxBlockSize;
  protected final int phyBlockSize;
  protected final static int HEADER = 8; /* 2 ints */
  
  protected VariableIntFixedPhyBlockIndexInput(final IndexInput in) throws IOException {
    this.in = in;
    maxBlockSize = in.readInt();
    phyBlockSize = in.readInt();
  }

  @Override
  public Reader reader() throws IOException {
    final int[] buffer = new int[maxBlockSize];
    final IndexInput clone = (IndexInput) in.clone();
    // TODO: can this be simplified?
    return new Reader(clone, buffer, this.getBlockReader(clone, buffer), phyBlockSize);
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  @Override
  public Index index() {
    return new Index();
  }

  protected abstract BlockReader getBlockReader(IndexInput in, int[] buffer) throws IOException;

  public interface BlockReader {
    public int readBlock() throws IOException;
    // nocommit -- do we really need?
    //public void seek(long pos) throws IOException;
  }

  public static class Reader extends BulkPostingsEnum.BlockReader {
    private final IndexInput in;

    public final int[] pending;

    private int offset;
    private long lastBlockFP;
    //private int blockSize;                        // nocommit redundant w/ limit?
    private final BlockReader blockReader;
    private int limit;
    private final int phyBlockSize;
    
    public Reader(final IndexInput in, final int[] pending, final BlockReader blockReader, final int phyBlockSize)
      throws IOException {
      this.in = in;
      this.pending = pending;
      this.blockReader = blockReader;
      this.phyBlockSize = phyBlockSize;
    }

    void seek(final long fp, final int upto) throws IOException {
      //System.out.println("vintb seek fp=" + fp + " upto=" + upto);
      // TODO: should we do this in real-time, not lazy?
      offset = upto;
      assert offset >= 0: "pendingUpto=" + offset;
      if (fp != lastBlockFP) {
        // Seek to new block
        in.seek(fp);
        // nocommit -- why?
        //blockReader.seek(fp);
        lastBlockFP = fp;
        limit = blockReader.readBlock();
      } else {
        // Seek w/in current block
      }

      // TODO: if we were more clever when writing the
      // index, such that a seek point wouldn't be written
      // until the int encoder "committed", we could avoid
      // this (likely minor) inefficiency:

      // This is necessary for int encoders that are
      // non-causal, ie must see future int values to
      // encode the current ones.
      while(offset >= limit) {
        //System.out.println("NON CAUSAL! offset=" + offset + " limit=" + limit);
        offset -= limit;
        fill();
      }
      //System.out.println("  after skip bock offset=" + offset);
    }

    @Override
    public int[] getBuffer() {
      return pending;
    }

    @Override
    public int end() {
      return limit;
    }

    @Override
    public int offset() {
      return offset;
    }

    @Override
    public int fill() throws IOException {
      lastBlockFP += phyBlockSize;
      return limit = blockReader.readBlock();
    }
  }

  private class Index extends IntIndexInput.Index {
    private long fp;
    private int upto;

    // This is used when reading skip data:
    @Override
    public void read(final DataInput indexIn, final boolean absolute) throws IOException {
      if (absolute) {
        fp = HEADER + (phyBlockSize * indexIn.readVLong());
        upto = indexIn.readByte()&0xFF;
      } else {
        final long delta = indexIn.readVLong();
        if ((delta & 1) == 1) {
          // same block
          upto += (delta >>> 1);
        } else {
          // new block
          fp += (phyBlockSize * (delta >>> 1));
          upto = indexIn.readByte()&0xFF;
        }
      }
      // TODO: we can't do this assert because non-causal
      // int encoders can have upto over the buffer size
      //assert upto < maxBlockSize: "upto=" + upto + " max=" + maxBlockSize;
    }

    @Override
    public String toString() {
      return "VarIntFixedPhyBlock.Index fp=" + fp + " upto=" + upto + " maxBlock=" + maxBlockSize;
    }

    @Override
    public void seek(final BulkPostingsEnum.BlockReader other) throws IOException {
      ((Reader) other).seek(fp, upto);
    }

    @Override
    public void set(final IntIndexInput.Index other) {
      final Index idx = (Index) other;
      fp = idx.fp;
      upto = idx.upto;
    }

    @Override
    public Object clone() {
      Index other = new Index();
      other.fp = fp;
      other.upto = upto;
      return other;
    }
  }
}
