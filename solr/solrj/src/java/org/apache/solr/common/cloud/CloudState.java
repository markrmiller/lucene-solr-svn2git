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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.XMLErrorLogger;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

// quasi immutable :(
public class CloudState {
	protected static Logger log = LoggerFactory.getLogger(CloudState.class);
	private static final XMLErrorLogger xmllog = new XMLErrorLogger(log);
	private final Map<String, Map<String, Slice>> collectionStates;
	private final Set<String> liveNodes;

	public CloudState() {
		this.liveNodes = new HashSet<String>();
		this.collectionStates = new HashMap<String, Map<String, Slice>>(0);
	}

	public CloudState(Set<String> liveNodes,
			Map<String, Map<String, Slice>> collectionStates) {
		this.liveNodes = new HashSet<String>(liveNodes.size());
		this.liveNodes.addAll(liveNodes);
		this.collectionStates = new HashMap<String, Map<String, Slice>>(collectionStates.size());
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
			collectionStates.put(collection, new HashMap<String, Slice>());
		}
		if (!collectionStates.get(collection).containsKey(slice.getName())) {
			collectionStates.get(collection).put(slice.getName(), slice);
		} else {
			Map<String, ZkNodeProps> shards = new HashMap<String, ZkNodeProps>();
			
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

	public static CloudState load(SolrZkClient zkClient, Set<String> liveNodes) throws KeeperException, InterruptedException {
    byte[] state = zkClient.getData(ZkStateReader.CLUSTER_STATE,
        null, null);
    return load(state, liveNodes);
	}
	
	public static CloudState load(byte[] state, Set<String> liveNodes) throws KeeperException, InterruptedException {
	  String dataString = null;
    if (state != null) {
      try {
        dataString = new String(state, "UTF-8");
      } catch (UnsupportedEncodingException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
    }
	  System.out.println("read state:" + dataString);
	  
	  Map<String,Map<String,Slice>> colStates = new HashMap<String, Map<String, Slice>>();
	  
		if(state != null && state.length > 0) {
			InputSource is = new InputSource(new ByteArrayInputStream(state));
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	
			try {
				DocumentBuilder db = dbf.newDocumentBuilder();
	
				db.setErrorHandler(xmllog);
				Document doc = db.parse(is);
	
				
				
				Element root = doc.getDocumentElement();
	
				NodeList collectionStates = root.getChildNodes();
				for (int x = 0; x < collectionStates.getLength(); x++) {
					Node collectionState = collectionStates.item(x);
					String collectionName = collectionState.getAttributes()
							.getNamedItem("name").getNodeValue();
					NodeList slices = collectionState.getChildNodes();
					for (int y = 0; y < slices.getLength(); y++) {
						Node slice = slices.item(y);
						Node sliceName = slice.getAttributes().getNamedItem("name");
						
						NodeList shardsNodeList = slice.getChildNodes();
						Map<String, ZkNodeProps> shards = new HashMap<String, ZkNodeProps>();
						for (int z = 0; z < shardsNodeList.getLength(); z++) {
							Node shard = shardsNodeList.item(z);
							String shardName = shard.getAttributes()
									.getNamedItem("name").getNodeValue();
							NodeList propsList = shard.getChildNodes();
							Map<String,String> props = new HashMap<String,String>();
							
							for (int i = 0; i < propsList.getLength(); i++) {
								Node prop = propsList.item(i);
								String propName = prop.getAttributes()
										.getNamedItem("name").getNodeValue();
								String propValue = prop.getTextContent();
								props.put(propName, propValue);
							}
							shards.put(shardName, new ZkNodeProps(props));
						}
            Map<String,Slice> s = null;
            if (!colStates.containsKey(collectionName)) {
              s = new HashMap<String,Slice>();
              colStates.put(collectionName, s);
            } else {
              s = colStates.get(collectionName);
            }
            String sn = sliceName.getTextContent();
            Slice sl = s.get(sn);

            if (sl == null) {
              sl = new Slice(sliceName.getTextContent(), shards);
              s.put(sn, sl);
            } else {
              sl = new Slice(sliceName.getTextContent(), shards);
            }
			      
//			      Slice existingSlice = colStates.get(collection).get(slice.getName());
//			      shards.putAll(existingSlice.getShards());
//			      shards.putAll(slice.getShards());
//			      Slice updatedSlice = new Slice(slice.getName(), shards);
//			      collectionStates.get(collection).put(slice.getName(), updatedSlice);
//				    }
//						
//						Slice s = new Slice(sliceName.getNodeValue(), shards);
//	
//						colStates.put(collectionName, s);
					}
				}
			} catch (SAXException e) {
        log.error("", e);
        throw new ZooKeeperException(
            SolrException.ErrorCode.SERVER_ERROR, "", e);
			} catch (IOException e) {
        log.error("", e);
        throw new ZooKeeperException(
            SolrException.ErrorCode.SERVER_ERROR, "", e);
			} catch (ParserConfigurationException e) {
        log.error("", e);
        throw new ZooKeeperException(
            SolrException.ErrorCode.SERVER_ERROR, "", e);
			} finally {
				// some XML parsers are broken and don't close the byte stream (but
				// they should according to spec)
				IOUtils.closeQuietly(is.getByteStream());
			}
		}
		
		CloudState cloudState = new CloudState(liveNodes, colStates);
		System.out.println("read state: "+ cloudState);
		return cloudState;
	}

	public static byte[] store(CloudState state)
			throws UnsupportedEncodingException, IOException {
		StringWriter stringWriter = new StringWriter();
		Writer w = new BufferedWriter(stringWriter);
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
		w.write("<clusterstate>");
		Map<String, Map<String, Slice>> collectionStates = state
				.getCollectionStates();
		for (String collectionName : collectionStates.keySet()) {
			w.write("<collectionstate name=\"" + collectionName + "\">");
			Map<String, Slice> collection = collectionStates
					.get(collectionName);
			for (String sliceName : collection.keySet()) {
				w.write("<shard name=\"" + sliceName + "\">");
				Slice slice = collection.get(sliceName);
				Map<String, ZkNodeProps> shards = slice.getShards();
				for (String shardName : shards.keySet()) {
					w.write("<replica name=\"" + shardName + "\">");
					ZkNodeProps props = shards.get(shardName);
					for (String propName : props.keySet()) {
						w.write("<str name=\"" + propName + "\">"
								+ props.get(propName) + "</str>");
					}
					w.write("</replica>");

				}
				w.write("</shard>");
			}
			w.write("</collectionstate>");
		}
		w.write("</clusterstate>");
		w.flush();
		w.close();
		String xml = stringWriter.toString();
		System.out.println("xml:" + xml);
		return xml.getBytes("UTF-8");

	}

//  public void setLiveNodes(List<String> liveNodes) {
//    Set<String> liveNodesSet = new HashSet<String>(liveNodes.size());
//    liveNodesSet.addAll(liveNodes);
//    this.liveNodes = liveNodesSet;
//  }
}
