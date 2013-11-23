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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.solr.BaseDistributedSearchTestCase;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.spelling.suggest.SuggesterParams;
import org.junit.BeforeClass;

/**
 * Test for SuggestComponent's distributed querying
 *
 * @see org.apache.solr.handler.component.SuggestComponent
 */
@Slow
public class DistributedSuggesterComponentTest extends BaseDistributedSearchTestCase {
  
  public DistributedSuggesterComponentTest() {
    //Helpful for debugging
    //fixShardCount=true;
    //shardCount=2;
    //stress=0;
    //deadServers=null;
    configString = "solrconfig-suggestercomponent.xml";
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    useFactory(null); // need an FS factory
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }
  
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }
  
  @Override
  public void validateControlData(QueryResponse control) throws Exception
  {    
    NamedList<Object> nl = control.getResponse();
    @SuppressWarnings("unchecked")
    NamedList<Object> sc = (NamedList<Object>) nl.get("suggest");
    @SuppressWarnings("unchecked")
    NamedList<Object> sug = (NamedList<Object>) sc.get("suggestions");
    if(sug.size()==0) {
      Assert.fail("Control data did not return any suggestions.");
    }
  } 
  
  @Override
  public void doTest() throws Exception {
    del("*:*");
    index(id, "1", "cat", "This is another title", "price", "10", "weight", "10");
    index(id, "2", "cat", "Yet another", "price", "15", "weight", "10");
    index(id, "3", "cat", "Yet another title", "price", "20", "weight", "20");
    index(id, "4", "cat", "suggestions for suggest", "price", "25", "weight", "20");
    index(id, "5", "cat", "Red fox", "price", "30", "weight", "20");
    index(id, "6", "cat", "Rad fox", "price", "35", "weight", "30");
    index(id, "7", "cat", "example data", "price", "40", "weight", "30");
    index(id, "8", "cat", "example inputdata", "price", "45", "weight", "30");
    index(id, "9", "cat", "blah in blah", "price", "50", "weight", "40");
    index(id, "10", "cat", "another blah in blah", "price", "55", "weight", "40");
    commit();

    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    handle.put("maxScore", SKIPVAL);
    handle.put("response", SKIP);
    
    String requestHandlerName = "/suggest";
    String docDictName = "suggest_fuzzy_doc_dict";
    String docExprDictName = "suggest_fuzzy_doc_expr_dict";
    
    //Shortcut names
    String build = SuggesterParams.SUGGEST_BUILD;
    String count = SuggesterParams.SUGGEST_COUNT;
    String dictionaryName = SuggesterParams.SUGGEST_DICT;
    
    //Build the suggest dictionary 
    query(buildRequest("", true, requestHandlerName, build, "true", dictionaryName, docDictName));
    query(buildRequest("", true, requestHandlerName, build, "true", dictionaryName, docExprDictName));
    
    //Test Basic Functionality
    query(buildRequest("exampel", false, requestHandlerName, dictionaryName, docDictName, count, "2"));
    query(buildRequest("Yet", false, requestHandlerName, dictionaryName, docExprDictName, count, "2"));
    query(buildRequest("blah", true, requestHandlerName, dictionaryName, docExprDictName, count, "2"));
    query(buildRequest("blah", true, requestHandlerName, dictionaryName, docDictName, count, "2"));
    
  }
  private Object[] buildRequest(String q, boolean useSuggestQ, String handlerName, String... addlParams) {
    List<Object> params = new ArrayList<Object>();

    if(useSuggestQ) {
      params.add("suggest.q");
    } else {
      params.add("q");
    }
    params.add(q);

    params.add("qt");
    params.add(handlerName);
    
    params.add("shards.qt");
    params.add(handlerName);
    
    if(addlParams!=null) {
      params.addAll(Arrays.asList(addlParams));
    }
    return params.toArray(new Object[params.size()]);    
  }
  
}
