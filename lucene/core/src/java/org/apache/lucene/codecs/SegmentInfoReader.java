package org.apache.lucene.codecs;

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
import java.util.Set;

import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

/**
 * Specifies an API for classes that can read {@link SegmentInfo} information.
 * @lucene.experimental
 */

public abstract class SegmentInfoReader {

  /** Sole constructor. (For invocation by subclass 
   *  constructors, typically implicit.) */
  protected SegmentInfoReader() {
  }

  /**
   * Read {@link SegmentInfo} data from a directory.
   * @param directory directory to read from
   * @param segmentName name of the segment to read
   * @param context IO context to use
   * @return infos instance to be populated with data
   * @throws IOException If an I/O error occurs
   */
  public abstract SegmentInfo read(Directory directory, String segmentName, IOContext context) throws IOException;
  
  /**
   * Add files of update segments to the segment info.
   * @param info The segment info to update
   * @param dir The containing directory
   * @param segmentName The name of the handled segment
   * @param context The IOContext
   * @throws IOException If error occurred when reading files lists
   */
  protected void addUpdateSegmentsFiles(final SegmentInfo info, Directory dir,
      String segmentName, IOContext context)
      throws IOException {
    int generation = 1;
    while (generation > 0) {
      Set<String> files = readFilesList(dir, segmentName, generation, context);
      if (files == null) {
        generation = -1;
      } else {
        info.addFiles(files);
        generation++;
      }
    }
  }

  /**
   * Read list of files related to a certain generation in an updated segment
   * 
   * @param dir
   *          The containing directory
   * @param segmentName
   *          The name of the handled segment
   * @param generation
   *          The update generation
   * @param context
   *          The IOContext
   * @return A list of the files corresponding to the update generation.
   * @throws IOException
   *           If error occurred when reading files list
   */
  protected abstract Set<String> readFilesList(Directory dir,
      String segmentName, long generation, IOContext context)
      throws IOException;

}
