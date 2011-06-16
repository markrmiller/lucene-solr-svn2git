package org.apache.lucene.search;

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

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.index.IndexReader.ReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermContext;
import org.apache.lucene.util.ReaderUtil;
import org.apache.lucene.util.ToStringUtils;

/** A Query that matches documents containing a term.
  This may be combined with other terms with a {@link BooleanQuery}.
  */
public class TermQuery extends Query {
  private final Term term;
  private int docFreq;
  private transient TermContext perReaderTermState;

  private class TermWeight extends Weight {
    private final Similarity similarity;
    private final Similarity.Stats stats;
    private transient TermContext termStates;

    public TermWeight(IndexSearcher searcher, TermContext termStates)
      throws IOException {
      assert termStates != null : "TermContext must not be null";
      this.termStates = termStates;
      this.similarity = searcher.getSimilarityProvider().get(term.field());
      this.stats = similarity.computeStats(searcher, term.field(), getBoost(), termStates);
    }

    @Override
    public String toString() { return "weight(" + TermQuery.this + ")"; }

    @Override
    public Query getQuery() { return TermQuery.this; }

    @Override
    public float getValueForNormalization() {
      return stats.getValueForNormalization();
    }

    @Override
    public void normalize(float queryNorm, float topLevelBoost) {
      stats.normalize(queryNorm, topLevelBoost);
    }

    @Override
    public Scorer scorer(AtomicReaderContext context, ScorerContext scorerContext) throws IOException {
      final String field = term.field();
      final IndexReader reader = context.reader;
      assert termStates.topReaderContext == ReaderUtil.getTopLevelContext(context) : "The top-reader used to create Weight (" + termStates.topReaderContext + ") is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
      final TermState state = termStates
          .get(context.ord);
      if (state == null) { // term is not present in that reader
        assert termNotInReader(reader, field, term.bytes()) : "no termstate found but term exists in reader";
        return null;
      }
      final DocsEnum docs = reader.termDocsEnum(reader.getDeletedDocs(), field, term.bytes(), state);
      assert docs != null;
      return new TermScorer(this, docs, similarity.exactDocScorer(stats, field, context));
    }
    
    private boolean termNotInReader(IndexReader reader, String field, BytesRef bytes) throws IOException {
      // only called from assert
      final Terms terms = reader.terms(field);
      return terms == null || terms.docFreq(bytes) == 0;
    }
    
    @Override
    public Explanation explain(AtomicReaderContext context, int doc)
      throws IOException {
      //nocommit: fix explains
      if (!(similarity instanceof TFIDFSimilarity))
        return new ComplexExplanation();
      final TFIDFSimilarity similarity = (TFIDFSimilarity) this.similarity;
      final TFIDFSimilarity.IDFStats stats = (TFIDFSimilarity.IDFStats) this.stats;
      
      final IndexReader reader = context.reader;

      ComplexExplanation result = new ComplexExplanation();
      result.setDescription("weight("+getQuery()+" in "+doc+"), product of:");

      Explanation expl = new Explanation(stats.idf.getIdf(), stats.idf.explain());

      // explain query weight
      Explanation queryExpl = new Explanation();
      queryExpl.setDescription("queryWeight(" + getQuery() + "), product of:");

      Explanation boostExpl = new Explanation(getBoost(), "boost");
      if (getBoost() != 1.0f)
        queryExpl.addDetail(boostExpl);
      queryExpl.addDetail(expl);

      Explanation queryNormExpl = new Explanation(stats.queryNorm,"queryNorm");
      queryExpl.addDetail(queryNormExpl);

      queryExpl.setValue(boostExpl.getValue() *
                         expl.getValue() *
                         queryNormExpl.getValue());

      result.addDetail(queryExpl);

      // explain field weight
      String field = term.field();
      ComplexExplanation fieldExpl = new ComplexExplanation();
      fieldExpl.setDescription("fieldWeight("+term+" in "+doc+
                               "), product of:");

      Explanation tfExplanation = new Explanation();
      int tf = 0;
      DocsEnum docs = reader.termDocsEnum(reader.getDeletedDocs(), term.field(), term.bytes());
      if (docs != null) {
          int newDoc = docs.advance(doc);
          if (newDoc == doc) {
            tf = docs.freq();
          }
        tfExplanation.setValue(similarity.tf(tf));
        tfExplanation.setDescription("tf(termFreq("+term+")="+tf+")");
      } else {
        tfExplanation.setValue(0.0f);
        tfExplanation.setDescription("no matching term");
      }
      fieldExpl.addDetail(tfExplanation);
      fieldExpl.addDetail(expl);

      Explanation fieldNormExpl = new Explanation();
      final byte[] fieldNorms = reader.norms(field);
      float fieldNorm =
        fieldNorms!=null ? similarity.decodeNormValue(fieldNorms[doc]) : 1.0f;
      fieldNormExpl.setValue(fieldNorm);
      fieldNormExpl.setDescription("fieldNorm(field="+field+", doc="+doc+")");
      fieldExpl.addDetail(fieldNormExpl);
      
      fieldExpl.setMatch(Boolean.valueOf(tfExplanation.isMatch()));
      fieldExpl.setValue(tfExplanation.getValue() *
                         expl.getValue() *
                         fieldNormExpl.getValue());

      result.addDetail(fieldExpl);
      result.setMatch(fieldExpl.getMatch());
      
      // combine them
      result.setValue(queryExpl.getValue() * fieldExpl.getValue());

      if (queryExpl.getValue() == 1.0f)
        return fieldExpl;

      return result;
    }
  }

  /** Constructs a query for the term <code>t</code>. */
  public TermQuery(Term t) {
    this(t, -1);
  }

  /** Expert: constructs a TermQuery that will use the
   *  provided docFreq instead of looking up the docFreq
   *  against the searcher. */
  public TermQuery(Term t, int docFreq) {
    term = t;
    this.docFreq = docFreq;
    perReaderTermState = null;
  }
  
  /** Expert: constructs a TermQuery that will use the
   *  provided docFreq instead of looking up the docFreq
   *  against the searcher. */
  public TermQuery(Term t, TermContext states) {
    assert states != null;
    term = t;
    docFreq = states.docFreq();
    perReaderTermState = states;
  }

  /** Returns the term of this query. */
  public Term getTerm() { return term; }

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    final ReaderContext context = searcher.getTopReaderContext();
    final TermContext termState;
    if (perReaderTermState == null || perReaderTermState.topReaderContext != context) {
      // make TermQuery single-pass if we don't have a PRTS or if the context differs!
      termState = TermContext.build(context, term, true); // cache term lookups!
    } else {
     // PRTS was pre-build for this IS
     termState = this.perReaderTermState;
    }

    // we must not ignore the given docFreq - if set use the given value (lie)
    if (docFreq != -1)
      termState.setDocFreq(docFreq);
    
    return new TermWeight(searcher, termState);
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    terms.add(getTerm());
  }

  /** Prints a user-readable version of this query. */
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (!term.field().equals(field)) {
      buffer.append(term.field());
      buffer.append(":");
    }
    buffer.append(term.text());
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  /** Returns true iff <code>o</code> is equal to this. */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TermQuery))
      return false;
    TermQuery other = (TermQuery)o;
    return (this.getBoost() == other.getBoost())
      && this.term.equals(other.term);
  }

  /** Returns a hash code value for this object.*/
  @Override
  public int hashCode() {
    return Float.floatToIntBits(getBoost()) ^ term.hashCode();
  }

}
