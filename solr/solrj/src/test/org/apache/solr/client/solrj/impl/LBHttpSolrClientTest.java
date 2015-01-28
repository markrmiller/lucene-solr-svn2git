/**
 * 
 */
package org.apache.solr.client.solrj.impl;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

/**
 * Test the LBHttpSolrClient.
 */
public class LBHttpSolrClientTest {
  
  /**
   * Test method for {@link LBHttpSolrClient#LBHttpSolrClient(org.apache.http.client.HttpClient, org.apache.solr.client.solrj.ResponseParser, java.lang.String[])}.
   * 
   * Validate that the parser passed in is used in the <code>HttpSolrClient</code> instances created.
   */
  @Test
  public void testLBHttpSolrClientHttpClientResponseParserStringArray() throws IOException {

    try (CloseableHttpClient httpClient = HttpClientUtil.createClient(new ModifiableSolrParams());
         LBHttpSolrClient testClient = new LBHttpSolrClient(httpClient, (ResponseParser) null);
         HttpSolrClient httpSolrClient = testClient.makeSolrClient("http://127.0.0.1:8080")) {
      assertNull("Generated server should have null parser.", httpSolrClient.getParser());
    }

    ResponseParser parser = new BinaryResponseParser();
    try (CloseableHttpClient httpClient = HttpClientUtil.createClient(new ModifiableSolrParams());
         LBHttpSolrClient testClient = new LBHttpSolrClient(httpClient, parser);
         HttpSolrClient httpSolrClient = testClient.makeSolrClient("http://127.0.0.1:8080")) {
      assertEquals("Invalid parser passed to generated server.", parser, httpSolrClient.getParser());
    }
  }
  
}
