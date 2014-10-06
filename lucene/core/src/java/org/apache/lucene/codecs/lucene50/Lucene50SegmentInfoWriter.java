package org.apache.lucene.codecs.lucene50;

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
import java.util.Set;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.SegmentInfoWriter;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * Lucene 5.0 implementation of {@link SegmentInfoWriter}.
 * 
 * @see Lucene50SegmentInfoFormat
 * @lucene.experimental
 */
public class Lucene50SegmentInfoWriter extends SegmentInfoWriter {

  /** Sole constructor. */
  public Lucene50SegmentInfoWriter() {
  }

  /** Save a single segment's info. */
  @Override
  public void write(Directory dir, SegmentInfo si, FieldInfos fis, IOContext ioContext) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(si.name, "", Lucene50SegmentInfoFormat.SI_EXTENSION);
    si.addFile(fileName);

    boolean success = false;
    try (IndexOutput output = dir.createOutput(fileName, ioContext)) {
      CodecUtil.writeHeader(output, Lucene50SegmentInfoFormat.CODEC_NAME, Lucene50SegmentInfoFormat.VERSION_CURRENT);
      Version version = si.getVersion();
      if (version.major < 5) {
        throw new IllegalArgumentException("invalid major version: should be >= 5 but got: " + version.major + " segment=" + si);
      }
      // Write the Lucene version that created this segment, since 3.1
      output.writeInt(version.major);
      output.writeInt(version.minor);
      output.writeInt(version.bugfix);
      assert version.prerelease == 0;
      output.writeInt(si.getDocCount());

      output.writeByte((byte) (si.getUseCompoundFile() ? SegmentInfo.YES : SegmentInfo.NO));
      output.writeStringStringMap(si.getDiagnostics());
      Set<String> files = si.files();
      for (String file : files) {
        if (!IndexFileNames.parseSegmentName(file).equals(si.name)) {
          throw new IllegalArgumentException("invalid files: expected segment=" + si.name + ", got=" + files);
        }
      }
      output.writeStringSet(files);
      byte[] id = si.getId();
      if (id.length != StringHelper.ID_LENGTH) {
        throw new IllegalArgumentException("invalid id, got=" + StringHelper.idToString(id));
      }
      output.writeBytes(id, 0, id.length);
      CodecUtil.writeFooter(output);
      success = true;
    } finally {
      if (!success) {
        // TODO: are we doing this outside of the tracking wrapper? why must SIWriter cleanup like this?
        IOUtils.deleteFilesIgnoringExceptions(si.dir, fileName);
      }
    }
  }
}
