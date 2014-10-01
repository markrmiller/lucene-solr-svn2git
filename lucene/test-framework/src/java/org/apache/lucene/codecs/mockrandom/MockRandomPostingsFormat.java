package org.apache.lucene.codecs.mockrandom;

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
import java.util.Random;

import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.PostingsReaderBase;
import org.apache.lucene.codecs.PostingsWriterBase;
import org.apache.lucene.codecs.TermStats;
import org.apache.lucene.codecs.blockterms.BlockTermsReader;
import org.apache.lucene.codecs.blockterms.BlockTermsWriter;
import org.apache.lucene.codecs.blockterms.FixedGapTermsIndexReader;
import org.apache.lucene.codecs.blockterms.FixedGapTermsIndexWriter;
import org.apache.lucene.codecs.blockterms.TermsIndexReaderBase;
import org.apache.lucene.codecs.blockterms.TermsIndexWriterBase;
import org.apache.lucene.codecs.blockterms.VariableGapTermsIndexReader;
import org.apache.lucene.codecs.blockterms.VariableGapTermsIndexWriter;
import org.apache.lucene.codecs.blocktree.BlockTreeTermsReader;
import org.apache.lucene.codecs.blocktree.BlockTreeTermsWriter;
import org.apache.lucene.codecs.blocktreeords.OrdsBlockTreeTermsReader;
import org.apache.lucene.codecs.blocktreeords.OrdsBlockTreeTermsWriter;
import org.apache.lucene.codecs.lucene41.Lucene41PostingsReader;
import org.apache.lucene.codecs.lucene41.Lucene41PostingsWriter;
import org.apache.lucene.codecs.memory.FSTOrdTermsReader;
import org.apache.lucene.codecs.memory.FSTOrdTermsWriter;
import org.apache.lucene.codecs.memory.FSTTermsReader;
import org.apache.lucene.codecs.memory.FSTTermsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

/**
 * Randomly combines terms index impl w/ postings impls.
 */

public final class MockRandomPostingsFormat extends PostingsFormat {
  private final Random seedRandom;
  private final String SEED_EXT = "sd";
  
  public MockRandomPostingsFormat() {
    // This ctor should *only* be used at read-time: get NPE if you use it!
    this(null);
  }
  
  public MockRandomPostingsFormat(Random random) {
    super("MockRandom");
    if (random == null) {
      this.seedRandom = new Random(0L) {
        @Override
        protected int next(int arg0) {
          throw new IllegalStateException("Please use MockRandomPostingsFormat(Random)");
        }
      };
    } else {
      this.seedRandom = new Random(random.nextLong());
    }
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    int minSkipInterval;
    if (state.segmentInfo.getDocCount() > 1000000) {
      // Test2BPostings can OOME otherwise:
      minSkipInterval = 3;
    } else {
      minSkipInterval = 2;
    }

    // we pull this before the seed intentionally: because its not consumed at runtime
    // (the skipInterval is written into postings header)
    int skipInterval = TestUtil.nextInt(seedRandom, minSkipInterval, 10);
    
    if (LuceneTestCase.VERBOSE) {
      System.out.println("MockRandomCodec: skipInterval=" + skipInterval);
    }
    
    final long seed = seedRandom.nextLong();

    if (LuceneTestCase.VERBOSE) {
      System.out.println("MockRandomCodec: writing to seg=" + state.segmentInfo.name + " formatID=" + state.segmentSuffix + " seed=" + seed);
    }

    final String seedFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, SEED_EXT);
    final IndexOutput out = state.directory.createOutput(seedFileName, state.context);
    try {
      out.writeLong(seed);
    } finally {
      out.close();
    }

    final Random random = new Random(seed);
    
    random.nextInt(); // consume a random for buffersize

    PostingsWriterBase postingsWriter = new Lucene41PostingsWriter(state, skipInterval);

    final FieldsConsumer fields;
    final int t1 = random.nextInt(5);

    if (t1 == 0) {
      boolean success = false;
      try {
        fields = new FSTTermsWriter(state, postingsWriter);
        success = true;
      } finally {
        if (!success) {
          postingsWriter.close();
        }
      }
    } else if (t1 == 1) {
      boolean success = false;
      try {
        fields = new FSTOrdTermsWriter(state, postingsWriter);
        success = true;
      } finally {
        if (!success) {
          postingsWriter.close();
        }
      }
    } else if (t1 == 2) {
      // Use BlockTree terms dict

      if (LuceneTestCase.VERBOSE) {
        System.out.println("MockRandomCodec: writing BlockTree terms dict");
      }

      // TODO: would be nice to allow 1 but this is very
      // slow to write
      final int minTermsInBlock = TestUtil.nextInt(random, 2, 100);
      final int maxTermsInBlock = Math.max(2, (minTermsInBlock-1)*2 + random.nextInt(100));

      boolean success = false;
      try {
        fields = new BlockTreeTermsWriter(state, postingsWriter, minTermsInBlock, maxTermsInBlock);
        success = true;
      } finally {
        if (!success) {
          postingsWriter.close();
        }
      }
    } else if (t1 == 3) {

      if (LuceneTestCase.VERBOSE) {
        System.out.println("MockRandomCodec: writing Block terms dict");
      }

      boolean success = false;

      final TermsIndexWriterBase indexWriter;
      try {
        if (random.nextBoolean()) {
          int termIndexInterval = TestUtil.nextInt(random, 1, 100);
          if (LuceneTestCase.VERBOSE) {
            System.out.println("MockRandomCodec: fixed-gap terms index (tii=" + termIndexInterval + ")");
          }
          indexWriter = new FixedGapTermsIndexWriter(state, termIndexInterval);
        } else {
          final VariableGapTermsIndexWriter.IndexTermSelector selector;
          final int n2 = random.nextInt(3);
          if (n2 == 0) {
            final int tii = TestUtil.nextInt(random, 1, 100);
            selector = new VariableGapTermsIndexWriter.EveryNTermSelector(tii);
           if (LuceneTestCase.VERBOSE) {
              System.out.println("MockRandomCodec: variable-gap terms index (tii=" + tii + ")");
            }
          } else if (n2 == 1) {
            final int docFreqThresh = TestUtil.nextInt(random, 2, 100);
            final int tii = TestUtil.nextInt(random, 1, 100);
            selector = new VariableGapTermsIndexWriter.EveryNOrDocFreqTermSelector(docFreqThresh, tii);
          } else {
            final long seed2 = random.nextLong();
            final int gap = TestUtil.nextInt(random, 2, 40);
            if (LuceneTestCase.VERBOSE) {
             System.out.println("MockRandomCodec: random-gap terms index (max gap=" + gap + ")");
            }
           selector = new VariableGapTermsIndexWriter.IndexTermSelector() {
                final Random rand = new Random(seed2);

                @Override
                public boolean isIndexTerm(BytesRef term, TermStats stats) {
                  return rand.nextInt(gap) == gap/2;
                }

                @Override
                  public void newField(FieldInfo fieldInfo) {
                }
              };
          }
          indexWriter = new VariableGapTermsIndexWriter(state, selector);
        }
        success = true;
      } finally {
        if (!success) {
          postingsWriter.close();
        }
      }

      success = false;
      try {
        fields = new BlockTermsWriter(indexWriter, state, postingsWriter);
        success = true;
      } finally {
        if (!success) {
          try {
            postingsWriter.close();
          } finally {
            indexWriter.close();
          }
        }
      }
    } else if (t1 == 4) {
      // Use OrdsBlockTree terms dict
      if (LuceneTestCase.VERBOSE) {
        System.out.println("MockRandomCodec: writing OrdsBlockTree");
      }

      // TODO: would be nice to allow 1 but this is very
      // slow to write
      final int minTermsInBlock = TestUtil.nextInt(random, 2, 100);
      final int maxTermsInBlock = Math.max(2, (minTermsInBlock-1)*2 + random.nextInt(100));

      boolean success = false;
      try {
        fields = new OrdsBlockTreeTermsWriter(state, postingsWriter, minTermsInBlock, maxTermsInBlock);
        success = true;
      } finally {
        if (!success) {
          postingsWriter.close();
        }
      }
      
    } else {
      // BUG!
      throw new AssertionError();
    }

    return fields;
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {

    final String seedFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, SEED_EXT);
    final IndexInput in = state.directory.openInput(seedFileName, state.context);
    final long seed = in.readLong();
    if (LuceneTestCase.VERBOSE) {
      System.out.println("MockRandomCodec: reading from seg=" + state.segmentInfo.name + " formatID=" + state.segmentSuffix + " seed=" + seed);
    }
    in.close();

    final Random random = new Random(seed);
    
    int readBufferSize = TestUtil.nextInt(random, 1, 4096);
    if (LuceneTestCase.VERBOSE) {
      System.out.println("MockRandomCodec: readBufferSize=" + readBufferSize);
    }

    PostingsReaderBase postingsReader = new Lucene41PostingsReader(state.directory, state.fieldInfos, state.segmentInfo, state.context, state.segmentSuffix);

    final FieldsProducer fields;
    final int t1 = random.nextInt(5);
    if (t1 == 0) {
      boolean success = false;
      try {
        fields = new FSTTermsReader(state, postingsReader);
        success = true;
      } finally {
        if (!success) {
          postingsReader.close();
        }
      }
    } else if (t1 == 1) {
      boolean success = false;
      try {
        fields = new FSTOrdTermsReader(state, postingsReader);
        success = true;
      } finally {
        if (!success) {
          postingsReader.close();
        }
      }
    } else if (t1 == 2) {
      // Use BlockTree terms dict
      if (LuceneTestCase.VERBOSE) {
        System.out.println("MockRandomCodec: reading BlockTree terms dict");
      }

      boolean success = false;
      try {
        fields = new BlockTreeTermsReader(state.directory,
                                          state.fieldInfos,
                                          state.segmentInfo,
                                          postingsReader,
                                          state.context,
                                          state.segmentSuffix);
        success = true;
      } finally {
        if (!success) {
          postingsReader.close();
        }
      }
    } else if (t1 == 3) {

      if (LuceneTestCase.VERBOSE) {
        System.out.println("MockRandomCodec: reading Block terms dict");
      }
      final TermsIndexReaderBase indexReader;
      boolean success = false;
      try {
        final boolean doFixedGap = random.nextBoolean();

        // randomness diverges from writer, here:

        if (doFixedGap) {
          if (LuceneTestCase.VERBOSE) {
            System.out.println("MockRandomCodec: fixed-gap terms index");
          }
          indexReader = new FixedGapTermsIndexReader(state);
        } else {
          final int n2 = random.nextInt(3);
          if (n2 == 1) {
            random.nextInt();
          } else if (n2 == 2) {
            random.nextLong();
          }
          if (LuceneTestCase.VERBOSE) {
            System.out.println("MockRandomCodec: variable-gap terms index");
          }
          indexReader = new VariableGapTermsIndexReader(state.directory,
                                                        state.fieldInfos,
                                                        state.segmentInfo.name,
                                                        state.segmentSuffix, state.context);

        }

        success = true;
      } finally {
        if (!success) {
          postingsReader.close();
        }
      }

      success = false;
      try {
        fields = new BlockTermsReader(indexReader,
                                      state.directory,
                                      state.fieldInfos,
                                      state.segmentInfo,
                                      postingsReader,
                                      state.context,
                                      state.segmentSuffix);
        success = true;
      } finally {
        if (!success) {
          try {
            postingsReader.close();
          } finally {
            indexReader.close();
          }
        }
      }
    } else if (t1 == 4) {
      // Use OrdsBlockTree terms dict
      if (LuceneTestCase.VERBOSE) {
        System.out.println("MockRandomCodec: reading OrdsBlockTree terms dict");
      }

      boolean success = false;
      try {
        fields = new OrdsBlockTreeTermsReader(state.directory,
                                              state.fieldInfos,
                                              state.segmentInfo,
                                              postingsReader,
                                              state.context,
                                              state.segmentSuffix);
        success = true;
      } finally {
        if (!success) {
          postingsReader.close();
        }
      }
    } else {
      // BUG!
      throw new AssertionError();
    }

    return fields;
  }
}
