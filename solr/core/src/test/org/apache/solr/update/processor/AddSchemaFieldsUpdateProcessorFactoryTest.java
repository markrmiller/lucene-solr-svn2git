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

package org.apache.solr.update.processor;

import org.apache.commons.io.FileUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.TestManagedSchema;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.Date;

/**
 * Tests for the field mutating update processors
 * that parse Dates, Longs, Doubles, and Booleans.
 */
public class AddSchemaFieldsUpdateProcessorFactoryTest extends UpdateProcessorTestBase {
  private static final String SOLRCONFIG_XML = "solrconfig-add-schema-fields-update-processor-chains.xml";
  private static final String SCHEMA_XML     = "schema-add-schema-fields-update-processor.xml";

  private static File tmpSolrHome;
  private static File tmpConfDir;

  private static final String collection = "collection1";
  private static final String confDir = collection + "/conf";

  @Before
  private void initManagedSchemaCore() throws Exception {
    final String tmpSolrHomePath
        = dataDir + File.separator + TestManagedSchema.class.getSimpleName() + System.currentTimeMillis();
    tmpSolrHome = new File(tmpSolrHomePath).getAbsoluteFile();
    tmpConfDir = new File(tmpSolrHome, confDir);
    File testHomeConfDir = new File(TEST_HOME(), confDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, SOLRCONFIG_XML), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, SCHEMA_XML), tmpConfDir);

    // initCore will trigger an upgrade to managed schema, since the solrconfig*.xml has
    // <schemaFactory class="ManagedIndexSchemaFactory" ... />
    initCore(SOLRCONFIG_XML, SCHEMA_XML, tmpSolrHome.getPath());
  }

  @After
  private void deleteCoreAndTempSolrHomeDirectory() throws Exception {
    deleteCore();
    FileUtils.deleteDirectory(tmpSolrHome);
  }

  public void testSingleField() throws Exception {
    IndexSchema schema = h.getCore().getLatestSchema();
    final String fieldName = "newfield1";
    assertNull(schema.getFieldOrNull(fieldName));
    String dateString = "2010-11-12T13:14:15.168Z";
    DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTime();
    Date date = dateTimeFormatter.parseDateTime(dateString).toDate();
    SolrInputDocument d = processAdd("add-fields-no-run-processor", doc(f("id", "1"), f(fieldName, date)));
    assertNotNull(d);
    schema = h.getCore().getLatestSchema();
    assertNotNull(schema.getFieldOrNull(fieldName));
    assertEquals("tdate", schema.getFieldType(fieldName).getTypeName());
  }

  public void testSingleFieldRoundTrip() throws Exception {
    IndexSchema schema = h.getCore().getLatestSchema();
    final String fieldName = "newfield2";
    assertNull(schema.getFieldOrNull(fieldName));
    Float floatValue = -13258.992f;
    SolrInputDocument d = processAdd("add-fields", doc(f("id", "2"), f(fieldName, floatValue)));
    assertNotNull(d);
    schema = h.getCore().getLatestSchema();
    assertNotNull(schema.getFieldOrNull(fieldName));
    assertEquals("tfloat", schema.getFieldType(fieldName).getTypeName());
    assertU(commit());
    assertQ(req("id:2"), "//arr[@name='" + fieldName + "']/float[.='" + floatValue.toString() + "']");
  }

  public void testSingleFieldMixedFieldTypesRoundTrip() throws Exception {
    IndexSchema schema = h.getCore().getLatestSchema();
    final String fieldName = "newfield3";
    assertNull(schema.getFieldOrNull(fieldName));
    Float fieldValue1 = -13258.0f;
    Double fieldValue2 = 8.4828800808E10; 
    SolrInputDocument d = processAdd
        ("add-fields", doc(f("id", "3"), f(fieldName, fieldValue1, fieldValue2)));
    assertNotNull(d);
    schema = h.getCore().getLatestSchema();
    assertNotNull(schema.getFieldOrNull(fieldName));
    assertEquals("tdouble", schema.getFieldType(fieldName).getTypeName());
    assertU(commit());
    assertQ(req("id:3")
        ,"//arr[@name='" + fieldName + "']/double[.='" + fieldValue1.toString() + "']"
        ,"//arr[@name='" + fieldName + "']/double[.='" + fieldValue2.toString() + "']");
  }

  public void testSingleFieldDefaultFieldTypeRoundTrip() throws Exception {
    IndexSchema schema = h.getCore().getLatestSchema();
    final String fieldName = "newfield4";
    assertNull(schema.getFieldOrNull(fieldName));
    Float fieldValue1 = -13258.0f;
    Double fieldValue2 = 8.4828800808E10;
    String fieldValue3 = "blah blah";
    SolrInputDocument d = processAdd
        ("add-fields", doc(f("id", "4"), f(fieldName, fieldValue1, fieldValue2, fieldValue3)));
    assertNotNull(d);
    schema = h.getCore().getLatestSchema();
    assertNotNull(schema.getFieldOrNull(fieldName));
    assertEquals("text", schema.getFieldType(fieldName).getTypeName());
    assertU(commit());
    assertQ(req("id:4")
        ,"//arr[@name='" + fieldName + "']/str[.='" + fieldValue1.toString() + "']"
        ,"//arr[@name='" + fieldName + "']/str[.='" + fieldValue2.toString() + "']"
        ,"//arr[@name='" + fieldName + "']/str[.='" + fieldValue3.toString() + "']"
    );
  }

  public void testMultipleFieldsRoundTrip() throws Exception {
    IndexSchema schema = h.getCore().getLatestSchema();
    final String fieldName1 = "newfield5";
    final String fieldName2 = "newfield6";
    assertNull(schema.getFieldOrNull(fieldName1));
    assertNull(schema.getFieldOrNull(fieldName2));
    Float field1Value1 = -13258.0f;
    Double field1Value2 = 8.4828800808E10;
    Long field1Value3 = 999L;
    Integer field2Value1 = 55123;
    Long field2Value2 = 1234567890123456789L;
    SolrInputDocument d = processAdd
        ("add-fields", doc(f("id", "5"), f(fieldName1, field1Value1, field1Value2, field1Value3),
                                         f(fieldName2, field2Value1, field2Value2)));
    assertNotNull(d);
    schema = h.getCore().getLatestSchema();
    assertNotNull(schema.getFieldOrNull(fieldName1));
    assertNotNull(schema.getFieldOrNull(fieldName2));
    assertEquals("tdouble", schema.getFieldType(fieldName1).getTypeName());
    assertEquals("tlong", schema.getFieldType(fieldName2).getTypeName());
    assertU(commit());
    assertQ(req("id:5")
        ,"//arr[@name='" + fieldName1 + "']/double[.='" + field1Value1.toString() + "']"
        ,"//arr[@name='" + fieldName1 + "']/double[.='" + field1Value2.toString() + "']"
        ,"//arr[@name='" + fieldName1 + "']/double[.='" + field1Value3.doubleValue() + "']"
        ,"//arr[@name='" + fieldName2 + "']/long[.='" + field2Value1.toString() + "']"
        ,"//arr[@name='" + fieldName2 + "']/long[.='" + field2Value2.toString() + "']");
  }

  public void testParseAndAddMultipleFieldsRoundTrip() throws Exception {
    IndexSchema schema = h.getCore().getLatestSchema();
    final String fieldName1 = "newfield7";
    final String fieldName2 = "newfield8";
    final String fieldName3 = "newfield9";
    final String fieldName4 = "newfield10";
    assertNull(schema.getFieldOrNull(fieldName1));
    assertNull(schema.getFieldOrNull(fieldName2));
    assertNull(schema.getFieldOrNull(fieldName3));
    assertNull(schema.getFieldOrNull(fieldName4));
    String field1String1 = "-13,258.0"; 
    Float field1Value1 = -13258.0f;
    String field1String2 = "84,828,800,808.0"; 
    Double field1Value2 = 8.4828800808E10;
    String field1String3 = "999";
    Long field1Value3 = 999L;
    String field2String1 = "55,123";
    Integer field2Value1 = 55123;
    String field2String2 = "1,234,567,890,123,456,789";
    Long field2Value2 = 1234567890123456789L;
    String field3String1 = "blah-blah";
    String field3Value1 = field3String1;
    String field3String2 = "-5.28E-3";
    Double field3Value2 = -5.28E-3;
    String field4String1 = "1999-04-17 17:42";
    DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").withZoneUTC();
    DateTime dateTime =  dateTimeFormatter.parseDateTime(field4String1);
    Date field4Value1 = dateTime.toDate();
    DateTimeFormatter dateTimeFormatter2 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").withZoneUTC();
    String field4Value1String = dateTimeFormatter2.print(dateTime) + "Z";
    
    SolrInputDocument d = processAdd
        ("parse-and-add-fields", doc(f("id", "6"), f(fieldName1, field1String1, field1String2, field1String3),
                                                   f(fieldName2, field2String1, field2String2),
                                                   f(fieldName3, field3String1, field3String2),
                                                   f(fieldName4, field4String1)));
    assertNotNull(d);
    schema = h.getCore().getLatestSchema();
    assertNotNull(schema.getFieldOrNull(fieldName1));
    assertNotNull(schema.getFieldOrNull(fieldName2));
    assertNotNull(schema.getFieldOrNull(fieldName3));
    assertNotNull(schema.getFieldOrNull(fieldName4));
    assertEquals("tdouble", schema.getFieldType(fieldName1).getTypeName());
    assertEquals("tlong", schema.getFieldType(fieldName2).getTypeName());
    assertEquals("text", schema.getFieldType(fieldName3).getTypeName());
    assertEquals("tdate", schema.getFieldType(fieldName4).getTypeName());
    assertU(commit());
    assertQ(req("id:6")
        ,"//arr[@name='" + fieldName1 + "']/double[.='" + field1Value1.toString() + "']"
        ,"//arr[@name='" + fieldName1 + "']/double[.='" + field1Value2.toString() + "']"
        ,"//arr[@name='" + fieldName1 + "']/double[.='" + field1Value3.doubleValue() + "']"
        ,"//arr[@name='" + fieldName2 + "']/long[.='" + field2Value1.toString() + "']"
        ,"//arr[@name='" + fieldName2 + "']/long[.='" + field2Value2.toString() + "']"
        ,"//arr[@name='" + fieldName3 + "']/str[.='" + field3String1 + "']"
        ,"//arr[@name='" + fieldName3 + "']/str[.='" + field3String2 + "']"
        ,"//arr[@name='" + fieldName4 + "']/date[.='" + field4Value1String + "']");
  }
}
