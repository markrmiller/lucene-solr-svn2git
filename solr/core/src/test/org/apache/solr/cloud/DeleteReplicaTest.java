package org.apache.solr.cloud;

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

import static org.apache.solr.cloud.OverseerCollectionProcessor.DELETEREPLICA;
import static org.apache.solr.cloud.OverseerCollectionProcessor.MAX_SHARDS_PER_NODE;
import static org.apache.solr.cloud.OverseerCollectionProcessor.NUM_SLICES;
import static org.apache.solr.cloud.OverseerCollectionProcessor.REPLICATION_FACTOR;
import static org.apache.solr.common.cloud.ZkNodeProps.makeMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class DeleteReplicaTest extends AbstractFullDistribZkTestBase {
  private CloudSolrServer client;
  
  @BeforeClass
  public static void beforeThisClass2() throws Exception {

  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    System.setProperty("numShards", Integer.toString(sliceCount));
    System.setProperty("solr.xml.persist", "true");
    client = createCloudClient(null);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    client.shutdown();
  }

  protected String getSolrXml() {
    return "solr-no-core.xml";
  }

  public DeleteReplicaTest() {
    fixShardCount = true;

    sliceCount = 2;
    shardCount = 4;

    checkCreatedVsState = false;
  }

  @Override
  public void doTest() throws Exception {
    deleteLiveReplicaTest();
  }

  private void deleteLiveReplicaTest() throws Exception {
    String collectionName = "delLiveColl";
    CloudSolrServer client = createCloudClient(null);
    try {
      createCollection(collectionName, client);
      
      waitForRecoveriesToFinish(collectionName, false);
      
      DocCollection testcoll = getCommonCloudSolrServer().getZkStateReader()
          .getClusterState().getCollection(collectionName);
      
      Slice shard1 = null;
      Replica replica1 = null;
      for (Slice slice : testcoll.getSlices()) {
        if ("active".equals(slice.getStr("state"))) {
          shard1 = slice;
          for (Replica replica : shard1.getReplicas())
            if ("active".equals(replica.getStr("state"))) replica1 = replica;
        }
      }
      // final Slice shard1 = testcoll.getSlices().iterator().next();
      // if(!shard1.getState().equals(Slice.ACTIVE))
      // fail("shard is not active");
      // for (Replica replica : shard1.getReplicas())
      // if("active".equals(replica.getStr("state"))) replica1 =replica;
      if (replica1 == null) fail("no active replicas found");
      removeAndWaitForReplicaGone(collectionName, client, replica1,
          shard1.getName());
    } finally {
      client.shutdown();
    }
  }

  protected void removeAndWaitForReplicaGone(String COLL_NAME,
      CloudSolrServer client, Replica replica, String shard)
      throws SolrServerException, IOException, InterruptedException {
    Map m = makeMap("collection", COLL_NAME, "action", DELETEREPLICA, "shard",
        shard, "replica", replica.getName());
    SolrParams params = new MapSolrParams(m);
    SolrRequest request = new QueryRequest(params);
    request.setPath("/admin/collections");
    client.request(request);
    long endAt = System.currentTimeMillis() + 3000;
    boolean success = false;
    DocCollection testcoll = null;
    while (System.currentTimeMillis() < endAt) {
      testcoll = getCommonCloudSolrServer().getZkStateReader()
          .getClusterState().getCollection(COLL_NAME);
      success = testcoll.getSlice(shard).getReplica(replica.getName()) == null;
      if (success) {
        log.info("replica cleaned up {}/{} core {}",
            shard + "/" + replica.getName(), replica.getStr("core"));
        log.info("current state {}", testcoll);
        break;
      }
      Thread.sleep(100);
    }
    assertTrue("Replica not cleaned up", success);
  }

  protected void createCollection(String COLL_NAME, CloudSolrServer client) throws Exception {
    int replicationFactor = 2;
    int numShards = 2;
    int maxShardsPerNode = ((((numShards+1) * replicationFactor) / getCommonCloudSolrServer()
        .getZkStateReader().getClusterState().getLiveNodes().size())) + 1;

    Map<String, Object> props = makeMap(
        REPLICATION_FACTOR, replicationFactor,
        MAX_SHARDS_PER_NODE, maxShardsPerNode,
        NUM_SLICES, numShards);
    Map<String,List<Integer>> collectionInfos = new HashMap<String,List<Integer>>();
    createCollection(collectionInfos, COLL_NAME, props, client);
  }
}
