package org.apache.solr.cloud.overseer;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.solr.cloud.Overseer;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.util.stats.TimerContext;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.singletonMap;

public class ZkStateWriter {
  private static Logger log = LoggerFactory.getLogger(ZkStateWriter.class);
  public static ZkWriteCommand NO_OP = new ZkWriteCommand();

  protected final ZkStateReader reader;
  protected final Overseer.Stats stats;

  protected Map<String, DocCollection> updates = new HashMap<>();
  protected ClusterState clusterState = null;
  protected boolean isClusterStateModified = false;
  protected long lastUpdatedTime = -1;

  public ZkStateWriter(ZkStateReader zkStateReader, Overseer.Stats stats) {
    assert zkStateReader != null;

    this.reader = zkStateReader;
    this.stats = stats;
  }

  public ClusterState enqueueUpdate(ClusterState prevState, ZkWriteCommand cmd) {
    if (cmd == NO_OP) return prevState;

    if (cmd.collection == null) {
      isClusterStateModified = true;
      clusterState = prevState.copyWith(singletonMap(cmd.name, (DocCollection) null));
      updates.put(cmd.name, null);
    } else {
      if (cmd.collection.getStateFormat() > 1) {
        updates.put(cmd.name, cmd.collection);
      } else {
        isClusterStateModified = true;
      }
      clusterState = prevState.copyWith(singletonMap(cmd.name, cmd.collection));
    }
    return clusterState;
  }

  public boolean hasPendingUpdates() {
    return !updates.isEmpty() || isClusterStateModified;
  }

  public ClusterState writePendingUpdates() throws KeeperException, InterruptedException {
    if (!hasPendingUpdates()) throw new IllegalStateException("No queued updates to execute");
    TimerContext timerContext = stats.time("update_state");
    boolean success = false;
    try {
      if (!updates.isEmpty()) {
        for (Map.Entry<String, DocCollection> entry : updates.entrySet()) {
          String name = entry.getKey();
          String path = ZkStateReader.getCollectionPath(name);
          DocCollection c = entry.getValue();

          if (c == null) {
            // let's clean up the collections path for this collection
            reader.getZkClient().clean("/collections/" + name);
          } else if (c.getStateFormat() > 1) {
            byte[] data = ZkStateReader.toJSON(new ClusterState(-1, Collections.<String>emptySet(), singletonMap(c.getName(), c)));
            if (reader.getZkClient().exists(path, true)) {
              assert c.getZNodeVersion() >= 0;
              log.info("going to update_collection {}", path);
              Stat stat = reader.getZkClient().setData(path, data, c.getZNodeVersion(), true);
              DocCollection newCollection = new DocCollection(name, c.getSlicesMap(), c.getProperties(), c.getRouter(), stat.getVersion(), path);
              clusterState = clusterState.copyWith(singletonMap(name, newCollection));
            } else {
              log.info("going to create_collection {}", path);
              reader.getZkClient().create(path, data, CreateMode.PERSISTENT, true);
              DocCollection newCollection = new DocCollection(name, c.getSlicesMap(), c.getProperties(), c.getRouter(), 0, path);
              clusterState = clusterState.copyWith(singletonMap(name, newCollection));
              isClusterStateModified = true;
            }
          } else if (c.getStateFormat() == 1) {
            isClusterStateModified = true;
          }
        }

        updates.clear();
      }

      if (isClusterStateModified) {
        assert clusterState.getZkClusterStateVersion() >= 0;
        lastUpdatedTime = System.nanoTime();
        byte[] data = ZkStateReader.toJSON(clusterState);
        Stat stat = reader.getZkClient().setData(ZkStateReader.CLUSTER_STATE, data, clusterState.getZkClusterStateVersion(), true);
        Set<String> collectionNames = clusterState.getCollections();
        Map<String, DocCollection> collectionStates = new HashMap<>(collectionNames.size());
        for (String c : collectionNames) {
          collectionStates.put(c, clusterState.getCollection(c));
        }
        // use the reader's live nodes because our cluster state's live nodes may be stale
        clusterState = new ClusterState(stat.getVersion(), reader.getClusterState().getLiveNodes(), collectionStates);
        isClusterStateModified = false;
      }
      success = true;
    } finally {
      timerContext.stop();
      if (success) {
        stats.success("update_state");
      } else {
        stats.error("update_state");
      }
    }

    return clusterState;
  }

  public long getLastUpdatedTime() {
    return lastUpdatedTime;
  }

  public ClusterState getClusterState() {
    return clusterState;
  }
}

