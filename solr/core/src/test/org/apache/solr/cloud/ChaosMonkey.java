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

import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.cloud.FullSolrCloudTest.CloudJettyRunner;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.zookeeper.KeeperException;
import org.mortbay.jetty.servlet.FilterHolder;

/**
 * The monkey can stop random or specific jetties used with SolrCloud.
 * 
 * It can also run in a background thread and start and stop jetties
 * randomly.
 *
 */
public class ChaosMonkey {

  private static final boolean DONTKILLLEADER = true;
  private Map<String,List<CloudJettyRunner>> shardToJetty;
  
  private ZkTestServer zkServer;
  private ZkStateReader zkStateReader;
  private String collection;
  private Random random;
  private volatile boolean stop = false;
  private AtomicInteger stops = new AtomicInteger();
  private AtomicInteger starts = new AtomicInteger();
  private AtomicInteger expires = new AtomicInteger();
  private AtomicInteger connloss = new AtomicInteger();
  
  private Map<String,List<SolrServer>> shardToClient;
  private boolean expireSessions;
  private boolean causeConnectionLoss;
  
  public ChaosMonkey(ZkTestServer zkServer, ZkStateReader zkStateReader,
      String collection, Map<String,List<CloudJettyRunner>> shardToJetty,
      Map<String,List<SolrServer>> shardToClient, Random random) {
    this.shardToJetty = shardToJetty;
    this.shardToClient = shardToClient;
    this.zkServer = zkServer;
    this.zkStateReader = zkStateReader;
    this.collection = collection;
    this.random = random;
    
    expireSessions = random.nextBoolean();
    causeConnectionLoss = random.nextBoolean();
  }
  
  public void expireSession(JettySolrRunner jetty) {
    SolrDispatchFilter solrDispatchFilter = (SolrDispatchFilter) jetty.getDispatchFilter().getFilter();
    if (solrDispatchFilter != null) {
      CoreContainer cores = solrDispatchFilter.getCores();
      if (cores != null) {
        long sessionId = cores.getZkController().getZkClient().getSolrZooKeeper().getSessionId();
        zkServer.expire(sessionId);
      }
    }
  }
  
  public void expireRandomSession() throws KeeperException, InterruptedException {
    String sliceName = getRandomSlice();
    
    JettySolrRunner jetty = getRandomJetty(sliceName, DONTKILLLEADER);
    if (jetty != null) {
      expireSession(jetty);
      expires.incrementAndGet();
    }
  }
  
  public void randomConnectionLoss() throws KeeperException, InterruptedException {
    String sliceName = getRandomSlice();
    
    JettySolrRunner jetty = getRandomJetty(sliceName, DONTKILLLEADER);
    if (jetty != null) {
      causeConnectionLoss(jetty);
      connloss.incrementAndGet();
    }
  }
  
  private void causeConnectionLoss(JettySolrRunner jetty) {
    SolrDispatchFilter solrDispatchFilter = (SolrDispatchFilter) jetty
        .getDispatchFilter().getFilter();
    if (solrDispatchFilter != null) {
      CoreContainer cores = solrDispatchFilter.getCores();
      if (cores != null) {
        SolrZkClient zkClient = cores.getZkController().getZkClient();
        // must be at least double tick time...
        zkClient.getSolrZooKeeper().pauseCnxn(ZkTestServer.TICK_TIME * 2);
      }
    }
  }

  public JettySolrRunner stopShard(String slice, int index) throws Exception {
    JettySolrRunner jetty = shardToJetty.get(slice).get(index).jetty;
    stopJetty(jetty);
    return jetty;
  }

  public void stopJetty(JettySolrRunner jetty) throws Exception {
    if (jetty.isRunning()) {
      stops.incrementAndGet();
    }
    
    stop(jetty);
  }

  public void killJetty(JettySolrRunner jetty) throws Exception {
    if (jetty.isRunning()) {
      stops.incrementAndGet();
    }
    
    kill(jetty);

  }
  
  public static void stop(JettySolrRunner jetty) throws Exception {
    // get a clean shutdown so that no dirs are left open...
    FilterHolder fh = jetty.getDispatchFilter();
    if (fh != null) {
      SolrDispatchFilter sdf = (SolrDispatchFilter) fh.getFilter();
      if (sdf != null) {
        sdf.destroy();
      }
    }
    
    if (!jetty.isStopped()) {
      jetty.stop();
    }
    
    if (!jetty.isStopped()) {
      throw new RuntimeException("could not stop jetty");
    }
  }
  
  public static void kill(JettySolrRunner jetty) throws Exception {
    if (!jetty.isStopped()) {
      jetty.stop();
    }
    
    FilterHolder fh = jetty.getDispatchFilter();
    if (fh != null) {
      SolrDispatchFilter sdf = (SolrDispatchFilter) fh.getFilter();
      if (sdf != null) {
        sdf.destroy();
      }
    }
    
    if (!jetty.isStopped()) {
      throw new RuntimeException("could not kill jetty");
    }
  }
  
  public void stopShard(String slice) throws Exception {
    List<CloudJettyRunner> jetties = shardToJetty.get(slice);
    for (CloudJettyRunner jetty : jetties) {
      stopJetty(jetty.jetty);
    }
  }
  
  public void stopShardExcept(String slice, String shardName) throws Exception {
    List<CloudJettyRunner> jetties = shardToJetty.get(slice);
    for (CloudJettyRunner jetty : jetties) {
      if (!jetty.nodeName.equals(shardName)) {
        stopJetty(jetty.jetty);
      }
    }
  }
  
  public JettySolrRunner getShard(String slice, int index) throws Exception {
    JettySolrRunner jetty = shardToJetty.get(slice).get(index).jetty;
    return jetty;
  }
  
  public JettySolrRunner stopRandomShard() throws Exception {
    String sliceName = getRandomSlice();
    
    return stopRandomShard(sliceName);
  }
  
  public JettySolrRunner stopRandomShard(String slice) throws Exception {
    JettySolrRunner jetty = getRandomJetty(slice, DONTKILLLEADER);
    if (jetty != null) {
      stopJetty(jetty);
    }
    return jetty;
  }
  
  
  public JettySolrRunner killRandomShard() throws Exception {
    // add all the shards to a list
    String sliceName = getRandomSlice();
    
    return killRandomShard(sliceName);
  }

  private String getRandomSlice() {
    Map<String,Slice> slices = zkStateReader.getCloudState().getSlices(collection);
    
    List<String> sliceKeyList = new ArrayList<String>(slices.size());
    sliceKeyList.addAll(slices.keySet());
    String sliceName = sliceKeyList.get(random.nextInt(sliceKeyList.size()));
    return sliceName;
  }
  
  public JettySolrRunner killRandomShard(String slice) throws Exception {
    JettySolrRunner jetty = getRandomJetty(slice, DONTKILLLEADER);
    if (jetty != null) {
      killJetty(jetty);
    }
    return jetty;
  }
  
  public JettySolrRunner getRandomJetty(String slice, boolean dontkillleader) throws KeeperException, InterruptedException {
    // get latest cloud state
    zkStateReader.updateCloudState(true);
    Slice theShards = zkStateReader.getCloudState().getSlices(collection)
        .get(slice);
    int numRunning = 0;
    int numRecovering = 0;
    int numActive = 0;
    
    for (CloudJettyRunner cloudJetty : shardToJetty.get(slice)) {
      boolean running = true;
      
      ZkNodeProps props = theShards.getShards().get(cloudJetty.shardName);
      if (props == null) {
        throw new RuntimeException("shard name " + cloudJetty.shardName + " not found in " + theShards.getShards().keySet());
      }
      
      String state = props.get(ZkStateReader.STATE_PROP);
      String nodeName = props.get(ZkStateReader.NODE_NAME_PROP);
      
      
      if (!cloudJetty.jetty.isRunning()
          || !state.equals(ZkStateReader.ACTIVE)
          || !zkStateReader.getCloudState().liveNodesContain(nodeName)) {
        running = false;
      }
      
      if (cloudJetty.jetty.isRunning()
          && state.equals(ZkStateReader.RECOVERING)
          && zkStateReader.getCloudState().liveNodesContain(nodeName)) {
        numRecovering++;
      }
      
      if (cloudJetty.jetty.isRunning()
          && state.equals(ZkStateReader.ACTIVE)
          && zkStateReader.getCloudState().liveNodesContain(nodeName)) {
        numActive++;
      }
      
      if (running) {
        numRunning++;
      }
    }
    
    if (numActive < 2) {
      // we cannot kill anyone
      return null;
    }
    
    // get random shard
    List<CloudJettyRunner> jetties = shardToJetty.get(slice);
    int index = random.nextInt(jetties.size() - 1);
    JettySolrRunner jetty = jetties.get(index).jetty;
    
    ZkNodeProps leader = zkStateReader.getLeaderProps(collection, slice);
    
    if (dontkillleader && leader.get(ZkStateReader.NODE_NAME_PROP).equals(jetties.get(index).nodeName)) {
      // we don't kill leaders...
      return null;
    }
    
    return jetty;
  }
  
  public SolrServer getRandomClient(String slice) throws KeeperException, InterruptedException {
    // get latest cloud state
    zkStateReader.updateCloudState(true);

    // get random shard
    List<SolrServer> clients = shardToClient.get(slice);
    int index = random.nextInt(clients.size() - 1);
    SolrServer client = clients.get(index);

    return client;
  }
  
  // synchronously starts and stops shards randomly, unless there is only one
  // active shard up for a slice or if there is one active and others recovering
  public void startTheMonkey() {
    stop = false;
    new Thread() {
      private List<JettySolrRunner> deadPool = new ArrayList<JettySolrRunner>();

      @Override
      public void run() {
        while (!stop) {
          try {
            Thread.sleep(500);
            
            if (random.nextBoolean()) {
             if (!deadPool.isEmpty()) {
               JettySolrRunner jetty = deadPool.remove(random.nextInt(deadPool.size()));
               if (jetty.isRunning()) {
                 
               }
               try {
                 jetty.start();
               } catch (BindException e) {
                 jetty.stop();
                 sleep(2000);
                 try {
                   jetty.start();
                 } catch (BindException e2) {
                   jetty.stop();
                   sleep(5000);
                   try {
                     jetty.start();
                   } catch (BindException e3) {
                     // we coud not get the port
                     continue;
                   }
                 }
               }
               //System.out.println("started on port:" + jetty.getLocalPort());
               starts.incrementAndGet();
               continue;
             }
            }
            
            int rnd = random.nextInt(10);

            if (expireSessions && rnd < 3) {
              expireRandomSession();
            } 
            
            if (causeConnectionLoss && rnd < 2) {
              randomConnectionLoss();
              randomConnectionLoss();
            }
            
            JettySolrRunner jetty;
            if (random.nextBoolean()) {
              jetty = stopRandomShard();
            } else {
              jetty = killRandomShard();
            }
            if (jetty == null) {
              // we cannot kill
            } else {
              deadPool.add(jetty);
            }
            
          } catch (InterruptedException e) {
            //
          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
        
        System.out.println("I stopped " + stops + " and I started " + starts
            + ". I also expired " + expires.get() + " and caused " + connloss
            + " connection losses");
      }
    }.start();
  }
  
  public void stopTheMonkey() {
    stop = true;
  }

  public int getStarts() {
    return starts.get();
  }

}