package org.apache.lucene.codecs;

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
import java.util.Set;

import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

/**
 * Controls the format of term vectors
 */
public abstract class TermVectorsFormat {
  /** Returns a {@link TermVectorsReader} to read term
   *  vectors. */
  public abstract TermVectorsReader vectorsReader(Directory directory, SegmentInfo segmentInfo, FieldInfos fieldInfos, IOContext context) throws IOException;

  /** Returns a {@link TermVectorsWriter} to write term
   *  vectors. */
  public abstract TermVectorsWriter vectorsWriter(Directory directory, String segment, IOContext context) throws IOException;

  /** Gathers files (exact file name or a Pattern regex)
   *  associated with this segment. */
  public abstract void files(SegmentInfo info, Set<String> files) throws IOException;
}
