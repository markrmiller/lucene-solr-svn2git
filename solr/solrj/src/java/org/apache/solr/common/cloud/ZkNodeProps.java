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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.noggit.JSONUtil;
import org.apache.noggit.ObjectBuilder;

// Immutable
public class ZkNodeProps  {

  private final Map<String,String> propMap;

  public ZkNodeProps(Map<String,String> propMap) {
    this.propMap = new HashMap<String,String>();
    this.propMap.putAll(propMap);
  }
  
  public ZkNodeProps(ZkNodeProps zkNodeProps) {
    this.propMap = new HashMap<String,String>();
    this.propMap.putAll(zkNodeProps.propMap);
  }
  
  public ZkNodeProps() {
    propMap = new HashMap<String,String>();
  }
  
  public Set<String> keySet() {
    return Collections.unmodifiableSet(propMap.keySet());
  }

  public Map<String,String> getProperties() {
    return Collections.unmodifiableMap(propMap);
  }

  public static ZkNodeProps load(byte[] bytes) throws IOException {
    Map<String, String> props = (Map<String, String>) CloudState.fromJSON(bytes);
    return new ZkNodeProps(props);
  }

  public byte[] store() throws IOException {
    return CloudState.toJSON(propMap);
  }
  
  public String get(String key) {
    return propMap.get(key);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    Set<Entry<String,String>> entries = propMap.entrySet();
    for(Entry<String,String> entry : entries) {
      sb.append(entry.getKey() + "=" + entry.getValue() + "\n");
    }
    return sb.toString();
  }
  
  public boolean containsKey(String key) {
    return propMap.containsKey(key);
  }

}
