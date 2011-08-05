package org.apache.lucene.search.similarities;

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

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;
import org.apache.lucene.util.TermContext;

/**
 * A subclass of {@code Similarity} that provides a simplified API for its
 * descendants.
 * @lucene.experimental
 */
public abstract class EasySimilarity extends Similarity {
  /** For {@link #log2(double)}. Precomputed for efficiency reasons. */
  private static final double LOG_2 = Math.log(2);
  
  /**
   * Calls {@link #fillEasyStats(EasyStats, IndexSearcher, String, TermContext...)}.
   * Subclasses that override this method may invoke {@code fillStats} with any
   * subclass of {@code EasyStats}.
   */
  @Override
  public EasyStats computeStats(IndexSearcher searcher, String fieldName,
      float queryBoost, TermContext... termContexts) throws IOException {
    EasyStats stats = new EasyStats(queryBoost);
    fillEasyStats(stats, searcher, fieldName, termContexts);
    return stats;
  }
  
  /** Fills all member fields defined in {@code EasyStats} in {@code stats}. */
  protected final void fillEasyStats(EasyStats stats, IndexSearcher searcher,
      String fieldName, TermContext... termContexts) throws IOException {
    IndexReader reader = searcher.getIndexReader();
    int numberOfDocuments = reader.maxDoc();
    long numberOfFieldTokens = MultiFields.getTerms(searcher.getIndexReader(),
        fieldName).getSumTotalTermFreq();
    float avgFieldLength = (float)numberOfFieldTokens / numberOfDocuments;
    
    // nocommit This is for phrases, and it doesn't really work... have to
    // find a method that makes sense
    int docFreq = 0;
    long totalTermFreq = 0;
    for (final TermContext context : termContexts) {
      docFreq += context.docFreq();
      totalTermFreq += context.totalTermFreq();
    }
    
    stats.setNumberOfDocuments(numberOfDocuments);
    stats.setNumberOfFieldTokens(numberOfFieldTokens);
    stats.setAvgFieldLength(avgFieldLength);
    stats.setDocFreq(docFreq);
    stats.setTotalTermFreq(totalTermFreq);
    // nocommit uniqueTermCount? (LUCENE-3290)
  }
  
  /**
   * Scores the document {@code doc}.
   * <p>Subclasses must apply their scoring formula in this class.</p>
   * @param stats the corpus level statistics.
   * @param freq the term frequency.
   * @param norm the current document's field norm.
   * @return the score.
   */
  protected abstract float score(EasyStats stats, float freq, byte norm);
  
  /**
   * Subclasses should implement this method to explain the score. {@code expl}
   * already contains the score, the name of the class and the doc id, as well
   * as the term frequency and its explanation; subclasses can add additional
   * clauses to explain details of their scoring formulae.
   * <p>The default implementation does nothing.</p>
   * 
   * @param expl the explanation to extend with details.
   * @param stats the corpus level statistics.
   * @param doc the document id.
   * @param freq the term frequency.
   * @param norm the current document's field norm.
   */
  protected void explain(
      Explanation expl, EasyStats stats, int doc, float freq, byte norm) {}
  
  /**
   * Explains the score. The implementation here provides a basic explanation
   * in the format <em>score(name-of-similarity, doc=doc-id,
   * freq=term-frequency), computed from:</em>, and
   * attaches the score (computed via the {@link #score(EasyStats, float, byte)}
   * method) and the explanation for the term frequency. Subclasses content with
   * this format may add additional details in
   * {@link #explain(Explanation, EasyStats, int, float, byte)}.
   *  
   * @param stats the corpus level statistics.
   * @param doc the document id.
   * @param freq the term frequency and its explanation.
   * @param norm the current document's field norm.
   * @return the explanation.
   */
  protected Explanation explain(
      EasyStats stats, int doc, Explanation freq, byte norm) {
    Explanation result = new Explanation(); 
    result.setValue(score(stats, freq.getValue(), norm));
    result.setDescription("score(" + getClass().getSimpleName() +
        ", doc=" + doc + ", freq=" + freq.getValue() +"), computed from:");
    result.addDetail(freq);
    
    explain(result, stats, doc, freq.getValue(), norm);
    
    return result;
  }
  
  @Override
  public ExactDocScorer exactDocScorer(Stats stats, String fieldName,
      AtomicReaderContext context) throws IOException {
    return new EasyExactDocScorer((EasyStats) stats,
                                  context.reader.norms(fieldName));
  }
  
  @Override
  public SloppyDocScorer sloppyDocScorer(Stats stats, String fieldName,
      AtomicReaderContext context) throws IOException {
    return new EasySloppyDocScorer((EasyStats) stats,
                                   context.reader.norms(fieldName));
  }

  // ------------------------------ Norm handling ------------------------------
  
  /** Cache of decoded bytes. */
  private static final float[] NORM_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++)
      NORM_TABLE[i] = SmallFloat.byte315ToFloat((byte)i);
  }

  /** Encodes the document length in the same way as {@link TFIDFSimilarity}. */
  @Override
  public byte computeNorm(FieldInvertState state) {
    final int numTerms;
    // nocommit: to include discountOverlaps?
//    if (discountOverlaps)
//      numTerms = state.getLength() - state.getNumOverlap();
//    else
      numTerms = state.getLength();
//    return encodeNormValue(state.getBoost() * ((float) (1.0 / Math.sqrt(numTerms))));
    return encodeNormValue(numTerms);
  }
  
  /** Decodes a normalization factor (document length) stored in an index.
   * @see #encodeNormValue(float)
   */
  // nocommit to protected?
  // nocommit is int OK?
  public int decodeNormValue(byte norm) {
    float floatNorm = NORM_TABLE[norm & 0xFF];  // & 0xFF maps negative bytes to positive above 127
    return (int)(1.0 / (floatNorm * floatNorm));  
  }
  
  /** Encodes the length to a byte via SmallFloat. */
  // nocommit to protected?
  public byte encodeNormValue(int length) {
    return SmallFloat.floatToByte315((float)(1.0 / Math.sqrt(length)));
  }
  
  // ----------------------------- Static methods ------------------------------
  
  /** Returns the base two logarithm of {@code x}. */
  public static double log2(double x) {
    // Put this to a 'util' class?
    return Math.log(x) / LOG_2;
  }
  
  // --------------------------------- Classes ---------------------------------
  
  /** Delegates the {@link #score(int, int)} and
   * {@link #explain(int, Explanation)} methods to
   * {@link EasySimilarity#score(EasyStats, float, byte)} and
   * {@link EasySimilarity#explain(EasyStats, int, Explanation, byte)},
   * respectively.
   */
  private class EasyExactDocScorer extends ExactDocScorer {
    private final EasyStats stats;
    private final byte[] norms;
    
    EasyExactDocScorer(EasyStats stats, byte norms[]) {
      this.stats = stats;
      this.norms = norms;
    }
    
    @Override
    public float score(int doc, int freq) {
      return EasySimilarity.this.score(stats, freq, norms[doc]);
    }
    
    @Override
    public Explanation explain(int doc, Explanation freq) {
      return EasySimilarity.this.explain(stats, doc, freq, norms[doc]);
    }
  }
  
  /** Delegates the {@link #score(int, int)} and
   * {@link #explain(int, Explanation)} methods to
   * {@link EasySimilarity#score(EasyStats, float, byte)} and
   * {@link EasySimilarity#explain(EasyStats, int, Explanation, byte)},
   * respectively.
   */
  private class EasySloppyDocScorer extends SloppyDocScorer {
    private final EasyStats stats;
    private final byte[] norms;
    
    EasySloppyDocScorer(EasyStats stats, byte norms[]) {
      this.stats = stats;
      this.norms = norms;
    }
    
    // todo: optimize
    @Override
    public float score(int doc, float freq) {
      return EasySimilarity.this.score(stats, freq, norms[doc]);
    }
    @Override
    public Explanation explain(int doc, Explanation freq) {
      return EasySimilarity.this.explain(stats, doc, freq, norms[doc]);
    }

    @Override
    public float computeSlopFactor(int distance) {
      return 1.0f / (distance + 1);
    }

    // nocommit: do we care about exposing this?
    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return 1f;
    }
  }
}
