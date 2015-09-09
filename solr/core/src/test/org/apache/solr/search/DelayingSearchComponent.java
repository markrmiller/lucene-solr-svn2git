package org.apache.solr.search;

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

import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;

import java.io.IOException;

/**
 * Search component used to add delay to each request.
 */
public class DelayingSearchComponent extends SearchComponent{

  @Override
  public void prepare(ResponseBuilder rb) throws IOException {
    rb.rsp.addHttpHeader("Warning", "This is a test warning");
  }

  @Override
  public void process(ResponseBuilder rb) throws IOException {
    int sleep = rb.req.getParams().getInt("sleep",0);
    try {
      if (sleep > 0) {
        Thread.sleep(sleep);
      }
    } catch (InterruptedException e) {
      // Do nothing?
    }
  }

  @Override
  public String getDescription() {
    return "SearchComponent used to add delay to each request";
  }

}
