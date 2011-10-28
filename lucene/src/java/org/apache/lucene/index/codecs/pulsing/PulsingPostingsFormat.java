package org.apache.lucene.index.codecs.pulsing;

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
import java.util.Set;

import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.codecs.PostingsReaderBase;
import org.apache.lucene.index.codecs.PostingsWriterBase;
import org.apache.lucene.index.codecs.BlockTreeTermsReader;
import org.apache.lucene.index.codecs.BlockTreeTermsWriter;
import org.apache.lucene.index.codecs.PostingsFormat;
import org.apache.lucene.index.codecs.FieldsConsumer;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsFormat;
import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsReader;
import org.apache.lucene.index.codecs.lucene40.Lucene40PostingsWriter;
import org.apache.lucene.store.Directory;

/** This postings format "inlines" the postings for terms that have
 *  low docFreq.  It wraps another postings format, which is used for
 *  writing the non-inlined terms.
 *
 *  Currently in only inlines docFreq=1 terms, and
 *  otherwise uses the normal "Lucene40" format. 
 *  @lucene.experimental */

public class PulsingPostingsFormat extends PostingsFormat {

  private final int freqCutoff;
  private final int minBlockSize;
  private final int maxBlockSize;

  public PulsingPostingsFormat() {
    this(1);
  }
  
  public PulsingPostingsFormat(int freqCutoff) {
    this(freqCutoff, BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE, BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE);
  }

  /** Terms with freq <= freqCutoff are inlined into terms
   *  dict. */
  public PulsingPostingsFormat(int freqCutoff, int minBlockSize, int maxBlockSize) {
    super("Pulsing");
    this.freqCutoff = freqCutoff;
    this.minBlockSize = minBlockSize;
    assert minBlockSize > 1;
    this.maxBlockSize = maxBlockSize;
  }

  @Override
  public String toString() {
    return name + "(freqCutoff=" + freqCutoff + " minBlockSize=" + minBlockSize + " maxBlockSize=" + maxBlockSize + ")";
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    // We wrap StandardPostingsWriter, but any PostingsWriterBase
    // will work:

    PostingsWriterBase docsWriter = new Lucene40PostingsWriter(state);

    // Terms that have <= freqCutoff number of docs are
    // "pulsed" (inlined):
    PostingsWriterBase pulsingWriter = new PulsingPostingsWriter(freqCutoff, docsWriter);

    // Terms dict
    boolean success = false;
    try {
      FieldsConsumer ret = new BlockTreeTermsWriter(state, pulsingWriter, minBlockSize, maxBlockSize);
      success = true;
      return ret;
    } finally {
      if (!success) {
        pulsingWriter.close();
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {

    // We wrap StandardPostingsReader, but any StandardPostingsReader
    // will work:
    PostingsReaderBase docsReader = new Lucene40PostingsReader(state.dir, state.segmentInfo, state.context, state.formatId);
    PostingsReaderBase pulsingReader = new PulsingPostingsReader(docsReader);

    boolean success = false;
    try {
      FieldsProducer ret = new BlockTreeTermsReader(
                                                    state.dir, state.fieldInfos, state.segmentInfo.name,
                                                    pulsingReader,
                                                    state.context,
                                                    state.formatId,
                                                    state.termsIndexDivisor);
      success = true;
      return ret;
    } finally {
      if (!success) {
        pulsingReader.close();
      }
    }
  }

  public int getFreqCutoff() {
    return freqCutoff;
  }

  @Override
  public void files(Directory dir, SegmentInfo segmentInfo, int codecID, Set<String> files) throws IOException {
    Lucene40PostingsReader.files(dir, segmentInfo, codecID, files);
    BlockTreeTermsReader.files(dir, segmentInfo, codecID, files);
  }
}
