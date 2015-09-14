package org.apache.lucene.search.payloads;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.FilterSpans.AcceptStatus;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanScorer;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ToStringUtils;

/**
 * Only return those matches that have a specific payload at the given position.
 */
public class SpanPayloadCheckQuery extends SpanQuery {

  protected final List<BytesRef> payloadToMatch;
  protected final SpanQuery match;

  /**
   * @param match The underlying {@link org.apache.lucene.search.spans.SpanQuery} to check
   * @param payloadToMatch The {@link java.util.List} of payloads to match
   */
  public SpanPayloadCheckQuery(SpanQuery match, List<BytesRef> payloadToMatch) {
    this.match = match;
    this.payloadToMatch = payloadToMatch;
  }

  @Override
  public String getField() {
    return match.getField();
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    SpanWeight matchWeight = match.createWeight(searcher, false);
    return new SpanPayloadCheckWeight(searcher, needsScores ? getTermContexts(matchWeight) : null, matchWeight);
  }

  /**
   * Weight that pulls its Spans using a PayloadSpanCollector
   */
  public class SpanPayloadCheckWeight extends SpanWeight {

    final SpanWeight matchWeight;

    public SpanPayloadCheckWeight(IndexSearcher searcher, Map<Term, TermContext> termContexts, SpanWeight matchWeight) throws IOException {
      super(SpanPayloadCheckQuery.this, searcher, termContexts);
      this.matchWeight = matchWeight;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      matchWeight.extractTerms(terms);
    }

    @Override
    public void extractTermContexts(Map<Term, TermContext> contexts) {
      matchWeight.extractTermContexts(contexts);
    }

    @Override
    public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
      final PayloadChecker collector = new PayloadChecker();
      Spans matchSpans = matchWeight.getSpans(context, requiredPostings.atLeast(Postings.PAYLOADS));
      return (matchSpans == null) ? null : new FilterSpans(matchSpans) {
        @Override
        protected AcceptStatus accept(Spans candidate) throws IOException {
          collector.reset();
          candidate.collect(collector);
          return collector.match();
        }
      };
    }

    @Override
    public Scorer scorer(LeafReaderContext context) throws IOException {
      if (field == null)
        return null;

      Terms terms = context.reader().terms(field);
      if (terms != null && terms.hasPositions() == false) {
        throw new IllegalStateException("field \"" + field + "\" was indexed without position data; cannot run SpanQuery (query=" + parentQuery + ")");
      }

      Spans spans = getSpans(context, Postings.PAYLOADS);
      Similarity.SimScorer simScorer = simWeight == null ? null : similarity.simScorer(simWeight, context);
      return (spans == null) ? null : new SpanScorer(spans, this, simScorer);
    }
  }

  private class PayloadChecker implements SpanCollector {

    int upto = 0;
    boolean matches = true;

    @Override
    public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
      if (!matches)
        return;
      if (upto >= payloadToMatch.size()) {
        matches = false;
        return;
      }
      BytesRef payload = postings.getPayload();
      if (payloadToMatch.get(upto) == null) {
        matches = payload == null;
        upto++;
        return;
      }
      if (payload == null) {
        matches = false;
        upto++;
        return;
      }
      matches = payloadToMatch.get(upto).bytesEquals(payload);
      upto++;
    }

    AcceptStatus match() {
      return matches && upto == payloadToMatch.size() ? AcceptStatus.YES : AcceptStatus.NO;
    }

    @Override
    public void reset() {
      this.upto = 0;
      this.matches = true;
    }
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("spanPayCheck(");
    buffer.append(match.toString(field));
    buffer.append(", payloadRef: ");
    for (BytesRef bytes : payloadToMatch) {
      buffer.append(bytes.utf8ToString());
      buffer.append(';');
    }
    buffer.append(")");
    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (! super.equals(o)) {
      return false;
    }
    SpanPayloadCheckQuery other = (SpanPayloadCheckQuery)o;
    return this.payloadToMatch.equals(other.payloadToMatch);
  }

  @Override
  public int hashCode() {
    int h = super.hashCode();
    h = (h * 63) ^ payloadToMatch.hashCode();
    return h;
  }
}