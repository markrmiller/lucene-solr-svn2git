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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

public class TestRollback extends LuceneTestCase {

  // LUCENE-2536
  public void testRollbackIntegrityWithBufferFlush() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter rw = new RandomIndexWriter(random, dir);

    for (int i = 0; i < 5; i++) {
      Document doc = new Document();
      doc.add(newField("pk", Integer.toString(i), Store.YES, Index.ANALYZED_NO_NORMS));
      rw.addDocument(doc);
    }
    rw.close();

    // If buffer size is small enough to cause a flush, errors ensue...
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMaxBufferedDocs(2).setOpenMode(IndexWriterConfig.OpenMode.APPEND));

    Term pkTerm = new Term("pk", "");
    for (int i = 0; i < 3; i++) {
      Document doc = new Document();
      String value = Integer.toString(i);
      doc.add(newField("pk", value, Store.YES, Index.ANALYZED_NO_NORMS));
      doc.add(newField("text", "foo", Store.YES, Index.ANALYZED_NO_NORMS));
      w.updateDocument(pkTerm.createTerm(value), doc);
    }
    w.rollback();

    IndexReader r = IndexReader.open(dir, true);
    assertEquals("index should contain same number of docs post rollback", 5, r.numDocs());
    r.close();
    dir.close();
  }
}
