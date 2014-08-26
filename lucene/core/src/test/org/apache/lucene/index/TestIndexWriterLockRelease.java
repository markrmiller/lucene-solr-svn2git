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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.Version;

/**
 * This tests the patch for issue #LUCENE-715 (IndexWriter does not
 * release its write lock when trying to open an index which does not yet
 * exist).
 */
public class TestIndexWriterLockRelease extends LuceneTestCase {
  
  public void testIndexWriterLockRelease() throws IOException {
    Directory dir = newFSDirectory(createTempDir("testLockRelease"));
    try {
      new IndexWriter(dir, new IndexWriterConfig(Version.LATEST, new MockAnalyzer(random())).setOpenMode(OpenMode.APPEND));
    } catch (FileNotFoundException | NoSuchFileException e) {
      try {
        new IndexWriter(dir, new IndexWriterConfig(Version.LATEST, new MockAnalyzer(random())).setOpenMode(OpenMode.APPEND));
      } catch (FileNotFoundException | NoSuchFileException e1) {
      }
    } finally {
      dir.close();
    }
  }
}
