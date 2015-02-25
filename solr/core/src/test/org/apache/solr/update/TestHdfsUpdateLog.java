package org.apache.solr.update;

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
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.SolrTestCaseJ4.SuppressObjectReleaseTracker;
import org.apache.solr.cloud.hdfs.HdfsTestUtil;
import org.apache.solr.common.util.IOUtils;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;

@ThreadLeakScope(Scope.NONE) // hdfs mini cluster currently leaks threads
@SuppressObjectReleaseTracker(bugUrl = "https://issues.apache.org/jira/browse/SOLR-7115")
public class TestHdfsUpdateLog extends SolrTestCaseJ4 {
  
  private static MiniDFSCluster dfsCluster;

  private static String hdfsUri;
  
  private static FileSystem fs;
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    dfsCluster = HdfsTestUtil.setupClass(createTempDir().toFile().getAbsolutePath());
    hdfsUri = dfsCluster.getFileSystem().getUri().toString();
    
    try {
      URI uri = new URI(hdfsUri);
      Configuration conf = new Configuration();
      conf.setBoolean("fs.hdfs.impl.disable.cache", true);
      fs = FileSystem.get(uri, conf);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    
    System.setProperty("solr.ulog.dir", hdfsUri + "/solr/shard1");
    
    initCore("solrconfig-tlog.xml","schema15.xml");
  }
  
  @AfterClass
  public static void afterClass() throws Exception {
    System.clearProperty("solr.ulog.dir");
    System.clearProperty("test.build.data");
    System.clearProperty("test.cache.data");
    deleteCore();
    IOUtils.closeQuietly(fs);
    fs = null;
    HdfsTestUtil.teardownClass(dfsCluster);
    
    hdfsDataDir = null;
    dfsCluster = null;
  }

  @Test
  public void testFSThreadSafety() throws Exception {

    final SolrQueryRequest req = req();
    final UpdateHandler uhandler = req.getCore().getUpdateHandler();
    ((DirectUpdateHandler2) uhandler).getCommitTracker().setTimeUpperBound(100);
    ((DirectUpdateHandler2) uhandler).getCommitTracker().setOpenSearcher(false);
    final UpdateLog ulog = uhandler.getUpdateLog();
    
    clearIndex();
    assertU(commit());
    
    // we hammer on init in a background thread to make
    // sure we don't run into any filesystem already closed
    // problems (SOLR-7113)
    
    Thread thread = new Thread() {
      public void run() {
        int cnt = 0;
        while (true) {
          ulog.init(uhandler, req.getCore());
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {

          }
          if (cnt++ > 50) {
            break;
          }
        }
      }
    };
    
    Thread thread2 = new Thread() {
      public void run() {
        int cnt = 0;
        while (true) {
          assertU(adoc("id", Integer.toString(cnt)));
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {

          }
          if (cnt++ > 500) {
            break;
          }
        }
      }
    };
    


    thread.start();
    thread2.start();
    thread.join();
    thread2.join();
    
  }

}

