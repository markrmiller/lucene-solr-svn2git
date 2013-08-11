package org.apache.solr.common.cloud;

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

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.apache.solr.common.params.ShardParams._ROUTE_;

/** This document router is for custom sharding
 */
public class ImplicitDocRouter extends DocRouter {
  public static final String NAME = "implicit";
//  @Deprecated
//  public static final String DEFAULT_SHARD_PARAM = "_shard_";
  private static Logger log = LoggerFactory
      .getLogger(ImplicitDocRouter.class);

  @Override
  public Slice getTargetSlice(String id, SolrInputDocument sdoc, SolrParams params, DocCollection collection) {
    String shard = null;
    if (sdoc != null) {
      String f = collection.getStr(ROUTE_FIELD);
      if(f !=null) {
        Object o = sdoc.getFieldValue(f);
        if (o != null) shard = o.toString();
        else throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "No value for field "+f +" in " + sdoc);
      }
      if(shard == null) {
        Object o = sdoc.getFieldValue(_ROUTE_);
        if (o == null) o = sdoc.getFieldValue("_shard_");//deprecated . for backcompat remove later
        if (o != null) {
          shard = o.toString();
        }
      }
    }

    if (shard == null) {
      shard = params.get(_ROUTE_);
      if(shard == null) shard =params.get("_shard_"); //deperecated for back compat
    }

    if (shard != null) {

      Slice slice = collection.getSlice(shard);
      if (slice == null) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "No shard called =" + shard + " in " + collection);
      }
      return slice;
    }

    return null;  // no shard specified... use default.
  }

  @Override
  public boolean isTargetSlice(String id, SolrInputDocument sdoc, SolrParams params, String shardId, DocCollection collection) {

    // todo : how to handle this?
    return false;
  }

  @Override
  public Collection<Slice> getSearchSlicesSingle(String shardKey, SolrParams params, DocCollection collection) {

    if (shardKey == null) {
      return collection.getActiveSlices();
    }

    // assume the shardKey is just a slice name
    Slice slice = collection.getSlice(shardKey);
    if (slice == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "implicit router can't find shard " + shardKey + " in collection " + collection.getName());
    }

    return Collections.singleton(slice);
  }

  @Override
  public List<Range> partitionRange(int partitions, Range range) {
    return null;
  }
}
