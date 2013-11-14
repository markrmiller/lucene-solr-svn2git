package org.apache.lucene.facet.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.facet.params.CategoryListParams;
import org.apache.lucene.facet.search.FacetArrays;
import org.apache.lucene.facet.search.FacetRequest;
import org.apache.lucene.facet.search.FacetsAggregator;
import org.apache.lucene.facet.search.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.FacetLabel;

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

/**
 * A {@link FacetsAggregator} which chains multiple aggregators for aggregating
 * the association values of categories that belong to the same category list.
 * While nothing prevents you from chaining general purpose aggregators, it is
 * only useful for aggregating association values, as each association type is
 * written in its own list.
 * 
 * @lucene.experimental
 */
public class MultiFacetsAggregator implements FacetsAggregator {
  
  private final Map<FacetLabel,FacetsAggregator> categoryAggregators;
  private final List<FacetsAggregator> aggregators;
  
  /**
   * Constructor.
   * <p>
   * The mapping is used to rollup the values of the specific category by the
   * corresponding {@link FacetsAggregator}. It is ok to pass differnet
   * {@link FacetsAggregator} instances for each {@link FacetLabel} - the
   * constructor ensures that each aggregator <u>type</u> (determined by its
   * class) is invoked only once.
   */
  public MultiFacetsAggregator(Map<FacetLabel,FacetsAggregator> aggregators) {
    this.categoryAggregators = aggregators;
    
    // make sure that each FacetsAggregator class is invoked only once, or
    // otherwise categories may be aggregated multiple times.
    Map<Class<? extends FacetsAggregator>, FacetsAggregator> aggsClasses = 
        new HashMap<Class<? extends FacetsAggregator>,FacetsAggregator>();
    for (FacetsAggregator fa : aggregators.values()) {
      aggsClasses.put(fa.getClass(), fa);
    }
    this.aggregators = new ArrayList<FacetsAggregator>(aggsClasses.values());
  }
  
  @Override
  public void aggregate(MatchingDocs matchingDocs, CategoryListParams clp, FacetArrays facetArrays) throws IOException {
    for (FacetsAggregator fa : aggregators) {
      fa.aggregate(matchingDocs, clp, facetArrays);
    }
  }
  
  @Override
  public void rollupValues(FacetRequest fr, int ordinal, int[] children, int[] siblings, FacetArrays facetArrays) {
    categoryAggregators.get(fr.categoryPath).rollupValues(fr, ordinal, children, siblings, facetArrays);
  }
  
  @Override
  public boolean requiresDocScores() {
    for (FacetsAggregator fa : aggregators) {
      if (fa.requiresDocScores()) {
        return true;
      }
    }
    return false;
  }
  
  @Override
  public OrdinalValueResolver createOrdinalValueResolver(FacetRequest facetRequest, FacetArrays arrays) {
    return categoryAggregators.get(facetRequest.categoryPath).createOrdinalValueResolver(facetRequest, arrays);
  }
  
}
