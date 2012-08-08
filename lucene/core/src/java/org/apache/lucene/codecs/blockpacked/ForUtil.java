package org.apache.lucene.codecs.blockpacked;
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

import static org.apache.lucene.codecs.blockpacked.BlockPackedPostingsFormat.BLOCK_SIZE;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.packed.PackedInts;
import org.apache.lucene.util.packed.PackedInts.FormatAndBits;

/**
 * Encode all values in normal area with fixed bit width, 
 * which is determined by the max value in this block.
 */
final class ForUtil {

  /**
   * Special number of bits per value used whenever all values to encode are equal.
   */
  private static final int ALL_VALUES_EQUAL = 0;
  private static final int PACKED_INTS_VERSION = 0; // nocommit: encode in the stream?

  /**
   * Minimum length of the buffer that holds encoded bytes.
   */
  static final int MIN_ENCODED_SIZE = BLOCK_SIZE * 4;

  /**
   * Minimum length of the buffer that holds data.
   */
  static final int MIN_DATA_SIZE;
  static {
    int minDataSize = 0;
    for (PackedInts.Format format : PackedInts.Format.values()) {
      for (int bpv = 1; bpv <= 32; ++bpv) {
        if (!format.isSupported(bpv)) {
          continue;
        }
        final PackedInts.Decoder decoder = PackedInts.getDecoder(format, PACKED_INTS_VERSION, bpv);
        final int iterations = (int) Math.ceil((float) BLOCK_SIZE / decoder.valueCount());
        minDataSize = Math.max(minDataSize, iterations * decoder.valueCount());
      }
    }
    MIN_DATA_SIZE = minDataSize;
  }

  private static int computeIterations(PackedInts.Decoder decoder) {
    return (int) Math.ceil((float) BLOCK_SIZE / decoder.valueCount());
  }

  private final PackedInts.FormatAndBits[] formats;
  private final PackedInts.Encoder[] encoders;
  private final PackedInts.Decoder[] decoders;
  private final int[] iterations;

  /**
   * Create a new {@link ForUtil} instance and save state into <code>out</code>.
   */
  ForUtil(float acceptableOverheadRatio, DataOutput out) throws IOException {
    formats = new PackedInts.FormatAndBits[33];
    encoders = new PackedInts.Encoder[33];
    decoders = new PackedInts.Decoder[33];
    iterations = new int[33];

    for (int bpv = 1; bpv <= 32; ++bpv) {
      final FormatAndBits formatAndBits = PackedInts.fastestFormatAndBits(
          BLOCK_SIZE, bpv, acceptableOverheadRatio);
      assert formatAndBits.format.isSupported(formatAndBits.bitsPerValue);
      assert formatAndBits.bitsPerValue <= 32;
      formats[bpv] = formatAndBits;
      encoders[bpv] = PackedInts.getEncoder(
          formatAndBits.format, PACKED_INTS_VERSION, formatAndBits.bitsPerValue);
      decoders[bpv] = PackedInts.getDecoder(
          formatAndBits.format, PACKED_INTS_VERSION, formatAndBits.bitsPerValue);
      iterations[bpv] = computeIterations(decoders[bpv]);

      out.writeVInt(formatAndBits.format.getId() << 5 | (formatAndBits.bitsPerValue - 1));
    }
  }

  /**
   * Restore a {@link ForUtil} from a {@link DataInput}.
   */
  ForUtil(DataInput in) throws IOException {
    formats = new PackedInts.FormatAndBits[33];
    encoders = new PackedInts.Encoder[33];
    decoders = new PackedInts.Decoder[33];
    iterations = new int[33];

    for (int bpv = 1; bpv <= 32; ++bpv) {
      final int code = in.readVInt();
      final int formatId = code >>> 5;
      final int bitsPerValue = (code & 31) + 1;

      final PackedInts.Format format = PackedInts.Format.byId(formatId);
      assert format.isSupported(bitsPerValue);
      formats[bpv] = new PackedInts.FormatAndBits(format, bitsPerValue);
      encoders[bpv] = PackedInts.getEncoder(
          format, PACKED_INTS_VERSION, bitsPerValue);
      decoders[bpv] = PackedInts.getDecoder(
          format, PACKED_INTS_VERSION, bitsPerValue);
      iterations[bpv] = computeIterations(decoders[bpv]);
    }
  }

  /**
   * Compute the minimum size of the buffer that holds values. This method exists
   * because {@link Decoder}s cannot decode less than a given amount of blocks
   * at a time.
   */
  int getMinRequiredBufferSize() {
    int minSize = 0;
    for (int bpv = 1; bpv <= 32; ++bpv) {
      minSize = Math.max(minSize, iterations[bpv] * decoders[bpv].valueCount());
    }
    return minSize;
  }

  /**
   * Write a block of data (<code>For</code> format).
   *
   * @param data     the data to write
   * @param encoded  a buffer to use to encode data
   * @param out      the destination output
   * @throws IOException
   */
  void writeBlock(int[] data, byte[] encoded, IndexOutput out) throws IOException {
    if (isAllEqual(data)) {
      out.writeVInt(ALL_VALUES_EQUAL);
      out.writeInt((int) data[0]);
      return;
    }

    final int numBits = bitsRequired(data);
    assert numBits > 0 && numBits <= 32 : numBits;
    final PackedInts.Encoder encoder = encoders[numBits];
    final int iters = iterations[numBits];
    assert iters * encoder.valueCount() >= BLOCK_SIZE;
    final int encodedSize = encodedSize(numBits);
    assert (iters * encoder.blockCount()) << 3 >= encodedSize;

    out.writeVInt(numBits);

    encoder.encode(data, 0, encoded, 0, iters);
    out.writeBytes(encoded, encodedSize);
  }

  /**
   * Read the next block of data (<code>For</code> format).
   *
   * @param in        the input to use to read data
   * @param encoded   a buffer that can be used to store encoded data
   * @param decoded   where to write decoded data
   * @throws IOException
   */
  void readBlock(IndexInput in, byte[] encoded, int[] decoded) throws IOException {
    final int numBits = in.readVInt();
    assert numBits <= 32 : numBits;

    if (numBits == ALL_VALUES_EQUAL) {
      final int value = in.readInt();
      Arrays.fill(decoded, 0, BLOCK_SIZE, value);
      return;
    }

    final int encodedSize = encodedSize(numBits);
    in.readBytes(encoded, 0, encodedSize);

    final PackedInts.Decoder decoder = decoders[numBits];
    final int iters = iterations[numBits];
    assert iters * decoder.valueCount() >= BLOCK_SIZE;

    decoder.decode(encoded, 0, decoded, 0, iters);
  }

  /**
   * Skip the next block of data.
   *
   * @param in      the input where to read data
   * @throws IOException
   */
  void skipBlock(IndexInput in) throws IOException {
    final int numBits = in.readVInt();
    if (numBits == ALL_VALUES_EQUAL) {
      in.seek(in.getFilePointer() + 4);
      return;
    }
    assert numBits > 0 && numBits <= 32 : numBits;
    final int encodedSize = encodedSize(numBits);
    in.seek(in.getFilePointer() + encodedSize);
  }

  /**
   * Read values that have been written using variable-length encoding instead of bit-packing.
   */
  static void readVIntBlock(IndexInput docIn, int[] docBuffer,
      int[] freqBuffer, int num, boolean indexHasFreq) throws IOException {
    if (indexHasFreq) {
      for(int i=0;i<num;i++) {
        final int code = docIn.readVInt();
        docBuffer[i] = code >>> 1;
        if ((code & 1) != 0) {
          freqBuffer[i] = 1;
        } else {
          freqBuffer[i] = docIn.readVInt();
        }
      }
    } else {
      for(int i=0;i<num;i++) {
        docBuffer[i] = docIn.readVInt();
      }
    }
  }

  // nocommit: we must have a util function for this, hmm?
  private static boolean isAllEqual(final int[] data) {
    final long v = data[0];
    for (int i = 1; i < BLOCK_SIZE; ++i) {
      if (data[i] != v) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compute the number of bits required to serialize any of the longs in
   * <code>data</code>.
   */
  private static int bitsRequired(final int[] data) {
    long or = 0;
    for (int i = 0; i < BLOCK_SIZE; ++i) {
      or |= data[i];
    }
    return PackedInts.bitsRequired(or);
  }

  /**
   * Compute the number of bytes required to encode a block of values that require
   * <code>bitsPerValue</code> bits per value.
   */
  private int encodedSize(int bitsPerValue) {
    final FormatAndBits formatAndBits = formats[bitsPerValue];
    return formatAndBits.format.nblocks(formatAndBits.bitsPerValue, BLOCK_SIZE) << 3;
  }

}
