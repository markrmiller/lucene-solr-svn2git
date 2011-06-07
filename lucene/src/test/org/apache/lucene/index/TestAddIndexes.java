package org.apache.lucene.index;

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
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.store.RAMDirectory;

import org.apache.lucene.search.PhraseQuery;

public class TestAddIndexes extends LuceneTestCase {
  
  public void testSimpleCase() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // two auxiliary directories
    Directory aux = newDirectory();
    Directory aux2 = newDirectory();

    IndexWriter writer = null;

    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer(random))
        .setOpenMode(OpenMode.CREATE));
    // add 100 documents
    addDocs(writer, 100);
    assertEquals(100, writer.maxDoc());
    writer.close();

    writer = newWriter(
        aux,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.CREATE).
            setMergePolicy(newLogMergePolicy(false))
    );
    // add 40 documents in separate files
    addDocs(writer, 40);
    assertEquals(40, writer.maxDoc());
    writer.close();

    writer = newWriter(aux2, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE));
    // add 50 documents in compound files
    addDocs2(writer, 50);
    assertEquals(50, writer.maxDoc());
    writer.close();

    // test doc count before segments are merged
    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    assertEquals(100, writer.maxDoc());
    writer.addIndexes(new Directory[] { aux, aux2 });
    assertEquals(190, writer.maxDoc());
    writer.close();

    // make sure the old index is correct
    verifyNumDocs(aux, 40);

    // make sure the new index is correct
    verifyNumDocs(dir, 190);

    // now add another set in.
    Directory aux3 = newDirectory();
    writer = newWriter(aux3, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    // add 40 documents
    addDocs(writer, 40);
    assertEquals(40, writer.maxDoc());
    writer.close();

    // test doc count before segments are merged/index is optimized
    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    assertEquals(190, writer.maxDoc());
    writer.addIndexes(new Directory[] { aux3 });
    assertEquals(230, writer.maxDoc());
    writer.close();

    // make sure the new index is correct
    verifyNumDocs(dir, 230);

    verifyTermDocs(dir, new Term("content", "aaa"), 180);

    verifyTermDocs(dir, new Term("content", "bbb"), 50);

    // now optimize it.
    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    writer.optimize();
    writer.close();

    // make sure the new index is correct
    verifyNumDocs(dir, 230);

    verifyTermDocs(dir, new Term("content", "aaa"), 180);

    verifyTermDocs(dir, new Term("content", "bbb"), 50);

    // now add a single document
    Directory aux4 = newDirectory();
    writer = newWriter(aux4, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    addDocs2(writer, 1);
    writer.close();

    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    assertEquals(230, writer.maxDoc());
    writer.addIndexes(new Directory[] { aux4 });
    assertEquals(231, writer.maxDoc());
    writer.close();

    verifyNumDocs(dir, 231);

    verifyTermDocs(dir, new Term("content", "bbb"), 51);
    dir.close();
    aux.close();
    aux2.close();
    aux3.close();
    aux4.close();
  }

  public void testWithPendingDeletes() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);
    IndexWriter writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    writer.setInfoStream(VERBOSE ? System.out : null);
    writer.setInfoStream(VERBOSE ? System.out : null);
    writer.addIndexes(aux);

    // Adds 10 docs, then replaces them with another 10
    // docs, so 10 pending deletes:
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(newField("id", "" + (i % 10), Field.Store.NO, Field.Index.NOT_ANALYZED));
      doc.add(newField("content", "bbb " + i, Field.Store.NO,
                        Field.Index.ANALYZED));
      writer.updateDocument(new Term("id", "" + (i%10)), doc);
    }
    // Deletes one of the 10 added docs, leaving 9:
    PhraseQuery q = new PhraseQuery();
    q.add(new Term("content", "bbb"));
    q.add(new Term("content", "14"));
    writer.deleteDocuments(q);

    writer.optimize();
    writer.commit();

    verifyNumDocs(dir, 1039);
    verifyTermDocs(dir, new Term("content", "aaa"), 1030);
    verifyTermDocs(dir, new Term("content", "bbb"), 9);

    writer.close();
    dir.close();
    aux.close();
  }

  public void testWithPendingDeletes2() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);
    IndexWriter writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    // Adds 10 docs, then replaces them with another 10
    // docs, so 10 pending deletes:
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(newField("id", "" + (i % 10), Field.Store.NO, Field.Index.NOT_ANALYZED));
      doc.add(newField("content", "bbb " + i, Field.Store.NO, Field.Index.ANALYZED));
      writer.updateDocument(new Term("id", "" + (i%10)), doc);
    }
    
    writer.addIndexes(new Directory[] {aux});
    
    // Deletes one of the 10 added docs, leaving 9:
    PhraseQuery q = new PhraseQuery();
    q.add(new Term("content", "bbb"));
    q.add(new Term("content", "14"));
    writer.deleteDocuments(q);

    writer.optimize();
    writer.commit();

    verifyNumDocs(dir, 1039);
    verifyTermDocs(dir, new Term("content", "aaa"), 1030);
    verifyTermDocs(dir, new Term("content", "bbb"), 9);

    writer.close();
    dir.close();
    aux.close();
  }

  public void testWithPendingDeletes3() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);
    IndexWriter writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));

    // Adds 10 docs, then replaces them with another 10
    // docs, so 10 pending deletes:
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(newField("id", "" + (i % 10), Field.Store.NO, Field.Index.NOT_ANALYZED));
      doc.add(newField("content", "bbb " + i, Field.Store.NO,
                        Field.Index.ANALYZED));
      writer.updateDocument(new Term("id", "" + (i%10)), doc);
    }

    // Deletes one of the 10 added docs, leaving 9:
    PhraseQuery q = new PhraseQuery();
    q.add(new Term("content", "bbb"));
    q.add(new Term("content", "14"));
    writer.deleteDocuments(q);

    writer.addIndexes(new Directory[] {aux});

    writer.optimize();
    writer.commit();

    verifyNumDocs(dir, 1039);
    verifyTermDocs(dir, new Term("content", "aaa"), 1030);
    verifyTermDocs(dir, new Term("content", "bbb"), 9);

    writer.close();
    dir.close();
    aux.close();
  }

  // case 0: add self or exceed maxMergeDocs, expect exception
  public void testAddSelf() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    IndexWriter writer = null;

    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
    // add 100 documents
    addDocs(writer, 100);
    assertEquals(100, writer.maxDoc());
    writer.close();

    writer = newWriter(
        aux,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.CREATE).
            setMaxBufferedDocs(1000).
            setMergePolicy(newLogMergePolicy(false))
    );
    // add 140 documents in separate files
    addDocs(writer, 40);
    writer.close();
    writer = newWriter(
        aux,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.CREATE).
            setMaxBufferedDocs(1000).
            setMergePolicy(newLogMergePolicy(false))
    );
    addDocs(writer, 100);
    writer.close();

    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.APPEND));
    try {
      // cannot add self
      writer.addIndexes(new Directory[] { aux, dir });
      assertTrue(false);
    }
    catch (IllegalArgumentException e) {
      assertEquals(100, writer.maxDoc());
    }
    writer.close();

    // make sure the index is correct
    verifyNumDocs(dir, 100);
    dir.close();
    aux.close();
  }

  // in all the remaining tests, make the doc count of the oldest segment
  // in dir large so that it is never merged in addIndexes()
  // case 1: no tail segments
  public void testNoTailSegments() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);

    IndexWriter writer = newWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.APPEND).
            setMaxBufferedDocs(10).
            setMergePolicy(newLogMergePolicy(4))
    );
    addDocs(writer, 10);

    writer.addIndexes(new Directory[] { aux });
    assertEquals(1040, writer.maxDoc());
    assertEquals(1000, writer.getDocCount(0));
    writer.close();

    // make sure the index is correct
    verifyNumDocs(dir, 1040);
    dir.close();
    aux.close();
  }

  // case 2: tail segments, invariants hold, no copy
  public void testNoCopySegments() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);

    IndexWriter writer = newWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.APPEND).
            setMaxBufferedDocs(9).
            setMergePolicy(newLogMergePolicy(4))
    );
    addDocs(writer, 2);

    writer.addIndexes(new Directory[] { aux });
    assertEquals(1032, writer.maxDoc());
    assertEquals(1000, writer.getDocCount(0));
    writer.close();

    // make sure the index is correct
    verifyNumDocs(dir, 1032);
    dir.close();
    aux.close();
  }

  // case 3: tail segments, invariants hold, copy, invariants hold
  public void testNoMergeAfterCopy() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);

    IndexWriter writer = newWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.APPEND).
            setMaxBufferedDocs(10).
            setMergePolicy(newLogMergePolicy(4))
    );

    writer.addIndexes(new Directory[] { aux, new MockDirectoryWrapper(random, new RAMDirectory(aux)) });
    assertEquals(1060, writer.maxDoc());
    assertEquals(1000, writer.getDocCount(0));
    writer.close();

    // make sure the index is correct
    verifyNumDocs(dir, 1060);
    dir.close();
    aux.close();
  }

  // case 4: tail segments, invariants hold, copy, invariants not hold
  public void testMergeAfterCopy() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();

    setUpDirs(dir, aux);

    IndexReader reader = IndexReader.open(aux, false);
    for (int i = 0; i < 20; i++) {
      reader.deleteDocument(i);
    }
    assertEquals(10, reader.numDocs());
    reader.close();

    IndexWriter writer = newWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.APPEND).
            setMaxBufferedDocs(4).
            setMergePolicy(newLogMergePolicy(4))
    );

    writer.addIndexes(new Directory[] { aux, new MockDirectoryWrapper(random, new RAMDirectory(aux)) });
    assertEquals(1020, writer.maxDoc());
    assertEquals(1000, writer.getDocCount(0));
    writer.close();
    dir.close();
    aux.close();
  }

  // case 5: tail segments, invariants not hold
  public void testMoreMerges() throws IOException {
    // main directory
    Directory dir = newDirectory();
    // auxiliary directory
    Directory aux = newDirectory();
    Directory aux2 = newDirectory();

    setUpDirs(dir, aux);

    IndexWriter writer = newWriter(
        aux2,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.CREATE).
            setMaxBufferedDocs(100).
            setMergePolicy(newLogMergePolicy(10))
    );
    writer.setInfoStream(VERBOSE ? System.out : null);
    writer.addIndexes(aux);
    assertEquals(30, writer.maxDoc());
    writer.close();

    IndexReader reader = IndexReader.open(aux, false);
    for (int i = 0; i < 27; i++) {
      reader.deleteDocument(i);
    }
    assertEquals(3, reader.numDocs());
    reader.close();

    reader = IndexReader.open(aux2, false);
    for (int i = 0; i < 8; i++) {
      reader.deleteDocument(i);
    }
    assertEquals(22, reader.numDocs());
    reader.close();

    writer = newWriter(
        dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.APPEND).
            setMaxBufferedDocs(6).
            setMergePolicy(newLogMergePolicy(4))
    );

    writer.addIndexes(new Directory[] { aux, aux2 });
    assertEquals(1040, writer.maxDoc());
    assertEquals(1000, writer.getDocCount(0));
    writer.close();
    dir.close();
    aux.close();
    aux2.close();
  }

  private IndexWriter newWriter(Directory dir, IndexWriterConfig conf)
      throws IOException {
    conf.setMergePolicy(new LogDocMergePolicy());
    final IndexWriter writer = new IndexWriter(dir, conf);
    return writer;
  }

  private void addDocs(IndexWriter writer, int numDocs) throws IOException {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(newField("content", "aaa", Field.Store.NO,
                        Field.Index.ANALYZED));
      writer.addDocument(doc);
    }
  }

  private void addDocs2(IndexWriter writer, int numDocs) throws IOException {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(newField("content", "bbb", Field.Store.NO,
                        Field.Index.ANALYZED));
      writer.addDocument(doc);
    }
  }

  private void verifyNumDocs(Directory dir, int numDocs) throws IOException {
    IndexReader reader = IndexReader.open(dir, true);
    assertEquals(numDocs, reader.maxDoc());
    assertEquals(numDocs, reader.numDocs());
    reader.close();
  }

  private void verifyTermDocs(Directory dir, Term term, int numDocs)
      throws IOException {
    IndexReader reader = IndexReader.open(dir, true);
    TermDocs termDocs = reader.termDocs(term);
    int count = 0;
    while (termDocs.next())
      count++;
    assertEquals(numDocs, count);
    reader.close();
  }

  private void setUpDirs(Directory dir, Directory aux) throws IOException {
    IndexWriter writer = null;

    writer = newWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setOpenMode(OpenMode.CREATE).setMaxBufferedDocs(1000));
    // add 1000 documents in 1 segment
    addDocs(writer, 1000);
    assertEquals(1000, writer.maxDoc());
    assertEquals(1, writer.getSegmentCount());
    writer.close();

    writer = newWriter(
        aux,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
            setOpenMode(OpenMode.CREATE).
            setMaxBufferedDocs(1000).
            setMergePolicy(newLogMergePolicy(false, 10))
    );
    // add 30 documents in 3 segments
    for (int i = 0; i < 3; i++) {
      addDocs(writer, 10);
      writer.close();
      writer = newWriter(
          aux,
          newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).
              setOpenMode(OpenMode.APPEND).
              setMaxBufferedDocs(1000).
              setMergePolicy(newLogMergePolicy(false, 10))
      );
    }
    assertEquals(30, writer.maxDoc());
    assertEquals(3, writer.getSegmentCount());
    writer.close();
  }

  // LUCENE-1270
  public void testHangOnClose() throws IOException {

    Directory dir = newDirectory();
    LogByteSizeMergePolicy lmp = new LogByteSizeMergePolicy();
    lmp.setUseCompoundFile(false);
    lmp.setMergeFactor(100);
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(
        TEST_VERSION_CURRENT, new MockAnalyzer(random))
        .setMaxBufferedDocs(5).setMergePolicy(lmp));

    Document doc = new Document();
    doc.add(newField("content", "aaa bbb ccc ddd eee fff ggg hhh iii", Field.Store.YES,
                      Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
    for(int i=0;i<60;i++)
      writer.addDocument(doc);

    Document doc2 = new Document();
    doc2.add(newField("content", "aaa bbb ccc ddd eee fff ggg hhh iii", Field.Store.YES,
                      Field.Index.NO));
    doc2.add(newField("content", "aaa bbb ccc ddd eee fff ggg hhh iii", Field.Store.YES,
                      Field.Index.NO));
    doc2.add(newField("content", "aaa bbb ccc ddd eee fff ggg hhh iii", Field.Store.YES,
                      Field.Index.NO));
    doc2.add(newField("content", "aaa bbb ccc ddd eee fff ggg hhh iii", Field.Store.YES,
                      Field.Index.NO));
    for(int i=0;i<10;i++)
      writer.addDocument(doc2);
    writer.close();

    Directory dir2 = newDirectory();
    lmp = new LogByteSizeMergePolicy();
    lmp.setMinMergeMB(0.0001);
    lmp.setUseCompoundFile(false);
    lmp.setMergeFactor(4);
    writer = new IndexWriter(dir2, newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer(random))
        .setMergeScheduler(new SerialMergeScheduler()).setMergePolicy(lmp));
    writer.addIndexes(new Directory[] {dir});
    writer.close();
    dir.close();
    dir2.close();
  }

  // TODO: these are also in TestIndexWriter... add a simple doc-writing method
  // like this to LuceneTestCase?
  private void addDoc(IndexWriter writer) throws IOException
  {
      Document doc = new Document();
      doc.add(newField("content", "aaa", Field.Store.NO, Field.Index.ANALYZED));
      writer.addDocument(doc);
  }
  
  private abstract class RunAddIndexesThreads {

    Directory dir, dir2;
    final static int NUM_INIT_DOCS = 17;
    IndexWriter writer2;
    final List<Throwable> failures = new ArrayList<Throwable>();
    volatile boolean didClose;
    final IndexReader[] readers;
    final int NUM_COPY;
    final static int NUM_THREADS = 5;
    final Thread[] threads = new Thread[NUM_THREADS];

    public RunAddIndexesThreads(int numCopy) throws Throwable {
      NUM_COPY = numCopy;
      dir = new MockDirectoryWrapper(random, new RAMDirectory());
      IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(
          TEST_VERSION_CURRENT, new MockAnalyzer(random))
          .setMaxBufferedDocs(2));
      for (int i = 0; i < NUM_INIT_DOCS; i++)
        addDoc(writer);
      writer.close();

      dir2 = newDirectory();
      writer2 = new IndexWriter(dir2, new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      writer2.setInfoStream(VERBOSE ? System.out : null);
      writer2.commit();
      

      readers = new IndexReader[NUM_COPY];
      for(int i=0;i<NUM_COPY;i++)
        readers[i] = IndexReader.open(dir, true);
    }

    void launchThreads(final int numIter) {

      for(int i=0;i<NUM_THREADS;i++) {
        threads[i] = new Thread() {
            @Override
            public void run() {
              try {

                final Directory[] dirs = new Directory[NUM_COPY];
                for(int k=0;k<NUM_COPY;k++)
                  dirs[k] = new MockDirectoryWrapper(random, new RAMDirectory(dir));

                int j=0;

                while(true) {
                  // System.out.println(Thread.currentThread().getName() + ": iter j=" + j);
                  if (numIter > 0 && j == numIter)
                    break;
                  doBody(j++, dirs);
                }
              } catch (Throwable t) {
                handle(t);
              }
            }
          };
      }

      for(int i=0;i<NUM_THREADS;i++)
        threads[i].start();
    }

    void joinThreads() throws Exception {
      for(int i=0;i<NUM_THREADS;i++)
        threads[i].join();
    }

    void close(boolean doWait) throws Throwable {
      didClose = true;
      writer2.close(doWait);
    }

    void closeDir() throws Throwable {
      for(int i=0;i<NUM_COPY;i++)
        readers[i].close();
      dir2.close();
    }

    abstract void doBody(int j, Directory[] dirs) throws Throwable;
    abstract void handle(Throwable t);
  }

  private class CommitAndAddIndexes extends RunAddIndexesThreads {
    public CommitAndAddIndexes(int numCopy) throws Throwable {
      super(numCopy);
    }

    @Override
    void handle(Throwable t) {
      t.printStackTrace(System.out);
      synchronized(failures) {
        failures.add(t);
      }
    }

    @Override
    void doBody(int j, Directory[] dirs) throws Throwable {
      switch(j%5) {
      case 0:
        if (VERBOSE) {
          System.out.println(Thread.currentThread().getName() + ": TEST: addIndexes(Dir[]) then optimize");
        }
        writer2.addIndexes(dirs);
        writer2.optimize();
        break;
      case 1:
        if (VERBOSE) {
          System.out.println(Thread.currentThread().getName() + ": TEST: addIndexes(Dir[])");
        }
        writer2.addIndexes(dirs);
        break;
      case 2:
        if (VERBOSE) {
          System.out.println(Thread.currentThread().getName() + ": TEST: addIndexes(IndexReader[])");
        }
        writer2.addIndexes(readers);
        break;
      case 3:
        if (VERBOSE) {
          System.out.println(Thread.currentThread().getName() + ": TEST: addIndexes(Dir[]) then maybeMerge");
        }
        writer2.addIndexes(dirs);
        writer2.maybeMerge();
        break;
      case 4:
        if (VERBOSE) {
          System.out.println(Thread.currentThread().getName() + ": TEST: commit");
        }
        writer2.commit();
      }
    }
  }
  
  // LUCENE-1335: test simultaneous addIndexes & commits
  // from multiple threads
  public void testAddIndexesWithThreads() throws Throwable {

    final int NUM_ITER = TEST_NIGHTLY ? 15 : 5;
    final int NUM_COPY = 3;
    CommitAndAddIndexes c = new CommitAndAddIndexes(NUM_COPY);
    c.writer2.setInfoStream(VERBOSE ? System.out : null);
    c.launchThreads(NUM_ITER);

    for(int i=0;i<100;i++)
      addDoc(c.writer2);

    c.joinThreads();

    int expectedNumDocs = 100+NUM_COPY*(4*NUM_ITER/5)*RunAddIndexesThreads.NUM_THREADS*RunAddIndexesThreads.NUM_INIT_DOCS;
    assertEquals(expectedNumDocs, c.writer2.numDocs());

    c.close(true);

    assertTrue(c.failures.size() == 0);

    IndexReader reader = IndexReader.open(c.dir2, true);
    assertEquals(expectedNumDocs, reader.numDocs());
    reader.close();

    c.closeDir();
  }

  private class CommitAndAddIndexes2 extends CommitAndAddIndexes {
    public CommitAndAddIndexes2(int numCopy) throws Throwable {
      super(numCopy);
    }

    @Override
    void handle(Throwable t) {
      if (!(t instanceof AlreadyClosedException) && !(t instanceof NullPointerException)) {
        t.printStackTrace(System.out);
        synchronized(failures) {
          failures.add(t);
        }
      }
    }
  }

  // LUCENE-1335: test simultaneous addIndexes & close
  public void testAddIndexesWithClose() throws Throwable {
    final int NUM_COPY = 3;
    CommitAndAddIndexes2 c = new CommitAndAddIndexes2(NUM_COPY);
    //c.writer2.setInfoStream(System.out);
    c.launchThreads(-1);

    // Close w/o first stopping/joining the threads
    c.close(true);
    //c.writer2.close();

    c.joinThreads();

    c.closeDir();

    assertTrue(c.failures.size() == 0);
  }

  private class CommitAndAddIndexes3 extends RunAddIndexesThreads {
    public CommitAndAddIndexes3(int numCopy) throws Throwable {
      super(numCopy);
    }

    @Override
    void doBody(int j, Directory[] dirs) throws Throwable {
      switch(j%5) {
      case 0:
        if (VERBOSE) {
          System.out.println("TEST: " + Thread.currentThread().getName() + ": addIndexes + optimize");
        }
        writer2.addIndexes(dirs);
        writer2.optimize();
        break;
      case 1:
        if (VERBOSE) {
          System.out.println("TEST: " + Thread.currentThread().getName() + ": addIndexes");
        }
        writer2.addIndexes(dirs);
        break;
      case 2:
        if (VERBOSE) {
          System.out.println("TEST: " + Thread.currentThread().getName() + ": addIndexes(IR[])");
        }
        writer2.addIndexes(readers);
        break;
      case 3:
        if (VERBOSE) {
          System.out.println("TEST: " + Thread.currentThread().getName() + ": optimize");
        }
        writer2.optimize();
        break;
      case 4:
        if (VERBOSE) {
          System.out.println("TEST: " + Thread.currentThread().getName() + ": commit");
        }
        writer2.commit();
      }
    }

    @Override
    void handle(Throwable t) {
      boolean report = true;

      if (t instanceof AlreadyClosedException || t instanceof MergePolicy.MergeAbortedException || t instanceof NullPointerException) {
        report = !didClose;
      } else if (t instanceof IOException)  {
        Throwable t2 = t.getCause();
        if (t2 instanceof MergePolicy.MergeAbortedException) {
          report = !didClose;
        }
      }
      if (report) {
        t.printStackTrace(System.out);
        synchronized(failures) {
          failures.add(t);
        }
      }
    }
  }

  // LUCENE-1335: test simultaneous addIndexes & close
  public void testAddIndexesWithCloseNoWait() throws Throwable {

    final int NUM_COPY = 50;
    CommitAndAddIndexes3 c = new CommitAndAddIndexes3(NUM_COPY);
    if (VERBOSE) {
      c.writer2.setInfoStream(System.out);
    }
    c.launchThreads(-1);

    Thread.sleep(_TestUtil.nextInt(random, 10, 500));

    // Close w/o first stopping/joining the threads
    if (VERBOSE) {
      System.out.println("TEST: now close(false)");
    }
    c.close(false);

    c.joinThreads();

    if (VERBOSE) {
      System.out.println("TEST: done join threads");
    }
    c.closeDir();

    assertTrue(c.failures.size() == 0);
  }

  // LUCENE-1335: test simultaneous addIndexes & close
  public void testAddIndexesWithRollback() throws Throwable {

    final int NUM_COPY = TEST_NIGHTLY ? 50 : 5;
    CommitAndAddIndexes3 c = new CommitAndAddIndexes3(NUM_COPY);
    c.launchThreads(-1);

    Thread.sleep(_TestUtil.nextInt(random, 10, 500));

    // Close w/o first stopping/joining the threads
    if (VERBOSE) {
      System.out.println("TEST: now force rollback");
    }
    c.didClose = true;
    c.writer2.rollback();

    c.joinThreads();

    c.closeDir();

    assertTrue(c.failures.size() == 0);
  }

  // LUCENE-2790: tests that the non CFS files were deleted by addIndexes
  public void testNonCFSLeftovers() throws Exception {
    Directory[] dirs = new Directory[2];
    for (int i = 0; i < dirs.length; i++) {
      dirs[i] = new RAMDirectory();
      IndexWriter w = new IndexWriter(dirs[i], new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)));
      Document d = new Document();
      d.add(new Field("c", "v", Store.YES, Index.ANALYZED, TermVector.YES));
      w.addDocument(d);
      w.close();
    }
    
    IndexReader[] readers = new IndexReader[] { IndexReader.open(dirs[0]), IndexReader.open(dirs[1]) };
    
    Directory dir = new RAMDirectory();
    IndexWriterConfig conf = new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMergePolicy(newLogMergePolicy());
    LogMergePolicy lmp = (LogMergePolicy) conf.getMergePolicy();
    lmp.setNoCFSRatio(1.0); // Force creation of CFS
    lmp.setUseCompoundFile(true);
    IndexWriter w3 = new IndexWriter(dir, conf);
    w3.addIndexes(readers);
    w3.close();
    
    assertEquals("Only one compound segment should exist", 3, dir.listAll().length);
  }
 
  // LUCENE-2996: tests that addIndexes(IndexReader) applies existing deletes correctly.
  public void testExistingDeletes() throws Exception {
    Directory[] dirs = new Directory[2];
    for (int i = 0; i < dirs.length; i++) {
      dirs[i] = newDirectory();
      IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random));
      IndexWriter writer = new IndexWriter(dirs[i], conf);
      Document doc = new Document();
      doc.add(new Field("id", "myid", Store.NO, Index.NOT_ANALYZED_NO_NORMS));
      writer.addDocument(doc);
      writer.close();
    }

    IndexWriterConfig conf = new IndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random));
    IndexWriter writer = new IndexWriter(dirs[0], conf);

    // Now delete the document
    writer.deleteDocuments(new Term("id", "myid"));
    IndexReader r = IndexReader.open(dirs[1]);
    try {
      writer.addIndexes(r);
    } finally {
      r.close();
    }
    writer.commit();
    assertEquals("Documents from the incoming index should not have been deleted", 1, writer.numDocs());
    writer.close();

    for (Directory dir : dirs) {
      dir.close();
    }

  }
  
  // LUCENE-3126: tests that if a non-CFS segment is copied, it is converted to
  // a CFS, given MP preferences
  public void testCopyIntoCFS() throws Exception {
    // create an index, no CFS (so we can assert that existing segments are not affected)
    Directory target = newDirectory();
    LogMergePolicy lmp = newLogMergePolicy(false);
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, null).setMergePolicy(lmp);
    IndexWriter w = new IndexWriter(target, conf);
    w.addDocument(new Document());
    w.commit();
    assertFalse(w.segmentInfos.info(0).getUseCompoundFile());

    // prepare second index, no-CFS too + .del file + separate norms file
    Directory src = newDirectory();
    LogMergePolicy lmp2 = newLogMergePolicy(false);
    IndexWriterConfig conf2 = newIndexWriterConfig(TEST_VERSION_CURRENT,
        new MockAnalyzer(random)).setMergePolicy(lmp2);
    IndexWriter w2 = new IndexWriter(src, conf2);
    Document doc = new Document();
    doc.add(new Field("c", "some text", Store.YES, Index.ANALYZED));
    w2.addDocument(doc);
    doc = new Document();
    doc.add(new Field("d", "delete", Store.NO, Index.NOT_ANALYZED_NO_NORMS));
    w2.addDocument(doc);
    w2.commit();
    w2.deleteDocuments(new Term("d", "delete"));
    w2.commit();
    w2.close();

    // create separate norms file
    IndexReader r = IndexReader.open(src, false);
    r.setNorm(0, "c", (byte) 1);
    r.close();
    assertTrue(".del file not found", src.fileExists("_0_1.del"));
    assertTrue("separate norms file not found", src.fileExists("_0_1.s0"));
    
    // Case 1: force 'CFS' on target
    lmp.setUseCompoundFile(true);
    lmp.setNoCFSRatio(1.0);
    w.addIndexes(src);
    w.commit();
    assertFalse("existing segments should not be modified by addIndexes", w.segmentInfos.info(0).getUseCompoundFile());
    assertTrue("segment should have been converted to a CFS by addIndexes", w.segmentInfos.info(1).getUseCompoundFile());
    assertTrue(".del file not found", target.fileExists("_1_1.del"));
    assertTrue("separate norms file not found", target.fileExists("_1_1.s0"));

    // Case 2: LMP disallows CFS
    lmp.setUseCompoundFile(false);
    w.addIndexes(src);
    w.commit();
    assertFalse("segment should not have been converted to a CFS by addIndexes if MP disallows", w.segmentInfos.info(2).getUseCompoundFile());

    w.close();
    
    // cleanup
    src.close();
    target.close();
  }

}
