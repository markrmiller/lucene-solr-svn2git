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

package org.apache.solr.handler.component;

import org.apache.solr.util.PivotListEntry;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

public class PivotFacetHelper {

  /**
   * Encodes a value path as a string for the purposes of a refinement request
   *
   * @see PivotFacetValue#getValuePath
   * @see #decodeRefinementValuePath
   */
  public static String encodeRefinementValuePath(List<String> values) {
    // HACK: prefix flag every value to account for empty string vs null
    // NOTE: even if we didn't have to worry about null's smartSplit is stupid about
    // pruning empty strings from list
    // "^" prefix = null
    // "~" prefix = not null, may be empty string

    assert null != values;

    // special case: empty list => empty string
    if (values.isEmpty()) { return ""; }

    
    StringBuilder out = new StringBuilder();
    for (String val : values) {
      if (null == val) {
        out.append('^');
      } else {
        out.append('~');
        StrUtils.appendEscapedTextToBuilder(out, val, ',');
      }
      out.append(',');
    }
    out.deleteCharAt(out.length()-1);  // prune the last seperator
    return out.toString();
    // return StrUtils.join(values, ',');
  }

  /**
   * Decodes a value path string specified for refinement.
   *
   * @see #encodeRefinementValuePath
   */
  public static List<String> decodeRefinementValuePath(String valuePath) {
    List <String> rawvals = StrUtils.splitSmart(valuePath, ",", true);
    // special case: empty list => empty string
    if (rawvals.isEmpty()) return rawvals;

    List<String> out = new ArrayList<String>(rawvals.size());
    for (String raw : rawvals) {
      assert 0 < raw.length();
      if ('^' == raw.charAt(0)) {
        assert 1 == raw.length();
        out.add(null);
      } else {
        assert '~' == raw.charAt(0);
        out.add(raw.substring(1));
      }
    }

    return out;
  }

  /** @see PivotListEntry#VALUE */
  public static Comparable getValue(NamedList<Object> pivotList) {
    return (Comparable) PivotListEntry.VALUE.extract(pivotList);
  }

  /** @see PivotListEntry#FIELD */
  public static String getField(NamedList<Object> pivotList) {
    return (String) PivotListEntry.FIELD.extract(pivotList);
  }
  
  /** @see PivotListEntry#COUNT */
  public static Integer getCount(NamedList<Object> pivotList) {
    return (Integer) PivotListEntry.COUNT.extract(pivotList);
  }

  /** @see PivotListEntry#PIVOT */
  public static List<NamedList<Object>> getPivots(NamedList<Object> pivotList) {
    return (List<NamedList<Object>>) PivotListEntry.PIVOT.extract(pivotList);
  }
  
  /** @see PivotListEntry#STATS */
  public static NamedList<NamedList<NamedList<?>>> getStats(NamedList<Object> pivotList) {
    return (NamedList<NamedList<NamedList<?>>>) PivotListEntry.STATS.extract(pivotList);
  }

  /**
   * Given a mapping of keys to {@link StatsValues} representing the currently 
   * known "merged" stats (which may be null if none exist yet), and a 
   * {@link NamedList} containing the "stats" response block returned by an individual 
   * shard, this method accumulates the stasts for each {@link StatsField} found in 
   * the shard response with the existing mergeStats
   *
   * @return the original <code>merged</code> Map after modifying, or a new Map if the <code>merged</code> param was originally null.
   * @see StatsInfo#getStatsField
   * @see StatsValuesFactory#createStatsValues
   * @see StatsValues#accumulate(NamedList)
   */
  public static Map<String,StatsValues> mergeStats
    (Map<String,StatsValues> merged, 
     NamedList<NamedList<NamedList<?>>> remoteWrapper, 
     StatsInfo statsInfo) {

    if (null == merged) merged = new LinkedHashMap<String,StatsValues>();

    NamedList<NamedList<?>> remoteStats = StatsComponent.unwrapStats(remoteWrapper);

    for (Entry<String,NamedList<?>> entry : remoteStats) {
      StatsValues receivingStatsValues = merged.get(entry.getKey());
      if (receivingStatsValues == null) {
        StatsField recievingStatsField = statsInfo.getStatsField(entry.getKey());
        if (null == recievingStatsField) {
          throw new SolrException(ErrorCode.SERVER_ERROR , "No stats.field found corrisponding to pivot stats recieved from shard: "+entry.getKey());
        }
        receivingStatsValues = StatsValuesFactory.createStatsValues(recievingStatsField);
        merged.put(entry.getKey(), receivingStatsValues);
      }
      receivingStatsValues.accumulate(entry.getValue());
    }
    return merged;
  }

}
