package org.apache.lucene.facet;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.facet.taxonomy.ParallelTaxonomyArrays;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;

/** Base class for all taxonomy-based facets impls. */
public abstract class TaxonomyFacets extends Facets {
  protected final String indexFieldName;
  protected final TaxonomyReader taxoReader;
  protected final FacetsConfig config;
  protected final int[] children;
  protected final int[] siblings;

  protected TaxonomyFacets(String indexFieldName, TaxonomyReader taxoReader, FacetsConfig config) throws IOException {
    this.indexFieldName = indexFieldName;
    this.taxoReader = taxoReader;
    this.config = config;
    ParallelTaxonomyArrays pta = taxoReader.getParallelTaxonomyArrays();
    children = pta.children();
    siblings = pta.siblings();
  }

  protected FacetsConfig.DimConfig verifyDim(String dim) {
    FacetsConfig.DimConfig dimConfig = config.getDimConfig(dim);
    if (!dimConfig.indexFieldName.equals(indexFieldName)) {
      // nocommit get test case to cover this:
      throw new IllegalArgumentException("dimension \"" + dim + "\" was not indexed into field \"" + indexFieldName);
    }
    return dimConfig;
  }

  @Override
  public List<FacetResult> getAllDims(int topN) throws IOException {
    int ord = children[TaxonomyReader.ROOT_ORDINAL];
    List<FacetResult> results = new ArrayList<FacetResult>();
    while (ord != TaxonomyReader.INVALID_ORDINAL) {
      String dim = taxoReader.getPath(ord).components[0];
      FacetsConfig.DimConfig dimConfig = config.getDimConfig(dim);
      if (dimConfig.indexFieldName.equals(indexFieldName)) {
        FacetResult result = getTopChildren(topN, dim);
        if (result != null) {
          results.add(result);
        }
      }
      ord = siblings[ord];
    }

    // Sort by highest value, tie break by value:
    Collections.sort(results,
                     new Comparator<FacetResult>() {
                       @Override
                       public int compare(FacetResult a, FacetResult b) {
                         if (a.value.doubleValue() > b.value.doubleValue()) {
                           return -1;
                         } else if (b.value.doubleValue() > a.value.doubleValue()) {
                           return 1;
                         } else {
                           return 0;
                         }
                       }
                     });

    return results;
  }
}