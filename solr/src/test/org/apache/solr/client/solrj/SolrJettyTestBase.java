package org.apache.solr.client.solrj;

import java.io.File;
import java.io.IOException;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.junit.AfterClass;

abstract public class SolrJettyTestBase extends SolrTestCaseJ4 
{
  // Try not introduce a dependency on the example schema or config unless you need to.
  // using configs in the test directory allows more flexibility to change "example"
  // without breaking configs.

  private static final String SOURCE_HOME = determineSourceHome();
  public static String EXAMPLE_HOME = new File(SOURCE_HOME, "example/solr").getAbsolutePath();
  public static String EXAMPLE_MULTICORE_HOME = new File(SOURCE_HOME, "example/multicore").getAbsolutePath();
  public static String EXAMPLE_SCHEMA=EXAMPLE_HOME+"/conf/schema.xml";
  public static String EXAMPLE_CONFIG=EXAMPLE_HOME+"/conf/solrconfig.xml";

  public String getSolrHome() { return EXAMPLE_HOME; }

  public static JettySolrRunner jetty;
  public static int port;
  public static SolrServer server;
  public static String context;

  static String determineSourceHome() {
    // ugly, ugly hack to determine the example home without depending on the CWD
    try {
      File file = new File("../../../example/solr");
      if (file.exists())
        return new File("../../../").getAbsolutePath();
      // let the hacks begin
      File base = getFile("solr/conf/");
      while (!new File(base, "solr/CHANGES.txt").exists()) {
        base = base.getParentFile();
      }
      return new File(base, "solr/").getAbsolutePath();
    } catch (IOException e) {
      throw new RuntimeException("Cannot determine example home!");
    }
  }

  public static JettySolrRunner createJetty(String solrHome, String configFile, String context) throws Exception {
    // creates the data dir
    initCore(null, null);

    ignoreException("maxWarmingSearchers");

    // this sets the property for jetty starting SolrDispatchFilter
    System.setProperty( "solr.solr.home", solrHome);
    System.setProperty( "solr.data.dir", dataDir.getCanonicalPath() );

    context = context==null ? "/solr" : context;
    SolrJettyTestBase.context = context;
    jetty = new JettySolrRunner( context, 0, configFile );

    jetty.start();
    port = jetty.getLocalPort();
    log.info("Jetty Assigned Port#" + port);
    return jetty;
  }


  @AfterClass
  public static void afterSolrJettyTestBase() throws Exception {
    if (jetty != null) {
      jetty.stop();
      jetty = null;
    }
    server = null;
  }


  public SolrServer getSolrServer() {
    {
      if (server == null) {
        server = createNewSolrServer();
      }
      return server;
    }
  }

  /**
   * Create a new solr server.
   * If createJetty was called, an http implementation will be created,
   * otherwise an embedded implementation will be created.
   * Subclasses should override for other options.
   */
  public SolrServer createNewSolrServer() {
    if (jetty != null) {
      try {
        // setup the server...
        String url = "http://localhost:"+port+context;
        CommonsHttpSolrServer s = new CommonsHttpSolrServer( url );
        s.setConnectionTimeout(100); // 1/10th sec
        s.setDefaultMaxConnectionsPerHost(100);
        s.setMaxTotalConnections(100);
        return s;
      }
      catch( Exception ex ) {
        throw new RuntimeException( ex );
      }
    } else {
      return new EmbeddedSolrServer( h.getCoreContainer(), "" );
    }
  }
}
