package org.apache.lucene.codecs;

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
import java.util.Arrays;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.store.BufferedChecksumIndexInput;
import org.apache.lucene.store.ChecksumIndexInput;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.StringHelper;

/**
 * Utility class for reading and writing versioned headers.
 * <p>
 * Writing codec headers is useful to ensure that a file is in 
 * the format you think it is.
 * 
 * @lucene.experimental
 */

public final class CodecUtil {
  private CodecUtil() {} // no instance

  /**
   * Constant to identify the start of a codec header.
   */
  public final static int CODEC_MAGIC = 0x3fd76c17;
  /**
   * Constant to identify the start of a codec footer.
   */
  public final static int FOOTER_MAGIC = ~CODEC_MAGIC;

  /**
   * Writes a codec header, which records both a string to
   * identify the file and a version number. This header can
   * be parsed and validated with 
   * {@link #checkHeader(DataInput, String, int, int) checkHeader()}.
   * <p>
   * CodecHeader --&gt; Magic,CodecName,Version
   * <ul>
   *    <li>Magic --&gt; {@link DataOutput#writeInt Uint32}. This
   *        identifies the start of the header. It is always {@value #CODEC_MAGIC}.
   *    <li>CodecName --&gt; {@link DataOutput#writeString String}. This
   *        is a string to identify this file.
   *    <li>Version --&gt; {@link DataOutput#writeInt Uint32}. Records
   *        the version of the file.
   * </ul>
   * <p>
   * Note that the length of a codec header depends only upon the
   * name of the codec, so this length can be computed at any time
   * with {@link #headerLength(String)}.
   * 
   * @param out Output stream
   * @param codec String to identify this file. It should be simple ASCII, 
   *              less than 128 characters in length.
   * @param version Version number
   * @throws IOException If there is an I/O error writing to the underlying medium.
   * @throws IllegalArgumentException If the codec name is not simple ASCII, or is more than 127 characters in length
   */
  public static void writeHeader(DataOutput out, String codec, int version) throws IOException {
    BytesRef bytes = new BytesRef(codec);
    if (bytes.length != codec.length() || bytes.length >= 128) {
      throw new IllegalArgumentException("codec must be simple ASCII, less than 128 characters in length [got " + codec + "]");
    }
    out.writeInt(CODEC_MAGIC);
    out.writeString(codec);
    out.writeInt(version);
  }
  
  /**
   * Writes a codec header for a per-segment, which records both a string to
   * identify the file, a version number, and the unique ID of the segment. 
   * This header can be parsed and validated with 
   * {@link #checkSegmentHeader(DataInput, String, int, int, byte[]) checkSegmentHeader()}.
   * <p>
   * CodecSegmentHeader --&gt; CodecHeader,SegmentID
   * <ul>
   *    <li>CodecHeader --&gt; {@link #writeHeader}
   *    <li>SegmentID   --&gt; {@link DataOutput#writeByte byte}<sup>16</sup>.
   *        Unique identifier for the segment.
   * </ul>
   * <p>
   * Note that the length of a segment header depends only upon the
   * name of the codec, so this length can be computed at any time
   * with {@link #headerLength(String)}.
   * 
   * @param out Output stream
   * @param codec String to identify this file. It should be simple ASCII, 
   *              less than 128 characters in length.
   * @param segmentID Unique identifier for the segment
   * @param version Version number
   * @throws IOException If there is an I/O error writing to the underlying medium.
   * @throws IllegalArgumentException If the codec name is not simple ASCII, or 
   *         is more than 127 characters in length, or if segmentID is invalid.
   */
  public static void writeSegmentHeader(DataOutput out, String codec, int version, byte[] segmentID) throws IOException {
    if (segmentID.length != StringHelper.ID_LENGTH) {
      throw new IllegalArgumentException("Invalid id: " + StringHelper.idToString(segmentID));
    }
    writeHeader(out, codec, version);
    out.writeBytes(segmentID, 0, segmentID.length);
  }

  /**
   * Computes the length of a codec header.
   * 
   * @param codec Codec name.
   * @return length of the entire codec header.
   * @see #writeHeader(DataOutput, String, int)
   */
  public static int headerLength(String codec) {
    return 9+codec.length();
  }
  
  /**
   * Computes the length of a segment header.
   * 
   * @param codec Codec name.
   * @return length of the entire segment header.
   * @see #writeSegmentHeader(DataOutput, String, int, byte[])
   */
  public static int segmentHeaderLength(String codec) {
    return headerLength(codec) + StringHelper.ID_LENGTH;
  }

  /**
   * Reads and validates a header previously written with 
   * {@link #writeHeader(DataOutput, String, int)}.
   * <p>
   * When reading a file, supply the expected <code>codec</code> and
   * an expected version range (<code>minVersion to maxVersion</code>).
   * 
   * @param in Input stream, positioned at the point where the
   *        header was previously written. Typically this is located
   *        at the beginning of the file.
   * @param codec The expected codec name.
   * @param minVersion The minimum supported expected version number.
   * @param maxVersion The maximum supported expected version number.
   * @return The actual version found, when a valid header is found 
   *         that matches <code>codec</code>, with an actual version 
   *         where <code>minVersion <= actual <= maxVersion</code>.
   *         Otherwise an exception is thrown.
   * @throws CorruptIndexException If the first four bytes are not
   *         {@link #CODEC_MAGIC}, or if the actual codec found is
   *         not <code>codec</code>.
   * @throws IndexFormatTooOldException If the actual version is less 
   *         than <code>minVersion</code>.
   * @throws IndexFormatTooNewException If the actual version is greater 
   *         than <code>maxVersion</code>.
   * @throws IOException If there is an I/O error reading from the underlying medium.
   * @see #writeHeader(DataOutput, String, int)
   */
  public static int checkHeader(DataInput in, String codec, int minVersion, int maxVersion) throws IOException {
    // Safety to guard against reading a bogus string:
    final int actualHeader = in.readInt();
    if (actualHeader != CODEC_MAGIC) {
      throw new CorruptIndexException("codec header mismatch: actual header=" + actualHeader + " vs expected header=" + CODEC_MAGIC, in);
    }
    return checkHeaderNoMagic(in, codec, minVersion, maxVersion);
  }

  /** Like {@link
   *  #checkHeader(DataInput,String,int,int)} except this
   *  version assumes the first int has already been read
   *  and validated from the input. */
  public static int checkHeaderNoMagic(DataInput in, String codec, int minVersion, int maxVersion) throws IOException {
    final String actualCodec = in.readString();
    if (!actualCodec.equals(codec)) {
      throw new CorruptIndexException("codec mismatch: actual codec=" + actualCodec + " vs expected codec=" + codec, in);
    }

    final int actualVersion = in.readInt();
    if (actualVersion < minVersion) {
      throw new IndexFormatTooOldException(in, actualVersion, minVersion, maxVersion);
    }
    if (actualVersion > maxVersion) {
      throw new IndexFormatTooNewException(in, actualVersion, minVersion, maxVersion);
    }

    return actualVersion;
  }
  
  /**
   * Reads and validates a header previously written with 
   * {@link #writeSegmentHeader(DataOutput, String, int, byte[])}.
   * <p>
   * When reading a file, supply the expected <code>codec</code>,
   * expected version range (<code>minVersion to maxVersion</code>),
   * and segment ID.
   * 
   * @param in Input stream, positioned at the point where the
   *        header was previously written. Typically this is located
   *        at the beginning of the file.
   * @param codec The expected codec name.
   * @param minVersion The minimum supported expected version number.
   * @param maxVersion The maximum supported expected version number.
   * @param segmentID The expected segment this file belongs to.
   * @return The actual version found, when a valid header is found 
   *         that matches <code>codec</code>, with an actual version 
   *         where <code>minVersion <= actual <= maxVersion</code>, 
   *         and matching <code>segmentID</code>
   *         Otherwise an exception is thrown.
   * @throws CorruptIndexException If the first four bytes are not
   *         {@link #CODEC_MAGIC}, or if the actual codec found is
   *         not <code>codec</code>, or if the <code>segmentID</code>
   *         does not match.
   * @throws IndexFormatTooOldException If the actual version is less 
   *         than <code>minVersion</code>.
   * @throws IndexFormatTooNewException If the actual version is greater 
   *         than <code>maxVersion</code>.
   * @throws IOException If there is an I/O error reading from the underlying medium.
   * @see #writeSegmentHeader(DataOutput, String, int, byte[])
   */
  public static int checkSegmentHeader(DataInput in, String codec, int minVersion, int maxVersion, byte[] segmentID) throws IOException {
    int version = checkHeader(in, codec, minVersion, maxVersion);
    byte id[] = new byte[StringHelper.ID_LENGTH];
    in.readBytes(id, 0, id.length);
    if (!Arrays.equals(id, segmentID)) {
      throw new CorruptIndexException("file mismatch, expected segment id=" + StringHelper.idToString(segmentID) 
                                                                 + ", got=" + StringHelper.idToString(id), in);
    }
    return version;
  }
  
  /**
   * Writes a codec footer, which records both a checksum
   * algorithm ID and a checksum. This footer can
   * be parsed and validated with 
   * {@link #checkFooter(ChecksumIndexInput) checkFooter()}.
   * <p>
   * CodecFooter --&gt; Magic,AlgorithmID,Checksum
   * <ul>
   *    <li>Magic --&gt; {@link DataOutput#writeInt Uint32}. This
   *        identifies the start of the footer. It is always {@value #FOOTER_MAGIC}.
   *    <li>AlgorithmID --&gt; {@link DataOutput#writeInt Uint32}. This
   *        indicates the checksum algorithm used. Currently this is always 0,
   *        for zlib-crc32.
   *    <li>Checksum --&gt; {@link DataOutput#writeLong Uint64}. The
   *        actual checksum value for all previous bytes in the stream, including
   *        the bytes from Magic and AlgorithmID.
   * </ul>
   * 
   * @param out Output stream
   * @throws IOException If there is an I/O error writing to the underlying medium.
   */
  public static void writeFooter(IndexOutput out) throws IOException {
    out.writeInt(FOOTER_MAGIC);
    out.writeInt(0);
    out.writeLong(out.getChecksum());
  }
  
  /**
   * Computes the length of a codec footer.
   * 
   * @return length of the entire codec footer.
   * @see #writeFooter(IndexOutput)
   */
  public static int footerLength() {
    return 16;
  }
  
  /** 
   * Validates the codec footer previously written by {@link #writeFooter}. 
   * @return actual checksum value
   * @throws IOException if the footer is invalid, if the checksum does not match, 
   *                     or if {@code in} is not properly positioned before the footer
   *                     at the end of the stream.
   */
  public static long checkFooter(ChecksumIndexInput in) throws IOException {
    validateFooter(in);
    long actualChecksum = in.getChecksum();
    long expectedChecksum = in.readLong();
    if (expectedChecksum != actualChecksum) {
      throw new CorruptIndexException("checksum failed (hardware problem?) : expected=" + Long.toHexString(expectedChecksum) +  
                                                       " actual=" + Long.toHexString(actualChecksum), in);
    }
    return actualChecksum;
  }
  
  /** 
   * Validates the codec footer previously written by {@link #writeFooter}, optionally
   * passing an unexpected exception that has already occurred.
   * <p>
   * When a {@code priorException} is provided, this method will add a suppressed exception 
   * indicating whether the checksum for the stream passes, fails, or cannot be computed, and 
   * rethrow it. Otherwise it behaves the same as {@link #checkFooter(ChecksumIndexInput)}.
   * <p>
   * Example usage:
   * <pre class="prettyprint">
   * try (ChecksumIndexInput input = ...) {
   *   Throwable priorE = null;
   *   try {
   *     // ... read a bunch of stuff ... 
   *   } catch (Throwable exception) {
   *     priorE = exception;
   *   } finally {
   *     CodecUtil.checkFooter(input, priorE);
   *   }
   * }
   * </pre>
   */
  public static void checkFooter(ChecksumIndexInput in, Throwable priorException) throws IOException {
    if (priorException == null) {
      checkFooter(in);
    } else {
      try {
        long remaining = in.length() - in.getFilePointer();
        if (remaining < footerLength()) {
          // corruption caused us to read into the checksum footer already: we can't proceed
          priorException.addSuppressed(new CorruptIndexException("checksum status indeterminate: remaining=" + remaining +
                                                                 ", please run checkindex for more details", in));
        } else {
          // otherwise, skip any unread bytes.
          in.skipBytes(remaining - footerLength());
          
          // now check the footer
          try {
            long checksum = checkFooter(in);
            priorException.addSuppressed(new CorruptIndexException("checksum passed (" + Long.toHexString(checksum) + 
                                                                   "). possibly transient resource issue, or a Lucene or JVM bug", in));
          } catch (CorruptIndexException t) {
            priorException.addSuppressed(t);
          }
        }
      } catch (Throwable t) {
        // catch-all for things that shouldn't go wrong (e.g. OOM during readInt) but could...
        priorException.addSuppressed(new CorruptIndexException("checksum status indeterminate: unexpected exception", in, t));
      }
      IOUtils.reThrow(priorException);
    }
  }
  
  /** 
   * Returns (but does not validate) the checksum previously written by {@link #checkFooter}.
   * @return actual checksum value
   * @throws IOException if the footer is invalid
   */
  public static long retrieveChecksum(IndexInput in) throws IOException {
    in.seek(in.length() - footerLength());
    validateFooter(in);
    return in.readLong();
  }
  
  private static void validateFooter(IndexInput in) throws IOException {
    long remaining = in.length() - in.getFilePointer();
    long expected = footerLength();
    if (remaining < expected) {
      throw new CorruptIndexException("misplaced codec footer (file truncated?): remaining=" + remaining + ", expected=" + expected, in);
    } else if (remaining > expected) {
      throw new CorruptIndexException("misplaced codec footer (file extended?): remaining=" + remaining + ", expected=" + expected, in);
    }
    
    final int magic = in.readInt();
    if (magic != FOOTER_MAGIC) {
      throw new CorruptIndexException("codec footer mismatch: actual footer=" + magic + " vs expected footer=" + FOOTER_MAGIC, in);
    }
    
    final int algorithmID = in.readInt();
    if (algorithmID != 0) {
      throw new CorruptIndexException("codec footer mismatch: unknown algorithmID: " + algorithmID, in);
    }
  }
  
  /**
   * Checks that the stream is positioned at the end, and throws exception
   * if it is not. 
   * @deprecated Use {@link #checkFooter} instead, this should only used for files without checksums 
   */
  @Deprecated
  public static void checkEOF(IndexInput in) throws IOException {
    if (in.getFilePointer() != in.length()) {
      throw new CorruptIndexException("did not read all bytes from file: read " + in.getFilePointer() + " vs size " + in.length(), in);
    }
  }
  
  /** 
   * Clones the provided input, reads all bytes from the file, and calls {@link #checkFooter} 
   * <p>
   * Note that this method may be slow, as it must process the entire file.
   * If you just need to extract the checksum value, call {@link #retrieveChecksum}.
   */
  public static long checksumEntireFile(IndexInput input) throws IOException {
    IndexInput clone = input.clone();
    clone.seek(0);
    ChecksumIndexInput in = new BufferedChecksumIndexInput(clone);
    assert in.getFilePointer() == 0;
    in.seek(in.length() - footerLength());
    return checkFooter(in);
  }
}
