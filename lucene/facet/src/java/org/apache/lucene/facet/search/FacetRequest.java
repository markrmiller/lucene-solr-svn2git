package org.apache.lucene.facet.search;

import java.io.IOException;

import org.apache.lucene.facet.params.CategoryListParams.OrdinalPolicy;
import org.apache.lucene.facet.params.FacetIndexingParams;
import org.apache.lucene.facet.range.RangeFacetRequest;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;

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
 * Defines an aggregation request for a category. Allows specifying the
 * {@link #numResults number of child categories} to return as well as
 * {@link #getSortOrder() which} categories to consider the "top" (highest or
 * lowest ranking ones).
 * <p>
 * If the category being aggregated is hierarchical, you can also specify the
 * {@link #setDepth(int) depth} up which to aggregate child categories as well
 * as how the result should be {@link #setResultMode(ResultMode) constructed}.
 * 
 * @lucene.experimental
 */
public abstract class FacetRequest {
  
  /**
   * When {@link FacetRequest#getDepth()} is greater than 1, defines the
   * structure of the result as well as how constraints such as
   * {@link FacetRequest#numResults} and {@link FacetRequest#getNumLabel()} are
   * applied.
   */
  public enum ResultMode { 
    /**
     * Constraints are applied per node, and the result has a full tree
     * structure. Default result mode.
     */
    PER_NODE_IN_TREE, 
    
    /**
     * Constraints are applied globally, on total number of results, and the
     * result has a flat structure.
     */
    GLOBAL_FLAT
  }
  
  /**
   * Specifies which array of {@link FacetArrays} should be used to resolve
   * values. When set to {@link #INT} or {@link #FLOAT}, allows creating an
   * optimized {@link FacetResultsHandler}, which does not call
   * {@link FacetRequest#getValueOf(FacetArrays, int)} for every ordinal.
   * <p>
   * If set to {@link #BOTH}, the {@link FacetResultsHandler} will use
   * {@link FacetRequest#getValueOf(FacetArrays, int)} to resolve ordinal
   * values, although it is recommended that you consider writing a specialized
   * {@link FacetResultsHandler}.
   * <p>
   * Can also be set to {@link #NONE}, to indicate that this
   * {@link FacetRequest} does not use {@link FacetArrays} to aggregate its
   * result categories. Such requests won't use {@link FacetResultsHandler}.
   */
  public enum FacetArraysSource { INT, FLOAT, BOTH, NONE }
  
  /**
   * Defines which categories to return. If {@link #DESCENDING} (the default),
   * the highest {@link FacetRequest#numResults} weighted categories will be
   * returned, otherwise the lowest ones.
   */
  public enum SortOrder { ASCENDING, DESCENDING }

  /** The category being aggregated in this facet request. */
  public final CategoryPath categoryPath;
  
  /** The number of child categories to return for {@link #categoryPath}. */
  public final int numResults;
  
  private int numLabel;
  private int depth = 1;
  private SortOrder sortOrder = SortOrder.DESCENDING;
  private ResultMode resultMode = ResultMode.PER_NODE_IN_TREE;
  
  // Computed at construction; based on categoryPath and numResults.
  private final int hashCode;
  
  /**
   * Constructor with the given category to aggregate and the number of child
   * categories to return.
   * 
   * @param path
   *          the category to aggregate. Cannot be {@code null}.
   * @param numResults
   *          the number of child categories to return. If set to
   *          {@code Integer.MAX_VALUE}, all immediate child categories will be
   *          returned. Must be greater than 0.
   */
  public FacetRequest(CategoryPath path, int numResults) {
    if (numResults <= 0) {
      throw new IllegalArgumentException("num results must be a positive (>0) number: " + numResults);
    }
    if (path == null) {
      throw new IllegalArgumentException("category path cannot be null!");
    }
    categoryPath = path;
    this.numResults = numResults;
    numLabel = numResults;
    hashCode = categoryPath.hashCode() ^ this.numResults;
  }
  
  /**
   * Create an aggregator for this facet request. Aggregator action depends on
   * request definition. For a count request, it will usually increment the
   * count for that facet.
   * 
   * @param useComplements
   *          whether the complements optimization is being used for current
   *          computation.
   * @param arrays
   *          provider for facet arrays in use for current computation.
   * @param taxonomy
   *          reader of taxonomy in effect.
   * @throws IOException If there is a low-level I/O error.
   */
  public Aggregator createAggregator(boolean useComplements, FacetArrays arrays, TaxonomyReader taxonomy) 
      throws IOException {
    throw new UnsupportedOperationException("this FacetRequest does not support this type of Aggregator anymore; " +
        "you should override FacetsAccumulator to return the proper FacetsAggregator");
  }
  
  /**
   * Returns the {@link FacetsAggregator} which can aggregate the categories of
   * this facet request. The aggregator is expected to aggregate category values
   * into {@link FacetArrays}. If the facet request does not support that, e.g.
   * {@link RangeFacetRequest}, it can return {@code null}. Note though that
   * such requests require a dedicated {@link FacetsAccumulator}.
   */
  public abstract FacetsAggregator createFacetsAggregator(FacetIndexingParams fip);
  
  @Override
  public boolean equals(Object o) {
    if (o instanceof FacetRequest) {
      FacetRequest that = (FacetRequest) o;
     return that.hashCode == this.hashCode &&
          that.categoryPath.equals(this.categoryPath) &&
          that.numResults == this.numResults &&
          that.depth == this.depth &&
          that.resultMode == this.resultMode &&
          that.numLabel == this.numLabel &&
          that.sortOrder == this.sortOrder;
    }
    return false;
  }
  
  /**
   * How deeply to look under {@link #categoryPath}. By default, only its
   * immediate children are aggregated (depth=1). If set to
   * {@code Integer.MAX_VALUE}, the entire sub-tree of the category will be
   * aggregated.
   * <p>
   * <b>NOTE:</b> setting depth to 0 means that only the category itself should
   * be aggregated. In that case, make sure to index the category with
   * {@link OrdinalPolicy#ALL_PARENTS}, unless it is not the root category (the
   * dimension), in which case {@link OrdinalPolicy#ALL_BUT_DIMENSION} is fine
   * too.
   */
  public final int getDepth() {
    // TODO an AUTO_EXPAND option could be useful  
    return depth;
  }
  
  /**
   * Returns the {@link FacetArraysSource} this request uses in
   * {@link #getValueOf(FacetArrays, int)}.
   */
  public abstract FacetArraysSource getFacetArraysSource();
  
  /**
   * Allows to specify the number of categories to label. By default all
   * returned categories are labeled.
   * <p>
   * This allows an app to request a large number of results to return, while
   * labeling them on-demand (e.g. when the UI requests to show more
   * categories).
   */
  public final int getNumLabel() {
    return numLabel;
  }
  
  /** Return the requested result mode (defaults to {@link ResultMode#PER_NODE_IN_TREE}. */
  public final ResultMode getResultMode() {
    return resultMode;
  }
  
  /** Return the requested order of results (defaults to {@link SortOrder#DESCENDING}. */
  public final SortOrder getSortOrder() {
    return sortOrder;
  }
  
  /**
   * Return the weight of the requested category ordinal. A {@link FacetRequest}
   * is responsible for resolving the weight of a category given the
   * {@link FacetArrays} and {@link #getFacetArraysSource()}. E.g. a counting
   * request will probably return the value of the category from
   * {@link FacetArrays#getIntArray()} while an average-weighting request will
   * compute the value using both arrays.
   * 
   * @param arrays
   *          the arrays used to aggregate the categories weights.
   * @param ordinal
   *          the category ordinal for which to return the weight.
   */
  // TODO perhaps instead of getValueOf we can have a postProcess(FacetArrays)
  // That, together with getFacetArraysSource should allow ResultHandlers to
  // efficiently obtain the values from the arrays directly
  public abstract double getValueOf(FacetArrays arrays, int ordinal);
  
  @Override
  public int hashCode() {
    return hashCode; 
  }
  
  /**
   * Sets the depth up to which to aggregate facets.
   * 
   * @see #getDepth()
   */
  public void setDepth(int depth) {
    this.depth = depth;
  }
  
  /**
   * Sets the number of categories to label.
   * 
   * @see #getNumLabel()
   */
  public void setNumLabel(int numLabel) {
    this.numLabel = numLabel;
  }
  
  /**
   * Sets the {@link ResultMode} for this request.
   * 
   * @see #getResultMode()
   */
  public void setResultMode(ResultMode resultMode) {
    this.resultMode = resultMode;
  }

  /**
   * Sets the {@link SortOrder} for this request.
   * 
   * @see #getSortOrder()
   */
  public void setSortOrder(SortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }
  
  @Override
  public String toString() {
    return categoryPath.toString() + " nRes=" + numResults + " nLbl=" + numLabel;
  }
  
}
