package org.apache.solr.cloud;

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

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.RequestHandlers.LazyRequestHandlerWrapper;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.ReplicationHandler;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.update.UpdateLog.RecoveryInfo;
import org.apache.solr.util.RefCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoveryStrat {
  private static final String REPLICATION_HANDLER = "/replication";

  private static Logger log = LoggerFactory.getLogger(RecoveryStrat.class);
  
  private volatile RecoveryListener recoveryListener;
  

  interface OnFinish {
    public void run();
  }
  
  // for now, just for tests
  public interface RecoveryListener {
    public void startRecovery();
    public void finishedReplication();
    public void finishedRecovery();
  }
  
  // TODO: we want to be pretty noisy if we don't properly recover?
  // Also, if we cannot talk to the leader, we need to pause and see if there is
  // a new leader or continue trying
  public void recover(final SolrCore core, final String leaderUrl,
      final boolean iamLeader, final OnFinish onFinish) {
    log.info("Start recovery process");
    if (recoveryListener != null) recoveryListener.startRecovery();
    core.getUpdateHandler().getUpdateLog().bufferUpdates();
    Thread thread = new Thread() {
      {
        setDaemon(true);
      }
      
      @Override
      public void run() {
        try {
          doRecovery(core, leaderUrl, iamLeader);
          System.out.println("apply buffered updates");
          Future<RecoveryInfo> future = core.getUpdateHandler().getUpdateLog()
              .applyBufferedUpdates();
          if (future == null) {
            // no replay needed\
            log.info("No replay needed");
          } else {
            // wait for replay
            future.get();
          }
          System.out.println("replay done");
          EmbeddedSolrServer server = new EmbeddedSolrServer(core);
          server.commit();
          
          RefCounted<SolrIndexSearcher> searcher = core.getSearcher(true, true,
              null);
          System.out.println("DOCS AFTER REPLAY:"
              + searcher.get().search(new MatchAllDocsQuery(), 1).totalHits);
          searcher.decref();
          if (recoveryListener != null) recoveryListener.finishedRecovery();
          onFinish.run();
        } catch (SolrServerException e) {
          log.error("", e);
        } catch (IOException e) {
          log.error("", e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.error("", e);
        } catch (ExecutionException e) {
          log.error("", e);
        } 
        log.info("Finished recovery process");
        // nocommit: if we get an exception, recovery failed...
      }
    };
    thread.start();
    
  }
  
  private void doRecovery(SolrCore core, String leaderUrl, boolean iamleader)
      throws SolrServerException, IOException {
    
    // start buffer updates to tran log
    // and do recovery - either replay via realtime get (eventually)
    // or full index replication
    
    if (!iamleader) {
      // if we are the leader, either we are trying to recover faster
      // then our ephemeral timed out or we are the only node
      
      // TODO: first, issue a hard commit?
      // nocommit: require /update?
      
      CommonsHttpSolrServer server = new CommonsHttpSolrServer(leaderUrl);
      server.commit(false, false);
      
      // use rep handler directly, so we can do this sync rather than async
      
      SolrRequestHandler handler = core.getRequestHandler(REPLICATION_HANDLER);
      if (handler instanceof LazyRequestHandlerWrapper) {
        handler = ((LazyRequestHandlerWrapper)handler).getWrappedHandler();
      }
      ReplicationHandler replicationHandler = (ReplicationHandler) handler;
      
      if (replicationHandler == null) {
        // nocommit: we should not just return - we don't want to falsely advertise as active
        log.error("Skipping recovery, no /replication handler found");
        return;
      }
      
      ModifiableSolrParams solrParams = new ModifiableSolrParams();
      solrParams.set(ReplicationHandler.MASTER_URL, leaderUrl + "replication");
      
      replicationHandler.doFetch(solrParams);
      
      RefCounted<SolrIndexSearcher> searcher = core.getSearcher(true, true,
          null);
      System.out.println("DOCS AFTER REPLICATE:"
          + searcher.get().search(new MatchAllDocsQuery(), 1).totalHits);
      searcher.decref();
      if (recoveryListener != null) recoveryListener.finishedReplication();
    }
  }
  
  public RecoveryListener getRecoveryListener() {
    return recoveryListener;
  }

  public void setRecoveryListener(RecoveryListener recoveryListener) {
    this.recoveryListener = recoveryListener;
  }
}
