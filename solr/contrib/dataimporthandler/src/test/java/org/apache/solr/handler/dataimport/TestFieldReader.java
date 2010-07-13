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
package org.apache.solr.handler.dataimport;

import junit.framework.Assert;
import static org.apache.solr.handler.dataimport.AbstractDataImportHandlerTestCase.createMap;

import org.apache.solr.SolrTestCaseJ4;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test for FieldReaderDataSource
 *
 * @version $Id$
 * @see org.apache.solr.handler.dataimport.FieldReaderDataSource
 * @since 1.4
 */
public class TestFieldReader extends SolrTestCaseJ4 {

  @Test
  public void simple() {
    DataImporter di = new DataImporter();
    di.loadAndInit(config);
    TestDocBuilder.SolrWriterImpl sw = new TestDocBuilder.SolrWriterImpl();
    DataImporter.RequestParams rp = new DataImporter.RequestParams(createMap("command", "full-import"));
    List<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
    l.add(createMap("xml", xml));
    MockDataSource.setIterator("select * from a", l.iterator());
    di.runCmd(rp, sw);
    Assert.assertEquals(sw.docs.get(0).getFieldValue("y"), "Hello");
    MockDataSource.clearCache();
  }

  String config = "<dataConfig>\n" +
          "  <dataSource type=\"FieldReaderDataSource\" name=\"f\"/>\n" +
          "  <dataSource type=\"MockDataSource\"/>\n" +
          "  <document>\n" +
          "    <entity name=\"a\" query=\"select * from a\" >\n" +
          "      <entity name=\"b\" dataSource=\"f\" processor=\"XPathEntityProcessor\" forEach=\"/x\" dataField=\"a.xml\">\n" +
          "        <field column=\"y\" xpath=\"/x/y\"/>\n" +
          "      </entity>\n" +
          "    </entity>\n" +
          "  </document>\n" +
          "</dataConfig>";

  String xml = "<x>\n" +
          " <y>Hello</y>\n" +
          "</x>";
}
