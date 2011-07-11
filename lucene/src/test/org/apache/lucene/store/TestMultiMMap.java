package org.apache.lucene.store;

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

import java.io.File;
import java.util.Random;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document2.Document;
import org.apache.lucene.document2.Field;
import org.apache.lucene.document2.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;

/**
 * Tests MMapDirectory's MultiMMapIndexInput
 * <p>
 * Because Java's ByteBuffer uses an int to address the
 * values, it's necessary to access a file >
 * Integer.MAX_VALUE in size using multiple byte buffers.
 */
public class TestMultiMMap extends LuceneTestCase {
  File workDir;
  
  @Override
  public void setUp() throws Exception {
      super.setUp();
      workDir = _TestUtil.getTempDir("TestMultiMMap");
      workDir.mkdirs();
  }
  
  public void testRandomChunkSizes() throws Exception {
    int num = atLeast(10);
    for (int i = 0; i < num; i++)
      assertChunking(random, _TestUtil.nextInt(random, 20, 100));
  }
  
  private void assertChunking(Random random, int chunkSize) throws Exception {
    File path = _TestUtil.createTempFile("mmap" + chunkSize, "tmp", workDir);
    path.delete();
    path.mkdirs();
    MMapDirectory dir = new MMapDirectory(path);
    dir.setMaxChunkSize(chunkSize);
    // we will map a lot, try to turn on the unmap hack
    if (MMapDirectory.UNMAP_SUPPORTED)
      dir.setUseUnmap(true);
    RandomIndexWriter writer = new RandomIndexWriter(random, dir, newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setMergePolicy(newLogMergePolicy()));
    Document doc = new Document();
    Field docid = newField("docid", "0", StringField.TYPE_STORED);
    Field junk = newField("junk", "", StringField.TYPE_STORED);
    doc.add(docid);
    doc.add(junk);
    
    int numDocs = 100;
    for (int i = 0; i < numDocs; i++) {
      docid.setValue("" + i);
      junk.setValue(_TestUtil.randomUnicodeString(random));
      writer.addDocument(doc);
    }
    IndexReader reader = writer.getReader();
    writer.close();
    
    int numAsserts = atLeast(100);
    for (int i = 0; i < numAsserts; i++) {
      int docID = random.nextInt(numDocs);
      assertEquals("" + docID, reader.document(docID).get("docid"));
    }
    reader.close();
    dir.close();
  }
}
