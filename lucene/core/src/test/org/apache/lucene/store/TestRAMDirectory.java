package org.apache.lucene.store;

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

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredDocument;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.English;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.TestUtil;

/**
 * JUnit testcase to test RAMDirectory. RAMDirectory itself is used in many testcases,
 * but not one of them uses an different constructor other than the default constructor.
 */
public class TestRAMDirectory extends BaseDirectoryTestCase {
  
  @Override
  protected Directory getDirectory(File path) {
    return new RAMDirectory();
  }
  
  // add enough document so that the index will be larger than RAMDirectory.READ_BUFFER_SIZE
  private final int docsToAdd = 500;

  private File buildIndex() throws IOException {
    File path = createTempDir("buildIndex");
    
    Directory dir = newFSDirectory(path);
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(
        new MockAnalyzer(random())).setOpenMode(OpenMode.CREATE));
    // add some documents
    Document doc = null;
    for (int i = 0; i < docsToAdd; i++) {
      doc = new Document();
      doc.add(newStringField("content", English.intToEnglish(i).trim(), Field.Store.YES));
      writer.addDocument(doc);
    }
    assertEquals(docsToAdd, writer.maxDoc());
    writer.close();
    dir.close();

    return path;
  }
  
  // LUCENE-1468
  public void testCopySubdir() throws Throwable {
    File path = createTempDir("testsubdir");
    Directory fsDir = null;
    try {
      path.mkdirs();
      new File(path, "subdir").mkdirs();
      fsDir = newFSDirectory(path);
      assertEquals(0, new RAMDirectory(fsDir, newIOContext(random())).listAll().length);
    } finally {
      IOUtils.close(fsDir);
      IOUtils.rm(path);
    }
  }

  public void testRAMDirectory () throws IOException {
    File indexDir = buildIndex();
    
    Directory dir = newFSDirectory(indexDir);
    MockDirectoryWrapper ramDir = new MockDirectoryWrapper(random(), new RAMDirectory(dir, newIOContext(random())));
    
    // close the underlaying directory
    dir.close();
    
    // Check size
    assertEquals(ramDir.sizeInBytes(), ramDir.getRecomputedSizeInBytes());
    
    // open reader to test document count
    IndexReader reader = DirectoryReader.open(ramDir);
    assertEquals(docsToAdd, reader.numDocs());
    
    // open search zo check if all doc's are there
    IndexSearcher searcher = newSearcher(reader);
    
    // search for all documents
    for (int i = 0; i < docsToAdd; i++) {
      StoredDocument doc = searcher.doc(i);
      assertTrue(doc.getField("content") != null);
    }

    // cleanup
    reader.close();
  }
  
  private final int numThreads = 10;
  private final int docsPerThread = 40;
  
  public void testRAMDirectorySize() throws IOException, InterruptedException {

    File indexDir = buildIndex();
      
    Directory dir = newFSDirectory(indexDir);
    final MockDirectoryWrapper ramDir = new MockDirectoryWrapper(random(), new RAMDirectory(dir, newIOContext(random())));
    dir.close();
    
    final IndexWriter writer = new IndexWriter(ramDir, new IndexWriterConfig(
        new MockAnalyzer(random())).setOpenMode(OpenMode.APPEND));
    writer.forceMerge(1);
    
    assertEquals(ramDir.sizeInBytes(), ramDir.getRecomputedSizeInBytes());
    
    Thread[] threads = new Thread[numThreads];
    for (int i=0; i<numThreads; i++) {
      final int num = i;
      threads[i] = new Thread(){
        @Override
        public void run() {
          for (int j=1; j<docsPerThread; j++) {
            Document doc = new Document();
            doc.add(newStringField("sizeContent", English.intToEnglish(num*docsPerThread+j).trim(), Field.Store.YES));
            try {
              writer.addDocument(doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      };
    }
    for (int i=0; i<numThreads; i++)
      threads[i].start();
    for (int i=0; i<numThreads; i++)
      threads[i].join();

    writer.forceMerge(1);
    assertEquals(ramDir.sizeInBytes(), ramDir.getRecomputedSizeInBytes());
    
    writer.close();
  }
}
