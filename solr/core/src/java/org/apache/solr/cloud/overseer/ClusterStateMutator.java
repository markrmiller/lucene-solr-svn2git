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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.cloud.OverseerCollectionProcessor;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.DocRouter;
import org.apache.solr.common.cloud.ImplicitDocRouter;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.singletonMap;

public class ClusterStateMutator {
  private static Logger log = LoggerFactory.getLogger(ClusterStateMutator.class);

  protected final ZkStateReader zkStateReader;

  public ClusterStateMutator(ZkStateReader zkStateReader) {
    this.zkStateReader = zkStateReader;
  }

  public ZkWriteCommand createCollection(ClusterState clusterState, ZkNodeProps message) {
    String cName = message.getStr("name");
    log.info("building a new cName: " + cName);
    if (clusterState.hasCollection(cName)) {
      log.warn("Collection {} already exists. exit", cName);
      return ZkStateWriter.NO_OP;
    }

    ArrayList<String> shards = new ArrayList<>();

    if (ImplicitDocRouter.NAME.equals(message.getStr("router.name", DocRouter.DEFAULT_NAME))) {
      getShardNames(shards, message.getStr("shards", DocRouter.DEFAULT_NAME));
    } else {
      int numShards = message.getInt(ZkStateReader.NUM_SHARDS_PROP, -1);
      if (numShards < 1)
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "numShards is a required parameter for 'compositeId' router");
      getShardNames(numShards, shards);
    }

    Map<String, Object> routerSpec = DocRouter.getRouterSpec(message);
    String routerName = routerSpec.get("name") == null ? DocRouter.DEFAULT_NAME : (String) routerSpec.get("name");
    DocRouter router = DocRouter.getDocRouter(routerName);

    List<DocRouter.Range> ranges = router.partitionRange(shards.size(), router.fullRange());


    Map<String, Slice> newSlices = new LinkedHashMap<>();

    for (int i = 0; i < shards.size(); i++) {
      String sliceName = shards.get(i);

      Map<String, Object> sliceProps = new LinkedHashMap<>(1);
      sliceProps.put(Slice.RANGE, ranges == null ? null : ranges.get(i));

      newSlices.put(sliceName, new Slice(sliceName, null, sliceProps));
    }

    Map<String, Object> collectionProps = new HashMap<>();

    for (Map.Entry<String, Object> e : OverseerCollectionProcessor.COLL_PROPS.entrySet()) {
      Object val = message.get(e.getKey());
      if (val == null) {
        val = OverseerCollectionProcessor.COLL_PROPS.get(e.getKey());
      }
      if (val != null) collectionProps.put(e.getKey(), val);
    }
    collectionProps.put(DocCollection.DOC_ROUTER, routerSpec);

    if (message.getStr("fromApi") == null) {
      collectionProps.put("autoCreated", "true");
    }

    String znode = message.getInt(DocCollection.STATE_FORMAT, 1) == 1 ? null
        : ZkStateReader.getCollectionPath(cName);

    DocCollection newCollection = new DocCollection(cName,
        newSlices, collectionProps, router, -1, znode);

    return new ZkWriteCommand(cName, newCollection);
  }

  public ZkWriteCommand deleteCollection(ClusterState clusterState, ZkNodeProps message) {
    final String collection = message.getStr("name");
    if (!CollectionMutator.checkKeyExistence(message, "name")) return ZkStateWriter.NO_OP;
    DocCollection coll = clusterState.getCollectionOrNull(collection);
    if (coll == null) return ZkStateWriter.NO_OP;

    return new ZkWriteCommand(coll.getName(), null);
  }

  public static ClusterState newState(ClusterState state, String name, DocCollection collection) {
    ClusterState newClusterState = null;
    if (collection == null) {
      newClusterState = state.copyWith(name, (DocCollection) null);
    } else {
      newClusterState = state.copyWith(name, collection);
    }
    return newClusterState;
  }

  public static void getShardNames(Integer numShards, List<String> shardNames) {
    if (numShards == null)
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "numShards" + " is a required param");
    for (int i = 0; i < numShards; i++) {
      final String sliceName = "shard" + (i + 1);
      shardNames.add(sliceName);
    }

  }

  public static void getShardNames(List<String> shardNames, String shards) {
    if (shards == null)
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "shards" + " is a required param");
    for (String s : shards.split(",")) {
      if (s == null || s.trim().isEmpty()) continue;
      shardNames.add(s.trim());
    }
    if (shardNames.isEmpty())
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "shards" + " is a required param");
  }

  /*
       * Return an already assigned id or null if not assigned
       */
  public static String getAssignedId(final ClusterState state, final String nodeName,
                              final ZkNodeProps coreState) {
    Collection<Slice> slices = state.getSlices(coreState.getStr(ZkStateReader.COLLECTION_PROP));
    if (slices != null) {
      for (Slice slice : slices) {
        if (slice.getReplicasMap().get(nodeName) != null) {
          return slice.getName();
        }
      }
    }
    return null;
  }

  public static String getAssignedCoreNodeName(ClusterState state, ZkNodeProps message) {
    Collection<Slice> slices = state.getSlices(message.getStr(ZkStateReader.COLLECTION_PROP));
    if (slices != null) {
      for (Slice slice : slices) {
        for (Replica replica : slice.getReplicas()) {
          String nodeName = replica.getStr(ZkStateReader.NODE_NAME_PROP);
          String core = replica.getStr(ZkStateReader.CORE_NAME_PROP);

          String msgNodeName = message.getStr(ZkStateReader.NODE_NAME_PROP);
          String msgCore = message.getStr(ZkStateReader.CORE_NAME_PROP);

          if (nodeName.equals(msgNodeName) && core.equals(msgCore)) {
            return replica.getName();
          }
        }
      }
    }
    return null;
  }
}

