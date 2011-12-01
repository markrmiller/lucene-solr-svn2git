package org.apache.lucene.index.codecs;

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
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ChecksumIndexOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FlushInfo;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.IOUtils;

/**
 * Default implementation of {@link SegmentInfosWriter}.
 * @lucene.experimental
 */
public class DefaultSegmentInfosWriter extends SegmentInfosWriter {

  /** This format adds optional per-segment String
   *  diagnostics storage, and switches userData to Map */
  public static final int FORMAT_DIAGNOSTICS = -9;

  /** Each segment records whether it has term vectors */
  public static final int FORMAT_HAS_VECTORS = -10;

  /** Each segment records the Lucene version that created it. */
  public static final int FORMAT_3_1 = -11;

  /** Each segment records whether its postings are written
   *  in the new flex format */
  public static final int FORMAT_4_0 = -12;

  /** This must always point to the most recent file format.
   * whenever you add a new format, make it 1 smaller (negative version logic)! */
  // TODO: move this, as its currently part of required preamble
  public static final int FORMAT_CURRENT = FORMAT_4_0;
  
  /** This must always point to the first supported file format. */
  public static final int FORMAT_MINIMUM = FORMAT_DIAGNOSTICS;

  @Override
  public IndexOutput writeInfos(Directory dir, String segmentFileName, String codecID, SegmentInfos infos, IOContext context)
          throws IOException {
    IndexOutput out = createOutput(dir, segmentFileName, new IOContext(new FlushInfo(infos.size(), infos.totalDocCount())));
    boolean success = false;
    try {
      out.writeInt(FORMAT_CURRENT); // write FORMAT
      out.writeString(codecID); // write codecID
      out.writeLong(infos.version);
      out.writeInt(infos.counter); // write counter
      out.writeInt(infos.size()); // write infos
      for (SegmentInfo si : infos) {
        writeInfo(out, si);
      }
      out.writeStringStringMap(infos.getUserData());
      success = true;
      return out;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(out);
      }
    }
  }
  
  /** Save a single segment's info. */
  private void writeInfo(IndexOutput output, SegmentInfo si) throws IOException {
    assert si.getDelCount() <= si.docCount: "delCount=" + si.getDelCount() + " docCount=" + si.docCount + " segment=" + si.name;
    // Write the Lucene version that created this segment, since 3.1
    output.writeString(si.getVersion());
    output.writeString(si.name);
    output.writeInt(si.docCount);
    output.writeLong(si.getDelGen());

    output.writeInt(si.getDocStoreOffset());
    if (si.getDocStoreOffset() != -1) {
      output.writeString(si.getDocStoreSegment());
      output.writeByte((byte) (si.getDocStoreIsCompoundFile() ? 1:0));
    }

    Map<Integer,Long> normGen = si.getNormGen();
    if (normGen == null) {
      output.writeInt(SegmentInfo.NO);
    } else {
      output.writeInt(normGen.size());
      for (Entry<Integer,Long> entry : normGen.entrySet()) {
        output.writeInt(entry.getKey());
        output.writeLong(entry.getValue());
      }
    }

    output.writeByte((byte) (si.getUseCompoundFile() ? SegmentInfo.YES : SegmentInfo.NO));
    output.writeInt(si.getDelCount());
    output.writeByte((byte) (si.getHasProxInternal()));
    output.writeString(si.getCodec().getName());
    output.writeStringStringMap(si.getDiagnostics());
    output.writeByte((byte) (si.getHasVectorsInternal()));
  }
  
  protected IndexOutput createOutput(Directory dir, String segmentFileName, IOContext context)
      throws IOException {
    IndexOutput plainOut = dir.createOutput(segmentFileName, context);
    ChecksumIndexOutput out = new ChecksumIndexOutput(plainOut);
    return out;
  }

  @Override
  public void prepareCommit(IndexOutput segmentOutput) throws IOException {
    ((ChecksumIndexOutput)segmentOutput).prepareCommit();
  }

  @Override
  public void finishCommit(IndexOutput out) throws IOException {
    ((ChecksumIndexOutput)out).finishCommit();
    out.close();
  }
}
