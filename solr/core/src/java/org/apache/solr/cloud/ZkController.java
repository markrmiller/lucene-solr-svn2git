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
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.cloud.RecoveryStrat.OnFinish;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.cloud.*;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.apache.solr.update.UpdateLog;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle ZooKeeper interactions.
 * 
 * notes: loads everything on init, creates what's not there - further updates
 * are prompted with Watches.
 * 
 * TODO: exceptions during shutdown on attempts to update cloud state
 * 
 */
public final class ZkController {

  private static Logger log = LoggerFactory.getLogger(ZkController.class);

  static final String NEWL = System.getProperty("line.separator");


  private final static Pattern URL_POST = Pattern.compile("https?://(.*)");
  private final static Pattern URL_PREFIX = Pattern.compile("(https?://).*");

  
  // package private for tests

  static final String CONFIGS_ZKNODE = "/configs";

  public final static String COLLECTION_PARAM_PREFIX="collection.";
  public final static String CONFIGNAME_PROP="configName";

  // nocommit: access to this is not thread safe!
  private final HashMap<String, CoreState> coreStates = new HashMap<String, CoreState>();
  private SolrZkClient zkClient;
  
  private ZkStateReader zkStateReader;

  private LeaderElector leaderElector;
  
  private String zkServerAddress;

  private String localHostPort;
  private String localHostContext;
  private String localHostName;
  private String localHost;

  private String hostName;

  private OverseerElector overseerElector;

  private Map<String, CoreAssignment> assignments = new HashMap<String, CoreAssignment>();

  private RecoveryStrat recoveryStrat = new RecoveryStrat();

  public static void main(String[] args) throws Exception {
    // start up a tmp zk server first
    String zkServerAddress = args[0];
    
    String solrHome = args[1];
    String solrPort = args[2];
    
    String confDir = args[3];
    String confName = args[4];
    
    SolrZkServer zkServer = new SolrZkServer("true", null, solrHome, solrPort);
    zkServer.parseConfig();
    zkServer.start();
    
    SolrZkClient zkClient = new SolrZkClient(zkServerAddress, 15000, 5000,
        new OnReconnect() {
          @Override
          public void command() {
          }});
    
    uploadConfigDir(zkClient, new File(confDir), confName);
    
    zkServer.stop();
  }


  /**
   * @param coreContainer
   * @param zkServerAddress
   * @param zkClientTimeout
   * @param zkClientConnectTimeout
   * @param localHost
   * @param locaHostPort
   * @param localHostContext
   * @param numShards 
   * @throws InterruptedException
   * @throws TimeoutException
   * @throws IOException
   */
  public ZkController(String zkServerAddress, int zkClientTimeout, int zkClientConnectTimeout, String localHost, String locaHostPort,
      String localHostContext, final CurrentCoreDescriptorProvider registerOnReconnect) throws InterruptedException,
      TimeoutException, IOException {
 
    this.zkServerAddress = zkServerAddress;
    this.localHostPort = locaHostPort;
    this.localHostContext = localHostContext;
    this.localHost = localHost;

    zkClient = new SolrZkClient(zkServerAddress, zkClientTimeout, zkClientConnectTimeout,
        // on reconnect, reload cloud info
        new OnReconnect() {

          public void command() {
            try {
              // we need to create all of our lost watches
              createEphemeralLiveNode();
              zkStateReader.createClusterStateWatchersAndUpdate();
              
              // re register all descriptors
              List<CoreDescriptor> descriptors = registerOnReconnect
                  .getCurrentDescriptors();
              if (descriptors != null) {
                for (CoreDescriptor descriptor : descriptors) {
                  // nocommit: non reloaded cores will try and
                  // recover - reloaded cores will not
                  register(descriptor.getName(), descriptor);
                }
              }

            } catch (InterruptedException e) {
              // Restore the interrupted status
              Thread.currentThread().interrupt();
              log.error("", e);
              throw new ZooKeeperException(
                  SolrException.ErrorCode.SERVER_ERROR, "", e);
            } catch (Exception e) {
              log.error("", e);
              throw new ZooKeeperException(
                  SolrException.ErrorCode.SERVER_ERROR, "", e);
            }

          }
        });
    
    leaderElector = new LeaderElector(zkClient);
    zkStateReader = new ZkStateReader(zkClient);
    init();
  }

  /**
   * Closes the underlying ZooKeeper client.
   */
  public void close() {
    try {
      zkClient.close();
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
      log.warn("", e);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "", e);
    }
    
    recoveryStrat.close();
  }

  /**
   * @param collection
   * @param fileName
   * @return true if config file exists
   * @throws KeeperException
   * @throws InterruptedException
   */
  public boolean configFileExists(String collection, String fileName)
      throws KeeperException, InterruptedException {
    Stat stat = zkClient.exists(CONFIGS_ZKNODE + "/" + collection + "/" + fileName, null);
    return stat != null;
  }

  /**
   * @return information about the cluster from ZooKeeper
   */
  public CloudState getCloudState() {
    return zkStateReader.getCloudState();
  }

  /**
   * @param zkConfigName
   * @param fileName
   * @return config file data (in bytes)
   * @throws KeeperException
   * @throws InterruptedException
   */
  public byte[] getConfigFileData(String zkConfigName, String fileName)
      throws KeeperException, InterruptedException {
    String zkPath = CONFIGS_ZKNODE + "/" + zkConfigName + "/" + fileName;
    byte[] bytes = zkClient.getData(zkPath, null, null);
    if (bytes == null) {
      log.error("Config file contains no data:" + zkPath);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "Config file contains no data:" + zkPath);
    }
    
    return bytes;
  }

  // TODO: consider how this is done
  private String getHostAddress() throws IOException {

    if (localHost == null) {
      localHost = "http://" + InetAddress.getLocalHost().getHostName();
    } else {
      Matcher m = URL_PREFIX.matcher(localHost);
      if (m.matches()) {
        String prefix = m.group(1);
        localHost = prefix + localHost;
      } else {
        localHost = "http://" + localHost;
      }
    }

    return localHost;
  }
  
  public String getHostName() {
    return hostName;
  }

  public SolrZkClient getZkClient() {
    return zkClient;
  }

  /**
   * @return zookeeper server address
   */
  public String getZkServerAddress() {
    return zkServerAddress;
  }

  private void init() {

    try {
      localHostName = getHostAddress();
      Matcher m = URL_POST.matcher(localHostName);

      if (m.matches()) {
        hostName = m.group(1);
      } else {
        log.error("Unrecognized host:" + localHostName);
        throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
            "Unrecognized host:" + localHostName);
      }
      
      // makes nodes zkNode
      try {
        zkClient.makePath(ZkStateReader.LIVE_NODES_ZKNODE);
      } catch (KeeperException e) {
        // its okay if another beats us creating the node
        if (e.code() != KeeperException.Code.NODEEXISTS) {
          log.error("", e);
          throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
              "", e);
        }
      }
      
      Overseer.createClientNodes(zkClient, getNodeName());
      createEphemeralLiveNode();
      setUpCollectionsNode();
      
      byte[] assignments = zkClient.getData(getAssignmentsNode(), new Watcher(){

        @Override
        public void process(WatchedEvent event) {
          //read latest assignments
          try {
            byte[] assignments = zkClient.getData(getAssignmentsNode(), this, null);
            try {
              processAssignmentsUpdate(assignments);
            } catch (IOException e) {
              log.error("Assignment data was malformed", e);
              return;
            }
          } catch (KeeperException e) {
            log.warn("Could not read node assignments.", e);
            return;
          } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            log.warn("Could not read node assignments.", e);
            return;
          }
          
        }
        
      }, null);

      processAssignmentsUpdate(assignments);
      
      overseerElector = new OverseerElector(zkClient, zkStateReader);
      ElectionContext context = new OverseerElectionContext(getNodeName());
      overseerElector.setup(context);
      overseerElector.joinElection(context);
      zkStateReader.createClusterStateWatchersAndUpdate();
      
    } catch (IOException e) {
      log.error("", e);
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
          "Can't create ZooKeeperController", e);
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
      log.error("", e);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "", e);
    } catch (KeeperException e) {
      log.error("", e);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "", e);
    }

  }

  private String getAssignmentsNode() {
    return Overseer.ASSIGNMENTS_NODE + "/" + getNodeName();
  }

  private String getStatesNode() {
    return Overseer.STATES_NODE + "/" + getNodeName();
  }

  private void createEphemeralLiveNode() throws KeeperException,
      InterruptedException {
    String nodeName = getNodeName();
    String nodePath = ZkStateReader.LIVE_NODES_ZKNODE + "/" + nodeName;
    log.info("Register node as live in ZooKeeper:" + nodePath);
   
    try {
      boolean nodeDeleted = true;
      try {
        // we attempt a delete in the case of a quick server bounce -
        // if there was not a graceful shutdown, the node may exist
        // until expiration timeout - so a node won't be created here because
        // it exists, but eventually the node will be removed. So delete
        // in case it exists and create a new node.
        zkClient.delete(nodePath, -1);
      } catch (KeeperException.NoNodeException e) {
        // fine if there is nothing to delete
        // TODO: annoying that ZK logs a warning on us
        nodeDeleted = false;
      }
      if (nodeDeleted) {
        log
            .info("Found a previous node that still exists while trying to register a new live node "
                + nodePath + " - removing existing node to create another.");
      }
      zkClient.makePath(nodePath, CreateMode.EPHEMERAL);
    } catch (KeeperException e) {
      // its okay if the node already exists
      if (e.code() != KeeperException.Code.NODEEXISTS) {
        throw e;
      }
    }    
  }
  
  public String getNodeName() {
    return hostName + ":" + localHostPort + "_" + localHostContext;
  }

  /**
   * @param path
   * @return true if the path exists
   * @throws KeeperException
   * @throws InterruptedException
   */
  public boolean pathExists(String path) throws KeeperException,
      InterruptedException {
    return zkClient.exists(path);
  }

  /**
   * @param collection
   * @return config value
   * @throws KeeperException
   * @throws InterruptedException
   * @throws IOException 
   */
  public String readConfigName(String collection) throws KeeperException,
      InterruptedException, IOException {

    String configName = null;

    String path = ZkStateReader.COLLECTIONS_ZKNODE + "/" + collection;
    if (log.isInfoEnabled()) {
      log.info("Load collection config from:" + path);
    }
    byte[] data = zkClient.getData(path, null, null);
    
    if(data != null) {
      ZkNodeProps props = ZkNodeProps.load(data);
      configName = props.get(CONFIGNAME_PROP);
    }
    
    if (configName != null && !zkClient.exists(CONFIGS_ZKNODE + "/" + configName)) {
      log.error("Specified config does not exist in ZooKeeper:" + configName);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "Specified config does not exist in ZooKeeper:" + configName);
    }

    return configName;
  }


  /**
   * Register shard with ZooKeeper.
   * 
   * @param coreName
   * @param cloudDesc
   * @return
   * @throws Exception 
   */
  public String register(String coreName, final CoreDescriptor desc) throws Exception {  
    final String shardUrl = localHostName + ":" + localHostPort + "/" + localHostContext
        + "/" + coreName;
    
    final CloudDescriptor cloudDesc = desc.getCloudDescriptor();
    final String collection = cloudDesc.getCollectionName();
    

    log.info("Attempting to update " + ZkStateReader.CLUSTER_STATE + " version "
        + null);
    CloudState state = CloudState.load(zkClient, zkStateReader.getCloudState().getLiveNodes());
    final String shardZkNodeName = getNodeName() + "_" + coreName;
    
    // checkRecovery will have updated the shardId if it already exists...
    String shardId = cloudDesc.getShardId();

    Map<String,String> props = new HashMap<String,String>();
    props.put(ZkStateReader.URL_PROP, shardUrl);
    props.put(ZkStateReader.NODE_NAME_PROP, getNodeName());
    props.put(ZkStateReader.ROLES_PROP, cloudDesc.getRoles());
    props.put(ZkStateReader.STATE_PROP, ZkStateReader.RECOVERING);
    if(shardId!=null) {
      props.put(ZkStateReader.SHARD_ID_PROP, shardId);
    }

    if (shardId == null && getShardId(desc, state, shardZkNodeName)) {
      publishState(cloudDesc, shardZkNodeName, props); //need to publish state to get overseer assigned id
      shardId = doGetShardIdProcess(coreName, cloudDesc);
      cloudDesc.setShardId(shardId);
      props.put(ZkStateReader.SHARD_ID_PROP, shardId);
    } else {
      // shard id was picked up in getShardId
      props.put(ZkStateReader.SHARD_ID_PROP, cloudDesc.getShardId());
      shardId = cloudDesc.getShardId();
      publishState(cloudDesc, shardZkNodeName, props);
    }

    if (log.isInfoEnabled()) {
        log.info("Register shard - core:" + coreName + " address:"
            + shardUrl + "shardId:" + shardId);
    }

    // we only put a subset of props into the leader node
    ZkNodeProps leaderProps = new ZkNodeProps(ZkStateReader.NODE_NAME_PROP,
        props.get(ZkStateReader.NODE_NAME_PROP), ZkStateReader.URL_PROP,
        props.get(ZkStateReader.URL_PROP));

    ElectionContext context = new ShardLeaderElectionContext(shardId, collection, shardZkNodeName, ZkStateReader.toJSON(leaderProps));
    
    leaderElector.setup(context);
    leaderElector.joinElection(context);
    
    // should be fine if we do this rather than read from cloud state since it's rare?
    String leaderUrl = zkStateReader.getLeaderUrl(collection, cloudDesc.getShardId());
    
    SolrCore core = null;
    try {
      boolean doRecovery = true;
      if (leaderUrl.equals(shardUrl)) {
        doRecovery = false;

        // recover from local transaction log and wait for it to complete before
        // going active
        // TODO: should this be moved to another thread? To recoveryStrat?
        // TODO: should this actually be done earlier, before (or as part of)
        // leader election perhaps?
        // TODO: ensure that a replica that is trying to recover waits until I'm
        // active (or don't make me the
        // leader until my local replay is done. But this replay is only needed
        // on the leader - replicas
        // will do recovery anyway
        CoreContainer cc = desc.getCoreContainer();
        if (cc != null) { // TODO: CoreContainer only null in tests?
          core = cc.getCore(desc.getName());
          if (!core.isReloaded()) {
            Future<UpdateLog.RecoveryInfo> recoveryFuture = core
                .getUpdateHandler().getUpdateLog().recoverFromLog();
            if (recoveryFuture != null) {
              recoveryFuture.get(); // NOTE: this could potentially block for
                                    // minutes or more!
              // TODO: public as recovering in the mean time?
            }
          }
        }
        
        // publish new props
        publishAsActive(shardUrl, cloudDesc, shardZkNodeName, shardId);
      } else {
        CoreContainer cc = desc.getCoreContainer();
        // CoreContainer can be null for some tests...
        if (cc != null) {
          core = cc.getCore(desc.getName());
          
          if (core.isReloaded()) {
            doRecovery = false;
          }
          
        } else {
          log.warn("Cannot recover without access to CoreContainer");
          return shardId;
        }
        
      }
      
      if (doRecovery) {
        recoveryStrat.recover(core, zkStateReader, shardUrl, new OnFinish() {

          @Override
          public void run() {
            // publish new props
            publishAsActive(shardUrl, cloudDesc, shardZkNodeName, cloudDesc.getShardId());
            
          }});
      }
    } finally {
      if (core != null) {
        core.close();
      }
    }
    
    // make sure we have an update cluster state right away
    zkStateReader.updateCloudState(true);

    return shardId;
  }


  private void publishAsActive(String shardUrl,
      final CloudDescriptor cloudDesc, String shardZkNodeName, String shardId) {
    Map<String,String> finalProps = new HashMap<String,String>();
    finalProps.put(ZkStateReader.URL_PROP, shardUrl);
    finalProps.put(ZkStateReader.NODE_NAME_PROP, getNodeName());
    finalProps.put(ZkStateReader.STATE_PROP, ZkStateReader.ACTIVE);
    finalProps.put(ZkStateReader.SHARD_ID_PROP, shardId);
    publishState(cloudDesc, shardZkNodeName, finalProps);
  }


  private boolean getShardId(final CoreDescriptor desc,
      CloudState state, String shardZkNodeName) {

    CloudDescriptor cloudDesc = desc.getCloudDescriptor();
    
    Map<String,Slice> slices = state.getSlices(cloudDesc.getCollectionName());
    if (slices != null) {
      Map<String,String> nodes = new HashMap<String,String>();

      for (Slice s : slices.values()) {
        for (String node : s.getShards().keySet()) {
          nodes.put(node, s.getName());
        }
      }
      if (nodes.containsKey(shardZkNodeName)) {
        // TODO: we where already registered - go into recovery mode
        cloudDesc.setShardId(nodes.get(shardZkNodeName));
        return false;
      }
    }
    return true;
  }

  /**
   * @param coreName
   * @param cloudDesc
   */
  public void unregister(String coreName, CloudDescriptor cloudDesc) {
    // TODO : perhaps mark the core down in zk?
  }

  /**
   * @param dir
   * @param zkPath
   * @throws IOException
   * @throws KeeperException
   * @throws InterruptedException
   */
  public void uploadToZK(File dir, String zkPath) throws IOException, KeeperException, InterruptedException {
    uploadToZK(zkClient, dir, zkPath);
  }
  
  /**
   * @param dir
   * @param configName
   * @throws IOException
   * @throws KeeperException
   * @throws InterruptedException
   */
  public void uploadConfigDir(File dir, String configName) throws IOException, KeeperException, InterruptedException {
    uploadToZK(zkClient, dir, ZkController.CONFIGS_ZKNODE + "/" + configName);
  }

  // convenience for testing
  void printLayoutToStdOut() throws KeeperException, InterruptedException {
    zkClient.printLayoutToStdOut();
  }

  private void setUpCollectionsNode() throws KeeperException, InterruptedException {
    try {
      if (!zkClient.exists(ZkStateReader.COLLECTIONS_ZKNODE)) {
        if (log.isInfoEnabled()) {
          log.info("creating zk collections node:" + ZkStateReader.COLLECTIONS_ZKNODE);
        }
        // makes collections zkNode if it doesn't exist
        zkClient.makePath(ZkStateReader.COLLECTIONS_ZKNODE, CreateMode.PERSISTENT, null);
      }
    } catch (KeeperException e) {
      // its okay if another beats us creating the node
      if (e.code() != KeeperException.Code.NODEEXISTS) {
        log.error("", e);
        throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
            "", e);
      }
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
      log.error("", e);
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR,
          "", e);
    }
    
  }

  public void createCollectionZkNode(CloudDescriptor cd) throws KeeperException, InterruptedException, IOException {
    String collection = cd.getCollectionName();
    
    log.info("Check for collection zkNode:" + collection);
    String collectionPath = ZkStateReader.COLLECTIONS_ZKNODE + "/" + collection;
    
    try {
      if(!zkClient.exists(collectionPath)) {
        log.info("Creating collection in ZooKeeper:" + collection);
       SolrParams params = cd.getParams();

        try {
          Map<String,String> collectionProps = new HashMap<String,String>();
          // TODO: if collection.configName isn't set, and there isn't already a conf in zk, just use that?
          String defaultConfigName = System.getProperty(COLLECTION_PARAM_PREFIX+CONFIGNAME_PROP, "configuration1");

          // params passed in - currently only done via core admin (create core commmand).
          if (params != null) {
            Iterator<String> iter = params.getParameterNamesIterator();
            while (iter.hasNext()) {
              String paramName = iter.next();
              if (paramName.startsWith(COLLECTION_PARAM_PREFIX)) {
                collectionProps.put(paramName.substring(COLLECTION_PARAM_PREFIX.length()), params.get(paramName));
              }
            }

            // if the config name wasn't passed in, use the default
            if (!collectionProps.containsKey(CONFIGNAME_PROP))
              collectionProps.put(CONFIGNAME_PROP,  defaultConfigName);
            
          } else if(System.getProperty("bootstrap_confdir") != null) {
            // if we are bootstrapping a collection, default the config for
            // a new collection to the collection we are bootstrapping
            log.info("Setting config for collection:" + collection + " to " + defaultConfigName);

            Properties sysProps = System.getProperties();
            for (String sprop : System.getProperties().stringPropertyNames()) {
              if (sprop.startsWith(COLLECTION_PARAM_PREFIX)) {
                collectionProps.put(sprop.substring(COLLECTION_PARAM_PREFIX.length()), sysProps.getProperty(sprop));                
              }
            }
            
            // if the config name wasn't passed in, use the default
            if (!collectionProps.containsKey(CONFIGNAME_PROP))
              collectionProps.put(CONFIGNAME_PROP,  defaultConfigName);

          } else {
            // check for configName
            log.info("Looking for collection configName");
            int retry = 1;
            for (; retry < 6; retry++) {
              if (zkClient.exists(collectionPath)) {
                ZkNodeProps cProps = ZkNodeProps.load(zkClient.getData(collectionPath, null, null));
                if (cProps.containsKey(CONFIGNAME_PROP)) {
                  break;
                }
              }
              // if there is only one conf, use that
              List<String> configNames = zkClient.getChildren(CONFIGS_ZKNODE, null);
              if (configNames.size() == 1) {
                // no config set named, but there is only 1 - use it
                log.info("Only one config set found in zk - using it:" + configNames.get(0));
                collectionProps.put(CONFIGNAME_PROP,  configNames.get(0));
                break;
              }
              log.info("Could not find collection configName - pausing for 2 seconds and trying again - try: " + retry);
              Thread.sleep(2000);
            }
            if (retry == 6) {
              log.error("Could not find configName for collection " + collection);
              throw new ZooKeeperException(
                  SolrException.ErrorCode.SERVER_ERROR,
                  "Could not find configName for collection " + collection);
            }
          }
          
          ZkNodeProps zkProps = new ZkNodeProps(collectionProps);
          zkClient.makePath(collectionPath, ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, null, true);
         
          // ping that there is a new collection
          zkClient.setData(ZkStateReader.COLLECTIONS_ZKNODE, (byte[])null);
        } catch (KeeperException e) {
          // its okay if the node already exists
          if (e.code() != KeeperException.Code.NODEEXISTS) {
            throw e;
          }
        }
      } else {
        log.info("Collection zkNode exists");
      }
      
    } catch (KeeperException e) {
      // its okay if another beats us creating the node
      if (e.code() != KeeperException.Code.NODEEXISTS) {
        throw e;
      }
    }
    
  }
  
  public ZkStateReader getZkStateReader() {
    return zkStateReader;
  }

  
  private void publishState(CloudDescriptor cloudDesc, String shardZkNodeName,
      Map<String,String> props) {
    CoreState coreState = new CoreState(shardZkNodeName,
        cloudDesc.getCollectionName(), props);
    coreStates.put(shardZkNodeName, coreState);
    final String nodePath = "/node_states/" + getNodeName();

    try {
      log.info("publishing node state:" + coreStates.values());
      zkClient.setData(
          nodePath,
          ZkStateReader.toJSON(coreStates.values()));

    } catch (KeeperException e) {
      throw new ZooKeeperException(
          SolrException.ErrorCode.SERVER_ERROR,
          "could not publish node state", e);
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
      throw new ZooKeeperException(
          SolrException.ErrorCode.SERVER_ERROR,
          "could not publish node state", e);
    }
  }

  private String doGetShardIdProcess(String coreName, CloudDescriptor descriptor) throws InterruptedException {
    final String shardZkNodeName = getNodeName() + "_" + coreName;
    int retryCount = 40;
    while (retryCount-->0) {
      synchronized (assignments) {
        CoreAssignment assignment = assignments.get(shardZkNodeName);
        if (assignment != null
            && assignment.getProperties().get(ZkStateReader.SHARD_ID_PROP) != null) {
          return assignment.getProperties().get(ZkStateReader.SHARD_ID_PROP);
        }
        
        try {
          assignments.wait(500);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
        }
      }
    }
    throw new SolrException(ErrorCode.SERVER_ERROR, "Could not get shard_id for core: " + coreName);
  }

  /**
   * Atomic assignments update
   * @throws IOException 
   */
  private void processAssignmentsUpdate(byte[] assignments) throws IOException {

    if (assignments == null) {
      return;
    }

    HashMap<String, CoreAssignment> newAssignments = new HashMap<String, CoreAssignment>();
    CoreAssignment[] assignments2 = CoreAssignment.load(assignments);
    
    for (CoreAssignment assignment : assignments2) {
      newAssignments.put(assignment.getCoreName(), assignment);
    }

    // nocommit: is this right? It locks on the ref'd object, not the field.
    synchronized (this.assignments) {
      this.assignments.notifyAll();
      this.assignments = newAssignments;
    }
  }


  public RecoveryStrat getRecoveryStrat() {
    return recoveryStrat;
  }
  
  public static void uploadToZK(SolrZkClient zkClient, File dir, String zkPath) throws IOException, KeeperException, InterruptedException {
    File[] files = dir.listFiles();
    if (files == null) {
      throw new IllegalArgumentException("Illegal directory: " + dir);
    }
    for(File file : files) {
      if (!file.getName().startsWith(".")) {
        if (!file.isDirectory()) {
          zkClient.makePath(zkPath + "/" + file.getName(), file);
        } else {
          uploadToZK(zkClient, file, zkPath + "/" + file.getName());
        }
      }
    }
  }
  
  public static void uploadConfigDir(SolrZkClient zkClient, File dir, String configName) throws IOException, KeeperException, InterruptedException {
    uploadToZK(zkClient, dir, ZkController.CONFIGS_ZKNODE + "/" + configName);
  }
}
