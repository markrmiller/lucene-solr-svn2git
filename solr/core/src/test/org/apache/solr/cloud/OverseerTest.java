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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.cloud.CloudState;
import org.apache.solr.common.cloud.CoreState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.core.CoreDescriptor;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.junit.BeforeClass;
import org.junit.Test;

public class OverseerTest extends SolrTestCaseJ4 {

  static final int TIMEOUT = 10000;
  private static final boolean DEBUG = false;

  
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore();
  }

  @Test
  public void testShardAssignment() throws Exception {
    String zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";

    ZkTestServer server = new ZkTestServer(zkDir);

    ZkController zkController = null;
    SolrZkClient zkClient = null;
    try {
      server.run();
      AbstractZkTestCase.tryCleanSolrZkNode(server.getZkHost());
      AbstractZkTestCase.makeSolrZkNode(server.getZkHost());

      zkClient = new SolrZkClient(server.getZkAddress(), TIMEOUT);

      System.setProperty(ZkStateReader.NUM_SHARDS_PROP, "3");

      zkController = new ZkController(server.getZkAddress(), TIMEOUT, 10000,
          "localhost", "8983", "solr", new CurrentCoreDescriptorProvider() {

            @Override
            public List<CoreDescriptor> getCurrentDescriptors() {
              // do nothing
              return null;
            }
          });

      System.setProperty("bootstrap_confdir", getFile("solr/conf")
          .getAbsolutePath());
      
      CloudDescriptor collection1Desc = new CloudDescriptor();
      collection1Desc.setCollectionName("collection1");

      CoreDescriptor desc = new CoreDescriptor(null, "core1", "");
      desc.setCloudDescriptor(collection1Desc);
      String shard1 = zkController.register("core1", desc);
      collection1Desc.setShardId(null);
      desc = new CoreDescriptor(null, "core2", "");
      desc.setCloudDescriptor(collection1Desc);
      String shard2 = zkController.register("core2", desc);
      collection1Desc.setShardId(null);
      desc = new CoreDescriptor(null, "core3", "");
      desc.setCloudDescriptor(collection1Desc);
      String shard3 = zkController.register("core3", desc);
      collection1Desc.setShardId(null);
      desc = new CoreDescriptor(null, "core4", "");
      desc.setCloudDescriptor(collection1Desc);
      String shard4 = zkController.register("core4", desc);
      collection1Desc.setShardId(null);
      desc = new CoreDescriptor(null, "core5", "");
      desc.setCloudDescriptor(collection1Desc);
      String shard5 = zkController.register("core5", desc);
      collection1Desc.setShardId(null);
      desc = new CoreDescriptor(null, "core6", "");
      desc.setCloudDescriptor(collection1Desc);
      String shard6 = zkController.register("core6", desc);
      collection1Desc.setShardId(null);

      assertEquals("shard1", shard1);
      assertEquals("shard2", shard2);
      assertEquals("shard3", shard3);
      assertEquals("shard1", shard4);
      assertEquals("shard2", shard5);
      assertEquals("shard3", shard6);

    } finally {
      if (DEBUG) {
        if (zkController != null) {
          zkController.printLayoutToStdOut();
        }
      }
      if (zkClient != null) {
        zkClient.close();
      }
      if (zkController != null) {
        zkController.close();
      }
      server.shutdown();
    }
    
    System.clearProperty(ZkStateReader.NUM_SHARDS_PROP);
  }

  @Test
  public void testShardAssignmentBigger() throws Exception {
    String zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";

    final int nodeCount = 10; //how many simulated nodes
    final int coreCount = 66; //how many cores to register

    ZkTestServer server = new ZkTestServer(zkDir);

    SolrZkClient zkClient = null;
    ZkStateReader reader = null;
    final ZkController[] controllers = new ZkController[nodeCount];

    try {
      server.run();
      AbstractZkTestCase.tryCleanSolrZkNode(server.getZkHost());
      AbstractZkTestCase.makeSolrZkNode(server.getZkHost());

      zkClient = new SolrZkClient(server.getZkAddress(), TIMEOUT);
      reader = new ZkStateReader(zkClient);
      
      System.setProperty(ZkStateReader.NUM_SHARDS_PROP, "3");

      for (int i = 0; i < nodeCount; i++) {
      
      controllers[i] = new ZkController(server.getZkAddress(), TIMEOUT, 10000,
          "localhost", "898" + i, "solr", new CurrentCoreDescriptorProvider() {

            @Override
            public List<CoreDescriptor> getCurrentDescriptors() {
              // do nothing
              return null;
            }
          });
      }

      System.setProperty("bootstrap_confdir", getFile("solr/conf")
          .getAbsolutePath());

      
      final ExecutorService[] nodeExecutors = new ExecutorService[nodeCount];
      for (int i = 0; i < nodeCount; i++) {
        nodeExecutors[i] = Executors.newFixedThreadPool(1);
      }
      
      final String[] ids = new String[coreCount];
      //register total of coreCount cores
      for (int i = 0; i < coreCount; i++) {
        final int slot = i;
        Runnable coreStarter = new Runnable() {
          @Override
          public void run() {
            // TODO Auto-generated method stub
            CloudDescriptor collection1Desc = new CloudDescriptor();
            collection1Desc.setCollectionName("collection1");

            final String coreName = "core" + slot;
            
            CoreDescriptor desc = new CoreDescriptor(null, coreName, "");
            desc.setCloudDescriptor(collection1Desc);
            try {
              ids[slot] = controllers[slot % nodeCount].register(coreName, desc);
            } catch (Exception e) {
              fail("register threw exception:" + e);
            }
          }
        };
        
        nodeExecutors[i % nodeCount].submit(coreStarter);
      }
      
      for (int i = 0; i < nodeCount; i++) {
        nodeExecutors[i].shutdown();
      }

      for (int i = 0; i < nodeCount; i++) {
        while (!nodeExecutors[i].awaitTermination(100, TimeUnit.MILLISECONDS));
      }
      
      // make sure all cores have been assigned a id in cloudstate
      for (int i = 0; i < 40; i++) {
        reader.updateCloudState(true);
        CloudState state = reader.getCloudState();
        Map<String,Slice> slices = state.getSlices("collection1");
        int count = 0;
        for (String name : slices.keySet()) {
          count += slices.get(name).getShards().size();
        }
        if (coreCount == count) break;
        Thread.sleep(200);
      }

      // make sure all cores have been returned a id
      for (int i = 0; i < 40; i++) {
        int assignedCount = 0;
        for (int j = 0; j < coreCount; j++) {
          if (ids[j] != null) {
            assignedCount++;
          }
        }
        if (coreCount == assignedCount) {
          break;
        }
        Thread.sleep(200);
      }
      
      final HashMap<String, AtomicInteger> counters = new HashMap<String,AtomicInteger>();
      for (int i = 1; i < 4; i++) {
        counters.put("shard" + i, new AtomicInteger());
      }
      
      for (int i = 0; i < coreCount; i++) {
        final AtomicInteger ai = counters.get(ids[i]);
        assertNotNull("could not find counter for shard:" + ids[i], ai);
        ai.incrementAndGet();
      }

      for (String counter: counters.keySet()) {
        int count = counters.get(counter).intValue();
        int expectedCount = coreCount / 3;
        if (count != expectedCount) {
          fail("unevenly assigned shard ids, " + counter + " had " + count
              + ", expected " + expectedCount + " (+-1)");
        }
      }
      
    } finally {
      if (DEBUG) {
        if (controllers[0] != null) {
          controllers[0].printLayoutToStdOut();
        }
      }
      if (zkClient != null) {
        zkClient.close();
      }
      if (reader != null) {
        reader.close();
      }
      for (int i = 0; i < controllers.length; i++)
        if (controllers[i] != null) {
          controllers[i].close();
        }
      server.shutdown();
    }
    
    System.clearProperty(ZkStateReader.NUM_SHARDS_PROP);
  }

  //wait until i slices for collection have appeared 
  private void waitForSliceCount(ZkStateReader stateReader, String collection, int i) throws InterruptedException, KeeperException {
    waitForCollections(stateReader, collection);
    int maxIterations = 200;
    while (0 < maxIterations--) {
      CloudState state = stateReader.getCloudState();
      Map<String,Slice> sliceMap = state.getSlices(collection);
      if (sliceMap != null && sliceMap.keySet().size() == i) {
        return;
      }
      Thread.sleep(100);
    }
  }

  //wait until collections are available
  private void waitForCollections(ZkStateReader stateReader, String... collections) throws InterruptedException, KeeperException {
    int maxIterations = 100;
    while (0 < maxIterations--) {
      stateReader.updateCloudState(true);
      final CloudState state = stateReader.getCloudState();
      Set<String> availableCollections = state.getCollections();
      int availableCount = 0;
      for(String requiredCollection: collections) {
        if(availableCollections.contains(requiredCollection)) {
          availableCount++;
        }
        if(availableCount == collections.length) return;
        Thread.sleep(50);
      }
    }
    log.warn("Timeout waiting for collections: " + Arrays.asList(collections) + " state:" + stateReader.getCloudState());
  }
  
  @Test
  public void testStateChange() throws Exception {
    String zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";
    
    ZkTestServer server = new ZkTestServer(zkDir);
    
    SolrZkClient zkClient = null;
    ZkStateReader reader = null;
    SolrZkClient overseerClient = null;
    
    try {
      server.run();
      zkClient = new SolrZkClient(server.getZkAddress(), TIMEOUT);
      
      AbstractZkTestCase.tryCleanSolrZkNode(server.getZkHost());
      AbstractZkTestCase.makeSolrZkNode(server.getZkHost());
      zkClient.makePath("/live_nodes");

      System.setProperty(ZkStateReader.NUM_SHARDS_PROP, "2");

      //live node
      String nodePath = ZkStateReader.LIVE_NODES_ZKNODE + "/" + "node1";
      zkClient.makePath(nodePath,CreateMode.EPHEMERAL);

      reader = new ZkStateReader(zkClient);
      reader.createClusterStateWatchersAndUpdate();

      Overseer.createClientNodes(zkClient, "node1");
      
      ElectionContext ec = new OverseerElectionContext("node1");
      
      overseerClient = electNewOverseer(server.getZkAddress(), reader, ec);

      HashMap<String, String> coreProps = new HashMap<String,String>();
      coreProps.put(ZkStateReader.URL_PROP, "http://127.0.0.1/solr");
      coreProps.put(ZkStateReader.NODE_NAME_PROP, "node1");
      coreProps.put(ZkStateReader.ROLES_PROP, "");
      coreProps.put(ZkStateReader.STATE_PROP, ZkStateReader.RECOVERING);
      CoreState state = new CoreState("core1", "collection1", coreProps);
      
      nodePath = "/node_states/node1";

      try {
        zkClient.makePath(nodePath, CreateMode.EPHEMERAL);
      } catch (KeeperException ke) {
        if(ke.code()!=Code.NODEEXISTS) {
          throw ke;
        }
      }
      //publish node state (recovering)
      
      zkClient.setData(nodePath, ZkStateReader.toJSON(new CoreState[]{state}));

      //wait overseer assignment
      waitForSliceCount(reader, "collection1", 1);
      
      assertEquals("Illegal state", ZkStateReader.RECOVERING, reader.getCloudState().getSlice("collection1", "shard1").getShards().get("core1").get(ZkStateReader.STATE_PROP));

      //publish node state (active)
      coreProps.put(ZkStateReader.STATE_PROP, ZkStateReader.ACTIVE);
      
      coreProps.put(ZkStateReader.SHARD_ID_PROP, "shard1");
      state = new CoreState("core1", "collection1", coreProps);

      zkClient.setData(nodePath, ZkStateReader.toJSON(new CoreState[]{state}));

      verifyStatus(reader, ZkStateReader.ACTIVE);

    } finally {
      System.clearProperty(ZkStateReader.NUM_SHARDS_PROP);

      if (zkClient != null) {
        zkClient.close();
      }
      if (overseerClient != null) {
        overseerClient.close();
      }

      if (reader != null) {
        reader.close();
      }
      server.shutdown();
    }
  }

  private void verifyStatus(ZkStateReader reader, String expectedState) throws InterruptedException {
    int maxIterations = 100;
    String coreState = null;
    while(maxIterations-->0) {
      coreState = reader.getCloudState().getSlice("collection1", "shard1").getShards().get("core1").get(ZkStateReader.STATE_PROP);
      if(coreState.equals(expectedState)) {
        return;
      }
      Thread.sleep(50);
    }
    fail("Illegal state, was:" + coreState + " expected:" + expectedState + "cloudState:" + reader.getCloudState());
  }
  
  @Test
  public void testOverseerFailure() throws Exception {
    String zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";
    
    ZkTestServer server = new ZkTestServer(zkDir);
    
    SolrZkClient controllerClient = null;
    SolrZkClient overseerClient = null;
    ZkStateReader reader = null;
    
    try {
      server.run();
      controllerClient = new SolrZkClient(server.getZkAddress(), TIMEOUT);
      
      AbstractZkTestCase.tryCleanSolrZkNode(server.getZkHost());
      AbstractZkTestCase.makeSolrZkNode(server.getZkHost());
      controllerClient.makePath("/live_nodes");
      
      reader = new ZkStateReader(controllerClient);
      reader.createClusterStateWatchersAndUpdate();

      Overseer.createClientNodes(controllerClient, "node1");

      
      ElectionContext ec = new OverseerElectionContext("node1");
      
      overseerClient = electNewOverseer(server.getZkAddress(), reader, ec);
      
      // live node
      final String nodePath = ZkStateReader.LIVE_NODES_ZKNODE + "/" + "node1";
      controllerClient.makePath(nodePath, CreateMode.EPHEMERAL);
      
      HashMap<String,String> coreProps = new HashMap<String,String>();
      coreProps.put(ZkStateReader.STATE_PROP, ZkStateReader.RECOVERING);
      CoreState state = new CoreState("core1", "collection1", coreProps);
      
      final String statePath = Overseer.STATES_NODE + "/node1";
      // publish node state (recovering)
      controllerClient.setData(statePath, ZkStateReader.toJSON(new CoreState[] {state}));
      
      // wait overseer assignment
      waitForSliceCount(reader, "collection1", 1);

      verifyStatus(reader, ZkStateReader.RECOVERING);

      // publish node state (active)
      coreProps.put(ZkStateReader.STATE_PROP, ZkStateReader.ACTIVE);
      coreProps.put(ZkStateReader.SHARD_ID_PROP, "shard1");
      state = new CoreState("core1", "collection1", coreProps);
      controllerClient.setData(statePath,
          ZkStateReader.toJSON(new CoreState[] {state}));

      verifyStatus(reader, ZkStateReader.ACTIVE);
      overseerClient.close();
      
      coreProps.put(ZkStateReader.STATE_PROP, ZkStateReader.RECOVERING);
      state = new CoreState("core1", "collection1", coreProps);
             
      controllerClient.setData(statePath,
          ZkStateReader.toJSON(new CoreState[] {state}));

      overseerClient = electNewOverseer(server.getZkAddress(), reader, ec);
      
      verifyStatus(reader, ZkStateReader.RECOVERING);
      
      assertEquals("Live nodes count does not match", 1, reader.getCloudState()
          .getLiveNodes().size());
      assertEquals("Shard count does not match", 1, reader.getCloudState()
          .getSlice("collection1", "shard1").getShards().size());      
    } finally {
      
      if (overseerClient != null) {
       overseerClient.close();
      }
      if (controllerClient != null) {
        controllerClient.close();
      }
      if (reader != null) {
        reader.close();
      }
      server.shutdown();
    }
  }

  private SolrZkClient electNewOverseer(String address,
      ZkStateReader reader, ElectionContext ec) throws InterruptedException,
      TimeoutException, IOException, KeeperException {
    SolrZkClient overseerClient;
    OverseerElector overseerElector;
    overseerClient = new SolrZkClient(address, TIMEOUT);
    overseerElector = new OverseerElector(overseerClient, reader);
    overseerElector.setup(ec);
    overseerElector.joinElection(ec);
    return overseerClient;
  }
}