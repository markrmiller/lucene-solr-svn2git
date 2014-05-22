package org.apache.solr.client.solrj.request;

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

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.apache.solr.SolrJettyTestBase;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

/**
 * Test SolrPing in Solrj
 */
public class SolrPingTest extends SolrJettyTestBase {
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    File testHome = createTempDir();
    FileUtils.copyDirectory(getFile("solrj/solr"), testHome);
    initCore("solrconfig.xml", "schema.xml", testHome.getAbsolutePath(), "collection1");
  }
  
  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    clearIndex();
    assertU(commit());
    assertU(optimize());
    SolrInputDocument doc = new SolrInputDocument();
    doc.setField("id", 1);
    doc.setField("terms_s", "samsung");
    getSolrServer().add(doc);
    getSolrServer().commit(true, true);
  }
  
  @Test
  public void testEnabledSolrPing() throws Exception {
    SolrPing ping = new SolrPing();
    SolrPingResponse rsp = null;
    ping.setActionEnable();
    ping.process(getSolrServer());
    ping.removeAction();
    rsp = ping.process(getSolrServer());
    Assert.assertNotNull(rsp);
  }
  
  @Test(expected = SolrException.class)
  public void testDisabledSolrPing() throws Exception {
    SolrPing ping = new SolrPing();
    SolrPingResponse rsp = null;
    ping.setActionDisable();
    try {
      ping.process(getSolrServer());
    } catch (Exception e) {
      throw new Exception("disable action failed!");
    }
    ping.setActionPing();
    rsp = ping.process(getSolrServer());
    // the above line should fail with a 503 SolrException.
    Assert.assertNotNull(rsp);
  }
}
