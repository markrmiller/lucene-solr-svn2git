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

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.carrotsearch.randomizedtesting.rules.SystemPropertiesRestoreRule;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.util.TestUtil;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.apache.solr.update.UpdateShardHandlerConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.internal.matchers.StringContains.containsString;

public class TestSolrXml extends SolrTestCaseJ4 {

  @Rule
  public TestRule solrTestRules = RuleChain.outerRule(new SystemPropertiesRestoreRule());
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  // tmp dir, cleaned up automatically.
  private static File solrHome = null;
  private static SolrResourceLoader loader = null;

  @BeforeClass
  public static void setupLoader() throws Exception {
    solrHome = createTempDir().toFile();
    loader = new SolrResourceLoader(solrHome.toPath());
  }

  @AfterClass
  public static void cleanupLoader() throws Exception {
    solrHome = null;
    loader = null;
  }

  public void testAllInfoPresent() throws IOException {

    File testSrcRoot = new File(SolrTestCaseJ4.TEST_HOME());
    FileUtils.copyFile(new File(testSrcRoot, "solr-50-all.xml"), new File(solrHome, "solr.xml"));

    NodeConfig cfg = SolrXmlConfig.fromSolrHome(loader, solrHome.toPath());
    CloudConfig ccfg = cfg.getCloudConfig();
    UpdateShardHandlerConfig ucfg = cfg.getUpdateShardHandlerConfig();
    
    assertEquals("core admin handler class", "testAdminHandler", cfg.getCoreAdminHandlerClass());
    assertEquals("collection handler class", "testCollectionsHandler", cfg.getCollectionsHandlerClass());
    assertEquals("info handler class", "testInfoHandler", cfg.getInfoHandlerClass());
    assertEquals("config set handler class", "testConfigSetsHandler", cfg.getConfigSetsHandlerClass());
    assertEquals("core load threads", 11, cfg.getCoreLoadThreadCount());
    assertThat("core root dir", cfg.getCoreRootDirectory().toString(), containsString("testCoreRootDirectory"));
    assertEquals("distrib conn timeout", 22, cfg.getDistributedConnectionTimeout());
    assertEquals("distrib conn timeout", 22, cfg.getUpdateShardHandlerConfig().getDistributedConnectionTimeout());
    assertEquals("distrib socket timeout", 33, cfg.getDistributedSocketTimeout());
    assertEquals("distrib socket timeout", 33, cfg.getUpdateShardHandlerConfig().getDistributedSocketTimeout());
    assertEquals("max update conn", 3, cfg.getMaxUpdateConnections());
    assertEquals("max update conn", 3, cfg.getUpdateShardHandlerConfig().getMaxUpdateConnections());
    assertEquals("max update conn/host", 37, cfg.getMaxUpdateConnectionsPerHost());
    assertEquals("max update conn/host", 37, cfg.getUpdateShardHandlerConfig().getMaxUpdateConnectionsPerHost());
    assertEquals("distrib conn timeout", 22, ucfg.getDistributedConnectionTimeout());
    assertEquals("distrib socket timeout", 33, ucfg.getDistributedSocketTimeout());
    assertEquals("max update conn", 3, ucfg.getMaxUpdateConnections());
    assertEquals("max update conn/host", 37, ucfg.getMaxUpdateConnectionsPerHost());
    assertEquals("host", "testHost", ccfg.getHost());
    assertEquals("zk host context", "testHostContext", ccfg.getSolrHostContext());
    assertEquals("solr host port", 44, ccfg.getSolrHostPort());
    assertEquals("leader vote wait", 55, ccfg.getLeaderVoteWait());
    assertEquals("logging class", "testLoggingClass", cfg.getLogWatcherConfig().getLoggingClass());
    assertEquals("log watcher", true, cfg.getLogWatcherConfig().isEnabled());
    assertEquals("log watcher size", 88, cfg.getLogWatcherConfig().getWatcherSize());
    assertEquals("log watcher thresh", "99", cfg.getLogWatcherConfig().getWatcherThreshold());
    assertEquals("manage path", "testManagementPath", cfg.getManagementPath());
    assertEquals("shardLib", "testSharedLib", cfg.getSharedLibDirectory());
    assertEquals("schema cache", true, cfg.hasSchemaCache());
    assertEquals("trans cache size", 66, cfg.getTransientCacheSize());
    assertEquals("zk client timeout", 77, ccfg.getZkClientTimeout());
    assertEquals("zk host", "testZkHost", ccfg.getZkHost());
    assertEquals("zk ACL provider", "DefaultZkACLProvider", ccfg.getZkACLProviderClass());
    assertEquals("zk credentials provider", "DefaultZkCredentialsProvider", ccfg.getZkCredentialsProviderClass());
  }

  // Test  a few property substitutions that happen to be in solr-50-all.xml.
  public void testPropertySub() throws IOException {

    System.setProperty("coreRootDirectory", "myCoreRoot" + File.separator);
    System.setProperty("hostPort", "8888");
    System.setProperty("shareSchema", "false");
    System.setProperty("socketTimeout", "220");
    System.setProperty("connTimeout", "200");

    File testSrcRoot = new File(SolrTestCaseJ4.TEST_HOME());
    FileUtils.copyFile(new File(testSrcRoot, "solr-50-all.xml"), new File(solrHome, "solr.xml"));

    NodeConfig cfg = SolrXmlConfig.fromSolrHome(loader, solrHome.toPath());
    assertThat(cfg.getCoreRootDirectory().toString(), containsString("myCoreRoot"));
    assertEquals("solr host port", 8888, cfg.getCloudConfig().getSolrHostPort());
    assertEquals("schema cache", false, cfg.hasSchemaCache());
  }

  public void testExplicitNullGivesDefaults() throws IOException {
    String solrXml = "<solr>" +
        "<solrcloud>" +
        "<str name=\"host\">host</str>" +
        "<int name=\"hostPort\">8983</int>" +
        "<str name=\"hostContext\">solr</str>" +
        "<null name=\"leaderVoteWait\"/>" +
        "</solrcloud></solr>";

    NodeConfig cfg = SolrXmlConfig.fromString(loader, solrXml);
    assertEquals("leaderVoteWait", 180000, cfg.getCloudConfig().getLeaderVoteWait());
  }

  public void testIntAsLongBad() throws IOException {
    String bad = ""+TestUtil.nextLong(random(), Integer.MAX_VALUE, Long.MAX_VALUE);
    String solrXml = "<solr><long name=\"transientCacheSize\">"+bad+"</long></solr>";

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("transientCacheSize");
    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testIntAsLongOk() throws IOException {
    int ok = random().nextInt();
    String solrXml = "<solr><long name=\"transientCacheSize\">"+ok+"</long></solr>";
    NodeConfig cfg = SolrXmlConfig.fromString(loader, solrXml);
    assertEquals(ok, cfg.getTransientCacheSize());
  }

  public void testMultiCloudSectionError() throws IOException {
    String solrXml = "<solr>"
      + "<solrcloud><bool name=\"genericCoreNodeNames\">true</bool></solrcloud>"
      + "<solrcloud><bool name=\"genericCoreNodeNames\">false</bool></solrcloud>"
      + "</solr>";
    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Multiple instances of solrcloud section found in solr.xml");
    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testMultiLoggingSectionError() throws IOException {
    String solrXml = "<solr>"
      + "<logging><str name=\"class\">foo</str></logging>"
      + "<logging><str name=\"class\">foo</str></logging>"
      + "</solr>";
    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Multiple instances of logging section found in solr.xml");
    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testMultiLoggingWatcherSectionError() throws IOException {
    String solrXml = "<solr><logging>"
      + "<watcher><int name=\"threshold\">42</int></watcher>"
      + "<watcher><int name=\"threshold\">42</int></watcher>"
      + "<watcher><int name=\"threshold\">42</int></watcher>"
      + "</logging></solr>";

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Multiple instances of logging/watcher section found in solr.xml");
    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }
 
  public void testValidStringValueWhenBoolTypeIsExpected() throws IOException {
    boolean schemaCache = random().nextBoolean();
    String solrXml = String.format(Locale.ROOT, "<solr><str name=\"shareSchema\">%s</str></solr>", schemaCache);

    NodeConfig nodeConfig = SolrXmlConfig.fromString(loader, solrXml);
    assertEquals("gen core node names", schemaCache, nodeConfig.hasSchemaCache());
  }

  public void testValidStringValueWhenIntTypeIsExpected() throws IOException {
    int maxUpdateConnections = random().nextInt();
    String solrXml = String.format(Locale.ROOT, "<solr><updateshardhandler><str name=\"maxUpdateConnections\">%d</str></updateshardhandler></solr>", maxUpdateConnections);
    NodeConfig nodeConfig = SolrXmlConfig.fromString(loader, solrXml);
    assertEquals("max update conn", maxUpdateConnections, nodeConfig.getUpdateShardHandlerConfig().getMaxUpdateConnections());
  }

  public void testFailAtConfigParseTimeWhenIntTypeIsExpectedAndLongTypeIsGiven() throws IOException {
    long val = TestUtil.nextLong(random(), Integer.MAX_VALUE, Long.MAX_VALUE);
    String solrXml = String.format(Locale.ROOT, "<solr><solrcloud><long name=\"maxUpdateConnections\">%d</long></solrcloud></solr>", val);

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Error parsing 'maxUpdateConnections'");
    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenBoolTypeIsExpectedAndValueIsInvalidString() throws IOException {
    String solrXml = "<solr><solrcloud><bool name=\"genericCoreNodeNames\">NOT_A_BOOLEAN</bool></solrcloud></solr>";

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("invalid boolean value: NOT_A_BOOLEAN");
    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenIntTypeIsExpectedAndBoolTypeIsGiven() throws IOException {
    // given:
    boolean randomBoolean = random().nextBoolean();
    String solrXml = String.format(Locale.ROOT, "<solr><logging><int name=\"unknown-option\">%s</int></logging></solr>", randomBoolean);

    expectedException.expect(SolrException.class);
    expectedException.expectMessage(String.format(Locale.ROOT, "Value of 'unknown-option' can not be parsed as 'int': \"%s\"", randomBoolean));

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenUnrecognizedSolrCloudOptionWasFound() throws IOException {
    String solrXml = "<solr><solrcloud><str name=\"host\">host</str><int name=\"hostPort\">8983</int><str name=\"hostContext\"></str><bool name=\"unknown-option\">true</bool></solrcloud></solr>";

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Unknown configuration parameter in <solrcloud> section of solr.xml: unknown-option");

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenUnrecognizedSolrOptionWasFound() throws IOException {
    String solrXml = "<solr><bool name=\"unknown-bool-option\">true</bool><str name=\"unknown-str-option\">true</str></solr>";

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Unknown configuration value in solr.xml: unknown-bool-option");

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenUnrecognizedLoggingOptionWasFound() throws IOException {
    String solrXml = String.format(Locale.ROOT, "<solr><logging><bool name=\"unknown-option\">%s</bool></logging></solr>", random().nextBoolean());

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Unknown value in logwatcher config: unknown-option");

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenLoggingConfigParamsAreDuplicated() throws IOException {
    String v1 = ""+random().nextInt();
    String v2 = ""+random().nextInt();
    String solrXml = String.format(Locale.ROOT,
                                   "<solr><logging>" +
                                   "<str name=\"class\">%s</str>" +
                                   "<str name=\"class\">%s</str>" +
                                   "</logging></solr>",
                                   v1, v2);

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("<logging> section of solr.xml contains duplicated 'class'");

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testFailAtConfigParseTimeWhenSolrCloudConfigParamsAreDuplicated() throws IOException {
    String v1 = ""+random().nextInt();
    String v2 = ""+random().nextInt();
    String v3 = ""+random().nextInt();
    String solrXml = String.format(Locale.ROOT,
                                   "<solr><solrcloud>" +
                                   "<int name=\"zkClientTimeout\">%s</int>" +
                                   "<int name=\"zkClientTimeout\">%s</int>" +
                                   "<str name=\"zkHost\">foo</str>" + // other ok val in middle
                                   "<int name=\"zkClientTimeout\">%s</int>" +
                                   "</solrcloud></solr>",
                                   v1, v2, v3);
    
    expectedException.expect(SolrException.class);
    expectedException.expectMessage("<solrcloud> section of solr.xml contains duplicated 'zkClientTimeout'");

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  @Ignore
  public void testFailAtConfigParseTimeWhenSolrConfigParamsAreDuplicated() throws IOException {
    String v1 = ""+random().nextInt();
    String v2 = ""+random().nextInt();
    String solrXml = String.format(Locale.ROOT, 
                                   "<solr>" +
                                   "<int name=\"coreLoadThreads\">%s</int>" +
                                   "<str name=\"coreLoadThreads\">%s</str>" +
                                   "</solr>",
                                   v1, v2);

    expectedException.expect(SolrException.class);
    expectedException.expectMessage("Main section of solr.xml contains duplicated 'coreLoadThreads'");

    SolrXmlConfig.fromString(loader, solrXml); // return not used, only for validation
  }

  public void testCloudConfigRequiresHost() throws Exception {
    expectedException.expect(SolrException.class);
    expectedException.expectMessage("solrcloud section missing required entry 'host'");

    SolrXmlConfig.fromString(loader, "<solr><solrcloud></solrcloud></solr>");
  }

  public void testCloudConfigRequiresHostPort() throws Exception {
    expectedException.expect(SolrException.class);
    expectedException.expectMessage("solrcloud section missing required entry 'hostPort'");

    SolrXmlConfig.fromString(loader, "<solr><solrcloud><str name=\"host\">host</str></solrcloud></solr>");
  }

  public void testCloudConfigRequiresHostContext() throws Exception {
    expectedException.expect(SolrException.class);
    expectedException.expectMessage("solrcloud section missing required entry 'hostContext'");

    SolrXmlConfig.fromString(loader, "<solr><solrcloud><str name=\"host\">host</str><int name=\"hostPort\">8983</int></solrcloud></solr>");
  }
}
