package org.apache.lucene.search.suggest.fst;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.spell.TermFreqIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.fst.FSTCompletion.Completion;
import org.apache.lucene.search.suggest.fst.Sort.SortInfo;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.util.*;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.NoOutputs;

/**
 * An adapter from {@link Lookup} API to {@link FSTCompletion}.
 * 
 * <p>This adapter differs from {@link FSTCompletion} in that it attempts
 * to discretize any "weights" as passed from in {@link TermFreqIterator#freq()}
 * to match the number of buckets. For the rationale for bucketing, see
 * {@link FSTCompletion}.
 * 
 * <p><b>Note:</b>Discretization requires an additional sorting pass.
 * 
 * <p>The range of weights for bucketing/ discretization is determined 
 * by sorting the input by weight and then dividing into
 * equal ranges. Then, scores within each range are assigned to that bucket. 
 * 
 * <p>Note that this means that even large differences in weights may be lost 
 * during automaton construction, but the overall distinction between "classes"
 * of weights will be preserved regardless of the distribution of weights. 
 * 
 * <p>For fine-grained control over which weights are assigned to which buckets,
 * use {@link FSTCompletion} directly or {@link TSTLookup}, for example.
 * 
 * @see FSTCompletion
 */
public class FSTCompletionLookup extends Lookup {
  /** 
   * An invalid bucket count if we're creating an object
   * of this class from an existing FST.
   * 
   * @see #FSTCompletionLookup(FSTCompletion, boolean)
   */
  private static int INVALID_BUCKETS_COUNT = -1;
  
  /**
   * Shared tail length for conflating in the created automaton. Setting this
   * to larger values ({@link Integer#MAX_VALUE}) will create smaller (or minimal) 
   * automata at the cost of RAM for keeping nodes hash in the {@link FST}. 
   *  
   * <p>Empirical pick.
   */
  private final static int sharedTailLength = 5;

  /**
   * File name for the automaton.
   * 
   * @see #store(File)
   * @see #load(File)
   */
  private static final String FILENAME = "fst.bin";

  private int buckets;
  private boolean exactMatchFirst;

  /**
   * Automaton used for completions with higher weights reordering.
   */
  private FSTCompletion higherWeightsCompletion;

  /**
   * Automaton used for normal completions.
   */
  private FSTCompletion normalCompletion;

  /**
   * This constructor prepares for creating a suggested FST using the
   * {@link #build(TermFreqIterator)} method. The number of weight
   * discretization buckets is set to {@link FSTCompletion#DEFAULT_BUCKETS} and
   * exact matches are promoted to the top of the suggestions list.
   */
  public FSTCompletionLookup() {
    this(FSTCompletion.DEFAULT_BUCKETS, true);
  }

  /**
   * This constructor prepares for creating a suggested FST using the
   * {@link #build(TermFreqIterator)} method.
   * 
   * @param buckets
   *          The number of weight discretization buckets (see
   *          {@link FSTCompletion} for details).
   * 
   * @param exactMatchFirst
   *          If <code>true</code> exact matches are promoted to the top of the
   *          suggestions list. Otherwise they appear in the order of
   *          discretized weight and alphabetical within the bucket.
   */
  public FSTCompletionLookup(int buckets, boolean exactMatchFirst) {
    this.buckets = buckets;
    this.exactMatchFirst = exactMatchFirst;
  }

  /**
   * This constructor takes a pre-built automaton.
   * 
   *  @param completion 
   *          An instance of {@link FSTCompletion}.
   *  @param exactMatchFirst
   *          If <code>true</code> exact matches are promoted to the top of the
   *          suggestions list. Otherwise they appear in the order of
   *          discretized weight and alphabetical within the bucket.
   */
  public FSTCompletionLookup(FSTCompletion completion, boolean exactMatchFirst) {
    this(INVALID_BUCKETS_COUNT, exactMatchFirst);
    this.normalCompletion = new FSTCompletion(
        completion.getFST(), false, exactMatchFirst);
    this.higherWeightsCompletion =  new FSTCompletion(
        completion.getFST(), true, exactMatchFirst);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void build(TermFreqIterator tfit) throws IOException {
    File tempInput = File.createTempFile(
        FSTCompletionLookup.class.getSimpleName(), ".input", Sort.defaultTempDir());
    File tempSorted = File.createTempFile(
        FSTCompletionLookup.class.getSimpleName(), ".sorted", Sort.defaultTempDir());

    Sort.ByteSequencesWriter writer = new Sort.ByteSequencesWriter(tempInput);
    Sort.ByteSequencesReader reader = null;

    // Push floats up front before sequences to sort them. For now, assume they are non-negative.
    // If negative floats are allowed some trickery needs to be done to find their byte order.
    boolean success = false;
    try {
      BytesRef tmp1 = new BytesRef();
      byte [] buffer = new byte [0];
      ByteArrayDataOutput output = new ByteArrayDataOutput(buffer);
      while (tfit.hasNext()) {
        String key = tfit.next();
        UnicodeUtil.UTF16toUTF8(key, 0, key.length(), tmp1);

        if (tmp1.length + 4 >= buffer.length) {
          buffer = ArrayUtil.grow(buffer, tmp1.length + 4);
        }

        output.reset(buffer);
        output.writeInt(FloatMagic.toSortable(tfit.freq()));
        output.writeBytes(tmp1.bytes, tmp1.offset, tmp1.length);
        writer.write(buffer, 0, output.getPosition());
      }
      writer.close();

      // We don't know the distribution of scores and we need to bucket them, so we'll sort
      // and divide into equal buckets.
      SortInfo info = new Sort().sort(tempInput, tempSorted);
      tempInput.delete();
      FSTCompletionBuilder builder = new FSTCompletionBuilder(
          buckets, new ExternalRefSorter(new Sort()), sharedTailLength);

      final int inputLines = info.lines;
      reader = new Sort.ByteSequencesReader(tempSorted);
      long line = 0;
      int previousBucket = 0;
      float previousScore = 0;
      ByteArrayDataInput input = new ByteArrayDataInput();
      BytesRef tmp2 = new BytesRef();
      while (reader.read(tmp1)) {
        input.reset(tmp1.bytes);
        float currentScore = FloatMagic.fromSortable(input.readInt());

        int bucket;
        if (line > 0 && currentScore == previousScore) {
          bucket = previousBucket;
        } else {
          bucket = (int) (line * buckets / inputLines);
        }
        previousScore = currentScore;
        previousBucket = bucket;

        // Only append the input, discard the weight.
        tmp2.bytes = tmp1.bytes;
        tmp2.offset = input.getPosition();
        tmp2.length = tmp1.length - input.getPosition();
        builder.add(tmp2, bucket);

        line++;
      }

      // The two FSTCompletions share the same automaton.
      this.higherWeightsCompletion = builder.build();
      this.normalCompletion = new FSTCompletion(
          higherWeightsCompletion.getFST(), false, exactMatchFirst);
      
      success = true;
    } finally {
      if (success) 
        IOUtils.close(reader, writer);
      else 
        IOUtils.closeWhileHandlingException(reader, writer);

      tempInput.delete();
      tempSorted.delete();
    }
  }

  @Override
  public List<LookupResult> lookup(String key, boolean higherWeightsFirst, int num) {
    final List<Completion> completions;
    if (higherWeightsFirst) {
      completions = higherWeightsCompletion.lookup(key, num);
    } else {
      completions = normalCompletion.lookup(key, num);
    }
    
    final ArrayList<LookupResult> results = new ArrayList<LookupResult>(completions.size());
    for (Completion c : completions) {
      results.add(new LookupResult(c.utf8.utf8ToString(), c.bucket));
    }
    return results;
  }

  @Override
  public boolean add(String key, Object value) {
    // Not supported.
    return false;
  }

  @Override
  public Float get(String key) {
    Integer bucket = normalCompletion.getBucket(key);
    if (bucket == null)
      return null;
    else
      return (float) normalCompletion.getBucket(key) / normalCompletion.getBucketCount();
  }

  /**
   * Deserialization from disk.
   */
  @Override
  public synchronized boolean load(File storeDir) throws IOException {
    File data = new File(storeDir, FILENAME);
    if (!data.exists() || !data.canRead()) {
      return false;
    }

    this.higherWeightsCompletion = new FSTCompletion(
        FST.read(data, NoOutputs.getSingleton()));
    this.normalCompletion = new FSTCompletion(
        higherWeightsCompletion.getFST(), false, exactMatchFirst);

    return true;
  }

  /**
   * Serialization to disk.
   */
  @Override
  public synchronized boolean store(File storeDir) throws IOException {
    if (!storeDir.exists() || !storeDir.isDirectory() || !storeDir.canWrite()) {
      return false;
    }

    if (this.normalCompletion == null) 
      return false;

    normalCompletion.getFST().save(new File(storeDir, FILENAME));
    return true;
  }
}
