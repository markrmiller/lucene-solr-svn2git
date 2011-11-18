package org.apache.solr.update;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.UpdateRequestExt;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.processor.DistributedUpdateProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessor;

public class SolrCmdDistributor {
  // TODO: shut this thing down
  static ThreadPoolExecutor commExecutor = new ThreadPoolExecutor(0,
      Integer.MAX_VALUE, 5, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
  
  static HttpClient client;
  
  static {
    MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
    mgr.getParams().setDefaultMaxConnectionsPerHost(8);
    mgr.getParams().setMaxTotalConnections(200);
    client = new HttpClient(mgr);
  }
  
  CompletionService<Request> completionService;
  Set<Future<Request>> pending;
  
  private final SolrQueryRequest req;
  private final SolrQueryResponse rsp;

  private final SchemaField idField;
  
  int maxBufferedAddsPerServer = 10;
  int maxBufferedDeletesPerServer = 100;
  
  private List<AddUpdateCommand> alist;
  private ArrayList<DeleteUpdateCommand> dlist;
  
  public SolrCmdDistributor(SolrQueryRequest req,
      SolrQueryResponse rsp) {
    this.req = req;
    this.rsp = rsp;
    this.idField = req.getSchema().getUniqueKeyField();
  }
  
  public void finish(String shardStr) {

    // piggyback on any outstanding adds or deletes if possible.
    flushAdds(1, null, shardStr);
    flushDeletes(1, null, shardStr);

    checkResponses(true);
  }
  
  public void distribDelete(DeleteUpdateCommand cmd, String shardStr) throws IOException {
    checkResponses(false);
    
    if (cmd.id != null) {
      doDelete(cmd, shardStr);
    } else if (cmd.query != null) {
      // TODO: query must be broadcast to all ??
      doDelete(cmd, shardStr);
    }
  }
  
  public void distribAdd(AddUpdateCommand cmd, String shardStr) throws IOException {
    
    checkResponses(false);
    
    SolrInputDocument doc = cmd.getSolrInputDocument();
    SolrInputField field = doc.getField(idField.getName());
    if (field == null) {
      throw new RuntimeException("no id field found");
    }
    
    // make sure any pending deletes are flushed
    flushDeletes(1, null, shardStr);
    
    // TODO: this is brittle
    // need to make a clone since these commands may be reused
    AddUpdateCommand clone = new AddUpdateCommand(req);
    
    clone.solrDoc = cmd.solrDoc;
    clone.commitWithin = cmd.commitWithin;
    clone.overwrite = cmd.overwrite;
    
    // nocommit: review as far as SOLR-2685
    // clone.indexedId = cmd.indexedId;
    // clone.doc = cmd.doc;
    

    if (alist == null) {
      alist = new ArrayList<AddUpdateCommand>(2);
    }
    alist.add(clone);
    
    flushAdds(maxBufferedAddsPerServer, null, shardStr);
  }
  
  public void distribCommit(CommitUpdateCommand cmd, String shardStr)
      throws IOException {
    
    // Wait for all outstanding repsonses to make sure that a commit
    // can't sneak in ahead of adds or deletes we already sent.
    // We could do this on a per-server basis, but it's more complex
    // and this solution will lead to commits happening closer together.
    checkResponses(true);
    
    // piggyback on any outstanding adds or deletes if possible.
    // TODO: review this
    flushAdds(1, cmd, shardStr);
    
    flushDeletes(1, cmd, shardStr);
    
    
    UpdateRequestExt ureq = new UpdateRequestExt();
    // pass on SEEN_LEADER
    // TODO: perhaps we should just pass all the incoming params...
    if (ureq.getParams() == null) {
      ureq.setParams(new ModifiableSolrParams());
    }
    String seenLeader = req.getParams().get(
        DistributedUpdateProcessor.SEEN_LEADER);
    if (seenLeader != null) {
      ureq.getParams().add(DistributedUpdateProcessor.SEEN_LEADER, seenLeader);
    }
    
    // nocommit: we add the right update chain - we should add the current one?
    ureq.getParams().add("update.chain", "distrib-update-chain");
    addCommit(ureq, cmd);
    submit(ureq, shardStr);
    
    // if (next != null && shardStr == null) next.processCommit(cmd);
    
    // if the command wanted to block until everything was committed,
    // then do that here.
    // nocommit
    if (/* cmd.waitFlush || */cmd.waitSearcher) {
      checkResponses(true);
    }
  }
  
  private void doDelete(DeleteUpdateCommand cmd, String shardStr) throws IOException {
    
    flushAdds(1, null, shardStr);
    
    if (dlist == null) {
      dlist = new ArrayList<DeleteUpdateCommand>(2);
    }
    dlist.add(clone(cmd));
    
    flushDeletes(maxBufferedDeletesPerServer, null, shardStr);
  }
  
  void addCommit(UpdateRequestExt ureq, CommitUpdateCommand cmd) {
    if (cmd == null) return;
    // nocommit
    ureq.setAction(cmd.optimize ? AbstractUpdateRequest.ACTION.OPTIMIZE
        : AbstractUpdateRequest.ACTION.COMMIT, false, cmd.waitSearcher);
  }
  
  boolean flushAdds(int limit, CommitUpdateCommand ccmd, String shardStr) {
    // check for pending deletes
    if (alist == null || alist.size() < limit) return false;
    
    UpdateRequestExt ureq = new UpdateRequestExt();
    // pass on seen leader
    if (ureq.getParams() == null) {
      ureq.setParams(new ModifiableSolrParams());
    }
    String seenLeader = req.getParams().get(DistributedUpdateProcessor.SEEN_LEADER);
    if (seenLeader != null) {
      ureq.getParams().add(DistributedUpdateProcessor.SEEN_LEADER, seenLeader);
    }
    // nocommit: we add the right update chain - we should add the current one?
    ureq.getParams().add("update.chain", "distrib-update-chain");
    addCommit(ureq, ccmd);
    
    for (AddUpdateCommand cmd : alist) {
      ureq.add(cmd.solrDoc, cmd.commitWithin, cmd.overwrite);
    }
    
    alist = null;
    submit(ureq, shardStr);
    return true;
  }
  
  boolean flushDeletes(int limit, CommitUpdateCommand ccmd, String shardStr) {
    // check for pending deletes
    if (dlist == null || dlist.size() < limit) return false;
    
    UpdateRequestExt ureq = new UpdateRequestExt();
    // pass on version
    if (ureq.getParams() == null) {
      ureq.setParams(new ModifiableSolrParams());
    }
    
    String seenLeader = req.getParams().get(DistributedUpdateProcessor.SEEN_LEADER);
    if (seenLeader != null) {
      ureq.getParams().add(DistributedUpdateProcessor.SEEN_LEADER, seenLeader);
    }
    
    // nocommit: we add the right update chain - we should add the current one?
    ureq.getParams().add("update.chain", "distrib-update-chain");
    addCommit(ureq, ccmd);
    for (DeleteUpdateCommand cmd : dlist) {
      if (cmd.id != null) {
        ureq.deleteById(cmd.id);
      }
      if (cmd.query != null) {
        ureq.deleteByQuery(cmd.query);
      }
    }
    
    dlist = null;
    submit(ureq, shardStr);
    return true;
  }
  
  // TODO: this is brittle
  private DeleteUpdateCommand clone(DeleteUpdateCommand cmd) {
    DeleteUpdateCommand c = new DeleteUpdateCommand(req);
    c.id = cmd.id;
    c.query = cmd.query;
    return c;
  }
  
  static class Request {
    // TODO: we may need to look at deep cloning this?
    String shard;
    UpdateRequestExt ureq;
    NamedList<Object> ursp;
    int rspCode;
    Exception exception;
  }
  
  void submit(UpdateRequestExt ureq, String shardStr) {
    Request sreq = new Request();
    sreq.shard = shardStr;
    sreq.ureq = ureq;
    submit(sreq);
  }
  
  void submit(final Request sreq) {
    if (completionService == null) {
      completionService = new ExecutorCompletionService<Request>(commExecutor);
      pending = new HashSet<Future<Request>>();
    }
    String[] shards;
    // look to see if we should send to multiple servers
    if (sreq.shard.contains("|")) {
      shards = sreq.shard.split("\\|");
    } else {
      shards = new String[1];
      shards[0] = sreq.shard;
    }
    for (final String shard : shards) {
      // TODO: when we break up shards, we might forward
      // to self again - makes things simple here, but we could
      // also have realized this before, done the req locally, and
      // removed self from this list.
      
      Callable<Request> task = new Callable<Request>() {
        @Override
        public Request call() throws Exception {
          Request clonedRequest = new Request();
          clonedRequest.shard = sreq.shard;
          clonedRequest.ureq = sreq.ureq;
          
          try {
            // TODO: what about https?
            String url;
            if (!shard.startsWith("http://")) {
              url = "http://" + shard;
            } else {
              url = shard;
            }
            System.out.println("URL:" + url);
            SolrServer server = new CommonsHttpSolrServer(url, client);
            clonedRequest.ursp = server.request(clonedRequest.ureq);
            
            // currently no way to get the request body.
          } catch (Exception e) {
            e.printStackTrace(System.out);
            clonedRequest.exception = e;
            if (e instanceof SolrException) {
              clonedRequest.rspCode = ((SolrException) e).code();
            } else {
              clonedRequest.rspCode = -1;
            }
          }
          System.out.println("RSPFirst:" + clonedRequest.rspCode);
          return clonedRequest;
        }
      };
      
      pending.add(completionService.submit(task));
    }
  }
  
  void checkResponses(boolean block) {
    
    int expectedResponses = pending == null ? 0 : pending.size();
    int failed = 0;
    while (pending != null && pending.size() > 0) {
      try {
        Future<Request> future = block ? completionService.take()
            : completionService.poll();
        if (future == null) return;
        pending.remove(future);
        
        try {
          Request sreq = future.get();
          System.out.println("RSP:" + sreq.rspCode);
          if (sreq.rspCode != 0) {
            // error during request
            failed++;
            // use the first exception encountered
            if (rsp.getException() == null) {
              Exception e = sreq.exception;
              String newMsg = "shard update error (" + sreq.shard + "):"
                  + e.getMessage();
              if (e instanceof SolrException) {
                SolrException se = (SolrException) e;
                e = new SolrException(ErrorCode.getErrorCode(se.code()),
                    newMsg, se.getCause());
              } else {
                e = new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "newMsg", e);
              }
              rsp.setException(e);
            }
            
            SolrException.logOnce(SolrCore.log, "shard update error ("
                + sreq.shard + ")", sreq.exception);
          }
          
        } catch (ExecutionException e) {
          // shouldn't happen since we catch exceptions ourselves
          SolrException.log(SolrCore.log,
              "error sending update request to shard", e);
        }
        
      } catch (InterruptedException e) {
        throw new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE,
            "interrupted waiting for shard update response", e);
      }
    }
    
    System.out.println("check failed rate:" + failed + " " + expectedResponses
        / 2);
    if (failed <= (expectedResponses / 2)) {
      // don't fail if half or more where fine
      rsp.setException(null);
    }
  }
}
