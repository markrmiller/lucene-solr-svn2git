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

package org.apache.solr.handler.component;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.util.FixedBitSet;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.schema.FieldType;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SyntaxError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO!
 *
 *
 * @since solr 1.3
 */
@SuppressWarnings("rawtypes")
public class FacetComponent extends SearchComponent {
  public static Logger log = LoggerFactory.getLogger(FacetComponent.class);
  
  public static final String COMPONENT_NAME = "facet";
  
  private static final String PIVOT_KEY = "facet_pivot";
  private static final String PIVOT_REFINE_PREFIX = "{!"+PivotFacet.REFINE_PARAM+"=";

  /**
   * incrememented counter used to track the values being refined in a given request.  
   * This counter is used in conjunction with {@link PivotFacet#REFINE_PARAM} to identify
   * which refinement values are associated with which pivots
   */
  int pivotRefinementCounter = 0;

  @Override
  public void prepare(ResponseBuilder rb) throws IOException {
    if (rb.req.getParams().getBool(FacetParams.FACET, false)) {
      rb.setNeedDocSet(true);
      rb.doFacets = true;
    }
  }
  
  /**
   * Actually run the query
   */
  @Override
  public void process(ResponseBuilder rb) throws IOException {

    //SolrParams params = rb.req.getParams();
    if (rb.doFacets) {
      ModifiableSolrParams params = new ModifiableSolrParams();
      SolrParams origParams = rb.req.getParams();
      Iterator<String> iter = origParams.getParameterNamesIterator();
      while (iter.hasNext()) {
        String paramName = iter.next();
        // Deduplicate the list with LinkedHashSet, but _only_ for facet params.
        if (paramName.startsWith(FacetParams.FACET) == false) {
          params.add(paramName, origParams.getParams(paramName));
          continue;
        }
        HashSet<String> deDupe = new LinkedHashSet<>(Arrays.asList(origParams.getParams(paramName)));
        params.add(paramName, deDupe.toArray(new String[deDupe.size()]));
      }

      SimpleFacets f = new SimpleFacets(rb.req, rb.getResults().docSet, params, rb);
      
      NamedList<Object> counts = f.getFacetCounts();
      String[] pivots = params.getParams(FacetParams.FACET_PIVOT);
      if (pivots != null && pivots.length > 0) {
        PivotFacetProcessor pivotProcessor 
          = new PivotFacetProcessor(rb.req, rb.getResults().docSet, params, rb);
        SimpleOrderedMap<List<NamedList<Object>>> v 
          = pivotProcessor.process(pivots);
        if (v != null) {
          counts.add(PIVOT_KEY, v);
        }
      }
      
      rb.rsp.add("facet_counts", counts);
    }
  }
  
  private static final String commandPrefix = "{!" + CommonParams.TERMS + "=$";
  
  @Override
  public int distributedProcess(ResponseBuilder rb) throws IOException {
    if (!rb.doFacets) {
      return ResponseBuilder.STAGE_DONE;
    }
    
    if (rb.stage == ResponseBuilder.STAGE_GET_FIELDS) {
      // overlap facet refinement requests (those shards that we need a count
      // for particular facet values from), where possible, with
      // the requests to get fields (because we know that is the
      // only other required phase).
      // We do this in distributedProcess so we can look at all of the
      // requests in the outgoing queue at once.

      for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
        List<String> distribFieldFacetRefinements = null;
        
        for (DistribFieldFacet dff : rb._facetInfo.facets.values()) {
          if (!dff.needRefinements) continue;
          List<String> refList = dff._toRefine[shardNum];
          if (refList == null || refList.size() == 0) continue;
          
          String key = dff.getKey(); // reuse the same key that was used for the
                                     // main facet
          String termsKey = key + "__terms";
          String termsVal = StrUtils.join(refList, ',');
          
          String facetCommand;
          // add terms into the original facet.field command
          // do it via parameter reference to avoid another layer of encoding.
          
          String termsKeyEncoded = QueryParsing.encodeLocalParamVal(termsKey);
          if (dff.localParams != null) {
            facetCommand = commandPrefix + termsKeyEncoded + " "
                + dff.facetStr.substring(2);
          } else {
            facetCommand = commandPrefix + termsKeyEncoded + '}' + dff.field;
          }
          
          if (distribFieldFacetRefinements == null) {
            distribFieldFacetRefinements = new ArrayList<>();
          }

          distribFieldFacetRefinements.add(facetCommand);
          distribFieldFacetRefinements.add(termsKey);
          distribFieldFacetRefinements.add(termsVal);
        }
        
        boolean pivotFacetRefinementRequestsExistForShard = 
          doAnyPivotFacetRefinementRequestsExistForShard(rb._facetInfo, shardNum);

        if (distribFieldFacetRefinements == null
            && !pivotFacetRefinementRequestsExistForShard) {
          // nothing to refine, short circuit out
          continue;
        }
        
        String shard = rb.shards[shardNum];
        ShardRequest shardsRefineRequest = null;
        boolean newRequest = false;
        
        // try to find a request that is already going out to that shard.
        // If nshards becomes to great, we way want to move to hashing for
        // better scalability.
        for (ShardRequest sreq : rb.outgoing) {
          if ((sreq.purpose & ShardRequest.PURPOSE_GET_FIELDS) != 0
              && sreq.shards != null 
              && sreq.shards.length == 1
              && sreq.shards[0].equals(shard)) {
            shardsRefineRequest = sreq;
            break;
          }
        }
        
        if (shardsRefineRequest == null) {
          // we didn't find any other suitable requests going out to that shard,
          // so create one ourselves.
          newRequest = true;
          shardsRefineRequest = new ShardRequest();
          shardsRefineRequest.shards = new String[] { rb.shards[shardNum] };
          shardsRefineRequest.params = new ModifiableSolrParams(rb.req.getParams());
          // don't request any documents
          shardsRefineRequest.params.remove(CommonParams.START);
          shardsRefineRequest.params.set(CommonParams.ROWS, "0");
        }
        
        // FieldFacetAdditions
        if (distribFieldFacetRefinements != null) {
          shardsRefineRequest.purpose |= ShardRequest.PURPOSE_REFINE_FACETS;
          shardsRefineRequest.params.set(FacetParams.FACET, "true");
          shardsRefineRequest.params.remove(FacetParams.FACET_FIELD);
          shardsRefineRequest.params.remove(FacetParams.FACET_QUERY);

          for (int i = 0; i < distribFieldFacetRefinements.size();) {
            String facetCommand = distribFieldFacetRefinements.get(i++);
            String termsKey = distribFieldFacetRefinements.get(i++);
            String termsVal = distribFieldFacetRefinements.get(i++);

            shardsRefineRequest.params.add(FacetParams.FACET_FIELD,
                facetCommand);
            shardsRefineRequest.params.set(termsKey, termsVal);
          }
        }

        if (newRequest) {
          rb.addRequest(this, shardsRefineRequest);
        }

        // PivotFacetAdditions
        if (pivotFacetRefinementRequestsExistForShard) {
          if (newRequest) {
            shardsRefineRequest.params.remove(FacetParams.FACET_PIVOT);
            shardsRefineRequest.params.remove(FacetParams.FACET_PIVOT_MINCOUNT);
          }
          
          enqueuePivotFacetShardRequests(null, rb, shardNum);
        }
      }
    }
    
    return ResponseBuilder.STAGE_DONE;
  }
  
  private void enqueuePivotFacetShardRequests
    (HashMap<String,List<String>> pivotFacetRefinements, 
     ResponseBuilder rb, int shardNum) {
    
    FacetInfo fi = rb._facetInfo;
    
    ShardRequest shardsRefineRequestPivot = new ShardRequest();
    shardsRefineRequestPivot.shards = new String[] {rb.shards[shardNum]};
    shardsRefineRequestPivot.params = new ModifiableSolrParams(rb.req.getParams());

    // don't request any documents
    shardsRefineRequestPivot.params.remove(CommonParams.START);
    shardsRefineRequestPivot.params.set(CommonParams.ROWS, "0");
    
    shardsRefineRequestPivot.purpose |= ShardRequest.PURPOSE_REFINE_PIVOT_FACETS;
    shardsRefineRequestPivot.params.set(FacetParams.FACET, "true");
    shardsRefineRequestPivot.params.remove(FacetParams.FACET_PIVOT_MINCOUNT);
    shardsRefineRequestPivot.params.set(FacetParams.FACET_PIVOT_MINCOUNT, -1);
    shardsRefineRequestPivot.params.remove(FacetParams.FACET_PIVOT);
    shardsRefineRequestPivot.params.remove(FacetParams.FACET_OFFSET);
    
    for (int pivotIndex = 0; pivotIndex < fi.pivotFacets.size(); pivotIndex++) {
      String pivotFacetKey = fi.pivotFacets.getName(pivotIndex);
      PivotFacet pivotFacet = fi.pivotFacets.getVal(pivotIndex);

      List<PivotFacetValue> queuedRefinementsForShard = 
        pivotFacet.getQueuedRefinements(shardNum);

      if ( ! queuedRefinementsForShard.isEmpty() ) {
        
        String fieldsKey = PivotFacet.REFINE_PARAM + pivotRefinementCounter;
        String command;
        
        if (pivotFacet.localParams != null) {
          command = PIVOT_REFINE_PREFIX + pivotRefinementCounter + " "
            + pivotFacet.facetStr.substring(2);
        } else {
          command = PIVOT_REFINE_PREFIX + pivotRefinementCounter + "}"
            + pivotFacet.getKey();
        }
        
        shardsRefineRequestPivot.params.add(FacetParams.FACET_PIVOT, command);
        for (PivotFacetValue refinementValue : queuedRefinementsForShard) {
          String refinementStr = PivotFacetHelper
            .encodeRefinementValuePath(refinementValue.getValuePath());
          shardsRefineRequestPivot.params.add(fieldsKey, refinementStr);
          
        }
      }
      pivotRefinementCounter++;
    }
    
    rb.addRequest(this, shardsRefineRequestPivot);
  }
  
  public void modifyRequest(ResponseBuilder rb, SearchComponent who,ShardRequest sreq) {

    if (!rb.doFacets) return;
    
    if ((sreq.purpose & ShardRequest.PURPOSE_GET_TOP_IDS) != 0) {
      sreq.purpose |= ShardRequest.PURPOSE_GET_FACETS;
      
      FacetInfo fi = rb._facetInfo;
      if (fi == null) {
        rb._facetInfo = fi = new FacetInfo();
        fi.parse(rb.req.getParams(), rb);
      }
      
      modifyRequestForFieldFacets(rb, sreq, fi);

      modifyRequestForRangeFacets(sreq, fi);
      
      modifyRequestForPivotFacets(rb, sreq, fi.pivotFacets);
      
      sreq.params.remove(FacetParams.FACET_MINCOUNT);
      sreq.params.remove(FacetParams.FACET_OFFSET);
      
    } else {
      // turn off faceting on other requests
      sreq.params.set(FacetParams.FACET, "false");
      // we could optionally remove faceting params
    }
  }

  // we must get all the range buckets back in order to have coherent lists at the end, see SOLR-6154
  private void modifyRequestForRangeFacets(ShardRequest sreq, FacetInfo fi) {
    // Collect all the range fields.
    if (sreq.params.getParams(FacetParams.FACET_RANGE) == null) {
      return;
    }
    List<String> rangeFields = new ArrayList<>();
    for (String field : sreq.params.getParams(FacetParams.FACET_RANGE)) {
      rangeFields.add(field);
    }

    for (String field : rangeFields) {
      sreq.params.remove("f." + field + ".facet.mincount");
      sreq.params.add("f." + field + ".facet.mincount", "0");
    }
  }

  private void modifyRequestForFieldFacets(ResponseBuilder rb, ShardRequest sreq, FacetInfo fi) {
    for (DistribFieldFacet dff : fi.facets.values()) {
      
      String paramStart = "f." + dff.field + '.';
      sreq.params.remove(paramStart + FacetParams.FACET_MINCOUNT);
      sreq.params.remove(paramStart + FacetParams.FACET_OFFSET);
      
      dff.initialLimit = dff.limit <= 0 ? dff.limit : dff.offset + dff.limit;
      
      if (dff.sort.equals(FacetParams.FACET_SORT_COUNT)) {
        if (dff.limit > 0) {
          // set the initial limit higher to increase accuracy
          dff.initialLimit = doOverRequestMath(dff.initialLimit, dff.overrequestRatio, 
                                               dff.overrequestCount);
          dff.initialMincount = 0; // TODO: we could change this to 1, but would
                                   // then need more refinement for small facet
                                   // result sets?
        } else {
          // if limit==-1, then no need to artificially lower mincount to 0 if
          // it's 1
          dff.initialMincount = Math.min(dff.minCount, 1);
        }
      } else {
        // we're sorting by index order.
        // if minCount==0, we should always be able to get accurate results w/o
        // over-requesting or refining
        // if minCount==1, we should be able to get accurate results w/o
        // over-requesting, but we'll need to refine
        // if minCount==n (>1), we can set the initialMincount to
        // minCount/nShards, rounded up.
        // For example, we know that if minCount=10 and we have 3 shards, then
        // at least one shard must have a count of 4 for the term
        // For the minCount>1 case, we can generate too short of a list (miss
        // terms at the end of the list) unless limit==-1
        // For example: each shard could produce a list of top 10, but some of
        // those could fail to make it into the combined list (i.e.
        // we needed to go beyond the top 10 to generate the top 10 combined).
        // Overrequesting can help a little here, but not as
        // much as when sorting by count.
        if (dff.minCount <= 1) {
          dff.initialMincount = dff.minCount;
        } else {
          dff.initialMincount = (int) Math.ceil((double) dff.minCount / rb.slices.length);
        }
      }

      // Currently this is for testing only and allows overriding of the
      // facet.limit set to the shards
      dff.initialLimit = rb.req.getParams().getInt("facet.shard.limit", dff.initialLimit);
      
      sreq.params.set(paramStart + FacetParams.FACET_LIMIT, dff.initialLimit);
      sreq.params.set(paramStart + FacetParams.FACET_MINCOUNT, dff.initialMincount);

    }
  }
  
  private void modifyRequestForPivotFacets(ResponseBuilder rb,
                                           ShardRequest sreq, 
                                           SimpleOrderedMap<PivotFacet> pivotFacets) {
    for (Entry<String,PivotFacet> pfwEntry : pivotFacets) {
      PivotFacet pivot = pfwEntry.getValue();
      for (String pivotField : StrUtils.splitSmart(pivot.getKey(), ',')) {
        modifyRequestForIndividualPivotFacets(rb, sreq, pivotField);
      }
    }
  }
  
  private void modifyRequestForIndividualPivotFacets(ResponseBuilder rb, ShardRequest sreq, 
                                                     String fieldToOverRequest) {

    final SolrParams originalParams = rb.req.getParams();
    final String paramStart = "f." + fieldToOverRequest + ".";

    final int requestedLimit = originalParams.getFieldInt(fieldToOverRequest,
                                                          FacetParams.FACET_LIMIT, 100);
    sreq.params.remove(paramStart + FacetParams.FACET_LIMIT);

    final int offset = originalParams.getFieldInt(fieldToOverRequest,
                                                  FacetParams.FACET_OFFSET, 0);
    sreq.params.remove(paramStart + FacetParams.FACET_OFFSET);
    
    final double overRequestRatio = originalParams.getFieldDouble
      (fieldToOverRequest, FacetParams.FACET_OVERREQUEST_RATIO, 1.5);
    sreq.params.remove(paramStart + FacetParams.FACET_OVERREQUEST_RATIO);
    
    final int overRequestCount = originalParams.getFieldInt
      (fieldToOverRequest, FacetParams.FACET_OVERREQUEST_COUNT, 10);
    sreq.params.remove(paramStart + FacetParams.FACET_OVERREQUEST_COUNT);
    
    final int requestedMinCount = originalParams.getFieldInt
      (fieldToOverRequest, FacetParams.FACET_PIVOT_MINCOUNT, 1);
    sreq.params.remove(paramStart + FacetParams.FACET_PIVOT_MINCOUNT);

    final String defaultSort = (requestedLimit > 0)
      ? FacetParams.FACET_SORT_COUNT : FacetParams.FACET_SORT_INDEX;
    final String sort = originalParams.getFieldParam
      (fieldToOverRequest, FacetParams.FACET_SORT, defaultSort);

    int shardLimit = requestedLimit + offset;
    int shardMinCount = requestedMinCount;

    // per-shard mincount & overrequest
    if ( FacetParams.FACET_SORT_INDEX.equals(sort) && 
         1 < requestedMinCount && 
         0 < requestedLimit) {

      // We can divide the mincount by num shards rounded up, because unless 
      // a single shard has at least that many it can't compete...
      shardMinCount = (int) Math.ceil((double) requestedMinCount / rb.slices.length);

      // ...but we still need to overrequest to reduce chances of missing something
      shardLimit = doOverRequestMath(shardLimit, overRequestRatio, overRequestCount);

      // (for mincount <= 1, no overrequest needed)

    } else if ( FacetParams.FACET_SORT_COUNT.equals(sort) ) {
      if ( 0 < requestedLimit ) {
        shardLimit = doOverRequestMath(shardLimit, overRequestRatio, overRequestCount);
        shardMinCount = 0; 
      } else {
        shardMinCount = Math.min(requestedMinCount, 1);
      }
    } 
    sreq.params.set(paramStart + FacetParams.FACET_LIMIT, shardLimit);
    sreq.params.set(paramStart + FacetParams.FACET_PIVOT_MINCOUNT, shardMinCount);
  }
  
  private int doOverRequestMath(int limit, double ratio, int count) {
    // NOTE: normally, "1.0F < ratio"
    //
    // if the user chooses a ratio < 1, we allow it and don't "bottom out" at
    // the original limit until *after* we've also added the count.
    int adjustedLimit = (int) (limit * ratio) + count;
    return Math.max(limit, adjustedLimit);
  }
  
  @Override
  public void handleResponses(ResponseBuilder rb, ShardRequest sreq) {
    if (!rb.doFacets) return;
    
    if ((sreq.purpose & ShardRequest.PURPOSE_GET_FACETS) != 0) {
      countFacets(rb, sreq);
    } else {
      // at present PURPOSE_REFINE_FACETS and PURPOSE_REFINE_PIVOT_FACETS
      // don't co-exist in individual requests, but don't assume that
      // will always be the case
      if ((sreq.purpose & ShardRequest.PURPOSE_REFINE_FACETS) != 0) {
        refineFacets(rb, sreq);
      }
      if ((sreq.purpose & ShardRequest.PURPOSE_REFINE_PIVOT_FACETS) != 0) {
        refinePivotFacets(rb, sreq);
      }
    }
  }
  
  private void countFacets(ResponseBuilder rb, ShardRequest sreq) {
    FacetInfo fi = rb._facetInfo;
    
    for (ShardResponse srsp : sreq.responses) {
      int shardNum = rb.getShardNum(srsp.getShard());
      NamedList facet_counts = null;
      try {
        facet_counts = (NamedList) srsp.getSolrResponse().getResponse().get("facet_counts");
      } catch (Exception ex) {
        if (rb.req.getParams().getBool(ShardParams.SHARDS_TOLERANT, false)) {
          continue; // looks like a shard did not return anything
        }
        throw new SolrException(ErrorCode.SERVER_ERROR,
            "Unable to read facet info for shard: " + srsp.getShard(), ex);
      }
      
      // handle facet queries
      NamedList facet_queries = (NamedList) facet_counts.get("facet_queries");
      if (facet_queries != null) {
        for (int i = 0; i < facet_queries.size(); i++) {
          String returnedKey = facet_queries.getName(i);
          long count = ((Number) facet_queries.getVal(i)).longValue();
          QueryFacet qf = fi.queryFacets.get(returnedKey);
          qf.count += count;
        }
      }
      
      // step through each facet.field, adding results from this shard
      NamedList facet_fields = (NamedList) facet_counts.get("facet_fields");
      
      if (facet_fields != null) {
        for (DistribFieldFacet dff : fi.facets.values()) {
          dff.add(shardNum, (NamedList) facet_fields.get(dff.getKey()), dff.initialLimit);
        }
      }

      // Distributed facet_dates
      doDistribDates(fi, facet_counts);

      // Distributed facet_ranges
      doDistribRanges(fi, facet_counts);

      // Distributed facet_intervals
      doDistribIntervals(fi, facet_counts);
      
      // Distributed facet_pivots - this is just the per shard collection,
      // refinement reqs still needed (below) once we've considered every shard
      doDistribPivots(rb, shardNum, facet_counts);

    } // end for-each-response-in-shard-request...
    
    // refine each pivot based on the new shard data
    for (Entry<String,PivotFacet> pivotFacet : fi.pivotFacets) {
      pivotFacet.getValue().queuePivotRefinementRequests();
    }
    
    //
    // This code currently assumes that there will be only a single
    // request ((with responses from all shards) sent out to get facets...
    // otherwise we would need to wait until all facet responses were received.
    //
    for (DistribFieldFacet dff : fi.facets.values()) {
      // no need to check these facets for refinement
      if (dff.initialLimit <= 0 && dff.initialMincount <= 1) continue;

      // only other case where index-sort doesn't need refinement is if minCount==0
      if (dff.minCount <= 1 && dff.sort.equals(FacetParams.FACET_SORT_INDEX)) continue;

      @SuppressWarnings("unchecked") // generic array's are annoying
      List<String>[] tmp = (List<String>[]) new List[rb.shards.length];
      dff._toRefine = tmp;

      ShardFacetCount[] counts = dff.getCountSorted();
      int ntop = Math.min(counts.length, 
                          dff.limit >= 0 ? dff.offset + dff.limit : Integer.MAX_VALUE);
      long smallestCount = counts.length == 0 ? 0 : counts[ntop - 1].count;
      
      for (int i = 0; i < counts.length; i++) {
        ShardFacetCount sfc = counts[i];
        boolean needRefinement = false;

        if (i < ntop) {
          // automatically flag the top values for refinement
          // this should always be true for facet.sort=index
          needRefinement = true;
        } else {
          // this logic should only be invoked for facet.sort=index (for now)
          
          // calculate the maximum value that this term may have
          // and if it is >= smallestCount, then flag for refinement
          long maxCount = sfc.count;
          for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
            FixedBitSet fbs = dff.counted[shardNum];
            // fbs can be null if a shard request failed
            if (fbs != null && (sfc.termNum >= fbs.length() || !fbs.get(sfc.termNum))) {
              // if missing from this shard, add the max it could be
              maxCount += dff.maxPossible(sfc, shardNum);
            }
          }
          if (maxCount >= smallestCount) {
            // TODO: on a tie, we could check the term values
            needRefinement = true;
          }
        }

        if (needRefinement) {
          // add a query for each shard missing the term that needs refinement
          for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
            FixedBitSet fbs = dff.counted[shardNum];
            // fbs can be null if a shard request failed
            if (fbs != null &&
                (sfc.termNum >= fbs.length() || !fbs.get(sfc.termNum)) &&
                dff.maxPossible(sfc, shardNum) > 0) {

              dff.needRefinements = true;
              List<String> lst = dff._toRefine[shardNum];
              if (lst == null) {
                lst = dff._toRefine[shardNum] = new ArrayList<>();
              }
              lst.add(sfc.name);
            }
          }
        }
      }
    }
    removeFieldFacetsUnderLimits(rb);
    removeRangeFacetsUnderLimits(rb);
    removeQueryFacetsUnderLimits(rb);

  }

  private void removeQueryFacetsUnderLimits(ResponseBuilder rb) {
    if (rb.stage != ResponseBuilder.STAGE_EXECUTE_QUERY) {
      return;
    }
    FacetInfo fi = rb._facetInfo;
    Map<String, QueryFacet> query_facets = fi.queryFacets;
    if (query_facets == null) {
      return;
    }
    LinkedHashMap<String, QueryFacet> newQueryFacets = new LinkedHashMap<>();

    // The
    int minCount = rb.req.getParams().getInt(FacetParams.FACET_MINCOUNT, 0);
    boolean replace = false;
    for (Map.Entry<String, QueryFacet> ent : query_facets.entrySet()) {
      if (ent.getValue().count >= minCount) {
        newQueryFacets.put(ent.getKey(), ent.getValue());
      } else {
        log.trace("Removing facetQuery/key: " + ent.getKey() + "/" + ent.getValue().toString() + " mincount=" + minCount);
        replace = true;
      }
    }
    if (replace) {
      fi.queryFacets = newQueryFacets;
    }
  }

  private void removeRangeFacetsUnderLimits(ResponseBuilder rb) {
    if (rb.stage != ResponseBuilder.STAGE_EXECUTE_QUERY) {
      return;
    }

    FacetInfo fi = rb._facetInfo;

    @SuppressWarnings("unchecked")
    SimpleOrderedMap<SimpleOrderedMap<Object>> facet_ranges =
        (SimpleOrderedMap<SimpleOrderedMap<Object>>)
            fi.rangeFacets;

    if (facet_ranges == null) {
      return;
    }

    // go through each facet_range
    for (Map.Entry<String, SimpleOrderedMap<Object>> entry : facet_ranges) {
      boolean replace = false;
      final String field = entry.getKey();
      int minCount = rb.req.getParams().getFieldInt(field, FacetParams.FACET_MINCOUNT, 0);
      if (minCount == 0) {
        continue;
      }

      @SuppressWarnings("unchecked")
      NamedList<Integer> vals
          = (NamedList<Integer>) facet_ranges.get(field).get("counts");
      NamedList newList = new NamedList();
      for (Map.Entry<String, Integer> pair : vals) {
        if (pair.getValue() >= minCount) {
          newList.add(pair.getKey(), pair.getValue());
        } else {
          log.trace("Removing facet/key: " + pair.getKey() + "/" + pair.getValue().toString() + " mincount=" + minCount);
          replace = true;
        }
      }
      if (replace) {
        vals.clear();
        vals.addAll(newList);
      }
    }
  }
  private void removeFieldFacetsUnderLimits(ResponseBuilder rb) {
    if (rb.stage != ResponseBuilder.STAGE_DONE) {
      return;
    }

    FacetInfo fi = rb._facetInfo;
    if (fi.facets == null) {
      return;
    }
    // Do field facets
    for (Entry<String, DistribFieldFacet> ent : fi.facets.entrySet()) {
      String field = ent.getKey();
      int minCount = rb.req.getParams().getFieldInt(field, FacetParams.FACET_MINCOUNT, 0);
      if (minCount == 0) { // return them all
        continue;
      }
      ent.getValue().respectMinCount(minCount);
    }
  }

  // The implementation below uses the first encountered shard's
  // facet_intervals as the basis for subsequent shards' data to be merged.
  private void doDistribIntervals(FacetInfo fi, NamedList facet_counts) {
    @SuppressWarnings("unchecked")
    SimpleOrderedMap<SimpleOrderedMap<Integer>> facet_intervals =
        (SimpleOrderedMap<SimpleOrderedMap<Integer>>)
            facet_counts.get("facet_intervals");

    if (facet_intervals != null) {

      for (Map.Entry<String, SimpleOrderedMap<Integer>> entry : facet_intervals) {
        final String field = entry.getKey();
        SimpleOrderedMap<Integer> existingCounts = fi.intervalFacets.get(field);
        if (existingCounts == null) {
          // first time we've seen this field, no merging
          fi.intervalFacets.add(field, entry.getValue());

        } else {
          // not the first time, merge current field counts
          Iterator<Map.Entry<String, Integer>> newItr = entry.getValue().iterator();
          Iterator<Map.Entry<String, Integer>> exItr = existingCounts.iterator();

          // all intervals should be returned by each shard, even if they have zero count,
          // and in the same order
          while (exItr.hasNext()) {
            Map.Entry<String, Integer> exItem = exItr.next();
            if (!newItr.hasNext()) {
              throw new SolrException(ErrorCode.SERVER_ERROR,
                  "Interval facet shard response missing key: " + exItem.getKey());
            }
            Map.Entry<String, Integer> newItem = newItr.next();
            if (!newItem.getKey().equals(exItem.getKey())) {
              throw new SolrException(ErrorCode.SERVER_ERROR,
                  "Interval facet shard response has extra key: " + newItem.getKey());
            }
            exItem.setValue(exItem.getValue() + newItem.getValue());
          }
          if (newItr.hasNext()) {
            throw new SolrException(ErrorCode.SERVER_ERROR,
                "Interval facet shard response has at least one extra key: "
                + newItr.next().getKey());
          }
        }
      }
    }
  }

  //
  // The implementation below uses the first encountered shard's
  // facet_ranges as the basis for subsequent shards' data to be merged.
  private void doDistribRanges(FacetInfo fi, NamedList facet_counts) {
    @SuppressWarnings("unchecked")
    SimpleOrderedMap<SimpleOrderedMap<Object>> facet_ranges =
      (SimpleOrderedMap<SimpleOrderedMap<Object>>)
      facet_counts.get("facet_ranges");

    if (facet_ranges != null) {

      // go through each facet_range
      for (Map.Entry<String,SimpleOrderedMap<Object>> entry : facet_ranges) {
        final String field = entry.getKey();
        if (fi.rangeFacets.get(field) == null) {
          // first time we've seen this field, no merging
          fi.rangeFacets.add(field, entry.getValue());

        } else {
          // not the first time, merge current field counts

          @SuppressWarnings("unchecked")
          NamedList<Integer> shardFieldValues
            = (NamedList<Integer>) entry.getValue().get("counts");

          @SuppressWarnings("unchecked")
          NamedList<Integer> existFieldValues
            = (NamedList<Integer>) fi.rangeFacets.get(field).get("counts");

          for (Map.Entry<String,Integer> existPair : existFieldValues) {
            final String key = existPair.getKey();
            // can be null if inconsistencies in shards responses
            Integer newValue = shardFieldValues.get(key);
            if  (null != newValue) {
              Integer oldValue = existPair.getValue();
              existPair.setValue(oldValue + newValue);
            }
          }
        }
      }
    }
  }

  //
  // The implementation below uses the first encountered shard's
  // facet_dates as the basis for subsequent shards' data to be merged.
  // (the "NOW" param should ensure consistency)
  private void doDistribDates(FacetInfo fi, NamedList facet_counts) {
    @SuppressWarnings("unchecked")
    SimpleOrderedMap<SimpleOrderedMap<Object>> facet_dates =
      (SimpleOrderedMap<SimpleOrderedMap<Object>>)
      facet_counts.get("facet_dates");

    if (facet_dates != null) {

      // go through each facet_date
      for (Map.Entry<String,SimpleOrderedMap<Object>> entry : facet_dates) {
        final String field = entry.getKey();
        if (fi.dateFacets.get(field) == null) {
          // first time we've seen this field, no merging
          fi.dateFacets.add(field, entry.getValue());

        } else {
          // not the first time, merge current field

          SimpleOrderedMap<Object> shardFieldValues
            = entry.getValue();
          SimpleOrderedMap<Object> existFieldValues
            = fi.dateFacets.get(field);

          for (Map.Entry<String,Object> existPair : existFieldValues) {
            final String key = existPair.getKey();
            if (key.equals("gap") ||
                key.equals("end") ||
                key.equals("start")) {
              // we can skip these, must all be the same across shards
              continue;
            }
            // can be null if inconsistencies in shards responses
            Integer newValue = (Integer) shardFieldValues.get(key);
            if  (null != newValue) {
              Integer oldValue = ((Integer) existPair.getValue());
              existPair.setValue(oldValue + newValue);
            }
          }
        }
      }
    }
  }

  private void doDistribPivots(ResponseBuilder rb, int shardNum, NamedList facet_counts) {
    @SuppressWarnings("unchecked")
    SimpleOrderedMap<List<NamedList<Object>>> facet_pivot 
      = (SimpleOrderedMap<List<NamedList<Object>>>) facet_counts.get(PIVOT_KEY);
    
    if (facet_pivot != null) {
      for (Map.Entry<String,List<NamedList<Object>>> pivot : facet_pivot) {
        final String pivotName = pivot.getKey();
        PivotFacet facet = rb._facetInfo.pivotFacets.get(pivotName);
        facet.mergeResponseFromShard(shardNum, rb, pivot.getValue());
      }
    }
  }


  private void refineFacets(ResponseBuilder rb, ShardRequest sreq) {
    FacetInfo fi = rb._facetInfo;

    for (ShardResponse srsp : sreq.responses) {
      // int shardNum = rb.getShardNum(srsp.shard);
      NamedList facet_counts = (NamedList) srsp.getSolrResponse().getResponse().get("facet_counts");
      NamedList facet_fields = (NamedList) facet_counts.get("facet_fields");
      
      if (facet_fields == null) continue; // this can happen when there's an exception
      
      for (int i = 0; i < facet_fields.size(); i++) {
        String key = facet_fields.getName(i);
        DistribFieldFacet dff = fi.facets.get(key);
        if (dff == null) continue;

        NamedList shardCounts = (NamedList) facet_fields.getVal(i);
        
        for (int j = 0; j < shardCounts.size(); j++) {
          String name = shardCounts.getName(j);
          long count = ((Number) shardCounts.getVal(j)).longValue();
          ShardFacetCount sfc = dff.counts.get(name);
          if (sfc == null) {
            // we got back a term we didn't ask for?
            log.error("Unexpected term returned for facet refining. key=" + key
                      + " term='" + name + "'" + "\n\trequest params=" + sreq.params
                      + "\n\ttoRefine=" + dff._toRefine + "\n\tresponse="
                      + shardCounts);
            continue;
          }
          sfc.count += count;
        }
      }
    }
  }
  
  private void refinePivotFacets(ResponseBuilder rb, ShardRequest sreq) {
    // This is after the shard has returned the refinement request
    FacetInfo fi = rb._facetInfo;
    for (ShardResponse srsp : sreq.responses) {
      
      int shardNumber = rb.getShardNum(srsp.getShard());
      
      NamedList facetCounts = (NamedList) srsp.getSolrResponse().getResponse().get("facet_counts");
      
      @SuppressWarnings("unchecked")
      NamedList<List<NamedList<Object>>> pivotFacetResponsesFromShard 
        = (NamedList<List<NamedList<Object>>>) facetCounts.get(PIVOT_KEY);

      if (null == pivotFacetResponsesFromShard) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, 
                                "No pivot refinement response from shard: " + srsp.getShard());
      }
      
      for (Entry<String,List<NamedList<Object>>> pivotFacetResponseFromShard : pivotFacetResponsesFromShard) {
        PivotFacet masterPivotFacet = fi.pivotFacets.get(pivotFacetResponseFromShard.getKey());
        masterPivotFacet.mergeResponseFromShard(shardNumber, rb, pivotFacetResponseFromShard.getValue());  
        masterPivotFacet.removeAllRefinementsForShard(shardNumber);
      }
    }
    
    if (allPivotFacetsAreFullyRefined(fi)) {
      for (Entry<String,PivotFacet> pf : fi.pivotFacets) {
        pf.getValue().queuePivotRefinementRequests();
      }
      reQueuePivotFacetShardRequests(rb);
    }
  }
  
  private boolean allPivotFacetsAreFullyRefined(FacetInfo fi) {
    
    for (Entry<String,PivotFacet> pf : fi.pivotFacets) {
      if (pf.getValue().isRefinementsRequired()) {
        return false;
      }
    }
    return true;
  }
  
  private boolean doAnyPivotFacetRefinementRequestsExistForShard(FacetInfo fi,
                                                                 int shardNum) {
    for (int i = 0; i < fi.pivotFacets.size(); i++) {
      PivotFacet pf = fi.pivotFacets.getVal(i);
      if ( ! pf.getQueuedRefinements(shardNum).isEmpty() ) {
        return true;
      }
    }
    return false;
  }
  
  private void reQueuePivotFacetShardRequests(ResponseBuilder rb) {
    for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
      if (doAnyPivotFacetRefinementRequestsExistForShard(rb._facetInfo, shardNum)) {
        enqueuePivotFacetShardRequests(null, rb, shardNum);
      }
    }
  }
  
  @Override
  public void finishStage(ResponseBuilder rb) {
    pivotRefinementCounter = 0;
    if (!rb.doFacets || rb.stage != ResponseBuilder.STAGE_GET_FIELDS) return;
    // wait until STAGE_GET_FIELDS
    // so that "result" is already stored in the response (for aesthetics)
    
    FacetInfo fi = rb._facetInfo;
    
    NamedList<Object> facet_counts = new SimpleOrderedMap<>();
    
    NamedList<Number> facet_queries = new SimpleOrderedMap<>();
    facet_counts.add("facet_queries", facet_queries);
    for (QueryFacet qf : fi.queryFacets.values()) {
      facet_queries.add(qf.getKey(), num(qf.count));
    }
    
    NamedList<Object> facet_fields = new SimpleOrderedMap<>();
    facet_counts.add("facet_fields", facet_fields);
    
    for (DistribFieldFacet dff : fi.facets.values()) {
      // order is important for facet values, so use NamedList
      NamedList<Object> fieldCounts = new NamedList<>(); 
      facet_fields.add(dff.getKey(), fieldCounts);
      
      ShardFacetCount[] counts;
      boolean countSorted = dff.sort.equals(FacetParams.FACET_SORT_COUNT);
      if (countSorted) {
        counts = dff.countSorted;
        if (counts == null || dff.needRefinements) {
          counts = dff.getCountSorted();
        }
      } else if (dff.sort.equals(FacetParams.FACET_SORT_INDEX)) {
        counts = dff.getLexSorted();
      } else { // TODO: log error or throw exception?
        counts = dff.getLexSorted();
      }
      
      if (countSorted) {
        int end = dff.limit < 0 
          ? counts.length : Math.min(dff.offset + dff.limit, counts.length);
        for (int i = dff.offset; i < end; i++) {
          if (counts[i].count < dff.minCount) {
            break;
          }
          fieldCounts.add(counts[i].name, num(counts[i].count));
        }
      } else {
        int off = dff.offset;
        int lim = dff.limit >= 0 ? dff.limit : Integer.MAX_VALUE;
        
        // index order...
        for (int i = 0; i < counts.length; i++) {
          long count = counts[i].count;
          if (count < dff.minCount) continue;
          if (off > 0) {
            off--;
            continue;
          }
          if (lim <= 0) {
            break;
          }
          lim--;
          fieldCounts.add(counts[i].name, num(count));
        }
      }

      if (dff.missing) {
        fieldCounts.add(null, num(dff.missingCount));
      }
    }

    facet_counts.add("facet_dates", fi.dateFacets);
    facet_counts.add("facet_ranges", fi.rangeFacets);
    facet_counts.add("facet_intervals", fi.intervalFacets);

    if (fi.pivotFacets != null && fi.pivotFacets.size() > 0) {
      facet_counts.add(PIVOT_KEY, createPivotFacetOutput(rb));
    }

    rb.rsp.add("facet_counts", facet_counts);

    rb._facetInfo = null;  // could be big, so release asap
  }

  private SimpleOrderedMap<List<NamedList<Object>>> createPivotFacetOutput(ResponseBuilder rb) {
    
    SimpleOrderedMap<List<NamedList<Object>>> combinedPivotFacets = new SimpleOrderedMap<>();
    for (Entry<String,PivotFacet> entry : rb._facetInfo.pivotFacets) {
      String key = entry.getKey();
      PivotFacet pivot = entry.getValue();
      List<NamedList<Object>> trimmedPivots = pivot.getTrimmedPivotsAsListOfNamedLists(rb);
      if (null == trimmedPivots) {
        trimmedPivots = Collections.<NamedList<Object>>emptyList();
      }

      combinedPivotFacets.add(key, trimmedPivots);
    }
    return combinedPivotFacets;
  }

  // use <int> tags for smaller facet counts (better back compatibility)
  private Number num(long val) {
   if (val < Integer.MAX_VALUE) return (int)val;
   else return val;
  }
  private Number num(Long val) {
    if (val.longValue() < Integer.MAX_VALUE) return val.intValue();
    else return val;
  }


  /////////////////////////////////////////////
  ///  SolrInfoMBean
  ////////////////////////////////////////////

  @Override
  public String getDescription() {
    return "Handle Faceting";
  }

  @Override
  public URL[] getDocs() {
    return null;
  }

  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class FacetInfo {

    public LinkedHashMap<String,QueryFacet> queryFacets;
    public LinkedHashMap<String,DistribFieldFacet> facets;
    public SimpleOrderedMap<SimpleOrderedMap<Object>> dateFacets
      = new SimpleOrderedMap<>();
    public SimpleOrderedMap<SimpleOrderedMap<Object>> rangeFacets
      = new SimpleOrderedMap<>();
    public SimpleOrderedMap<SimpleOrderedMap<Integer>> intervalFacets
      = new SimpleOrderedMap<>();
    public SimpleOrderedMap<PivotFacet> pivotFacets
      = new SimpleOrderedMap<>();

    void parse(SolrParams params, ResponseBuilder rb) {
      queryFacets = new LinkedHashMap<>();
      facets = new LinkedHashMap<>();

      String[] facetQs = params.getParams(FacetParams.FACET_QUERY);
      if (facetQs != null) {
        for (String query : facetQs) {
          QueryFacet queryFacet = new QueryFacet(rb, query);
          queryFacets.put(queryFacet.getKey(), queryFacet);
        }
      }
      
      String[] facetFs = params.getParams(FacetParams.FACET_FIELD);
      if (facetFs != null) {
        
        for (String field : facetFs) {
          DistribFieldFacet ff = new DistribFieldFacet(rb, field);
          facets.put(ff.getKey(), ff);
        }
      }

      // Develop Pivot Facet Information
      String[] facetPFs = params.getParams(FacetParams.FACET_PIVOT);
      if (facetPFs != null) {
        for (String fieldGroup : facetPFs) {
          PivotFacet pf = new PivotFacet(rb, fieldGroup);
          pivotFacets.add(pf.getKey(), pf);
        }
      }
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class FacetBase {
    String facetType; // facet.field, facet.query, etc (make enum?)
    String facetStr; // original parameter value of facetStr
    String facetOn; // the field or query, absent localParams if appropriate
    private String key; // label in the response for the result... 
                        // "foo" for {!key=foo}myfield
    SolrParams localParams; // any local params for the facet
    
    public FacetBase(ResponseBuilder rb, String facetType, String facetStr) {
      this.facetType = facetType;
      this.facetStr = facetStr;
      try {
        this.localParams = QueryParsing.getLocalParams(facetStr,
                                                       rb.req.getParams());
      } catch (SyntaxError e) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
      }
      this.facetOn = facetStr;
      this.key = facetStr;
      
      if (localParams != null) {
        // remove local params unless it's a query
        if (!facetType.equals(FacetParams.FACET_QUERY)) {
          facetOn = localParams.get(CommonParams.VALUE);
          key = facetOn;
        }
        
        key = localParams.get(CommonParams.OUTPUT_KEY, key);
      }
    }
    
    /** returns the key in the response that this facet will be under */
    public String getKey() { return key; }
    public String getType() { return facetType; }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class QueryFacet extends FacetBase {
    public long count;
    
    public QueryFacet(ResponseBuilder rb, String facetStr) {
      super(rb, FacetParams.FACET_QUERY, facetStr);
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class FieldFacet extends FacetBase {
    public String field; // the field to facet on... "myfield" for
                         // {!key=foo}myfield
    public FieldType ftype;
    public int offset;
    public int limit;
    public int minCount;
    public String sort;
    public boolean missing;
    public String prefix;
    public long missingCount;
    
    public FieldFacet(ResponseBuilder rb, String facetStr) {
      super(rb, FacetParams.FACET_FIELD, facetStr);
      fillParams(rb, rb.req.getParams(), facetOn);
    }
    
    protected void fillParams(ResponseBuilder rb, SolrParams params, String field) {
      this.field = field;
      this.ftype = rb.req.getSchema().getFieldTypeNoEx(this.field);
      this.offset = params.getFieldInt(field, FacetParams.FACET_OFFSET, 0);
      this.limit = params.getFieldInt(field, FacetParams.FACET_LIMIT, 100);
      Integer mincount = params.getFieldInt(field, FacetParams.FACET_MINCOUNT);
      if (mincount == null) {
        Boolean zeros = params.getFieldBool(field, FacetParams.FACET_ZEROS);
        // mincount = (zeros!=null && zeros) ? 0 : 1;
        mincount = (zeros != null && !zeros) ? 1 : 0;
        // current default is to include zeros.
      }
      this.minCount = mincount;
      this.missing = params.getFieldBool(field, FacetParams.FACET_MISSING, false);
      // default to sorting by count if there is a limit.
      this.sort = params.getFieldParam(field, FacetParams.FACET_SORT,
                                       (limit > 0 ? 
                                        FacetParams.FACET_SORT_COUNT
                                        : FacetParams.FACET_SORT_INDEX));
      if (this.sort.equals(FacetParams.FACET_SORT_COUNT_LEGACY)) {
        this.sort = FacetParams.FACET_SORT_COUNT;
      } else if (this.sort.equals(FacetParams.FACET_SORT_INDEX_LEGACY)) {
        this.sort = FacetParams.FACET_SORT_INDEX;
      }
      this.prefix = params.getFieldParam(field, FacetParams.FACET_PREFIX);
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  @SuppressWarnings("rawtypes")
  public static class DistribFieldFacet extends FieldFacet {
    public List<String>[] _toRefine; // a List<String> of refinements needed,
                                     // one for each shard.
    
    // SchemaField sf; // currently unneeded
    
    // the max possible count for a term appearing on no list
    public long missingMaxPossible;
    // the max possible count for a missing term for each shard (indexed by
    // shardNum)
    public long[] missingMax;
    // a bitset for each shard, keeping track of which terms seen
    public FixedBitSet[] counted; 
    public HashMap<String,ShardFacetCount> counts = new HashMap<>(128);
    public int termNum;
    
    public int initialLimit; // how many terms requested in first phase
    public int initialMincount; // mincount param sent to each shard
    public double overrequestRatio;
    public int overrequestCount;
    public boolean needRefinements;
    public ShardFacetCount[] countSorted;
    
    DistribFieldFacet(ResponseBuilder rb, String facetStr) {
      super(rb, facetStr);
      // sf = rb.req.getSchema().getField(field);
      missingMax = new long[rb.shards.length];
      counted = new FixedBitSet[rb.shards.length];
    }
    
    protected void fillParams(ResponseBuilder rb, SolrParams params, String field) {
      super.fillParams(rb, params, field);
      this.overrequestRatio
        = params.getFieldDouble(field, FacetParams.FACET_OVERREQUEST_RATIO, 1.5);
      this.overrequestCount 
        = params.getFieldInt(field, FacetParams.FACET_OVERREQUEST_COUNT, 10);
                             
    }
    
    void add(int shardNum, NamedList shardCounts, int numRequested) {
      // shardCounts could be null if there was an exception
      int sz = shardCounts == null ? 0 : shardCounts.size();
      int numReceived = sz;
      
      FixedBitSet terms = new FixedBitSet(termNum + sz);

      long last = 0;
      for (int i = 0; i < sz; i++) {
        String name = shardCounts.getName(i);
        long count = ((Number) shardCounts.getVal(i)).longValue();
        if (name == null) {
          missingCount += count;
          numReceived--;
        } else {
          ShardFacetCount sfc = counts.get(name);
          if (sfc == null) {
            sfc = new ShardFacetCount();
            sfc.name = name;
            sfc.indexed = ftype == null ? sfc.name : ftype.toInternal(sfc.name);
            sfc.termNum = termNum++;
            counts.put(name, sfc);
          }
          sfc.count += count;
          terms.set(sfc.termNum);
          last = count;
        }
      }
      
      // the largest possible missing term is initialMincount if we received
      // less than the number requested.
      if (numRequested < 0 || numRequested != 0 && numReceived < numRequested) {
        last = initialMincount;
      }
      
      missingMaxPossible += last;
      missingMax[shardNum] = last;
      counted[shardNum] = terms;
    }
    
    public ShardFacetCount[] getLexSorted() {
      ShardFacetCount[] arr 
        = counts.values().toArray(new ShardFacetCount[counts.size()]);
      Arrays.sort(arr, new Comparator<ShardFacetCount>() {
        @Override
        public int compare(ShardFacetCount o1, ShardFacetCount o2) {
          return o1.indexed.compareTo(o2.indexed);
        }
      });
      countSorted = arr;
      return arr;
    }
    
    public ShardFacetCount[] getCountSorted() {
      ShardFacetCount[] arr 
        = counts.values().toArray(new ShardFacetCount[counts.size()]);
      Arrays.sort(arr, new Comparator<ShardFacetCount>() {
        @Override
        public int compare(ShardFacetCount o1, ShardFacetCount o2) {
          if (o2.count < o1.count) return -1;
          else if (o1.count < o2.count) return 1;
          return o1.indexed.compareTo(o2.indexed);
        }
      });
      countSorted = arr;
      return arr;
    }
    
    // returns the max possible value this ShardFacetCount could have for this shard
    // (assumes the shard did not report a count for this value)
    long maxPossible(ShardFacetCount sfc, int shardNum) {
      return missingMax[shardNum];
      // TODO: could store the last term in the shard to tell if this term
      // comes before or after it. If it comes before, we could subtract 1
    }

    public void respectMinCount(long minCount) {
      HashMap<String, ShardFacetCount> newOne = new HashMap<>();
      boolean replace = false;
      for (Map.Entry<String, ShardFacetCount> ent : counts.entrySet()) {
        if (ent.getValue().count >= minCount) {
          newOne.put(ent.getKey(), ent.getValue());
        } else {
          log.trace("Removing facet/key: " + ent.getKey() + "/" + ent.getValue().toString() + " mincount=" + minCount);
          replace = true;
        }
      }
      if (replace) {
        counts = newOne;
      }
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class ShardFacetCount {
    public String name;
    // the indexed form of the name... used for comparisons
    public String indexed; 
    public long count;
    public int termNum; // term number starting at 0 (used in bit arrays)
    
    @Override
    public String toString() {
      return "{term=" + name + ",termNum=" + termNum + ",count=" + count + "}";
    }
  }
}
