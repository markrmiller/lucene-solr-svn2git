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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseTokenizer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;

public class TestIndexWriterExceptions extends LuceneTestCase {

  private class IndexerThread extends Thread {

    IndexWriter writer;

    final Random r = new java.util.Random(47);
    Throwable failure;

    public IndexerThread(int i, IndexWriter writer) {
      setName("Indexer " + i);
      this.writer = writer;
    }

    @Override
    public void run() {

      final Document doc = new Document();

      doc.add(newField("content1", "aaa bbb ccc ddd", Field.Store.YES, Field.Index.ANALYZED));
      doc.add(newField("content6", "aaa bbb ccc ddd", Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      doc.add(newField("content2", "aaa bbb ccc ddd", Field.Store.YES, Field.Index.NOT_ANALYZED));
      doc.add(newField("content3", "aaa bbb ccc ddd", Field.Store.YES, Field.Index.NO));

      doc.add(newField("content4", "aaa bbb ccc ddd", Field.Store.NO, Field.Index.ANALYZED));
      doc.add(newField("content5", "aaa bbb ccc ddd", Field.Store.NO, Field.Index.NOT_ANALYZED));

      doc.add(newField("content7", "aaa bbb ccc ddd", Field.Store.NO, Field.Index.NOT_ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));

      final Field idField = newField("id", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
      doc.add(idField);

      final long stopTime = System.currentTimeMillis() + 500;

      do {
        doFail.set(this);
        final String id = ""+r.nextInt(50);
        idField.setValue(id);
        Term idTerm = new Term("id", id);
        try {
          writer.updateDocument(idTerm, doc);
        } catch (RuntimeException re) {
          if (VERBOSE) {
            System.out.println(Thread.currentThread().getName() + ": EXC: ");
            re.printStackTrace(System.out);
          }
          try {
            _TestUtil.checkIndex(writer.getDirectory());
          } catch (IOException ioe) {
            System.out.println(Thread.currentThread().getName() + ": unexpected exception1");
            ioe.printStackTrace(System.out);
            failure = ioe;
            break;
          }
        } catch (Throwable t) {
          System.out.println(Thread.currentThread().getName() + ": unexpected exception2");
          t.printStackTrace(System.out);
          failure = t;
          break;
        }

        doFail.set(null);

        // After a possible exception (above) I should be able
        // to add a new document without hitting an
        // exception:
        try {
          writer.updateDocument(idTerm, doc);
        } catch (Throwable t) {
          System.out.println(Thread.currentThread().getName() + ": unexpected exception3");
          t.printStackTrace(System.out);
          failure = t;
          break;
        }
      } while(System.currentTimeMillis() < stopTime);
    }
  }

  ThreadLocal<Thread> doFail = new ThreadLocal<Thread>();

  private class MockIndexWriter extends IndexWriter {
    Random r = new java.util.Random(17);

    public MockIndexWriter(Directory dir, IndexWriterConfig conf) throws IOException {
      super(dir, conf);
    }

    @Override
    boolean testPoint(String name) {
      if (doFail.get() != null && !name.equals("startDoFlush") && r.nextInt(20) == 17) {
        if (VERBOSE) {
          System.out.println(Thread.currentThread().getName() + ": NOW FAIL: " + name);
          //new Throwable().printStackTrace(System.out);
        }
        throw new RuntimeException(Thread.currentThread().getName() + ": intentionally failing at " + name);
      }
      return true;
    }
  }

  public void testRandomExceptions() throws Throwable {
    MockDirectoryWrapper dir = newDirectory();

    MockIndexWriter writer  = new MockIndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new WhitespaceAnalyzer(TEST_VERSION_CURRENT))
        .setRAMBufferSizeMB(0.1).setMergeScheduler(new ConcurrentMergeScheduler()));
    ((ConcurrentMergeScheduler) writer.getConfig().getMergeScheduler()).setSuppressExceptions();
    //writer.setMaxBufferedDocs(10);
    writer.commit();

    if (VERBOSE)
      writer.setInfoStream(System.out);

    IndexerThread thread = new IndexerThread(0, writer);
    thread.run();
    if (thread.failure != null) {
      thread.failure.printStackTrace(System.out);
      fail("thread " + thread.getName() + ": hit unexpected failure");
    }

    writer.commit();

    try {
      writer.close();
    } catch (Throwable t) {
      System.out.println("exception during close:");
      t.printStackTrace(System.out);
      writer.rollback();
    }

    // Confirm that when doc hits exception partway through tokenization, it's deleted:
    IndexReader r2 = IndexReader.open(dir, true);
    final int count = r2.docFreq(new Term("content4", "aaa"));
    final int count2 = r2.docFreq(new Term("content4", "ddd"));
    assertEquals(count, count2);
    r2.close();

    _TestUtil.checkIndex(dir);
    dir.close();
  }

  public void testRandomExceptionsThreads() throws Throwable {
    MockDirectoryWrapper dir = newDirectory();
    MockIndexWriter writer  = new MockIndexWriter(dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new WhitespaceAnalyzer(TEST_VERSION_CURRENT))
        .setRAMBufferSizeMB(0.2).setMergeScheduler(new ConcurrentMergeScheduler()));
    ((ConcurrentMergeScheduler) writer.getConfig().getMergeScheduler()).setSuppressExceptions();
    //writer.setMaxBufferedDocs(10);
    writer.commit();

    if (VERBOSE)
      writer.setInfoStream(System.out);

    final int NUM_THREADS = 4;

    final IndexerThread[] threads = new IndexerThread[NUM_THREADS];
    for(int i=0;i<NUM_THREADS;i++) {
      threads[i] = new IndexerThread(i, writer);
      threads[i].start();
    }

    for(int i=0;i<NUM_THREADS;i++)
      threads[i].join();

    for(int i=0;i<NUM_THREADS;i++)
      if (threads[i].failure != null)
        fail("thread " + threads[i].getName() + ": hit unexpected failure");

    writer.commit();

    try {
      writer.close();
    } catch (Throwable t) {
      System.out.println("exception during close:");
      t.printStackTrace(System.out);
      writer.rollback();
    }

    // Confirm that when doc hits exception partway through tokenization, it's deleted:
    IndexReader r2 = IndexReader.open(dir, true);
    final int count = r2.docFreq(new Term("content4", "aaa"));
    final int count2 = r2.docFreq(new Term("content4", "ddd"));
    assertEquals(count, count2);
    r2.close();

    _TestUtil.checkIndex(dir);
    dir.close();
  }
  
  // LUCENE-1198
  private static final class MockIndexWriter2 extends IndexWriter {

    public MockIndexWriter2(Directory dir, IndexWriterConfig conf) throws IOException {
      super(dir, conf);
    }

    boolean doFail;

    @Override
    boolean testPoint(String name) {
      if (doFail && name.equals("DocumentsWriter.ThreadState.init start"))
        throw new RuntimeException("intentionally failing");
      return true;
    }
  }
  
  private class CrashingFilter extends TokenFilter {
    String fieldName;
    int count;

    public CrashingFilter(String fieldName, TokenStream input) {
      super(input);
      this.fieldName = fieldName;
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (this.fieldName.equals("crash") && count++ >= 4)
        throw new IOException("I'm experiencing problems");
      return input.incrementToken();
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      count = 0;
    }
  }

  public void testExceptionDocumentsWriterInit() throws IOException {
    Directory dir = newDirectory();
    MockIndexWriter2 w = new MockIndexWriter2(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));
    Document doc = new Document();
    doc.add(newField("field", "a field", Field.Store.YES,
                      Field.Index.ANALYZED));
    w.addDocument(doc);
    w.doFail = true;
    try {
      w.addDocument(doc);
      fail("did not hit exception");
    } catch (RuntimeException re) {
      // expected
    }
    w.close();
    _TestUtil.checkIndex(dir);
    dir.close();
  }

  // LUCENE-1208
  public void testExceptionJustBeforeFlush() throws IOException {
    Directory dir = newDirectory();
    MockIndexWriter w = new MockIndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()).setMaxBufferedDocs(2));
    Document doc = new Document();
    doc.add(newField("field", "a field", Field.Store.YES,
                      Field.Index.ANALYZED));
    w.addDocument(doc);

    Analyzer analyzer = new Analyzer() {
      @Override
      public TokenStream tokenStream(String fieldName, Reader reader) {
        return new CrashingFilter(fieldName, new WhitespaceTokenizer(TEST_VERSION_CURRENT, reader));
      }
    };

    Document crashDoc = new Document();
    crashDoc.add(newField("crash", "do it on token 4", Field.Store.YES,
                           Field.Index.ANALYZED));
    try {
      w.addDocument(crashDoc, analyzer);
      fail("did not hit expected exception");
    } catch (IOException ioe) {
      // expected
    }
    w.addDocument(doc);
    w.close();
    dir.close();
  }    

  private static final class MockIndexWriter3 extends IndexWriter {

    public MockIndexWriter3(Directory dir, IndexWriterConfig conf) throws IOException {
      super(dir, conf);
    }

    boolean doFail;
    boolean failed;

    @Override
    boolean testPoint(String name) {
      if (doFail && name.equals("startMergeInit")) {
        failed = true;
        throw new RuntimeException("intentionally failing");
      }
      return true;
    }
  }
  

  // LUCENE-1210
  public void testExceptionOnMergeInit() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer())
      .setMaxBufferedDocs(2).setMergeScheduler(new ConcurrentMergeScheduler());
    ((LogMergePolicy) conf.getMergePolicy()).setMergeFactor(2);
    MockIndexWriter3 w = new MockIndexWriter3(dir, conf);
    w.doFail = true;
    Document doc = new Document();
    doc.add(newField("field", "a field", Field.Store.YES,
                      Field.Index.ANALYZED));
    for(int i=0;i<10;i++)
      try {
        w.addDocument(doc);
      } catch (RuntimeException re) {
        break;
      }

    ((ConcurrentMergeScheduler) w.getConfig().getMergeScheduler()).sync();
    assertTrue(w.failed);
    w.close();
    dir.close();
  }
  
  // LUCENE-1072
  public void testExceptionFromTokenStream() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig( TEST_VERSION_CURRENT, new Analyzer() {

      @Override
      public TokenStream tokenStream(String fieldName, Reader reader) {
        return new TokenFilter(new LowerCaseTokenizer(TEST_VERSION_CURRENT, reader)) {
          private int count = 0;

          @Override
          public boolean incrementToken() throws IOException {
            if (count++ == 5) {
              throw new IOException();
            }
            return input.incrementToken();
          }
        };
      }

    });
    IndexWriter writer = new IndexWriter(dir, conf);

    Document doc = new Document();
    String contents = "aa bb cc dd ee ff gg hh ii jj kk";
    doc.add(newField("content", contents, Field.Store.NO,
        Field.Index.ANALYZED));
    try {
      writer.addDocument(doc);
      fail("did not hit expected exception");
    } catch (Exception e) {
    }

    // Make sure we can add another normal document
    doc = new Document();
    doc.add(newField("content", "aa bb cc dd", Field.Store.NO,
        Field.Index.ANALYZED));
    writer.addDocument(doc);

    // Make sure we can add another normal document
    doc = new Document();
    doc.add(newField("content", "aa bb cc dd", Field.Store.NO,
        Field.Index.ANALYZED));
    writer.addDocument(doc);

    writer.close();
    IndexReader reader = IndexReader.open(dir, true);
    final Term t = new Term("content", "aa");
    assertEquals(reader.docFreq(t), 3);

    // Make sure the doc that hit the exception was marked
    // as deleted:
    TermDocs tdocs = reader.termDocs(t);
    int count = 0;
    while(tdocs.next()) {
      count++;
    }
    assertEquals(2, count);

    assertEquals(reader.docFreq(new Term("content", "gg")), 0);
    reader.close();
    dir.close();
  }

  private static class FailOnlyOnFlush extends MockDirectoryWrapper.Failure {
    boolean doFail = false;
    int count;

    @Override
    public void setDoFail() {
      this.doFail = true;
    }
    @Override
    public void clearDoFail() {
      this.doFail = false;
    }

    @Override
    public void eval(MockDirectoryWrapper dir)  throws IOException {
      if (doFail) {
        StackTraceElement[] trace = new Exception().getStackTrace();
        boolean sawAppend = false;
        boolean sawFlush = false;
        for (int i = 0; i < trace.length; i++) {
          if ("org.apache.lucene.index.FreqProxTermsWriter".equals(trace[i].getClassName()) && "appendPostings".equals(trace[i].getMethodName()))
            sawAppend = true;
          if ("doFlush".equals(trace[i].getMethodName()))
            sawFlush = true;
        }

        if (sawAppend && sawFlush && count++ >= 30) {
          doFail = false;
          throw new IOException("now failing during flush");
        }
      }
    }
  }

  // LUCENE-1072: make sure an errant exception on flushing
  // one segment only takes out those docs in that one flush
  public void testDocumentsWriterAbort() throws IOException {
    MockDirectoryWrapper dir = newDirectory();
    FailOnlyOnFlush failure = new FailOnlyOnFlush();
    failure.setDoFail();
    dir.failOn(failure);

    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()).setMaxBufferedDocs(2));
    Document doc = new Document();
    String contents = "aa bb cc dd ee ff gg hh ii jj kk";
    doc.add(newField("content", contents, Field.Store.NO,
        Field.Index.ANALYZED));
    boolean hitError = false;
    for(int i=0;i<200;i++) {
      try {
        writer.addDocument(doc);
      } catch (IOException ioe) {
        // only one flush should fail:
        assertFalse(hitError);
        hitError = true;
      }
    }
    assertTrue(hitError);
    writer.close();
    IndexReader reader = IndexReader.open(dir, true);
    assertEquals(198, reader.docFreq(new Term("content", "aa")));
    reader.close();
    dir.close();
  }

  public void testDocumentsWriterExceptions() throws IOException {
    Analyzer analyzer = new Analyzer() {
      @Override
      public TokenStream tokenStream(String fieldName, Reader reader) {
        return new CrashingFilter(fieldName, new WhitespaceTokenizer(TEST_VERSION_CURRENT, reader));
      }
    };

    for(int i=0;i<2;i++) {
      MockDirectoryWrapper dir = newDirectory();
      IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, analyzer));
      //writer.setInfoStream(System.out);
      Document doc = new Document();
      doc.add(newField("contents", "here are some contents", Field.Store.YES,
                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      writer.addDocument(doc);
      writer.addDocument(doc);
      doc.add(newField("crash", "this should crash after 4 terms", Field.Store.YES,
                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      doc.add(newField("other", "this will not get indexed", Field.Store.YES,
                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      try {
        writer.addDocument(doc);
        fail("did not hit expected exception");
      } catch (IOException ioe) {
      }

      if (0 == i) {
        doc = new Document();
        doc.add(newField("contents", "here are some contents", Field.Store.YES,
                          Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
        writer.addDocument(doc);
        writer.addDocument(doc);
      }
      writer.close();

      IndexReader reader = IndexReader.open(dir, true);
      int expected = 3+(1-i)*2;
      assertEquals(expected, reader.docFreq(new Term("contents", "here")));
      assertEquals(expected, reader.maxDoc());
      int numDel = 0;
      for(int j=0;j<reader.maxDoc();j++) {
        if (reader.isDeleted(j))
          numDel++;
        else {
          reader.document(j);
          reader.getTermFreqVectors(j);
        }
      }
      reader.close();

      assertEquals(1, numDel);

      writer = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT,
          analyzer).setMaxBufferedDocs(10));
      doc = new Document();
      doc.add(newField("contents", "here are some contents", Field.Store.YES,
                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      for(int j=0;j<17;j++)
        writer.addDocument(doc);
      writer.optimize();
      writer.close();

      reader = IndexReader.open(dir, true);
      expected = 19+(1-i)*2;
      assertEquals(expected, reader.docFreq(new Term("contents", "here")));
      assertEquals(expected, reader.maxDoc());
      numDel = 0;
      for(int j=0;j<reader.maxDoc();j++) {
        if (reader.isDeleted(j))
          numDel++;
        else {
          reader.document(j);
          reader.getTermFreqVectors(j);
        }
      }
      reader.close();
      assertEquals(0, numDel);

      dir.close();
    }
  }

  public void testDocumentsWriterExceptionThreads() throws Exception {
    Analyzer analyzer = new Analyzer() {
      @Override
      public TokenStream tokenStream(String fieldName, Reader reader) {
        return new CrashingFilter(fieldName, new WhitespaceTokenizer(TEST_VERSION_CURRENT, reader));
      }
    };

    final int NUM_THREAD = 3;
    final int NUM_ITER = 100;

    for(int i=0;i<2;i++) {
      MockDirectoryWrapper dir = newDirectory();

      {
        final IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, analyzer).setMaxBufferedDocs(-1));
        ((LogMergePolicy) writer.getMergePolicy()).setMergeFactor(10);
        final int finalI = i;

        Thread[] threads = new Thread[NUM_THREAD];
        for(int t=0;t<NUM_THREAD;t++) {
          threads[t] = new Thread() {
              @Override
              public void run() {
                try {
                  for(int iter=0;iter<NUM_ITER;iter++) {
                    Document doc = new Document();
                    doc.add(newField("contents", "here are some contents", Field.Store.YES,
                                      Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
                    writer.addDocument(doc);
                    writer.addDocument(doc);
                    doc.add(newField("crash", "this should crash after 4 terms", Field.Store.YES,
                                      Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
                    doc.add(newField("other", "this will not get indexed", Field.Store.YES,
                                      Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
                    try {
                      writer.addDocument(doc);
                      fail("did not hit expected exception");
                    } catch (IOException ioe) {
                    }

                    if (0 == finalI) {
                      doc = new Document();
                      doc.add(newField("contents", "here are some contents", Field.Store.YES,
                                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
                      writer.addDocument(doc);
                      writer.addDocument(doc);
                    }
                  }
                } catch (Throwable t) {
                  synchronized(this) {
                    System.out.println(Thread.currentThread().getName() + ": ERROR: hit unexpected exception");
                    t.printStackTrace(System.out);
                  }
                  fail();
                }
              }
            };
          threads[t].start();
        }

        for(int t=0;t<NUM_THREAD;t++)
          threads[t].join();
            
        writer.close();
      }

      IndexReader reader = IndexReader.open(dir, true);
      int expected = (3+(1-i)*2)*NUM_THREAD*NUM_ITER;
      assertEquals("i=" + i, expected, reader.docFreq(new Term("contents", "here")));
      assertEquals(expected, reader.maxDoc());
      int numDel = 0;
      for(int j=0;j<reader.maxDoc();j++) {
        if (reader.isDeleted(j))
          numDel++;
        else {
          reader.document(j);
          reader.getTermFreqVectors(j);
        }
      }
      reader.close();

      assertEquals(NUM_THREAD*NUM_ITER, numDel);

      IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(
          TEST_VERSION_CURRENT, analyzer).setMaxBufferedDocs(10));
      Document doc = new Document();
      doc.add(newField("contents", "here are some contents", Field.Store.YES,
                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS));
      for(int j=0;j<17;j++)
        writer.addDocument(doc);
      writer.optimize();
      writer.close();

      reader = IndexReader.open(dir, true);
      expected += 17-NUM_THREAD*NUM_ITER;
      assertEquals(expected, reader.docFreq(new Term("contents", "here")));
      assertEquals(expected, reader.maxDoc());
      numDel = 0;
      for(int j=0;j<reader.maxDoc();j++) {
        if (reader.isDeleted(j))
          numDel++;
        else {
          reader.document(j);
          reader.getTermFreqVectors(j);
        }
      }
      reader.close();

      dir.close();
    }
  }
  
  // Throws IOException during MockDirectoryWrapper.sync
  private static class FailOnlyInSync extends MockDirectoryWrapper.Failure {
    boolean didFail;
    @Override
    public void eval(MockDirectoryWrapper dir)  throws IOException {
      if (doFail) {
        StackTraceElement[] trace = new Exception().getStackTrace();
        for (int i = 0; i < trace.length; i++) {
          if (doFail && "org.apache.lucene.store.MockDirectoryWrapper".equals(trace[i].getClassName()) && "sync".equals(trace[i].getMethodName())) {
            didFail = true;
            throw new IOException("now failing on purpose during sync");
          }
        }
      }
    }
  }
  
  // TODO: these are also in TestIndexWriter... add a simple doc-writing method
  // like this to LuceneTestCase?
  private void addDoc(IndexWriter writer) throws IOException
  {
      Document doc = new Document();
      doc.add(newField("content", "aaa", Field.Store.NO, Field.Index.ANALYZED));
      writer.addDocument(doc);
  }
  
  // LUCENE-1044: test exception during sync
  public void testExceptionDuringSync() throws IOException {
    MockDirectoryWrapper dir = newDirectory();
    FailOnlyInSync failure = new FailOnlyInSync();
    dir.failOn(failure);

    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer())
        .setMaxBufferedDocs(2).setMergeScheduler(new ConcurrentMergeScheduler()));
    failure.setDoFail();
    ((LogMergePolicy) writer.getConfig().getMergePolicy()).setMergeFactor(5);

    for (int i = 0; i < 23; i++) {
      addDoc(writer);
      if ((i-1)%2 == 0) {
        try {
          writer.commit();
        } catch (IOException ioe) {
          // expected
        }
      }
    }

    ((ConcurrentMergeScheduler) writer.getConfig().getMergeScheduler()).sync();
    assertTrue(failure.didFail);
    failure.clearDoFail();
    writer.close();

    IndexReader reader = IndexReader.open(dir, true);
    assertEquals(23, reader.numDocs());
    reader.close();
    dir.close();
  }
  
  private static class FailOnlyInCommit extends MockDirectoryWrapper.Failure {

    boolean fail1, fail2;

    @Override
    public void eval(MockDirectoryWrapper dir)  throws IOException {
      StackTraceElement[] trace = new Exception().getStackTrace();
      boolean isCommit = false;
      boolean isDelete = false;
      for (int i = 0; i < trace.length; i++) {
        if ("org.apache.lucene.index.SegmentInfos".equals(trace[i].getClassName()) && "prepareCommit".equals(trace[i].getMethodName()))
          isCommit = true;
        if ("org.apache.lucene.store.MockDirectoryWrapper".equals(trace[i].getClassName()) && "deleteFile".equals(trace[i].getMethodName()))
          isDelete = true;
      }

      if (isCommit) {
        if (!isDelete) {
          fail1 = true;
          throw new RuntimeException("now fail first");
        } else {
          fail2 = true;
          throw new IOException("now fail during delete");
        }
      }
    }
  }
  
  // LUCENE-1214
  public void testExceptionsDuringCommit() throws Throwable {
    MockDirectoryWrapper dir = newDirectory();
    FailOnlyInCommit failure = new FailOnlyInCommit();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));
    Document doc = new Document();
    doc.add(newField("field", "a field", Field.Store.YES,
                      Field.Index.ANALYZED));
    w.addDocument(doc);
    dir.failOn(failure);
    try {
      w.close();
      fail();
    } catch (IOException ioe) {
      fail("expected only RuntimeException");
    } catch (RuntimeException re) {
      // Expected
    }
    assertTrue(failure.fail1 && failure.fail2);
    w.rollback();
    dir.close();
  }
  
  public void testOptimizeExceptions() throws IOException {
    Directory startDir = newDirectory();
    IndexWriterConfig conf = newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()).setMaxBufferedDocs(2);
    ((LogMergePolicy) conf.getMergePolicy()).setMergeFactor(100);
    IndexWriter w = new IndexWriter(startDir, conf);
    for(int i=0;i<27;i++)
      addDoc(w);
    w.close();

    for(int i=0;i<200;i++) {
      MockDirectoryWrapper dir = new MockDirectoryWrapper(random, new RAMDirectory(startDir));
      conf = newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()).setMergeScheduler(new ConcurrentMergeScheduler());
      ((ConcurrentMergeScheduler) conf.getMergeScheduler()).setSuppressExceptions();
      w = new IndexWriter(dir, conf);
      dir.setRandomIOExceptionRate(0.5);
      try {
        w.optimize();
      } catch (IOException ioe) {
        if (ioe.getCause() == null)
          fail("optimize threw IOException without root cause");
      }
      dir.setRandomIOExceptionRate(0);
      w.close();
      dir.close();
    }
    startDir.close();
  }
  
  // LUCENE-1429
  public void testOutOfMemoryErrorCausesCloseToFail() throws Exception {

    final List<Throwable> thrown = new ArrayList<Throwable>();
    final Directory dir = newDirectory();
    final IndexWriter writer = new IndexWriter(dir,
        newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer())) {
        @Override
        public void message(final String message) {
          if (message.startsWith("now flush at close") && 0 == thrown.size()) {
            thrown.add(null);
            throw new OutOfMemoryError("fake OOME at " + message);
          }
        }
      };

    // need to set an info stream so message is called
    writer.setInfoStream(new PrintStream(new ByteArrayOutputStream()));
    try {
      writer.close();
      fail("OutOfMemoryError expected");
    }
    catch (final OutOfMemoryError expected) {}

    // throws IllegalStateEx w/o bug fix
    writer.close();
    dir.close();
  }
  
  // LUCENE-1347
  private static final class MockIndexWriter4 extends IndexWriter {

    public MockIndexWriter4(Directory dir, IndexWriterConfig conf) throws IOException {
      super(dir, conf);
    }

    boolean doFail;

    @Override
    boolean testPoint(String name) {
      if (doFail && name.equals("rollback before checkpoint"))
        throw new RuntimeException("intentionally failing");
      return true;
    }
  }
  
  // LUCENE-1347
  public void testRollbackExceptionHang() throws Throwable {
    Directory dir = newDirectory();
    MockIndexWriter4 w = new MockIndexWriter4(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));

    addDoc(w);
    w.doFail = true;
    try {
      w.rollback();
      fail("did not hit intentional RuntimeException");
    } catch (RuntimeException re) {
      // expected
    }
    
    w.doFail = false;
    w.rollback();
    dir.close();
  }
  
  // LUCENE-1044: Simulate checksum error in segments_N
  public void testSegmentsChecksumError() throws IOException {
    Directory dir = newDirectory();

    IndexWriter writer = null;

    writer = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));

    // add 100 documents
    for (int i = 0; i < 100; i++) {
      addDoc(writer);
    }

    // close
    writer.close();

    long gen = SegmentInfos.getCurrentSegmentGeneration(dir);
    assertTrue("segment generation should be > 0 but got " + gen, gen > 0);

    final String segmentsFileName = SegmentInfos.getCurrentSegmentFileName(dir);
    IndexInput in = dir.openInput(segmentsFileName);
    IndexOutput out = dir.createOutput(IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", 1+gen));
    out.copyBytes(in, in.length()-1);
    byte b = in.readByte();
    out.writeByte((byte) (1+b));
    out.close();
    in.close();

    IndexReader reader = null;
    try {
      reader = IndexReader.open(dir, true);
    } catch (IOException e) {
      e.printStackTrace(System.out);
      fail("segmentInfos failed to retry fallback to correct segments_N file");
    }
    reader.close();
    dir.close();
  }
  
  // Simulate a corrupt index by removing last byte of
  // latest segments file and make sure we get an
  // IOException trying to open the index:
  public void testSimulatedCorruptIndex1() throws IOException {
      Directory dir = newDirectory();

      IndexWriter writer = null;

      writer  = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));

      // add 100 documents
      for (int i = 0; i < 100; i++) {
          addDoc(writer);
      }

      // close
      writer.close();

      long gen = SegmentInfos.getCurrentSegmentGeneration(dir);
      assertTrue("segment generation should be > 0 but got " + gen, gen > 0);

      String fileNameIn = SegmentInfos.getCurrentSegmentFileName(dir);
      String fileNameOut = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                 "",
                                                                 1+gen);
      IndexInput in = dir.openInput(fileNameIn);
      IndexOutput out = dir.createOutput(fileNameOut);
      long length = in.length();
      for(int i=0;i<length-1;i++) {
        out.writeByte(in.readByte());
      }
      in.close();
      out.close();
      dir.deleteFile(fileNameIn);

      IndexReader reader = null;
      try {
        reader = IndexReader.open(dir, true);
        fail("reader did not hit IOException on opening a corrupt index");
      } catch (Exception e) {
      }
      if (reader != null) {
        reader.close();
      }
      dir.close();
  }
  
  // Simulate a corrupt index by removing one of the cfs
  // files and make sure we get an IOException trying to
  // open the index:
  public void testSimulatedCorruptIndex2() throws IOException {
      Directory dir = newDirectory();

      IndexWriter writer = null;

      writer  = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));
      ((LogMergePolicy) writer.getMergePolicy()).setUseCompoundFile(true);

      // add 100 documents
      for (int i = 0; i < 100; i++) {
          addDoc(writer);
      }

      // close
      writer.close();

      long gen = SegmentInfos.getCurrentSegmentGeneration(dir);
      assertTrue("segment generation should be > 0 but got " + gen, gen > 0);

      String[] files = dir.listAll();
      boolean corrupted = false;
      for(int i=0;i<files.length;i++) {
        if (files[i].endsWith(".cfs")) {
          dir.deleteFile(files[i]);
          corrupted = true;
          break;
        }
      }
      assertTrue("failed to find cfs file to remove", corrupted);

      IndexReader reader = null;
      try {
        reader = IndexReader.open(dir, true);
        fail("reader did not hit IOException on opening a corrupt index");
      } catch (Exception e) {
      }
      if (reader != null) {
        reader.close();
      }
      dir.close();
  }
  
  // Simulate a writer that crashed while writing segments
  // file: make sure we can still open the index (ie,
  // gracefully fallback to the previous segments file),
  // and that we can add to the index:
  public void testSimulatedCrashedWriter() throws IOException {
      MockDirectoryWrapper dir = newDirectory();
      dir.setPreventDoubleWrite(false);

      IndexWriter writer = null;

      writer  = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()));

      // add 100 documents
      for (int i = 0; i < 100; i++) {
          addDoc(writer);
      }

      // close
      writer.close();

      long gen = SegmentInfos.getCurrentSegmentGeneration(dir);
      assertTrue("segment generation should be > 0 but got " + gen, gen > 0);

      // Make the next segments file, with last byte
      // missing, to simulate a writer that crashed while
      // writing segments file:
      String fileNameIn = SegmentInfos.getCurrentSegmentFileName(dir);
      String fileNameOut = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                 "",
                                                                 1+gen);
      IndexInput in = dir.openInput(fileNameIn);
      IndexOutput out = dir.createOutput(fileNameOut);
      long length = in.length();
      for(int i=0;i<length-1;i++) {
        out.writeByte(in.readByte());
      }
      in.close();
      out.close();

      IndexReader reader = null;
      try {
        reader = IndexReader.open(dir, true);
      } catch (Exception e) {
        fail("reader failed to open on a crashed index");
      }
      reader.close();

      try {
        writer  = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer()).setOpenMode(OpenMode.CREATE));
      } catch (Exception e) {
        e.printStackTrace(System.out);
        fail("writer failed to open on a crashed index");
      }

      // add 100 documents
      for (int i = 0; i < 100; i++) {
          addDoc(writer);
      }

      // close
      writer.close();
      dir.close();
  }
}
