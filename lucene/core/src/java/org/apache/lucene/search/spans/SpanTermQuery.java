package org.apache.lucene.search.spans;

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
import java.util.Map;
import java.util.Set;
import java.util.Objects;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.ToStringUtils;

/** Matches spans containing a term.
 * This should not be used for terms that are indexed at position Integer.MAX_VALUE.
 */
public class SpanTermQuery extends SpanQuery {
  protected Term term;

  /** Construct a SpanTermQuery matching the named term's spans. */
  public SpanTermQuery(Term term) {
    this.term = Objects.requireNonNull(term);
  }

  /** Return the term whose spans are matched. */
  public Term getTerm() { return term; }

  @Override
  public String getField() { return term.field(); }

  @Override
  public void extractTerms(Set<Term> terms) {
    terms.add(term);
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (term.field().equals(field))
      buffer.append(term.text());
    else
      buffer.append(term.toString());
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + term.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (! super.equals(obj)) {
      return false;
    }
    SpanTermQuery other = (SpanTermQuery) obj;
    return term.equals(other.term);
  }

  @Override
  public Spans getSpans(final LeafReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts) throws IOException {
    TermContext termContext = termContexts.get(term);
    final TermState state;
    if (termContext == null) {
      // this happens with span-not query, as it doesn't include the NOT side in extractTerms()
      // so we seek to the term now in this segment..., this sucks because it's ugly mostly!
      final Terms terms = context.reader().terms(term.field());
      if (terms != null) {
        if (terms.hasPositions() == false) {
          throw new IllegalStateException("field \"" + term.field() + "\" was indexed without position data; cannot run SpanTermQuery (term=" + term.text() + ")");
        }

        final TermsEnum termsEnum = terms.iterator();
        if (termsEnum.seekExact(term.bytes())) {
          state = termsEnum.termState();
        } else {
          state = null;
        }
      } else {
        state = null;
      }
    } else {
      state = termContext.get(context.ord);
    }

    if (state == null) { // term is not present in that reader
      return null;
    }

    final TermsEnum termsEnum = context.reader().terms(term.field()).iterator();
    termsEnum.seekExact(term.bytes(), state);

    final PostingsEnum postings = termsEnum.postings(acceptDocs, null, PostingsEnum.PAYLOADS);
    return new TermSpans(postings, term);
  }
}
