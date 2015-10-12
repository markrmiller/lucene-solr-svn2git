package org.apache.lucene.search;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.similarities.Similarity;

/** A Query that matches documents matching boolean combinations of other
  * queries, e.g. {@link TermQuery}s, {@link PhraseQuery}s or other
  * BooleanQuerys.
  */
public class BooleanQuery extends Query implements Iterable<BooleanClause> {

  private static int maxClauseCount = 1024;

  /** Thrown when an attempt is made to add more than {@link
   * #getMaxClauseCount()} clauses. This typically happens if
   * a PrefixQuery, FuzzyQuery, WildcardQuery, or TermRangeQuery 
   * is expanded to many terms during search. 
   */
  public static class TooManyClauses extends RuntimeException {
    public TooManyClauses() {
      super("maxClauseCount is set to " + maxClauseCount);
    }
  }

  /** Return the maximum number of clauses permitted, 1024 by default.
   * Attempts to add more than the permitted number of clauses cause {@link
   * TooManyClauses} to be thrown.
   * @see #setMaxClauseCount(int)
   */
  public static int getMaxClauseCount() { return maxClauseCount; }

  /** 
   * Set the maximum number of clauses permitted per BooleanQuery.
   * Default value is 1024.
   */
  public static void setMaxClauseCount(int maxClauseCount) {
    if (maxClauseCount < 1) {
      throw new IllegalArgumentException("maxClauseCount must be >= 1");
    }
    BooleanQuery.maxClauseCount = maxClauseCount;
  }

  /** A builder for boolean queries. */
  public static class Builder {

    private boolean disableCoord;
    private int minimumNumberShouldMatch;
    private final List<BooleanClause> clauses = new ArrayList<>();

    /** Sole constructor. */
    public Builder() {}

    /**
     * {@link Similarity#coord(int,int)} may be disabled in scoring, as
     * appropriate. For example, this score factor does not make sense for most
     * automatically generated queries, like {@link WildcardQuery} and {@link
     * FuzzyQuery}.
     */
    public Builder setDisableCoord(boolean disableCoord) {
      this.disableCoord = disableCoord;
      return this;
    }

    /**
     * Specifies a minimum number of the optional BooleanClauses
     * which must be satisfied.
     *
     * <p>
     * By default no optional clauses are necessary for a match
     * (unless there are no required clauses).  If this method is used,
     * then the specified number of clauses is required.
     * </p>
     * <p>
     * Use of this method is totally independent of specifying that
     * any specific clauses are required (or prohibited).  This number will
     * only be compared against the number of matching optional clauses.
     * </p>
     *
     * @param min the number of optional clauses that must match
     */
    public Builder setMinimumNumberShouldMatch(int min) {
      this.minimumNumberShouldMatch = min;
      return this;
    }

    /**
     * Add a new clause to this {@link Builder}. Note that the order in which
     * clauses are added does not have any impact on matching documents or query
     * performance.
     * @throws TooManyClauses if the new number of clauses exceeds the maximum clause number
     */
    public Builder add(BooleanClause clause) {
      add(clause.getQuery(), clause.getOccur());
      return this;
    }

    /**
     * Add a new clause to this {@link Builder}. Note that the order in which
     * clauses are added does not have any impact on matching documents or query
     * performance.
     * @throws TooManyClauses if the new number of clauses exceeds the maximum clause number
     */
    public Builder add(Query query, Occur occur) {
      if (clauses.size() >= maxClauseCount) {
        throw new TooManyClauses();
      }
      clauses.add(new BooleanClause(query, occur));
      return this;
    }

    /** Create a new {@link BooleanQuery} based on the parameters that have
     *  been set on this builder. */
    public BooleanQuery build() {
      return new BooleanQuery(disableCoord, minimumNumberShouldMatch, clauses.toArray(new BooleanClause[0]));
    }

  }

  private final boolean disableCoord;
  private final int minimumNumberShouldMatch;
  private final List<BooleanClause> clauses;              // used for toString() and getClauses()
  private final Map<Occur, Collection<Query>> clauseSets; // used for equals/hashcode

  private BooleanQuery(boolean disableCoord, int minimumNumberShouldMatch,
      BooleanClause[] clauses) {
    this.disableCoord = disableCoord;
    this.minimumNumberShouldMatch = minimumNumberShouldMatch;
    this.clauses = Collections.unmodifiableList(Arrays.asList(clauses));
    clauseSets = new EnumMap<>(Occur.class);
    // duplicates matter for SHOULD and MUST
    clauseSets.put(Occur.SHOULD, new Multiset<>());
    clauseSets.put(Occur.MUST, new Multiset<>());
    // but not for FILTER and MUST_NOT
    clauseSets.put(Occur.FILTER, new HashSet<>());
    clauseSets.put(Occur.MUST_NOT, new HashSet<>());
    for (BooleanClause clause : clauses) {
      clauseSets.get(clause.getOccur()).add(clause.getQuery());
    }
  }

  /**
   * Return whether the coord factor is disabled.
   */
  public boolean isCoordDisabled() {
    return disableCoord;
  }

  /**
   * Gets the minimum number of the optional BooleanClauses
   * which must be satisfied.
   */
  public int getMinimumNumberShouldMatch() {
    return minimumNumberShouldMatch;
  }

  /** Return a list of the clauses of this {@link BooleanQuery}. */
  public List<BooleanClause> clauses() {
    return clauses;
  }

  /** Returns an iterator on the clauses in this query. It implements the {@link Iterable} interface to
   * make it possible to do:
   * <pre class="prettyprint">for (BooleanClause clause : booleanQuery) {}</pre>
   */
  @Override
  public final Iterator<BooleanClause> iterator() {
    return clauses.iterator();
  }

  private BooleanQuery rewriteNoScoring() {
    BooleanQuery.Builder newQuery = new BooleanQuery.Builder();
    // ignore disableCoord, which only matters for scores
    newQuery.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
    for (BooleanClause clause : clauses) {
      if (clause.getOccur() == Occur.MUST) {
        newQuery.add(clause.getQuery(), Occur.FILTER);
      } else {
        newQuery.add(clause);
      }
    }
    return newQuery.build();
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    BooleanQuery query = this;
    if (needsScores == false) {
      query = rewriteNoScoring();
    }
    return new BooleanWeight(query, searcher, needsScores, disableCoord);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (minimumNumberShouldMatch == 0 && clauses.size() == 1) {// optimize 1-clause queries
      BooleanClause c = clauses.get(0);
      if (!c.isProhibited()) {  // just return clause

        Query query = c.getQuery();

        if (c.isScoring() == false) {
          // our single clause is a filter, so we need to disable scoring
          query = new BoostQuery(new ConstantScoreQuery(query), 0);
        }

        return query;
      }
    }

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.setDisableCoord(isCoordDisabled());
    builder.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
    boolean actuallyRewritten = false;
    for (BooleanClause clause : this) {
      Query query = clause.getQuery();
      Query rewritten = query.rewrite(reader);
      if (rewritten != query) {
        actuallyRewritten = true;
      }
      builder.add(rewritten, clause.getOccur());
    }
    if (actuallyRewritten) {
      return builder.build();
    }
    return super.rewrite(reader);
  }

  /** Prints a user-readable version of this query. */
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    boolean needParens = getMinimumNumberShouldMatch() > 0;
    if (needParens) {
      buffer.append("(");
    }

    int i = 0;
    for (BooleanClause c : this) {
      buffer.append(c.getOccur().toString());

      Query subQuery = c.getQuery();
      if (subQuery instanceof BooleanQuery) {  // wrap sub-bools in parens
        buffer.append("(");
        buffer.append(subQuery.toString(field));
        buffer.append(")");
      } else {
        buffer.append(subQuery.toString(field));
      }

      if (i != clauses.size() - 1) {
        buffer.append(" ");
      }
      i += 1;
    }

    if (needParens) {
      buffer.append(")");
    }

    if (getMinimumNumberShouldMatch()>0) {
      buffer.append('~');
      buffer.append(getMinimumNumberShouldMatch());
    }

    return buffer.toString();
  }

  /**
   * Compares the specified object with this boolean query for equality.
   * Returns true if and only if the provided object<ul>
   * <li>is also a {@link BooleanQuery},</li>
   * <li>has the same value of {@link #isCoordDisabled()}</li>
   * <li>has the same value of {@link #getMinimumNumberShouldMatch()}</li>
   * <li>has the same {@link Occur#SHOULD} clauses, regardless of the order</li>
   * <li>has the same {@link Occur#MUST} clauses, regardless of the order</li>
   * <li>has the same set of {@link Occur#FILTER} clauses, regardless of the
   * order and regardless of duplicates</li>
   * <li>has the same set of {@link Occur#MUST_NOT} clauses, regardless of
   * the order and regardless of duplicates</li></ul>
   */
  @Override
  public boolean equals(Object o) {
    if (super.equals(o) == false) {
      return false;
    }
    BooleanQuery that = (BooleanQuery)o;
    return this.getMinimumNumberShouldMatch() == that.getMinimumNumberShouldMatch()
        && this.disableCoord == that.disableCoord
        && clauseSets.equals(that.clauseSets);
  }

  private int computeHashCode() {
    int hashCode = 31 * super.hashCode() + Objects.hash(disableCoord, minimumNumberShouldMatch, clauseSets);
    if (hashCode == 0) {
      hashCode = 1;
    }
    return hashCode;
  }

  // cached hash code is ok since boolean queries are immutable
  private int hashCode;

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      // no need for synchronization, in the worst case we would just compute the hash several times
      hashCode = computeHashCode();
      assert hashCode != 0;
    }
    assert hashCode == computeHashCode();
    return hashCode;
  }

}
