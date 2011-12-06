package org.apache.solr.common.cloud;

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
import java.io.InputStreamReader;
import java.util.*;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.noggit.*;
import org.apache.solr.common.cloud.CoreAssignment;
import org.apache.solr.common.cloud.CoreState;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// quasi immutable :(
public class CloudState {
	protected static Logger log = LoggerFactory.getLogger(CloudState.class);
	private final Map<String, Map<String,Slice>> collectionStates;  // Map<collectionName, Map<sliceName,Slice>>
	private final Set<String> liveNodes;

	public CloudState() {
		this.liveNodes = new HashSet<String>();
		this.collectionStates = new HashMap<String,Map<String,Slice>>(0);
	}

	public CloudState(Set<String> liveNodes,
			Map<String, Map<String,Slice>> collectionStates) {
		this.liveNodes = new HashSet<String>(liveNodes.size());
		this.liveNodes.addAll(liveNodes);
		this.collectionStates = new HashMap<String, Map<String,Slice>>(collectionStates.size());
		this.collectionStates.putAll(collectionStates);
	}

	public Slice getSlice(String collection, String slice) {
		if (collectionStates.containsKey(collection)
				&& collectionStates.get(collection).containsKey(slice))
			return collectionStates.get(collection).get(slice);
		return null;
	}

	// TODO: this method must die - this object should be immutable!!
	public void addSlice(String collection, Slice slice) {
		if (!collectionStates.containsKey(collection)) {
			log.info("New collection");
			collectionStates.put(collection, new HashMap<String,Slice>());
		}
		if (!collectionStates.get(collection).containsKey(slice.getName())) {
			collectionStates.get(collection).put(slice.getName(), slice);
		} else {
			Map<String,ZkNodeProps> shards = new HashMap<String,ZkNodeProps>();
			
			Slice existingSlice = collectionStates.get(collection).get(slice.getName());
			shards.putAll(existingSlice.getShards());
			shards.putAll(slice.getShards());
			Slice updatedSlice = new Slice(slice.getName(), shards);
			collectionStates.get(collection).put(slice.getName(), updatedSlice);
		}
	}

	public Map<String, Slice> getSlices(String collection) {
		if(!collectionStates.containsKey(collection))
			return null;
		return Collections.unmodifiableMap(collectionStates.get(collection));
	}

	public Set<String> getCollections() {
		return Collections.unmodifiableSet(collectionStates.keySet());
	}

	public Map<String, Map<String, Slice>> getCollectionStates() {
		return Collections.unmodifiableMap(collectionStates);
	}

	public Set<String> getLiveNodes() {
		return Collections.unmodifiableSet(liveNodes);
	}

//	public void setLiveNodes(Set<String> liveNodes) {
//		this.liveNodes = liveNodes;
//	}

	public boolean liveNodesContain(String name) {
		return liveNodes.contains(name);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("live nodes:" + liveNodes);
		sb.append(" collections:" + collectionStates);
		return sb.toString();
	}

	public static CloudState load(SolrZkClient zkClient, Set<String> liveNodes) throws KeeperException, InterruptedException, IOException {
    byte[] state = zkClient.getData(ZkStateReader.CLUSTER_STATE,
        null, null);
    return load(state, liveNodes);
	}
	
	public static CloudState load(byte[] bytes, Set<String> liveNodes) throws KeeperException, InterruptedException, IOException {
    if (bytes == null || bytes.length == 0) {
      return new CloudState(liveNodes, Collections.<String, Map<String,Slice>>emptyMap());
    }
    
    LinkedHashMap<String, Object> stateMap = (LinkedHashMap<String, Object>) fromJSON(bytes);
    HashMap<String,Map<String, Slice>> state = new HashMap<String,Map<String,Slice>>();

    for(String collectionName: stateMap.keySet()){
      Map<String, Object> collection = (Map<String, Object>)stateMap.get(collectionName);
      HashMap<String, Slice> slices = new HashMap<String,Slice>();
      for(String sliceName: collection.keySet()) {
        Map<String, Map<String, String>> sliceMap = (Map<String, Map<String, String>>)collection.get(sliceName);
        HashMap<String, ZkNodeProps> shards = new HashMap<String,ZkNodeProps>();
        for(String shardName: sliceMap.keySet()) {
          shards.put(shardName, new ZkNodeProps(sliceMap.get(shardName)));
        }
        Slice slice = new Slice(sliceName, shards);
        slices.put(sliceName, slice);
      }
      state.put(collectionName, slices);
    }
    return new CloudState(liveNodes, state);
	}

  private static class CloudJSONWriter extends JSONWriter {
    public CloudJSONWriter(CharArr out, int indent) {
      super(out, indent);
    }
    @Override
    public void handleUnknownClass(Object o) {
      if (o instanceof Slice) {
        setIndentSize(2); // this is big enough, we currently always want indenting
        write( ((Slice)o).getShards() );
      } else if (o instanceof ZkNodeProps) {
        write( ((ZkNodeProps) o).getProperties() );
      } else if (o instanceof CloudState) {
        setIndentSize(2); // this is big enough, we currently always want indenting
        write( ((CloudState)o).getCollectionStates() );
      } else if (o instanceof CoreAssignment) {
        write(((CoreAssignment) o).getProperties());
      } else if (o instanceof CoreState) {
        write(((CoreState) o).getProperties());
      } else {
        super.handleUnknownClass(o);
      }
    }
  }

  public static byte[] store(CloudState state)
      throws IOException {
    return toJSON(state);
  }

  // convenience methods... should these go somewhere else?

  public static byte[] toJSON(Object o) {
    CharArr out = new CharArr();
    new CloudJSONWriter(out, 2).write(o); // indentation by default
    return toUTF8(out);
  }

  public static byte[] toUTF8(CharArr out) {
    BytesRef br = new BytesRef(out);
    return Arrays.copyOf(br.bytes, br.length);
  }

  public static Object fromJSON(byte[] utf8) {
    // convert directly from bytes to chars
    // and parse directly from that instead of going through
    // intermediate strings or readers
    CharsRef chars = new CharsRef();
    UnicodeUtil.UTF8toUTF16(utf8, 0, utf8.length, chars);   // TODO: this method currently oversizes the array
    JSONParser parser = new JSONParser(chars.chars, chars.offset, chars.length);
    try {
      return ObjectBuilder.getVal(parser);
    } catch (IOException e) {
      throw new RuntimeException(e); // should never happen w/o using real IO
    }
  }

  public static byte[] store0(CloudState state)
      throws IOException {
    CharArr out = new CharArr();
    out.append(JSONUtil.OBJECT_START);
    int collCount = state.getCollections().size();

    for (String collectionName : state.getCollections()) {
      JSONUtil.writeString(collectionName, 0, collectionName.length(), out);
      out.append(JSONUtil.NAME_SEPARATOR);
      Map<String, Slice> slices = state.getSlices(collectionName);
      out.append(JSONUtil.OBJECT_START);
      int sliceCount = slices.keySet().size();
      for(String sliceName: slices.keySet()) {
        JSONUtil.writeString(sliceName, 0, sliceName.length(), out);
        out.append(JSONUtil.NAME_SEPARATOR);
        Slice slice = slices.get(sliceName);
        Map<String, ZkNodeProps> shards = slice.getShards();
        out.append(JSONUtil.OBJECT_START);
        int shardCount = shards.keySet().size();
        for(String shardName: shards.keySet()) {
          ZkNodeProps props = shards.get(shardName);
          JSONUtil.writeString(shardName, 0, shardName.length(), out);
          out.append(JSONUtil.NAME_SEPARATOR);
          out.append(JSONUtil.OBJECT_START);
          int propCount = props.keySet().size();
          for(String key: props.keySet()) {
            JSONUtil.writeString(key, 0, key.length(), out);
            out.append(JSONUtil.NAME_SEPARATOR);
            JSONUtil.writeString(props.get(key), 0, props.get(key).length(), out);
            if (--propCount != 0) {
              out.append(JSONUtil.VALUE_SEPARATOR);
            }
          }
          out.append(JSONUtil.OBJECT_END);
          if (--shardCount != 0) {
            out.append(JSONUtil.VALUE_SEPARATOR);
          }
        }
        out.append(JSONUtil.OBJECT_END);
        if (--sliceCount != 0) {
          out.append(JSONUtil.VALUE_SEPARATOR);
        }

      }
      out.append(JSONUtil.OBJECT_END);
      if (--collCount != 0) {
        out.append(JSONUtil.VALUE_SEPARATOR);
      }
    }
    out.append(JSONUtil.OBJECT_END);
    return new String(out.getArray()).getBytes("utf-8");
  }
}
