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

package org.apache.solr.update;

import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** @lucene.experimental */
class NullUpdateLog extends UpdateLog {
  @Override
  public void init(PluginInfo info) {
  }

  @Override
  public void init(UpdateHandler uhandler, SolrCore core) {
  }

  @Override
  public void add(AddUpdateCommand cmd) {
  }

  @Override
  public void delete(DeleteUpdateCommand cmd) {
  }

  @Override
  public void deleteByQuery(DeleteUpdateCommand cmd) {
  }

  @Override
  public void preCommit(CommitUpdateCommand cmd) {
  }

  @Override
  public void postCommit(CommitUpdateCommand cmd) {
  }

  @Override
  public void preSoftCommit(CommitUpdateCommand cmd) {
  }

  @Override
  public void postSoftCommit(CommitUpdateCommand cmd) {
  }

  @Override
  public Object lookup(BytesRef indexedId) {
    return null;
  }

  @Override
  public Long lookupVersion(BytesRef indexedId) {
    return null;
  }

  @Override
  public void close() {
  }

  @Override
  public VersionInfo getVersionInfo() {
    return null;
  }

  @Override
  public void finish(SyncLevel synclevel) {
  }

  @Override
  public boolean recoverFromLog() {
    return false;
  }

}

/** @lucene.experimental */
public class FSUpdateLog extends UpdateLog {

  public static String TLOG_NAME="tlog";

  long id = -1;

  private TransactionLog tlog;
  private TransactionLog prevTlog;

  private Map<BytesRef,LogPtr> map = new HashMap<BytesRef, LogPtr>();
  private Map<BytesRef,LogPtr> prevMap;  // used while committing/reopening is happening
  private Map<BytesRef,LogPtr> prevMap2;  // used while committing/reopening is happening
  private TransactionLog prevMapLog;  // the transaction log used to look up entries found in prevMap
  private TransactionLog prevMapLog2;  // the transaction log used to look up entries found in prevMap

  private final int numDeletesToKeep = 1000;
  // keep track of deletes only... this is not updated on an add
  private LinkedHashMap<BytesRef, LogPtr> oldDeletes = new LinkedHashMap<BytesRef, LogPtr>(numDeletesToKeep) {
    protected boolean removeEldestEntry(Map.Entry eldest) {
      return size() > numDeletesToKeep;
    }
  };


  private String[] tlogFiles;
  private File tlogDir;
  private Collection<String> globalStrings;

  private String dataDir;
  private String lastDataDir;

  private VersionInfo versionInfo;

  private SyncLevel defaultSyncLevel = SyncLevel.FLUSH;

  private volatile UpdateHandler uhandler;    // a core reload can change this reference!

  @Override
  public VersionInfo getVersionInfo() {
    return versionInfo;
  }

  @Override
  public void init(PluginInfo info) {
    dataDir = (String)info.initArgs.get("dir");
  }

  public void init(UpdateHandler uhandler, SolrCore core) {
    if (dataDir == null || dataDir.length()==0) {
      dataDir = core.getDataDir();
    }

    this.uhandler = uhandler;

    if (dataDir.equals(lastDataDir)) {
      // on a normal reopen, we currently shouldn't have to do anything
      return;
    }
    lastDataDir = dataDir;
    tlogDir = new File(dataDir, TLOG_NAME);
    tlogDir.mkdirs();
    tlogFiles = getLogList(tlogDir);
    id = getLastLogId() + 1;   // add 1 since we will create a new log for the next update

    versionInfo = new VersionInfo(uhandler, 256);

    recoverFromLog();  // TODO: is this too early?
  }

  static class LogPtr {
    final long pointer;
    final long version;

    public LogPtr(long pointer, long version) {
      this.pointer = pointer;
      this.version = version;
    }

    public String toString() {
      return "LogPtr(" + pointer + ")";
    }
  }

  public static String[] getLogList(File directory) {
    final String prefix = TLOG_NAME+'.';
    String[] names = directory.list(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.startsWith(prefix);
      }
    });
    Arrays.sort(names);
    return names;
  }


  public long getLastLogId() {
    if (id != -1) return id;
    if (tlogFiles.length == 0) return -1;
    String last = tlogFiles[tlogFiles.length-1];
    return Long.parseLong(last.substring(TLOG_NAME.length()+1));
  }


  @Override
  public void add(AddUpdateCommand cmd) {
    synchronized (this) {
      ensureLog();
      long pos = tlog.write(cmd);
      LogPtr ptr = new LogPtr(pos, cmd.getVersion());
      map.put(cmd.getIndexedId(), ptr);
      // SolrCore.verbose("TLOG: added id " + cmd.getPrintableId() + " to " + tlog + " " + ptr + " map=" + System.identityHashCode(map));
    }
  }

  @Override
  public void delete(DeleteUpdateCommand cmd) {
    BytesRef br = cmd.getIndexedId();

    synchronized (this) {
      ensureLog();
      long pos = tlog.writeDelete(cmd);
      LogPtr ptr = new LogPtr(pos, cmd.version);
      map.put(br, ptr);

      oldDeletes.put(br, ptr);
      // SolrCore.verbose("TLOG: added delete for id " + cmd.id + " to " + tlog + " " + ptr + " map=" + System.identityHashCode(map));
    }
  }

  @Override
  public void deleteByQuery(DeleteUpdateCommand cmd) {
    synchronized (this) {
      ensureLog();
      // TODO: how to support realtime-get, optimistic concurrency, or anything else in this case?
      // Maybe we shouldn't?
      // realtime-get could just do a reopen of the searcher
      // optimistic concurrency? Maybe we shouldn't support deleteByQuery w/ optimistic concurrency
      long pos = tlog.writeDeleteByQuery(cmd);
      LogPtr ptr = new LogPtr(pos, cmd.getVersion());
      // SolrCore.verbose("TLOG: added deleteByQuery " + cmd.query + " to " + tlog + " " + ptr + " map=" + System.identityHashCode(map));
    }
  }


  private void newMap() {
    prevMap2 = prevMap;
    prevMapLog2 = prevMapLog;

    prevMap = map;
    prevMapLog = tlog;

    map = new HashMap<BytesRef, LogPtr>();
  }

  private void clearOldMaps() {
    prevMap = null;
    prevMap2 = null;
  }

  @Override
  public void preCommit(CommitUpdateCommand cmd) {
    synchronized (this) {
      // since we're changing the log, we must change the map.
      newMap();

      // since document additions can happen concurrently with commit, create
      // a new transaction log first so that we know the old one is definitely
      // in the index.
      prevTlog = tlog;
      tlog = null;
      id++;

      if (prevTlog != null) {
        globalStrings = prevTlog.getGlobalStrings();
      }
    }
  }

  @Override
  public void postCommit(CommitUpdateCommand cmd) {
    synchronized (this) {
      if (prevTlog != null) {
        prevTlog.decref();
        prevTlog = null;
      }
    }
  }

  @Override
  public void preSoftCommit(CommitUpdateCommand cmd) {
    synchronized (this) {
      if (!cmd.softCommit) return;  // already handled this at the start of the hard commit
      newMap();

      // start adding documents to a new map since we won't know if
      // any added documents will make it into this commit or not.
      // But we do know that any updates already added will definitely
      // show up in the latest reader after the commit succeeds.
      map = new HashMap<BytesRef, LogPtr>();
      // SolrCore.verbose("TLOG: preSoftCommit: prevMap="+ System.identityHashCode(prevMap) + " new map=" + System.identityHashCode(map));
    }
  }

  @Override
  public void postSoftCommit(CommitUpdateCommand cmd) {
    synchronized (this) {
      // We can clear out all old maps now that a new searcher has been opened.
      // This currently only works since DUH2 synchronizes around preCommit to avoid
      // it being called in the middle of a preSoftCommit, postSoftCommit sequence.
      // If this DUH2 synchronization were to be removed, preSoftCommit should
      // record what old maps were created and only remove those.
      clearOldMaps();
      // SolrCore.verbose("TLOG: postSoftCommit: disposing of prevMap="+ System.identityHashCode(prevMap));
    }
  }

  @Override
  public Object lookup(BytesRef indexedId) {
    LogPtr entry;
    TransactionLog lookupLog;

    synchronized (this) {
      entry = map.get(indexedId);
      lookupLog = tlog;  // something found in "map" will always be in "tlog"
      // SolrCore.verbose("TLOG: lookup: for id ",indexedId.utf8ToString(),"in map",System.identityHashCode(map),"got",entry,"lookupLog=",lookupLog);
      if (entry == null && prevMap != null) {
        entry = prevMap.get(indexedId);
        // something found in prevMap will always be found in preMapLog (which could be tlog or prevTlog)
        lookupLog = prevMapLog;
        // SolrCore.verbose("TLOG: lookup: for id ",indexedId.utf8ToString(),"in prevMap",System.identityHashCode(map),"got",entry,"lookupLog=",lookupLog);
      }
      if (entry == null && prevMap2 != null) {
        entry = prevMap2.get(indexedId);
        // something found in prevMap2 will always be found in preMapLog2 (which could be tlog or prevTlog)
        lookupLog = prevMapLog2;
        // SolrCore.verbose("TLOG: lookup: for id ",indexedId.utf8ToString(),"in prevMap2",System.identityHashCode(map),"got",entry,"lookupLog=",lookupLog);
      }

      if (entry == null) {
        return null;
      }
      lookupLog.incref();
    }

    try {
      // now do the lookup outside of the sync block for concurrency
      return lookupLog.lookup(entry.pointer);
    } finally {
      lookupLog.decref();
    }

  }

  // This method works like realtime-get... it only guarantees to return the latest
  // version of the *completed* update.  There can be updates in progress concurrently
  // that have already grabbed higher version numbers.  Higher level coordination or
  // synchronization is needed for stronger guarantees (as VersionUpdateProcessor does).
  @Override
  public Long lookupVersion(BytesRef indexedId) {
    LogPtr entry;
    TransactionLog lookupLog;

    synchronized (this) {
      entry = map.get(indexedId);
      lookupLog = tlog;  // something found in "map" will always be in "tlog"
      // SolrCore.verbose("TLOG: lookup ver: for id ",indexedId.utf8ToString(),"in map",System.identityHashCode(map),"got",entry,"lookupLog=",lookupLog);
      if (entry == null && prevMap != null) {
        entry = prevMap.get(indexedId);
        // something found in prevMap will always be found in preMapLog (which could be tlog or prevTlog)
        lookupLog = prevMapLog;
        // SolrCore.verbose("TLOG: lookup ver: for id ",indexedId.utf8ToString(),"in prevMap",System.identityHashCode(map),"got",entry,"lookupLog=",lookupLog);
      }
      if (entry == null && prevMap2 != null) {
        entry = prevMap2.get(indexedId);
        // something found in prevMap2 will always be found in preMapLog2 (which could be tlog or prevTlog)
        lookupLog = prevMapLog2;
        // SolrCore.verbose("TLOG: lookup ver: for id ",indexedId.utf8ToString(),"in prevMap2",System.identityHashCode(map),"got",entry,"lookupLog=",lookupLog);
      }
    }

    if (entry != null) {
      return entry.version;
    }

    // Now check real index
    Long version = versionInfo.getVersionFromIndex(indexedId);

    if (version != null) {
      return version;
    }

    // We can't get any version info for deletes from the index, so if the doc
    // wasn't found, check a cache of recent deletes.

    synchronized (this) {
      entry = oldDeletes.get(indexedId);
    }

    if (entry != null) {
      return entry.version;
    }

    return null;
  }

  @Override
  public void finish(SyncLevel syncLevel) {
    if (syncLevel == null) {
      syncLevel = defaultSyncLevel;
    }
    if (syncLevel == SyncLevel.NONE) {
      return;
    }

    TransactionLog currLog;
    synchronized (this) {
      currLog = tlog;
      if (currLog == null) return;
      currLog.incref();
    }

    try {
      if (tlog != null) {
        tlog.finish(syncLevel);
      }
    } finally {
      currLog.decref();
    }
  }

  @Override
  public boolean recoverFromLog() {
    if (tlogFiles.length == 0) return false;
    TransactionLogReader tlogReader = null;
    try {
      tlogReader = new TransactionLogReader( new File(tlogDir, tlogFiles[tlogFiles.length-1]) );
      boolean completed = tlogReader.completed();
      if (completed) {
        return true;
      }

      recoveryExecutor.execute(new LogReplayer(tlogReader));
      return true;

    } catch (Exception ex) {
      // an error during recovery
      uhandler.log.warn("Exception during recovery", ex);
      if (tlogReader != null) tlogReader.close();
    }

    return false;
  }


  private void ensureLog() {
    if (tlog == null) {
      String newLogName = String.format("%s.%019d", TLOG_NAME, id);
      tlog = new TransactionLog(new File(tlogDir, newLogName), globalStrings);
    }
  }

  @Override
  public void close() {
    synchronized (this) {
      if (prevTlog != null) {
        prevTlog.decref();
      }
      if (tlog != null) {
        tlog.decref();
      }
    }
  }

  // TODO: do we let the log replayer run across core reloads?
  class LogReplayer implements Runnable {
    TransactionLogReader tlogReader;
    public LogReplayer(TransactionLogReader tlogReader) {
      this.tlogReader = tlogReader;
    }

    @Override
    public void run() {
      uhandler.core.log.warn("Starting log replay " + tlogReader);

      SolrParams params = new ModifiableSolrParams();
      long commitVersion = 0;

      for(;;) {
        Object o = tlogReader.readNext();
        if (o == null) break;

        // create a new request each time since the update handler and core could change
        SolrQueryRequest req = new LocalSolrQueryRequest(uhandler.core, params);

        // TODO: race?  This core could close on us if it was reloaded

        try {

          // should currently be a List<Oper,Ver,Doc/Id>
          List entry = (List)o;

          int oper = (Integer)entry.get(0);
          long version = (Long) entry.get(1);

          switch (oper) {
            case UpdateLog.ADD:
            {
              // byte[] idBytes = (byte[]) entry.get(2);
              SolrInputDocument sdoc = (SolrInputDocument)entry.get(entry.size()-1);
              AddUpdateCommand cmd = new AddUpdateCommand(req);
              // cmd.setIndexedId(new BytesRef(idBytes));
              cmd.solrDoc = sdoc;
              cmd.setVersion(version);
              cmd.setFlags(UpdateCommand.REPLAY);
              uhandler.addDoc(cmd);
              break;
            }
            case UpdateLog.DELETE:
            {
              byte[] idBytes = (byte[]) entry.get(2);
              DeleteUpdateCommand cmd = new DeleteUpdateCommand(req);
              cmd.setVersion(version);
              cmd.setFlags(UpdateCommand.REPLAY);
              uhandler.delete(cmd);
              break;
            }

            case UpdateLog.DELETE_BY_QUERY:
            {
              // TODO
              break;
            }

            case UpdateLog.COMMIT:
            {
              // TODO
              commitVersion = version;
              break;
            }

            default:
              throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,  "Unknown Operation! " + oper);
          }
        } catch (IOException ex) {

        } catch (ClassCastException cl) {
          uhandler.log.warn("Corrupt log", cl);
          // would be caused by a corrupt transaction log
        } catch (Exception ex) {
          uhandler.log.warn("Exception replaying log", ex);

          // something wrong with the request?
        }
      }
      tlogReader.close();

      SolrQueryRequest req = new LocalSolrQueryRequest(uhandler.core, params);
      CommitUpdateCommand cmd = new CommitUpdateCommand(req, false);
      cmd.setVersion(commitVersion);
      cmd.softCommit = false;
      cmd.waitSearcher = false;
      cmd.setFlags(UpdateCommand.REPLAY);
      try {
        uhandler.commit(cmd);
      } catch (IOException ex) {
        uhandler.log.error("Replay exception: final commit.", ex);
      }
      tlogReader.delete();

      uhandler.core.log.warn("Ending log replay " + tlogReader);

    }
  }


  static ThreadPoolExecutor recoveryExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
      1, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

}



