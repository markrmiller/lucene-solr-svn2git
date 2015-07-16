package org.apache.lucene.spatial.composite;

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

import com.spatial4j.core.shape.Shape;
import com.spatial4j.core.shape.SpatialRelation;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.spatial.prefix.AbstractVisitingPrefixTreeFilter;
import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.DocIdSetBuilder;

/**
 * A spatial Intersects predicate that distinguishes an approximated match from an exact match based on which cells
 * are within the query shape. It exposes a {@link TwoPhaseIterator} that will verify a match with a provided
 * predicate in the form of a {@link ValueSource} by calling {@link FunctionValues#boolVal(int)}.
 *
 * @lucene.internal
 */
public class IntersectsRPTVerifyQuery extends Query {

  private final IntersectsDifferentiatingFilter intersectsDiffFilter;
  private final ValueSource predicateValueSource; // we call FunctionValues.boolVal(doc)

  public IntersectsRPTVerifyQuery(Shape queryShape, String fieldName, SpatialPrefixTree grid, int detailLevel,
                                  int prefixGridScanLevel, ValueSource predicateValueSource) {
    this.predicateValueSource = predicateValueSource;
    this.intersectsDiffFilter = new IntersectsDifferentiatingFilter(queryShape, fieldName, grid, detailLevel,
        prefixGridScanLevel);
  }

  @Override
  public String toString(String field) {
    return "IntersectsVerified(fieldName=" + field + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;

    IntersectsRPTVerifyQuery that = (IntersectsRPTVerifyQuery) o;

    if (!intersectsDiffFilter.equals(that.intersectsDiffFilter)) return false;
    return predicateValueSource.equals(that.predicateValueSource);

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + intersectsDiffFilter.hashCode();
    result = 31 * result + predicateValueSource.hashCode();
    return result;
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    final Map valueSourceContext = ValueSource.newContext(searcher);

    return new ConstantScoreWeight(this) {
      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        // Compute approx & exact
        final IntersectsDifferentiatingFilter.IntersectsDifferentiatingVisitor result =
            intersectsDiffFilter.compute(context, null);
        if (result.approxDocIdSet == null) {
          return null;
        }
        final DocIdSetIterator approxDISI = result.approxDocIdSet.iterator();
        if (approxDISI == null) {
          return null;
        }
        final DocIdSetIterator exactIterator;
        if (result.exactDocIdSet != null) {
          // If both sets are the same, there's nothing to verify; we needn't return a TwoPhaseIterator
          if (result.approxDocIdSet == result.exactDocIdSet) {
            return new ConstantScoreScorer(this, score(), approxDISI);
          }
          exactIterator = result.exactDocIdSet.iterator();
          assert exactIterator != null;
        } else {
          exactIterator = null;
        }

        final FunctionValues predFuncValues = predicateValueSource.getValues(valueSourceContext, context);

        final TwoPhaseIterator twoPhaseIterator = new TwoPhaseIterator(approxDISI) {
          @Override
          public boolean matches() throws IOException {
            final int doc = approxDISI.docID();
            if (exactIterator != null) {
              if (exactIterator.docID() < doc) {
                exactIterator.advance(doc);
              }
              if (exactIterator.docID() == doc) {
                return true;
              }
            }

            return predFuncValues.boolVal(doc);
          }
        };

        return new ConstantScoreScorer(this, score(), twoPhaseIterator);
      }
    };
  }

  //This is a "Filter" but we don't use it as-such; the caller calls the constructor and then compute() and examines
  // the results which consists of two parts -- the approximated results, and a subset of exact matches. The
  // difference needs to be verified.
  // TODO refactor AVPTF to not be a Query/Filter?
  private static class IntersectsDifferentiatingFilter extends AbstractVisitingPrefixTreeFilter {

    public IntersectsDifferentiatingFilter(Shape queryShape, String fieldName, SpatialPrefixTree grid,
                                           int detailLevel, int prefixGridScanLevel) {
      super(queryShape, fieldName, grid, detailLevel, prefixGridScanLevel);
    }

    IntersectsDifferentiatingFilter.IntersectsDifferentiatingVisitor compute(LeafReaderContext context, Bits acceptDocs) throws IOException {
      final IntersectsDifferentiatingFilter.IntersectsDifferentiatingVisitor result = new IntersectsDifferentiatingFilter.IntersectsDifferentiatingVisitor(context, acceptDocs);
      result.getDocIdSet();//computes
      return result;
    }

    // TODO consider if IntersectsPrefixTreeFilter should simply do this and provide both sets

    class IntersectsDifferentiatingVisitor extends VisitorTemplate {
      DocIdSetBuilder approxBuilder = new DocIdSetBuilder(maxDoc);
      DocIdSetBuilder exactBuilder = new DocIdSetBuilder(maxDoc);
      boolean approxIsEmpty = true;
      boolean exactIsEmpty = true;
      DocIdSet exactDocIdSet;
      DocIdSet approxDocIdSet;

      public IntersectsDifferentiatingVisitor(LeafReaderContext context, Bits acceptDocs) throws IOException {
        super(context, acceptDocs);
      }

      @Override
      protected void start() throws IOException {
      }

      @Override
      protected DocIdSet finish() throws IOException {
        if (exactIsEmpty) {
          exactDocIdSet = null;
        } else {
          exactDocIdSet = exactBuilder.build();
        }
        if (approxIsEmpty) {
          approxDocIdSet = exactDocIdSet;//optimization
        } else {
          if (exactDocIdSet != null) {
            approxBuilder.add(exactDocIdSet.iterator());
          }
          approxDocIdSet = approxBuilder.build();
        }
        return null;//unused in this weird re-use of AVPTF
      }

      @Override
      protected boolean visitPrefix(Cell cell) throws IOException {
        if (cell.getShapeRel() == SpatialRelation.WITHIN) {
          exactIsEmpty = false;
          collectDocs(exactBuilder);//note: we'll add exact to approx on finish()
          return false;
        } else if (cell.getLevel() == detailLevel) {
          approxIsEmpty = false;
          collectDocs(approxBuilder);
          return false;
        }
        return true;
      }

      @Override
      protected void visitLeaf(Cell cell) throws IOException {
        if (cell.getShapeRel() == SpatialRelation.WITHIN) {
          exactIsEmpty = false;
          collectDocs(exactBuilder);//note: we'll add exact to approx on finish()
        } else {
          approxIsEmpty = false;
          collectDocs(approxBuilder);
        }
      }
    }

    @Override
    public DocIdSet getDocIdSet(LeafReaderContext context, Bits acceptDocs) throws IOException {
      throw new IllegalStateException();
    }

    @Override
    public String toString(String field) {
      throw new IllegalStateException();
    }
  }
}
