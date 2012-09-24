package org.apache.lucene.spatial.prefix;

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

import com.spatial4j.core.shape.Shape;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.spatial.prefix.tree.Node;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.query.UnsupportedSpatialOperation;

import java.util.List;

/**
 * A basic implementation of {@link PrefixTreeStrategy} using a large {@link
 * TermsFilter} of all the nodes from {@link SpatialPrefixTree#getNodes(com.spatial4j.core.shape.Shape,
 * int, boolean)}. It only supports the search of indexed Point shapes.
 * <p/>
 * The precision of query shapes (distErrPct) is an important factor in using
 * this Strategy. If the precision is too precise then it will result in many
 * terms which will amount to a slower query.
 *
 * @lucene.experimental
 */
public class TermQueryPrefixTreeStrategy extends PrefixTreeStrategy {

  public TermQueryPrefixTreeStrategy(SpatialPrefixTree grid, String fieldName) {
    super(grid, fieldName);
  }

  @Override
  public Filter makeFilter(SpatialArgs args) {
    final SpatialOperation op = args.getOperation();
    if (op != SpatialOperation.Intersects)
      throw new UnsupportedSpatialOperation(op);

    Shape shape = args.getShape();
    int detailLevel = grid.getLevelForDistance(args.resolveDistErr(ctx, distErrPct));
    List<Node> cells = grid.getNodes(shape, detailLevel, false);
    TermsFilter filter = new TermsFilter();
    for (Node cell : cells) {
      filter.addTerm(new Term(getFieldName(), cell.getTokenString()));
    }
    return filter;
  }

}
