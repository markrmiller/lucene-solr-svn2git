package org.apache.lucene.facet.search;

import org.apache.lucene.facet.params.FacetIndexingParams;
import org.apache.lucene.facet.taxonomy.CategoryPath;

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
 * Facet request for counting facets.
 * 
 * @lucene.experimental
 */
public class CountFacetRequest extends FacetRequest {

  public CountFacetRequest(CategoryPath path, int num) {
    super(path, num);
  }

  @Override
  public FacetsAggregator createFacetsAggregator(FacetIndexingParams fip) {
    return CountingFacetsAggregator.create(fip.getCategoryListParams(categoryPath));
  }
  
  @Override
  public double getValueOf(FacetArrays arrays, int ordinal) {
    return arrays.getIntArray()[ordinal];
  }

  @Override
  public FacetArraysSource getFacetArraysSource() {
    return FacetArraysSource.INT;
  }
  
}
