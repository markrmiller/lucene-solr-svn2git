package org.apache.lucene.index;

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
import java.util.Map;


/**
 * A {@link MergePolicy} which never returns merges to execute. Use it if you
 * want to prevent segment merges.
 */
public final class NoMergePolicy extends MergePolicy {

  /** Singleton instance. */
  public static final MergePolicy INSTANCE = new NoMergePolicy();

  private NoMergePolicy() {
    super();
  }

  @Override
  public void close() {}

  @Override
  public MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos segmentInfos) { return null; }

  @Override
  public MergeSpecification findForcedMerges(SegmentInfos segmentInfos,
             int maxSegmentCount, Map<SegmentCommitInfo,Boolean> segmentsToMerge) { return null; }

  @Override
  public MergeSpecification findForcedDeletesMerges(SegmentInfos segmentInfos) { return null; }

  @Override
  public boolean useCompoundFile(SegmentInfos segments, SegmentCommitInfo newSegment) {
    return newSegment.info.getUseCompoundFile();
  }

  @Override
  public void setIndexWriter(IndexWriter writer) {}
  
  @Override
  protected long size(SegmentCommitInfo info) throws IOException {
    return Long.MAX_VALUE;
  }

  @Override
  public String toString() {
    return "NoMergePolicy";
  }
}
