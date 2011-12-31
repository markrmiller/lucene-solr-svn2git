package org.apache.solr.update;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.UpdateRequestExt;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;



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
  
  int maxBufferedAddsPerServer = 10;
  int maxBufferedDeletesPerServer = 100;

  private Response response = new Response();
  
  private final Map<String,List<AddRequest>> adds = new HashMap<String,List<AddRequest>>();
  private final Map<String,List<DeleteRequest>> deletes = new HashMap<String,List<DeleteRequest>>();
  
  class AddRequest {
    AddUpdateCommand cmd;
    ModifiableSolrParams params;
  }
  
  class DeleteRequest {
    DeleteUpdateCommand cmd;
    ModifiableSolrParams params;
  }
  
  public void finish(List<Url> urls) {

    // piggyback on any outstanding adds or deletes if possible.
    flushAdds(1, null, null, urls);
    flushDeletes(1, null, null, urls);

    checkResponses(true);
  }
  
  public void distribDelete(DeleteUpdateCommand cmd, List<Url> urls, ModifiableSolrParams params) throws IOException {
    checkResponses(false);
    
    if (cmd.isDeleteById()) {
      doDelete(cmd, urls, params);
    } else {
      // nocommit TODO: query must be broadcast to all ??
      doDelete(cmd, urls, params);
    }
  }
  
  public void distribAdd(AddUpdateCommand cmd, List<Url> urls, ModifiableSolrParams params) throws IOException {
    
    checkResponses(false);
    
    // make sure any pending deletes are flushed
    flushDeletes(1, null, null, urls);
    
    // TODO: this is brittle
    // need to make a clone since these commands may be reused
    AddUpdateCommand clone = new AddUpdateCommand(null);
    
    clone.solrDoc = cmd.solrDoc;
    clone.commitWithin = cmd.commitWithin;
    clone.overwrite = cmd.overwrite;
    clone.setVersion(cmd.getVersion());
    AddRequest addRequest = new AddRequest();
    addRequest.cmd = clone;
    addRequest.params = params;
    // nocommit: review as far as SOLR-2685
    // clone.indexedId = cmd.indexedId;
    // clone.doc = cmd.doc;
    for (Url url : urls) {
      List<AddRequest> alist = adds.get(url.getUrl());
      if (alist == null) {
        alist = new ArrayList<AddRequest>(2);
        adds.put(url.getUrl(), alist);
      }
      alist.add(addRequest);
     // System.out.println("buffer add to " + url);
    }
    
    flushAdds(maxBufferedAddsPerServer, null, null, urls);
  }
  
  public void distribCommit(CommitUpdateCommand cmd, List<Url> urls, ModifiableSolrParams params)
      throws IOException {
    
    // Wait for all outstanding repsonses to make sure that a commit
    // can't sneak in ahead of adds or deletes we already sent.
    // We could do this on a per-server basis, but it's more complex
    // and this solution will lead to commits happening closer together.
    checkResponses(true);

    // piggyback on any outstanding adds or deletes if possible.
    boolean piggied = false;
    if (!flushAdds(1, cmd, params, urls)) {
      if (flushDeletes(1, cmd, params, urls)) piggied = true;
    } else {
      piggied = true;
    }
    
    if (!piggied) {
      UpdateRequestExt ureq = new UpdateRequestExt();
      ureq.setParams(params);
      
      addCommit(ureq, cmd);
      
      for (Url url : urls) {
        submit(ureq, url);
      }
    }
    
    // if the command wanted to block until everything was committed,
    // then do that here.
    // nocommit
    if (/* cmd.waitFlush || */cmd.waitSearcher) {
      checkResponses(true);
    }
  }
  
  private void doDelete(DeleteUpdateCommand cmd, List<Url> urls,
      ModifiableSolrParams params) throws IOException {
    
    flushAdds(1, null, null, urls);
    
    DeleteUpdateCommand clonedCmd = clone(cmd);
    DeleteRequest deleteRequest = new DeleteRequest();
    deleteRequest.cmd = clonedCmd;
    deleteRequest.params = params;
    for (Url url : urls) {
      List<DeleteRequest> dlist = deletes.get(url.getUrl());
      
      if (dlist == null) {
        dlist = new ArrayList<DeleteRequest>(2);
        deletes.put(url.getUrl(), dlist);
      }
      dlist.add(deleteRequest);
    }
    
    flushDeletes(maxBufferedDeletesPerServer, null, null, urls);
  }
  
  void addCommit(UpdateRequestExt ureq, CommitUpdateCommand cmd) {
    if (cmd == null) return;
    //System.out.println("piggy back commit on");
    // nocommit
    ureq.setAction(cmd.optimize ? AbstractUpdateRequest.ACTION.OPTIMIZE
        : AbstractUpdateRequest.ACTION.COMMIT, false, cmd.waitSearcher);
  }
  
  boolean flushAdds(int limit, CommitUpdateCommand ccmd, ModifiableSolrParams params, List<Url> urls) {
    // check for pending deletes
    UpdateRequestExt ureq = null;
    for (Url url : urls) {
      List<AddRequest> alist = adds.get(url.getUrl());
      if (alist == null || alist.size() < limit) return false;
      if (ureq == null) {
        ureq = new UpdateRequestExt();
        
        addCommit(ureq, ccmd);
        
        ModifiableSolrParams combinedParams = new ModifiableSolrParams();
        
        for (AddRequest aReq : alist) {
          AddUpdateCommand cmd = aReq.cmd;
          combinedParams.add(aReq.params);
          ureq.add(cmd.solrDoc, cmd.commitWithin, cmd.overwrite);
        }
        
        if (params != null) combinedParams.add(params);
        if (ureq.getParams() == null) ureq.setParams(new ModifiableSolrParams());
        ureq.getParams().add(combinedParams);
        //System.out.println("ureq is:" + ureq);
      }
      
      adds.remove(url.getUrl());
      //System.out.println("flush adds to " + url + " " + alist.size());
      //System.out.println("commit " + ureq.getAction());
      
      submit(ureq, url);
    }
    return true;
  }
  
  boolean flushDeletes(int limit, CommitUpdateCommand ccmd, ModifiableSolrParams params, List<Url> urls) {
    // check for pending deletes
    //System.out.println("flush deletes to " + urls);
    UpdateRequestExt ureq = null;
    for (Url url : urls) {
      List<DeleteRequest> dlist = deletes.get(url.getUrl());
      if (dlist == null || dlist.size() < limit) return false;
      if (ureq == null) {
        ureq = new UpdateRequestExt();
 
        addCommit(ureq, ccmd);
        
        ModifiableSolrParams combinedParams = new ModifiableSolrParams();
        
        for (DeleteRequest dReq : dlist) {
          DeleteUpdateCommand cmd = dReq.cmd;
          combinedParams.add(dReq.params);
          if (cmd.isDeleteById()) {
            ureq.deleteById(cmd.getId(), cmd.getVersion());
          } else {
            ureq.deleteByQuery(cmd.query);
          }
        }

        if (params != null) combinedParams.add(params);
        if (ureq.getParams() == null) ureq.setParams(new ModifiableSolrParams());
        ureq.getParams().add(combinedParams);
      }
      
      deletes.remove(url.getUrl());
      submit(ureq, url);
    }
    return true;
  }
  
  private DeleteUpdateCommand clone(DeleteUpdateCommand cmd) {
    DeleteUpdateCommand c = (DeleteUpdateCommand)cmd.clone();
    // TODO: shouldnt the clone do this?
    c.setFlags(cmd.getFlags());
    c.setVersion(cmd.getVersion());
    return c;
  }
  
  public static class Request {
    // TODO: we may need to look at deep cloning this?
    public Url url;
    UpdateRequestExt ureq;
    NamedList<Object> ursp;
    int rspCode;
    public Exception exception;
    String errorUrl;
    int retries;
  }
  
  void submit(UpdateRequestExt ureq, Url url) {
    Request sreq = new Request();
    sreq.errorUrl = url.getUrl();
    sreq.url = url;
    sreq.ureq = ureq;
    submit(sreq);
  }
  
  public void submit(final Request sreq) {
    if (completionService == null) {
      completionService = new ExecutorCompletionService<Request>(commExecutor);
      pending = new HashSet<Future<Request>>();
    }
    final String url = sreq.url.getUrl();
    //System.out.println("submit url:" + url);
    Callable<Request> task = new Callable<Request>() {
      @Override
      public Request call() throws Exception {
        Request clonedRequest = new Request();
        clonedRequest.url = sreq.url;
        clonedRequest.ureq = sreq.ureq;
        
        // TODO: yeah, this is a little odd...
        clonedRequest.errorUrl = url;
        
        try {
          String fullUrl;
          if (!url.startsWith("http://") && !url.startsWith("https://")) {
            fullUrl = "http://" + url;
          } else {
            fullUrl = url;
          }
          
          CommonsHttpSolrServer server = new CommonsHttpSolrServer(fullUrl,
              client);
          
          clonedRequest.ursp = server.request(clonedRequest.ureq);
          
          // currently no way to get the request body.
        } catch (Exception e) {
          clonedRequest.exception = e;
          if (e instanceof SolrException) {
            clonedRequest.rspCode = ((SolrException) e).code();
          } else {
            clonedRequest.rspCode = -1;
          }
        }
        return clonedRequest;
      }
    };
    
    pending.add(completionService.submit(task));
    
  }
  
  // TODO: need to consider those that call this with block=false now...
  void checkResponses(boolean block) {

    while (pending != null && pending.size() > 0) {
      try {
        Future<Request> future = block ? completionService.take()
            : completionService.poll();
        if (future == null) return;
        pending.remove(future);
        
        try {
          Request sreq = future.get();
          if (sreq.rspCode != 0) {
            // error during request
            
            // if there is a retry url, we want to retry...
            // TODO: but we really should only retry on connection errors...
            if (sreq.retries < 5 && sreq.url.checkRetry()) {
              sreq.retries++;
              sreq.rspCode = 0;
              sreq.exception = null;
              Thread.sleep(1000);
              submit(sreq);
              checkResponses(block);
            } else {
              Exception e = sreq.exception;
              Error error = new Error();
              error.e = e;
              error.url = sreq.errorUrl;
              response.errors.add(error);
              response.sreq = sreq;
              SolrException.logOnce(SolrCore.log, "shard update error "
                  + sreq.url + " (" + sreq.url + ")", sreq.exception);
            }
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
  }
  
  public static class Response {
    public Request sreq;
    public List<Error> errors = new ArrayList<Error>();
  }
  
  public static class Error {
    public String url;
    public Exception e;
  }

  public Response getResponse() {
    return response;
  }
  
  public static abstract class Url {
    public abstract String getUrl();
    public abstract boolean checkRetry();
  }

  public static class StdUrl extends Url {
    String url;
    
    public StdUrl(String url) {
      this.url = url;
    }
    
    @Override
    public String getUrl() {
      return url;
    }
    
    @Override
    public String toString() {
      return url;
    }

    @Override
    public boolean checkRetry() {
      return false;
    }
  }
}
