package org.apache.lucene.analysis.kuromoji.util;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import org.apache.lucene.analysis.kuromoji.dict.ConnectionCosts;

public class ConnectionCostsBuilder {
  
  public ConnectionCostsBuilder() {
    
  }
  
  public static ConnectionCosts build(String filename) throws IOException {
    FileInputStream inputStream = new FileInputStream(filename);
    InputStreamReader streamReader = new InputStreamReader(inputStream);
    LineNumberReader lineReader = new LineNumberReader(streamReader);
    
    String line = lineReader.readLine();
    String[] dimensions = line.split("\\s+");
    
    assert dimensions.length == 3;
    
    int forwardSize = Integer.parseInt(dimensions[0]);
    int backwardSize = Integer.parseInt(dimensions[1]);
    
    assert forwardSize > 0 && backwardSize > 0;
    
    ConnectionCosts costs = new ConnectionCosts(forwardSize, backwardSize);
    
    while ((line = lineReader.readLine()) != null) {
      String[] fields = line.split("\\s+");
      
      assert fields.length == 3;
      
      int forwardId = Integer.parseInt(fields[0]);
      int backwardId = Integer.parseInt(fields[1]);
      int cost = Integer.parseInt(fields[2]);
      
      costs.add(forwardId, backwardId, cost);
    }
    return costs;
  }
}
