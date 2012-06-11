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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.search.spell.SuggestWord;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.spelling.SpellCheckCollation;

public class SpellCheckMergeData {
  //original token -> corresponding Suggestion object (keep track of start,end)
  public Map<String, SpellCheckResponse.Suggestion> origVsSuggestion = new HashMap<String, SpellCheckResponse.Suggestion>();
  // original token string -> summed up frequency
  public Map<String, Integer> origVsFreq = new HashMap<String, Integer>();
  // original token string -> # of shards reporting it as misspelled
  public Map<String, Integer> origVsShards = new HashMap<String, Integer>();
  // original token string -> set of alternatives
  // must preserve order because collation algorithm can only work in-order
  public Map<String, HashSet<String>> origVsSuggested = new LinkedHashMap<String, HashSet<String>>();
  // alternative string -> corresponding SuggestWord object
  public Map<String, SuggestWord> suggestedVsWord = new HashMap<String, SuggestWord>();
  public Map<String, SpellCheckCollation> collations = new HashMap<String, SpellCheckCollation>();
  public int totalNumberShardResponses = 0;
}
