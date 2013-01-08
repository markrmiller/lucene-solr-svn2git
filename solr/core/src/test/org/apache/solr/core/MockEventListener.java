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
package org.apache.solr.core;

import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.common.util.NamedList;

import java.util.concurrent.atomic.AtomicInteger;

public class MockEventListener implements SolrEventListener {

  final static AtomicInteger createCounter = new AtomicInteger(0);

  public static final int getCreateCount() {
    return createCounter.intValue();
  }

  public MockEventListener() {
    createCounter.incrementAndGet();
  }

  @Override
  public void init(NamedList args) {
    /* NOOP */
  }

  @Override
  public void postCommit() {
    /* NOOP */
  }
  
  @Override
  public void postSoftCommit() {
    /* NOOP */
  }

  @Override
  public void newSearcher(SolrIndexSearcher newSearcher, 
                          SolrIndexSearcher currentSearcher) {
    /* NOOP */
  }

}
