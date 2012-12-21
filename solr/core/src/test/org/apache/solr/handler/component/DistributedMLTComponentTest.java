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

import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.solr.BaseDistributedSearchTestCase;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.junit.BeforeClass;
import org.junit.Ignore;

/**
 * Test for distributed MoreLikeThisComponent's 
 *
 * @since solr 4.1
 *
 * @see org.apache.solr.handler.component.MoreLikeThisComponent
 */
@Slow
@Ignore("distrib mlt not working right")
public class DistributedMLTComponentTest extends BaseDistributedSearchTestCase {
  
  private String requestHandlerName;

  public DistributedMLTComponentTest()
  {
    fixShardCount=true;
    shardCount=3;
    stress=0;
  }

  @BeforeClass
  public static void beforeClass() throws Exception {

  }

  @Override
  public void setUp() throws Exception {
    requestHandlerName = "mltrh";
    super.setUp();
  }
  
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }
  
  @Override
  public void doTest() throws Exception {
    del("*:*");
    index(id, "1", "lowerfilt", "toyota");
    index(id, "2", "lowerfilt", "chevrolet");
    index(id, "3", "lowerfilt", "suzuki");
    index(id, "4", "lowerfilt", "ford");
    index(id, "5", "lowerfilt", "ferrari");
    index(id, "6", "lowerfilt", "jaguar");
    index(id, "7", "lowerfilt", "mclaren moon or the moon and moon moon shine and the moon but moon was good foxes too");
    index(id, "8", "lowerfilt", "sonata");
    index(id, "9", "lowerfilt", "The quick red fox jumped over the lazy big and large brown dogs.");
    index(id, "10", "lowerfilt", "blue");
    index(id, "12", "lowerfilt", "glue");
    index(id, "13", "lowerfilt", "The quote red fox jumped over the lazy brown dogs.");
    index(id, "14", "lowerfilt", "The quote red fox jumped over the lazy brown dogs.");
    index(id, "15", "lowerfilt", "The fat red fox jumped over the lazy brown dogs.");
    index(id, "16", "lowerfilt", "The slim red fox jumped over the lazy brown dogs.");
    index(id, "17", "lowerfilt", "The quote red fox jumped moon over the lazy brown dogs moon. Of course moon. Foxes and moon come back to the foxes and moon");
    index(id, "18", "lowerfilt", "The quote red fox jumped over the lazy brown dogs.");
    index(id, "19", "lowerfilt", "The hose red fox jumped over the lazy brown dogs.");
    index(id, "20", "lowerfilt", "The quote red fox jumped over the lazy brown dogs.");
    index(id, "21", "lowerfilt", "The court red fox jumped over the lazy brown dogs.");
    index(id, "22", "lowerfilt", "The quote red fox jumped over the lazy brown dogs.");
    index(id, "23", "lowerfilt", "The quote red fox jumped over the lazy brown dogs.");
    index(id, "24", "lowerfilt", "The file red fox jumped over the lazy brown dogs.");
    index(id, "25", "lowerfilt", "rod fix");
    
    commit();

    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    handle.put("maxScore", SKIPVAL);
    // we care only about the mlt results
    handle.put("response", SKIP);
    
    // currently distrib mlt is sorting by score (even though it's not really comparable across shards)
    // so it may not match the sort of single shard mlt
    handle.put("17", UNORDERED);
    
    query("q", "match_none", "mlt", "true", "mlt.fl", "lowerfilt", "qt", requestHandlerName, "shards.qt", requestHandlerName);
    
    query("q", "lowerfilt:sonata", "mlt", "true", "mlt.fl", "lowerfilt", "qt", requestHandlerName, "shards.qt", requestHandlerName);
    
    handle.put("24", UNORDERED);
    handle.put("23", UNORDERED);
    handle.put("22", UNORDERED);
    handle.put("21", UNORDERED);
    handle.put("20", UNORDERED);
    handle.put("19", UNORDERED);
    handle.put("18", UNORDERED);
    handle.put("17", UNORDERED);
    handle.put("16", UNORDERED);
    handle.put("15", UNORDERED);
    handle.put("14", UNORDERED);
    handle.put("13", UNORDERED);
    handle.put("7", UNORDERED);
    
    // keep in mind that MLT params influence stats that are calulated
    // per shard - because of this, depending on params, distrib and single
    // shard queries will not match.
    
    query("q", "lowerfilt:moon", "fl", id, MoreLikeThisParams.MIN_TERM_FREQ, 2,
        MoreLikeThisParams.MIN_DOC_FREQ, 1, "sort", "id desc", "mlt", "true",
        "mlt.fl", "lowerfilt", "qt", requestHandlerName, "shards.qt",
        requestHandlerName);
    
    query("q", "lowerfilt:fox", "fl", id, MoreLikeThisParams.MIN_TERM_FREQ, 1,
        MoreLikeThisParams.MIN_DOC_FREQ, 1, "sort", "id desc", "mlt", "true",
        "mlt.fl", "lowerfilt", "qt", requestHandlerName, "shards.qt",
        requestHandlerName);

    query("q", "lowerfilt:the red fox", "fl", id, MoreLikeThisParams.MIN_TERM_FREQ, 1,
        MoreLikeThisParams.MIN_DOC_FREQ, 1, "sort", "id desc", "mlt", "true",
        "mlt.fl", "lowerfilt", "qt", requestHandlerName, "shards.qt",
        requestHandlerName);
    
    query("q", "lowerfilt:blue moon", "fl", id, MoreLikeThisParams.MIN_TERM_FREQ, 1,
        MoreLikeThisParams.MIN_DOC_FREQ, 1, "sort", "id desc", "mlt", "true",
        "mlt.fl", "lowerfilt", "qt", requestHandlerName, "shards.qt",
        requestHandlerName);
  }
}
