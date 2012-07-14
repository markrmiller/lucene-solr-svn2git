package org.apache.lucene.search.spans;

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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

/** Matches spans containing a term. */
public class SpanTermQuery extends MockSpanQuery {
  protected Term term;

  /** Construct a SpanTermQuery matching the named term's spans. */
  public SpanTermQuery(Term term) {
    this(term, true);
  }

  public SpanTermQuery(Term term, boolean needsPayloads) {
    this(term, new TermQuery(term), needsPayloads);
  }

  private SpanTermQuery(Term term, TermQuery query, boolean needsPayloads) {
    super(query, needsPayloads, term.field(), null);
    this.term = term;
  }

  /** Return the term whose spans are matched. */
  public Term getTerm() {
    return term;
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    terms.add(term);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((term == null) ? 0 : term.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    SpanTermQuery other = (SpanTermQuery) obj;
    if (term == null) {
      if (other.term != null) return false;
    } else if (!term.equals(other.term)) return false;
    return true;
  }

}
