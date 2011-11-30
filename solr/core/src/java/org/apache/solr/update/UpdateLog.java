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
import org.apache.solr.core.SolrCore;
import org.apache.solr.util.plugin.PluginInfoInitialized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/** @lucene.experimental */
public abstract class UpdateLog implements PluginInfoInitialized {
  public static Logger log = LoggerFactory.getLogger(UpdateLog.class);

  public enum SyncLevel { NONE, FLUSH, FSYNC }
  public enum State { REPLAYING, BUFFERING, APPLYING_BUFFERED, ACTIVE }

  public static final int ADD = 0x01;
  public static final int DELETE = 0x02;
  public static final int DELETE_BY_QUERY = 0x03;
  public static final int COMMIT = 0x04;

  public abstract void init(UpdateHandler uhandler, SolrCore core);
  public abstract void add(AddUpdateCommand cmd);
  public abstract void delete(DeleteUpdateCommand cmd);
  public abstract void deleteByQuery(DeleteUpdateCommand cmd);
  public abstract void preCommit(CommitUpdateCommand cmd);
  public abstract void postCommit(CommitUpdateCommand cmd);
  public abstract void preSoftCommit(CommitUpdateCommand cmd);
  public abstract void postSoftCommit(CommitUpdateCommand cmd);
  public abstract Object lookup(BytesRef indexedId);
  public abstract Long lookupVersion(BytesRef indexedId);
  public abstract void close();
  public abstract VersionInfo getVersionInfo();
  public abstract void finish(SyncLevel syncLevel);

  public abstract Future<RecoveryInfo> recoverFromLog();
  public abstract void bufferUpdates();
  public abstract Future<FSUpdateLog.RecoveryInfo> applyBufferedUpdates();
  public abstract State getState();


  public static class RecoveryInfo {
    public int adds;
    public int deletes;
    public int deleteByQuery;
    public int errors;
  }
}
