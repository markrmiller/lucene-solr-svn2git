package org.apache.solr.handler.component;

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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.spelling.suggest.SolrSuggester;
import org.apache.solr.spelling.suggest.SuggesterOptions;
import org.apache.solr.spelling.suggest.SuggesterParams;
import org.apache.solr.spelling.suggest.SuggesterResult;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SuggestComponent: interacts with multiple {@link SolrSuggester} to serve up suggestions
 * Responsible for routing commands and queries to the appropriate {@link SolrSuggester}
 * and for initializing them as specified by SolrConfig
 */
public class SuggestComponent extends SearchComponent implements SolrCoreAware, SuggesterParams {
  private static final Logger LOG = LoggerFactory.getLogger(SuggestComponent.class);
  
  /** Name used to identify whether the user query concerns this component */
  public static final String COMPONENT_NAME = "suggest";
  
  /** Name assigned to an unnamed suggester (at most one suggester) can be unnamed */
  private static final String DEFAULT_DICT_NAME = SolrSuggester.DEFAULT_DICT_NAME;
  
  /** SolrConfig label to identify  Config time settings */
  private static final String CONFIG_PARAM_LABEL = "suggester";
  
  /** SolrConfig label to identify boolean value to build suggesters on commit */
  private static final String BUILD_ON_COMMIT_LABEL = "buildOnCommit";
  
  /** SolrConfig label to identify boolean value to build suggesters on optimize */
  private static final String BUILD_ON_OPTIMIZE_LABEL = "buildOnOptimize";
  
  @SuppressWarnings("unchecked")
  protected NamedList initParams;
  
  /**
   * Key is the dictionary name used in SolrConfig, value is the corrosponding {@link SolrSuggester}
   */
  protected Map<String, SolrSuggester> suggesters = new ConcurrentHashMap<String, SolrSuggester>();
  
  /** Container for various labels used in the responses generated by this component */
  private static class SuggesterResultLabels {
    static final String SUGGEST = "suggest";
    static final String SUGGESTIONS = "suggestions";
    static final String SUGGESTION = "suggestion";
    static final String SUGGESTION_NUM_FOUND = "numFound";
    static final String SUGGESTION_TERM = "term";
    static final String SUGGESTION_WEIGHT = "weight";
    static final String SUGGESTION_PAYLOAD = "payload";
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public void init(NamedList args) {
    super.init(args);
    this.initParams = args;
  }
  
  @Override
  public void inform(SolrCore core) {
    if (initParams != null) {
      LOG.info("Initializing SuggesterComponent");
      boolean hasDefault = false;
      for (int i = 0; i < initParams.size(); i++) {
        if (initParams.getName(i).equals(CONFIG_PARAM_LABEL)) {
          NamedList suggesterParams = (NamedList) initParams.getVal(i);
          SolrSuggester suggester = new SolrSuggester();
          String dictionary = suggester.init(suggesterParams, core);
          if (dictionary != null) {
            boolean isDefault = dictionary.equals(DEFAULT_DICT_NAME);
            if (isDefault && !hasDefault) {
              hasDefault = true;
            } else if (isDefault){
              throw new RuntimeException("More than one dictionary is missing name.");
            }
            suggesters.put(dictionary, suggester);
          } else {
            if (!hasDefault){
              suggesters.put(DEFAULT_DICT_NAME, suggester);
              hasDefault = true;
            } else {
              throw new RuntimeException("More than one dictionary is missing name.");
            }
          }
          
          // Register event listeners for this Suggester
          core.registerFirstSearcherListener(new SuggesterListener(core, suggester, false, false));
          boolean buildOnCommit = Boolean.parseBoolean((String) suggesterParams.get(BUILD_ON_COMMIT_LABEL));
          boolean buildOnOptimize = Boolean.parseBoolean((String) suggesterParams.get(BUILD_ON_OPTIMIZE_LABEL));
          if (buildOnCommit || buildOnOptimize) {
            LOG.info("Registering newSearcher listener for suggester: " + suggester.getName());
            core.registerNewSearcherListener(new SuggesterListener(core, suggester, buildOnCommit, buildOnOptimize));
          }
        }
      }
    }
  }

  /** Responsible for issuing build and rebload command to the specified {@link SolrSuggester} */
  @Override
  public void prepare(ResponseBuilder rb) throws IOException {
    SolrParams params = rb.req.getParams();
    LOG.info("Suggester prepare with : " + params);
    if (!params.getBool(COMPONENT_NAME, false)) {
      return;
    }

    SolrSuggester suggester = getSuggester(params);
    if (suggester == null) {
      throw new IllegalArgumentException("Error in configuration, no suggester found");
    }
    if (params.getBool(SUGGEST_BUILD, false)) {
      suggester.build(rb.req.getCore(), rb.req.getSearcher());
      rb.rsp.add("command", "build");
    } else if (params.getBool(SUGGEST_RELOAD, false)) {
      suggester.reload(rb.req.getCore(), rb.req.getSearcher());
      rb.rsp.add("command", "reload");
    }
  }
  
  /** Dispatch shard request in <code>STAGE_EXECUTE_QUERY</code> stage */
  @Override
  public int distributedProcess(ResponseBuilder rb) {
    SolrParams params = rb.req.getParams();
    LOG.info("Suggester distributedProcess with : " + params);
    if (rb.stage < ResponseBuilder.STAGE_EXECUTE_QUERY) 
      return ResponseBuilder.STAGE_EXECUTE_QUERY;
    if (rb.stage == ResponseBuilder.STAGE_EXECUTE_QUERY) {
      ShardRequest sreq = new ShardRequest();
      sreq.purpose = ShardRequest.PURPOSE_GET_TOP_IDS;
      sreq.params = new ModifiableSolrParams(rb.req.getParams());
      sreq.params.remove(ShardParams.SHARDS);
      rb.addRequest(this, sreq);
      return ResponseBuilder.STAGE_GET_FIELDS;
    }

    return ResponseBuilder.STAGE_DONE;
  }

  /** 
   * Responsible for using the specified suggester to get the suggestions 
   * for the query and write the results 
   * */
  @Override
  public void process(ResponseBuilder rb) throws IOException {
    SolrParams params = rb.req.getParams();
    LOG.info("Suggester process with : " + params);
    if (!params.getBool(COMPONENT_NAME, false) || suggesters.isEmpty()) {
      return;
    }
    
    SolrSuggester suggester = getSuggester(params);
    String query = params.get(SUGGEST_Q);
    if (query == null) {
      query = rb.getQueryString();
      if (query == null) {
        query = params.get(CommonParams.Q);
      }
    }
    
    if (query != null) {
      int count = params.getInt(SUGGEST_COUNT, 1);
      SuggesterOptions options = new SuggesterOptions(new CharsRef(query), count); 
      SuggesterResult suggesterResult = suggester.getSuggestions(options);
      
      NamedList response = new SimpleOrderedMap();
      NamedList<NamedList> namedListResult = toNamedList(suggesterResult);
      response.add(SuggesterResultLabels.SUGGESTIONS, namedListResult);
      rb.rsp.add(SuggesterResultLabels.SUGGEST, response);
    }
  }
  
  /** 
   * Used in Distributed Search, merges the suggestion results from every shard
   * */
  @Override
  public void finishStage(ResponseBuilder rb) {
    SolrParams params = rb.req.getParams();
    LOG.info("Suggester finishStage with : " + params);
    if (!params.getBool(COMPONENT_NAME, false) || rb.stage != ResponseBuilder.STAGE_GET_FIELDS)
      return;
    int count = params.getInt(SUGGEST_COUNT, 1);
    
    List<SuggesterResult> suggesterResults = new ArrayList<SuggesterResult>();
    NamedList response = new SimpleOrderedMap();
    NamedList<NamedList> namedListResult = null;
    
    // Collect Shard responses
    for (ShardRequest sreq : rb.finished) {
      for (ShardResponse srsp : sreq.responses) {
        NamedList<NamedList> namedList = 
            (NamedList<NamedList>) srsp.getSolrResponse().getResponse().get(SuggesterResultLabels.SUGGEST);
        LOG.info(srsp.getShard() + " : " + namedList);
        suggesterResults.add(toSuggesterResult(namedList));
      }
    }
    
    // Merge Shard responses
    SuggesterResult suggesterResult = merge(suggesterResults, count);
    namedListResult = toNamedList(suggesterResult);
      
    response.add(SuggesterResultLabels.SUGGESTIONS, namedListResult);
    rb.rsp.add(SuggesterResultLabels.SUGGEST, response);
  };

  /** 
   * Given a list of {@link SuggesterResult} and <code>count</code>
   * returns a {@link SuggesterResult} containing <code>count</code>
   * number of {@link LookupResult}, sorted by their associated 
   * weights
   * */
  private static SuggesterResult merge(List<SuggesterResult> suggesterResults, int count) {
    SuggesterResult result = new SuggesterResult();
    Set<String> allTokens = new HashSet<String>();
    
    // collect all tokens
    for (SuggesterResult shardResult : suggesterResults) {
      allTokens.addAll(shardResult.getTokens());
    }
    
    // Get Top N for every token in every shard (using weights)
    for (String token : allTokens) {
      Lookup.LookupPriorityQueue resultQueue = new Lookup.LookupPriorityQueue(
          count);
      for (SuggesterResult shardResult : suggesterResults) {
        List<LookupResult> suggests = shardResult.getLookupResult(token);
        if (suggests == null) {
          continue;
        }
        for (LookupResult res : suggests) {
          resultQueue.insertWithOverflow(res);
        }
      }
      List<LookupResult> sortedSuggests = new LinkedList<LookupResult>();
      Collections.addAll(sortedSuggests, resultQueue.getResults());
      result.add(token, sortedSuggests);
    }
    return result;
  }
  
  @Override
  public String getDescription() {
    return "Suggester component";
  }

  @Override
  public String getSource() {
    return "$URL$";
  }
  
  @Override
  public NamedList getStatistics() {
    NamedList<String> stats = new SimpleOrderedMap<String>();
    stats.add("totalSizeInBytes", String.valueOf(sizeInBytes()));
    for (Map.Entry<String, SolrSuggester> entry : suggesters.entrySet()) {
      SolrSuggester suggester = entry.getValue();
      stats.add(entry.getKey(), suggester.toString());
    }
    return stats;
  }
  
  private long sizeInBytes() {
    long sizeInBytes = 0;
    for (SolrSuggester suggester : suggesters.values()) {
      sizeInBytes += suggester.sizeInBytes();
    }
    return sizeInBytes;
  }
  
  private SolrSuggester getSuggester(SolrParams params) {
    return suggesters.get(getSuggesterName(params));
    
  }
  
  private String getSuggesterName(SolrParams params){
    return (params.get(SUGGEST_DICT) != null) ? 
        (String)params.get(SUGGEST_DICT)
        : DEFAULT_DICT_NAME;

  }
  
  /** Convert {@link SuggesterResult} to NamedList for constructing responses */
  private NamedList<NamedList> toNamedList(SuggesterResult suggesterResult) {
    NamedList<NamedList> results = new NamedList<NamedList>();
    for (String token : suggesterResult.getTokens()) {
      SimpleOrderedMap suggestionBody = new SimpleOrderedMap();
      List<LookupResult> lookupResults = suggesterResult.getLookupResult(token);
      suggestionBody.add(SuggesterResultLabels.SUGGESTION_NUM_FOUND, lookupResults.size());
      
      for (LookupResult lookupResult : lookupResults) {
        String suggestionString = lookupResult.key.toString();
        long weight = lookupResult.value;
        String payload = (lookupResult.payload != null) ? 
            lookupResult.payload.utf8ToString()
            : "";
            
        SimpleOrderedMap suggestEntryNamedList = new SimpleOrderedMap();
        suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_TERM, suggestionString);
        suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_WEIGHT, weight);
        suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_PAYLOAD, payload);
        
        suggestionBody.add(SuggesterResultLabels.SUGGESTION, suggestEntryNamedList);
      }
      results.add(token, suggestionBody);
    }
    return results;
  }
  
  /** Convert NamedList (suggester response) to {@link SuggesterResult} */
  private SuggesterResult toSuggesterResult(NamedList<NamedList> suggesterRespNamedList) {
    SuggesterResult result = new SuggesterResult();
    if (suggesterRespNamedList == null) {
      return result;
    }
    NamedList suggestions = (NamedList) suggesterRespNamedList.get(SuggesterResultLabels.SUGGESTIONS);
    if (suggestions != null) {
      // for each token
      for(int i = 0; i < suggestions.size() ; i++) {
        String tokenString = suggestions.getName(i);
        List<LookupResult> lookupResults = new ArrayList<LookupResult>();
        NamedList suggestion = (NamedList) suggestions.getVal(i);
        // for each suggestion
        for (int j = 0; j < suggestion.size(); j++) {
          String property = suggestion.getName(j);
          if (property.equals(SuggesterResultLabels.SUGGESTION)) {
            NamedList suggestionEntry = (NamedList) suggestion.getVal(j);
            String term = (String) suggestionEntry.get(SuggesterResultLabels.SUGGESTION_TERM);
            Long weight = (Long) suggestionEntry.get(SuggesterResultLabels.SUGGESTION_WEIGHT);
            String payload = (String) suggestionEntry.get(SuggesterResultLabels.SUGGESTION_PAYLOAD);
            LookupResult res = new LookupResult(new CharsRef(term), weight, new BytesRef(payload));
            lookupResults.add(res);
          }
          result.add(tokenString, lookupResults);
        }
      }
    } 
    return result;
  }
  
  /** Listener to build or reload the maintained {@link SolrSuggester} by this component */
  private static class SuggesterListener implements SolrEventListener {
    private final SolrCore core;
    private final SolrSuggester suggester;
    private final boolean buildOnCommit;
    private final boolean buildOnOptimize;

    public SuggesterListener(SolrCore core, SolrSuggester checker, boolean buildOnCommit, boolean buildOnOptimize) {
      this.core = core;
      this.suggester = checker;
      this.buildOnCommit = buildOnCommit;
      this.buildOnOptimize = buildOnOptimize;
    }

    @Override
    public void init(NamedList args) {
    }

    @Override
    public void newSearcher(SolrIndexSearcher newSearcher,
                            SolrIndexSearcher currentSearcher) {
      if (currentSearcher == null) {
        // firstSearcher event
        try {
          LOG.info("Loading suggester index for: " + suggester.getName());
          suggester.reload(core, newSearcher);
        } catch (IOException e) {
          log.error("Exception in reloading suggester index for: " + suggester.getName(), e);
        }
      } else {
        // newSearcher event
        if (buildOnCommit)  {
          buildSuggesterIndex(newSearcher);
        } else if (buildOnOptimize) {
          if (newSearcher.getIndexReader().leaves().size() == 1)  {
            buildSuggesterIndex(newSearcher);
          } else  {
            LOG.info("Index is not optimized therefore skipping building suggester index for: " 
                    + suggester.getName());
          }
        }
      }

    }

    private void buildSuggesterIndex(SolrIndexSearcher newSearcher) {
      try {
        LOG.info("Building suggester index for: " + suggester.getName());
        suggester.build(core, newSearcher);
      } catch (Exception e) {
        log.error("Exception in building suggester index for: " + suggester.getName(), e);
      }
    }

    @Override
    public void postCommit() {}

    @Override
    public void postSoftCommit() {}
    
  }
}
