package org.apache.solr.util;
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

import java.io.IOException;
import java.net.URLEncoder;

import javax.xml.xpath.XPathExpressionException;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.common.params.ModifiableSolrParams;

/**
 * Facilitates testing Solr's REST API via a provided embedded Jetty
 */
public class RestTestHarness extends BaseTestHarness {
  private RESTfulServerProvider serverProvider;
  private HttpClient httpClient = HttpClientUtil.createClient(new
      ModifiableSolrParams());
  
  public RestTestHarness(RESTfulServerProvider serverProvider) {
    this.serverProvider = serverProvider;
  }
  
  public String getBaseURL() {
    return serverProvider.getBaseURL();
  }
  
  /**
   * Validates an XML "query" response against an array of XPath test strings
   *
   * @param request the Query to process
   * @return null if all good, otherwise the first test that fails.
   * @exception Exception any exception in the response.
   * @exception java.io.IOException if there is a problem writing the XML
   */
  public String validateQuery(String request, String... tests) throws Exception {

    String res = query(request);
    return validateXPath(res, tests);
  }


  /**
   * Validates an XML PUT response against an array of XPath test strings
   *
   * @param request the PUT request to process
   * @param content the content to send with the PUT request
   * @param tests the validating XPath tests
   * @return null if all good, otherwise the first test that fails.
   * @exception Exception any exception in the response.
   * @exception java.io.IOException if there is a problem writing the XML
   */
  public String validatePut(String request, String content, String... tests) throws Exception {

    String res = put(request, content);
    return validateXPath(res, tests);
  }


  /**
   * Processes a "query" using a URL path (with no context path) + optional query params,
   * e.g. "/schema/fields?indent=on"
   *
   * @param request the URL path and optional query params
   * @return The response to the query
   * @exception Exception any exception in the response.
   */
  public String query(String request) throws Exception {
    return getResponse(new HttpGet(getBaseURL() + request));
  }

  /**
   * Processes a PUT request using a URL path (with no context path) + optional query params,
   * e.g. "/schema/fields/newfield", PUTs the given content, and returns the response content.
   * 
   * @param request The URL path and optional query params
   * @param content The content to include with the PUT request
   * @return The response to the PUT request
   */
  public String put(String request, String content) throws IOException {
    HttpPut httpPut = new HttpPut(getBaseURL() + request);
    httpPut.setEntity(new StringEntity(content, ContentType.create(
        "application/json", "utf-8")));
    
    return getResponse(httpPut);
  }

  /**
   * Processes a POST request using a URL path (with no context path) + optional query params,
   * e.g. "/schema/fields/newfield", PUTs the given content, and returns the response content.
   *
   * @param request The URL path and optional query params
   * @param content The content to include with the POST request
   * @return The response to the PUT request
   */
  public String post(String request, String content) throws IOException {
    HttpPost httpPost = new HttpPost(getBaseURL() + request);
    httpPost.setEntity(new StringEntity(content, ContentType.create(
        "application/json", "utf-8")));
    
    return getResponse(httpPost);
  }


  public String checkResponseStatus(String xml, String code) throws Exception {
    try {
      String response = query(xml);
      String valid = validateXPath(response, "//int[@name='status']="+code );
      return (null == valid) ? null : response;
    } catch (XPathExpressionException e) {
      throw new RuntimeException("?!? static xpath has bug?", e);
    }
  }

  
  @Override
  public void reload() throws Exception {
    String xml = checkResponseStatus("/admin/cores?action=RELOAD", "0");
    if (null != xml) {
      throw new RuntimeException("RELOAD failed:\n" + xml);
    }
  }
  
  /**
   * Processes an "update" (add, commit or optimize) and
   * returns the response as a String.
   *
   * @param xml The XML of the update
   * @return The XML response to the update
   */
  @Override
  public String update(String xml) {
    try {
      return query("/update?stream.body=" + URLEncoder.encode(xml, "UTF-8"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private String getResponse(HttpUriRequest request) throws IOException {
    HttpEntity entity = null;
    try {
      entity = httpClient.execute(request).getEntity();
      return EntityUtils.toString(entity, "UTF-8");
    } finally {
      EntityUtils.consumeQuietly(entity);
    }
  }
}
