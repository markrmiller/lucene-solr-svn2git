package org.apache.lucene.index.codecs.pfordelta;

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

import java.util.Set;
import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.index.codecs.FieldsConsumer;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.codecs.sep.SepPostingsWriterImpl;
import org.apache.lucene.index.codecs.sep.SepPostingsReaderImpl;
import org.apache.lucene.index.codecs.standard.StandardCodec;
import org.apache.lucene.index.codecs.PrefixCodedTermsWriter;
import org.apache.lucene.index.codecs.PrefixCodedTermsReader;
import org.apache.lucene.index.codecs.TermsIndexWriterBase;
import org.apache.lucene.index.codecs.TermsIndexReaderBase;
import org.apache.lucene.index.codecs.PostingsReaderBase;
import org.apache.lucene.index.codecs.PostingsWriterBase;
import org.apache.lucene.index.codecs.FixedGapTermsIndexWriter;
import org.apache.lucene.index.codecs.FixedGapTermsIndexReader;

public class PatchedFrameOfRefCodec extends Codec {

  public PatchedFrameOfRefCodec() {
    name = "PatchedFrameOfRef";
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    PostingsWriterBase postingsWriter = new SepPostingsWriterImpl(state, new PForDeltaFactory(128));

    boolean success = false;
    TermsIndexWriterBase indexWriter;
    try {
      indexWriter = new FixedGapTermsIndexWriter(state);
      success = true;
    } finally {
      if (!success) {
        postingsWriter.close();
      }
    }

    success = false;
    try {
      FieldsConsumer ret = new PrefixCodedTermsWriter(indexWriter, state, postingsWriter, BytesRef.getUTF8SortedAsUnicodeComparator());
      success = true;
      return ret;
    } finally {
      if (!success) {
        try {
          postingsWriter.close();
        } finally {
          indexWriter.close();
        }
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    PostingsReaderBase postingsReader = new SepPostingsReaderImpl(state.dir,
                                                                  state.segmentInfo,
                                                                  state.readBufferSize,
                                                                  new PForDeltaFactory(128),
                                                                  state.codecId);

    TermsIndexReaderBase indexReader;
    boolean success = false;
    try {
      indexReader = new FixedGapTermsIndexReader(state.dir,
                                                 state.fieldInfos,
                                                 state.segmentInfo.name,
                                                 state.termsIndexDivisor,
                                                 BytesRef.getUTF8SortedAsUnicodeComparator(),
                                                 state.codecId);
      success = true;
    } finally {
      if (!success) {
        postingsReader.close();
      }
    }

    success = false;
    try {
      FieldsProducer ret = new PrefixCodedTermsReader(indexReader,
                                                      state.dir,
                                                      state.fieldInfos,
                                                      state.segmentInfo.name,
                                                      postingsReader,
                                                      state.readBufferSize,
                                                      BytesRef.getUTF8SortedAsUnicodeComparator(),
                                                      StandardCodec.TERMS_CACHE_SIZE,
                                                      state.codecId);
      success = true;
      return ret;
    } finally {
      if (!success) {
        try {
          postingsReader.close();
        } finally {
          indexReader.close();
        }
      }
    }
  }

  @Override
  public void files(Directory dir, SegmentInfo segmentInfo, String id, Set<String> files) {
    SepPostingsReaderImpl.files(segmentInfo, id, files);
    PrefixCodedTermsReader.files(dir, segmentInfo, id, files);
    FixedGapTermsIndexReader.files(dir, segmentInfo, id, files);
  }

  @Override
  public void getExtensions(Set<String> extensions) {
    SepPostingsWriterImpl.getExtensions(extensions);
    PrefixCodedTermsReader.getExtensions(extensions);
    FixedGapTermsIndexReader.getIndexExtensions(extensions);
  }
}
