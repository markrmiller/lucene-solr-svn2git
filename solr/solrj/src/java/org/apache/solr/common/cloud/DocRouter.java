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

import org.noggit.JSONWriter;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.Hash;
import org.apache.solr.common.util.StrUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Class to partition int range into n ranges.
 * @lucene.experimental
 */
public abstract class DocRouter {
  public static final String DEFAULT_NAME = CompositeIdRouter.NAME;
  public static final DocRouter DEFAULT = new CompositeIdRouter();

  public static DocRouter getDocRouter(Object routerSpec) {
    DocRouter router = routerMap.get(routerSpec);
    if (router != null) return router;
    throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unknown document router '"+ routerSpec + "'");
  }

  // currently just an implementation detail...
  private final static Map<String, DocRouter> routerMap;
  static {
    routerMap = new HashMap<String, DocRouter>();
    PlainIdRouter plain = new PlainIdRouter();
    // instead of doing back compat this way, we could always convert the clusterstate on first read to "plain" if it doesn't have any properties.
    routerMap.put(null, plain);     // back compat with 4.0
    routerMap.put(PlainIdRouter.NAME, plain);
    routerMap.put(CompositeIdRouter.NAME, DEFAULT_NAME.equals(CompositeIdRouter.NAME) ? DEFAULT : new CompositeIdRouter());
    routerMap.put(ImplicitDocRouter.NAME, new ImplicitDocRouter());
    // NOTE: careful that the map keys (the static .NAME members) are filled in by making them final
  }


  // Hash ranges can't currently "wrap" - i.e. max must be greater or equal to min.
  // TODO: ranges may not be all contiguous in the future (either that or we will
  // need an extra class to model a collection of ranges)
  public static class Range implements JSONWriter.Writable {
    public int min;  // inclusive
    public int max;  // inclusive

    public Range(int min, int max) {
      assert min <= max;
      this.min = min;
      this.max = max;
    }

    public boolean includes(int hash) {
      return hash >= min && hash <= max;
    }

    public boolean isSubsetOf(Range superset) {
      return superset.min <= min && superset.max >= max;
    }

    public boolean overlaps(Range other) {
      return includes(other.min) || includes(other.max) || isSubsetOf(other);
    }

    @Override
    public String toString() {
      return Integer.toHexString(min) + '-' + Integer.toHexString(max);
    }


    @Override
    public int hashCode() {
      // difficult numbers to hash... only the highest bits will tend to differ.
      // ranges will only overlap during a split, so we can just hash the lower range.
      return (min>>28) + (min>>25) + (min>>21) + min;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj.getClass() != getClass()) return false;
      Range other = (Range)obj;
      return this.min == other.min && this.max == other.max;
    }

    @Override
    public void write(JSONWriter writer) {
      writer.write(toString());
    }
  }

  public Range fromString(String range) {
    int middle = range.indexOf('-');
    String minS = range.substring(0, middle);
    String maxS = range.substring(middle+1);
    long min = Long.parseLong(minS, 16);  // use long to prevent the parsing routines from potentially worrying about overflow
    long max = Long.parseLong(maxS, 16);
    return new Range((int)min, (int)max);
  }

  public Range fullRange() {
    return new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  /**
   * Returns the range for each partition
   */
  public List<Range> partitionRange(int partitions, Range range) {
    int min = range.min;
    int max = range.max;

    assert max >= min;
    if (partitions == 0) return Collections.EMPTY_LIST;
    long rangeSize = (long)max - (long)min;
    long rangeStep = Math.max(1, rangeSize / partitions);

    List<Range> ranges = new ArrayList<Range>(partitions);

    long start = min;
    long end = start;

    while (end < max) {
      end = start + rangeStep;
      // make last range always end exactly on MAX_VALUE
      if (ranges.size() == partitions - 1) {
        end = max;
      }
      ranges.add(new Range((int)start, (int)end));
      start = end + 1L;
    }

    return ranges;
  }

  /** Returns the Slice that the document should reside on, or null if there is not enough information */
  public abstract Slice getTargetSlice(String id, SolrInputDocument sdoc, SolrParams params, DocCollection collection);

  /** This method is consulted to determine what slices should be queried for a request when
   *  an explicit shards parameter was not used.
   *  This method only accepts a single shard key (or null).  If you have a comma separated list of shard keys,
   *  call getSearchSlices
   **/
  public abstract Collection<Slice> getSearchSlicesSingle(String shardKey, SolrParams params, DocCollection collection);

  public abstract boolean isTargetSlice(String id, SolrInputDocument sdoc, SolrParams params, String shardId, DocCollection collection);


  /** This method is consulted to determine what slices should be queried for a request when
   *  an explicit shards parameter was not used.
   *  This method accepts a multi-valued shardKeys parameter (normally comma separated from the shard.keys request parameter)
   *  and aggregates the slices returned by getSearchSlicesSingle for each shardKey.
   **/
  public Collection<Slice> getSearchSlices(String shardKeys, SolrParams params, DocCollection collection) {
    if (shardKeys == null || shardKeys.indexOf(',') < 0) {
      return getSearchSlicesSingle(shardKeys, params, collection);
    }

    List<String> shardKeyList = StrUtils.splitSmart(shardKeys, ",", true);
    HashSet<Slice> allSlices = new HashSet<Slice>();
    for (String shardKey : shardKeyList) {
      allSlices.addAll( getSearchSlicesSingle(shardKey, params, collection) );
    }
    return allSlices;
  }

}

