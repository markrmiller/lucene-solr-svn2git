package org.apache.solr.schema;
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

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.solr.core.AbstractBadConfigTestBase;
import org.junit.After;
import org.junit.Before;

public class SpatialRPTFieldTypeTest extends AbstractBadConfigTestBase {
  
  private static File tmpSolrHome;
  private static File tmpConfDir;
  
  private static final String collection = "collection1";
  private static final String confDir = collection + "/conf";
  
  @Before
  private void initManagedSchemaCore() throws Exception {
    tmpSolrHome = createTempDir().toFile();
    tmpConfDir = new File(tmpSolrHome, confDir);
    File testHomeConfDir = new File(TEST_HOME(), confDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "solrconfig-managed-schema.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "solrconfig-basic.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "solrconfig.snippet.randomindexconfig.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "schema-one-field-no-dynamic-field.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "schema-one-field-no-dynamic-field-unique-key.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "schema-minimal.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "schema_codec.xml"), tmpConfDir);
    FileUtils.copyFileToDirectory(new File(testHomeConfDir, "schema-bm25.xml"), tmpConfDir);
    
    // initCore will trigger an upgrade to managed schema, since the solrconfig has
    // <schemaFactory class="ManagedIndexSchemaFactory" ... />
    System.setProperty("managed.schema.mutable", "false");
    System.setProperty("enable.update.log", "false");
    initCore("solrconfig-managed-schema.xml", "schema-minimal.xml", tmpSolrHome.getPath());
  }
  
  @After
  private void afterClass() throws Exception {
    deleteCore();
    System.clearProperty("managed.schema.mutable");
    System.clearProperty("enable.update.log");
  }
  
  final String INDEXED_COORDINATES = "25,82";
  final String QUERY_COORDINATES = "24,81";
  final String DISTANCE_DEGREES = "1.3520328";
  final String DISTANCE_KILOMETERS = "150.33939";
  final String DISTANCE_MILES = "93.416565";
  
  public void testUnitsDegrees() throws Exception { // test back compat behaviour
    setupRPTField("degrees", null, "true");
    
    assertU(adoc("str", "X", "geo", INDEXED_COORDINATES));
    assertU(commit());
    String q;
    
    q = "geo:{!geofilt score=distance filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_DEGREES+"']");
    
    q = "geo:{!geofilt score=degrees filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_DEGREES+"']");
    
    q = "geo:{!geofilt score=kilometers filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_KILOMETERS+"']");
    
    q = "geo:{!geofilt score=miles filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_MILES+"']");
  }
  
  public void testUnitsNonDegrees() throws Exception {
    try {
      setupRPTField("kilometers", null, "true");
      fail("Expected exception for deprecated units parameter.");
    } catch (Exception ex) {
      if(!ex.getMessage().startsWith("units parameter is deprecated"))
        throw ex;
    }
  }
  
  public void testDistanceUnitsDegrees() throws Exception {
    setupRPTField(null, "degrees", "true");
    
    assertU(adoc("str", "X", "geo", INDEXED_COORDINATES));
    assertU(commit());
    String q;
    
    q = "geo:{!geofilt score=distance filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_DEGREES+"']");
    
    q = "geo:{!geofilt score=degrees filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_DEGREES+"']");
    
    q = "geo:{!geofilt score=kilometers filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_KILOMETERS+"']");
    
    q = "geo:{!geofilt score=miles filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_MILES+"']");
  }
  
  public void testDistanceUnitsKilometers() throws Exception {
    setupRPTField(null, "kilometers", "true");
    
    assertU(adoc("str", "X", "geo", INDEXED_COORDINATES));
    assertU(commit());
    String q;
    
    q = "geo:{!geofilt score=distance filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_KILOMETERS+"']");
    
    q = "geo:{!geofilt score=degrees filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_DEGREES+"']");
    
    q = "geo:{!geofilt score=kilometers filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_KILOMETERS+"']");
    
    q = "geo:{!geofilt score=miles filter=false sfield=geo pt="+QUERY_COORDINATES+" d=1000}";
    assertQ(req("q", q, "fl", "*,score"), "//result/doc/float[@name='score'][.='"+DISTANCE_MILES+"']");
  }
  
  public void testBothUnitsAndDistanceUnits() throws Exception { // distanceUnits should take precedence
    try {
      setupRPTField("degrees", "kilometers", "true");  
      fail("Expected exception for deprecated units parameter.");
    } catch (Exception ex) {
      if(!ex.getMessage().startsWith("units parameter is deprecated"))
        throw ex;
    }
  }
  
  public void testJunkValuesForDistanceUnits() throws Exception {
    try {
      setupRPTField(null, "rose", "true");
      fail("Expected exception for bad value of distanceUnits.");
    } catch (Exception ex) {
      if(!ex.getMessage().startsWith("Must specify distanceUnits as one of"))
        throw ex;
    }
  }

  public void testMaxDistErrConversion() throws Exception {
    deleteCore();
    File managedSchemaFile = new File(tmpConfDir, "managed-schema");
    Files.delete(managedSchemaFile.toPath()); // Delete managed-schema so it won't block parsing a new schema
    System.setProperty("managed.schema.mutable", "true");
    initCore("solrconfig-managed-schema.xml", "schema-one-field-no-dynamic-field.xml", tmpSolrHome.getPath());
    
    String fieldName = "new_text_field";
    assertNull("Field '" + fieldName + "' is present in the schema",
        h.getCore().getLatestSchema().getFieldOrNull(fieldName));
    
    IndexSchema oldSchema = h.getCore().getLatestSchema();
    
    SpatialRecursivePrefixTreeFieldType rptFieldType = new SpatialRecursivePrefixTreeFieldType();
    Map<String, String> rptMap = new HashMap<String,String>();

    rptFieldType.setTypeName("location_rpt");
    rptMap.put("geo", "true");

    // test km
    rptMap.put("distanceUnits", "kilometers");
    rptMap.put("maxDistErr", "0.001"); // 1 meter
    rptFieldType.init(oldSchema, rptMap);
    assertEquals(11, rptFieldType.grid.getMaxLevels());

    // test miles
    rptMap.put("distanceUnits", "miles");
    rptMap.put("maxDistErr", "0.001");
    rptFieldType.init(oldSchema, rptMap);
    assertEquals(10, rptFieldType.grid.getMaxLevels());

    // test degrees
    rptMap.put("distanceUnits", "degrees");
    rptMap.put("maxDistErr", "0.001");
    rptFieldType.init(oldSchema, rptMap);
    assertEquals(8, rptFieldType.grid.getMaxLevels());
  }

  public void testGeoDistanceFunctionWithBackCompat() throws Exception {
    setupRPTField("degrees", null, "true");

    assertU(adoc("str", "X", "geo", "1,2"));
    assertU(commit());

    // geodist() should return in km
    assertJQ(req("defType","func",
        "q","geodist(3,4)",
        "sfield","geo",
        "fl","score")
        , 1e-5
        ,"/response/docs/[0]/score==314.4033"
    );
  }

  public void testGeoDistanceFunctionWithKilometers() throws Exception {
    setupRPTField(null, "kilometers", "true");

    assertU(adoc("str", "X", "geo", "1,2"));
    assertU(commit());

    assertJQ(req("defType","func",
        "q","geodist(3,4)",
        "sfield","geo",
        "fl","score")
        , 1e-5
        ,"/response/docs/[0]/score==314.4033"
    );
  }

  public void testGeoDistanceFunctionWithMiles() throws Exception {
    setupRPTField(null, "miles", "true");

    assertU(adoc("str", "X", "geo", "1,2"));
    assertU(commit());

    assertJQ(req("defType","func",
        "q","geodist(3,4)",
        "sfield","geo",
        "fl","score")
        , 1e-5
        ,"/response/docs/[0]/score==195.36115"
    );
  }

  private void setupRPTField(String units, String distanceUnits, String geo) throws Exception {
    deleteCore();
    File managedSchemaFile = new File(tmpConfDir, "managed-schema");
    Files.delete(managedSchemaFile.toPath()); // Delete managed-schema so it won't block parsing a new schema
    System.setProperty("managed.schema.mutable", "true");
    initCore("solrconfig-managed-schema.xml", "schema-one-field-no-dynamic-field.xml", tmpSolrHome.getPath());

    String fieldName = "new_text_field";
    assertNull("Field '" + fieldName + "' is present in the schema",
        h.getCore().getLatestSchema().getFieldOrNull(fieldName));
    
    IndexSchema oldSchema = h.getCore().getLatestSchema();

    SpatialRecursivePrefixTreeFieldType rptFieldType = new SpatialRecursivePrefixTreeFieldType();
    Map<String, String> rptMap = new HashMap<String,String>();
    if(units!=null)
      rptMap.put("units", units);
    if(distanceUnits!=null)
      rptMap.put("distanceUnits", distanceUnits);
    if(geo!=null)
      rptMap.put("geo", geo);
    rptFieldType.init(oldSchema, rptMap);
    rptFieldType.setTypeName("location_rpt");
    SchemaField newField = new SchemaField("geo", rptFieldType, SchemaField.STORED | SchemaField.INDEXED, null);
    IndexSchema newSchema = oldSchema.addField(newField);

    h.getCore().setLatestSchema(newSchema);

    assertU(delQ("*:*"));
  }
}
