package org.apache.lucene.codecs.lucene46;

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

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.SegmentInfoWriter;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;

/**
 * Lucene 4.0 implementation of {@link SegmentInfoWriter}.
 * 
 * @see Lucene46SegmentInfoFormat
 * @lucene.experimental
 */
public class Lucene46SegmentInfoWriter extends SegmentInfoWriter {

  /** Sole constructor. */
  public Lucene46SegmentInfoWriter() {
  }

  /** Save a single segment's info. */
  @Override
  public void write(Directory dir, SegmentInfo si, FieldInfos fis, IOContext ioContext) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(si.name, "", Lucene46SegmentInfoFormat.SI_EXTENSION);
    si.addFile(fileName);

    final IndexOutput output = dir.createOutput(fileName, ioContext);

    boolean success = false;
    try {
      CodecUtil.writeHeader(output, Lucene46SegmentInfoFormat.CODEC_NAME, Lucene46SegmentInfoFormat.VERSION_CURRENT);
      Version version = si.getVersion();
      if (version.major < 4) {
        throw new IllegalArgumentException("invalid major version: should be >= 4 but got: " + version.major + " segment=" + si);
      }
      // Write the Lucene version that created this segment, since 3.1
      output.writeString(version.toString());
      output.writeInt(si.getDocCount());

      output.writeByte((byte) (si.getUseCompoundFile() ? SegmentInfo.YES : SegmentInfo.NO));
      output.writeStringStringMap(si.getDiagnostics());
      output.writeStringSet(si.files());
      CodecUtil.writeFooter(output);
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(output);
        // TODO: are we doing this outside of the tracking wrapper? why must SIWriter cleanup like this?
        IOUtils.deleteFilesIgnoringExceptions(si.dir, fileName);
      } else {
        output.close();
      }
    }
  }
}
