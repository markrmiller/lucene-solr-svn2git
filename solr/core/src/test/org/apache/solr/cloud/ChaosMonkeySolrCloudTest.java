package org.apache.solr.cloud;

/**
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrInputDocument;
import org.apache.zookeeper.KeeperException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class ChaosMonkeySolrCloudTest extends FullSolrCloudTest {
  
  @BeforeClass
  public static void beforeSuperClass() throws Exception {
    
  }
  
  @AfterClass
  public static void afterSuperClass() throws Exception {
    
  }
  
  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // we expect this time of exception as shards go up and down...
    ignoreException("shard update error ");
    ignoreException("Connection refused");
    ignoreException("interrupted waiting for shard update response");
    ignoreException("org\\.mortbay\\.jetty\\.EofException");
    ignoreException("java\\.lang\\.InterruptedException");
    ignoreException("java\\.nio\\.channels\\.ClosedByInterruptException");
    
    
    // sometimes we cannot get the same port
    ignoreException("java\\.net\\.BindException: Address already in use");
    
    System.setProperty("numShards", Integer.toString(sliceCount));
  }
  
  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    resetExceptionIgnores();
  }
  
  public ChaosMonkeySolrCloudTest() {
    super();
    shardCount = atLeast(6);
    sliceCount = atLeast(2);
  }
  
  @Override
  public void doTest() throws Exception {
    
    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    
    List<StopableIndexingThread> threads = new ArrayList<StopableIndexingThread>();
    int threadCount = atLeast(2);
    for (int i = 0; i < threadCount; i++) {
      StopableIndexingThread indexThread = new StopableIndexingThread(i * 50000, true);
      threads.add(indexThread);
      indexThread.start();
    }
    
    chaosMonkey.startTheMonkey();
    
    Thread.sleep(atLeast(8000));
    
    chaosMonkey.stopTheMonkey();
    
    for (StopableIndexingThread indexThread : threads) {
      indexThread.safeStop();
    }
    
    // wait for stop...
    for (StopableIndexingThread indexThread : threads) {
      indexThread.join();
    }
       
    for (StopableIndexingThread indexThread : threads) {
      assertEquals(0, indexThread.getFails());
    }
    
    // try and wait for any replications and what not to finish...
    
    // wait until there are no recoveries...
    waitForThingsToLevelOut();

    checkShardConsistency(true);
    
    if (VERBOSE) System.out.println("control docs:" + controlClient.query(new SolrQuery("*:*")).getResults().getNumFound() + "\n\n");
  }

  private void waitForThingsToLevelOut() throws KeeperException,
      InterruptedException, Exception, IOException, URISyntaxException {
    int cnt = 0;
    boolean retry = false;
    do {
      waitForRecoveriesToFinish(VERBOSE);
      
      commit();
      
      updateMappingsFromZk(jettys, clients);
      
      Set<String> theShards = shardToClient.keySet();
      String failMessage = null;
      for (String shard : theShards) {
        failMessage = checkShardConsistency(shard, false);
      }
      
      if (failMessage != null) {
        retry  = true;
      }
      cnt++;
      if (cnt > 10) break;
      Thread.sleep(4000);
    } while (retry);
  }
  
  // skip the randoms - they can deadlock...
  protected void indexr(Object... fields) throws Exception {
    SolrInputDocument doc = new SolrInputDocument();
    addFields(doc, fields);
    addFields(doc, "rnd_b", true);
    indexDoc(doc);
  }

}
