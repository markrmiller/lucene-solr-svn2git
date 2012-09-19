package org.apache.solr.core;

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

import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.apache.solr.update.processor.RegexReplaceProcessorFactory;

import org.apache.solr.util.AbstractSolrTestCase;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assume;

public class TestXIncludeConfig extends AbstractSolrTestCase {

  @Override
  public String getSchemaFile() {
    return "schema-xinclude.xml";
  }

  //public String getSolrConfigFile() { return "solrconfig.xml"; }
  @Override
  public String getSolrConfigFile() {
    return "solrconfig-xinclude.xml";
  }

  @Override
  public void setUp() throws Exception {
    javax.xml.parsers.DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try {
      //see whether it even makes sense to run this test
      dbf.setXIncludeAware(true);
      dbf.setNamespaceAware(true);
    } catch (UnsupportedOperationException e) {
      Assume.assumeTrue(false);
    }
    super.setUp();
  }

  public void testXInclude() throws Exception {
    SolrCore core = h.getCore();

    assertNotNull("includedHandler is null", 
                  core.getRequestHandler("includedHandler"));

    UpdateRequestProcessorChain chain 
      = core.getUpdateProcessingChain("special-include");
    assertNotNull("chain is missing included processor", chain);
    assertEquals("chain with inclued processor is wrong size", 
                 1, chain.getFactories().length);
    assertEquals("chain has wrong included processor",
                 RegexReplaceProcessorFactory.class,
                 chain.getFactories()[0].getClass());

    assertNotNull("ft-included is null",
                  core.getSchema().getFieldTypeByName("ft-included"));
    assertNotNull("field-included is null",
                  core.getSchema().getFieldOrNull("field-included"));
  }
}
