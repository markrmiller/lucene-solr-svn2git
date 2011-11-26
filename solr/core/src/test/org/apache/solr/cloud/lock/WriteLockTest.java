package org.apache.solr.cloud.lock;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.cloud.AbstractZkTestCase;
import org.apache.solr.cloud.ZkTestServer;
import org.apache.solr.common.cloud.SolrZkClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * test for writelock
 */
public class WriteLockTest extends SolrTestCaseJ4 {
  private static boolean VERBOSE = false;
  private static int TIMEOUT = 30 * 1000;
  protected String dir = "/" + getClass().getName();
  protected WriteLock[] nodes;
  protected CountDownLatch latch = new CountDownLatch(1);
  private boolean restartServer = true;
  private boolean workAroundClosingLastZNodeFails = true;
  private boolean killLeader = true;
  private ZkTestServer server;
  private String zkDir;
  private List<SolrZkClient> zkClients = new ArrayList<SolrZkClient>();
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    createTempDir();
  }
  
  @AfterClass
  public static void afterClass() throws InterruptedException {
    // wait just a bit for any zk client threads to outlast timeout
    Thread.sleep(2000);
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";
    
    server = new ZkTestServer(zkDir);
    server.run();
    AbstractZkTestCase.tryCleanSolrZkNode(server.getZkHost());
    AbstractZkTestCase.makeSolrZkNode(server.getZkHost());
  }
  
  @Test
  public void runTest() throws Exception {
    doTest(5);
  }
  
  class LockCallback implements LockListener {
    public void lockAcquired() {
      latch.countDown();
    }
    
    public void lockReleased() {
      
    }
    
  }
  
  protected void doTest(int count) throws Exception {
    nodes = new WriteLock[count];
    for (int i = 0; i < count; i++) {
      SolrZkClient zkClient = new SolrZkClient(server.getZkAddress(), TIMEOUT);
      zkClients.add(zkClient);
      WriteLock leader = new WriteLock(zkClient.getSolrZooKeeper(), dir, null);
      leader.setLockListener(new LockCallback());
      nodes[i] = leader;
      
      leader.lock();
    }
    
    // lets wait for any previous leaders to die and one of our new
    // nodes to become the new leader
    latch.await(30, TimeUnit.SECONDS);
    
    WriteLock first = nodes[0];
    dumpNodes(count);
    
    // lets assert that the first election is the leader
    assertTrue("The first znode should be the leader " + first.getId(),
        first.isOwner());
    
    for (int i = 1; i < count; i++) {
      WriteLock node = nodes[i];
      assertFalse("Node should not be the leader " + node.getId(),
          node.isOwner());
    }
    
    if (count > 1) {
      if (killLeader) {
        if (VERBOSE) System.out.println("Now killing the leader");
        // now lets kill the leader
        latch = new CountDownLatch(1);
        first.unlock();
        latch.await(30, TimeUnit.SECONDS);
        // Thread.sleep(10000);
        WriteLock second = nodes[1];
        dumpNodes(count);
        
        // lets assert that the first election is the leader
        assertTrue("The second znode should be the leader " + second.getId(),
            second.isOwner());
        
        for (int i = 2; i < count; i++) {
          WriteLock node = nodes[i];
          assertFalse("Node should not be the leader " + node.getId(),
              node.isOwner());
        }
      }
      
      if (restartServer) {
        // now lets stop the server
        if (VERBOSE) System.out.println("Now stopping the server");
        server.shutdown();
        Thread.sleep(10000);
        
        // TODO lets assert that we are no longer the leader
        dumpNodes(count);
        
        Thread.sleep(300);
        
        // try a reconnect from disconnect
        server = new ZkTestServer(zkDir);
        server.run();
        
        for (int i = 0; i < count - 1; i++) {
          if (VERBOSE) System.out.println("Calling acquire for node: " + i);
          //nodes[i].lock();
        }
        dumpNodes(count);
        if (VERBOSE) System.out.println("Now closing down...");
      }
    }
  }
  
  private void printLayout(String zkHost) throws Exception {
    SolrZkClient zkClient = new SolrZkClient(zkHost, 10000);
    zkClient.printLayoutToStdOut();
    zkClient.close();
  }
  
  protected void dumpNodes(int count) {
    for (int i = 0; i < count; i++) {
      WriteLock node = nodes[i];
      if (VERBOSE) System.out.println("node: " + i + " id: " + node.getId() + " is leader: "
          + node.isOwner());
    }
  }
  
  @Override
  public void tearDown() throws Exception {
    if (nodes != null) {
      for (int i = 0; i < nodes.length; i++) {
        WriteLock node = nodes[i];
        if (node != null) {
          if (VERBOSE) System.out.println("Closing node: " + i);
          node.close();
          if (workAroundClosingLastZNodeFails && i == nodes.length - 1) {
            if (VERBOSE) System.out.println("Not closing zookeeper: " + i + " due to bug!");
          } else {
            if (VERBOSE) System.out.println("Closing zookeeper: " + i);
            node.getZookeeper().close();

          }
        }
      }
    }
    for (SolrZkClient client : zkClients) {
      client.close();
    }
    server.shutdown();
    super.tearDown();
    
  }
}
