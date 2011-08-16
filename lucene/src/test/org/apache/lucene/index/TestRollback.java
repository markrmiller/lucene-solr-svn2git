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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

public class TestRollback extends LuceneTestCase {

  // LUCENE-2536
  public void testRollbackIntegrityWithBufferFlush() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter rw = new RandomIndexWriter(random, dir);
    FieldType customType = new FieldType(TextField.TYPE_UNSTORED);
    customType.setStored(true);
    customType.setOmitNorms(true);
    for (int i = 0; i < 5; i++) {
      Document doc = new Document();
      doc.add(newField("pk", Integer.toString(i), customType));
      rw.addDocument(doc);
    }
    rw.close();

    // If buffer size is small enough to cause a flush, errors ensue...
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig( TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMaxBufferedDocs(2).setOpenMode(IndexWriterConfig.OpenMode.APPEND));

    for (int i = 0; i < 3; i++) {
      Document doc = new Document();
      String value = Integer.toString(i);
      doc.add(newField("pk", value, customType));
      doc.add(newField("text", "foo", customType));
      w.updateDocument(new Term("pk", value), doc);
    }
    w.rollback();

    IndexReader r = IndexReader.open(dir, true);
    assertEquals("index should contain same number of docs post rollback", 5, r.numDocs());
    r.close();
    dir.close();
  }
}
