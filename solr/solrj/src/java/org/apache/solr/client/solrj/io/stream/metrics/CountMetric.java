package org.apache.solr.client.solrj.io.stream.metrics;
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

import java.io.Serializable;

import org.apache.solr.client.solrj.io.Tuple;

public class CountMetric implements Metric, Serializable {

  private static final long serialVersionUID = 1;

  private long count;

  public String getName() {
    return "count(*)";
  }

  public void update(Tuple tuple) {
    ++count;
  }

  public double getValue() {
    return count;
  }

  public Metric newInstance() {
    return new CountMetric();
  }
}