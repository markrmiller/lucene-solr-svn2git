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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.CannedTokenStream;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldTypes;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;

public class TestCheckIndex extends LuceneTestCase {

  public void testDeletedDocs() throws IOException {
    Directory dir = newDirectory();
    IndexWriter writer  = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random()))
                                                 .setMaxBufferedDocs(2));
    FieldTypes fieldTypes = writer.getFieldTypes();
    fieldTypes.disableExistsFilters();
    fieldTypes.enableTermVectors("field");
    fieldTypes.enableTermVectorPositions("field");
    fieldTypes.enableTermVectorOffsets("field");

    for(int i=0;i<19;i++) {
      Document doc = writer.newDocument();
      doc.addLargeText("field", "aaa"+i);
      writer.addDocument(doc);
    }
    writer.forceMerge(1);
    writer.commit();
    writer.deleteDocuments(new Term("field","aaa5"));
    writer.close();

    ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
    CheckIndex checker = new CheckIndex(dir);
    checker.setInfoStream(new PrintStream(bos, false, IOUtils.UTF_8));
    if (VERBOSE) checker.setInfoStream(System.out);
    CheckIndex.Status indexStatus = checker.checkIndex();
    if (indexStatus.clean == false) {
      System.out.println("CheckIndex failed");
      System.out.println(bos.toString(IOUtils.UTF_8));
      fail();
    }
    
    final CheckIndex.Status.SegmentInfoStatus seg = indexStatus.segmentInfos.get(0);
    assertTrue(seg.openReaderPassed);

    assertNotNull(seg.diagnostics);
    
    assertNotNull(seg.fieldNormStatus);
    assertNull(seg.fieldNormStatus.error);
    assertEquals(1, seg.fieldNormStatus.totFields);

    assertNotNull(seg.termIndexStatus);
    assertNull(seg.termIndexStatus.error);
    assertEquals(18, seg.termIndexStatus.termCount);
    assertEquals(18, seg.termIndexStatus.totFreq);
    assertEquals(18, seg.termIndexStatus.totPos);

    assertNotNull(seg.storedFieldStatus);
    assertNull(seg.storedFieldStatus.error);
    assertEquals(18, seg.storedFieldStatus.docCount);
    assertEquals(18, seg.storedFieldStatus.totFields);

    assertNotNull(seg.termVectorStatus);
    assertNull(seg.termVectorStatus.error);
    assertEquals(18, seg.termVectorStatus.docCount);
    assertEquals(18, seg.termVectorStatus.totVectors);

    assertTrue(seg.diagnostics.size() > 0);
    final List<String> onlySegments = new ArrayList<>();
    onlySegments.add("_0");
    
    assertTrue(checker.checkIndex(onlySegments).clean == true);
    checker.close();
    dir.close();
  }
  
  // LUCENE-4221: we have to let these thru, for now
  public void testBogusTermVectors() throws IOException {
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(null));
    FieldTypes fieldTypes = iw.getFieldTypes();
    fieldTypes.enableTermVectors("foo");
    fieldTypes.enableTermVectorOffsets("foo");
    fieldTypes.disableHighlighting("foo");

    Document doc = iw.newDocument();
    doc.addLargeText("foo", new CannedTokenStream(
        new Token("bar", 5, 10), new Token("bar", 1, 4)
    ));
    iw.addDocument(doc);
    iw.close();
    dir.close(); // checkindex
  }
  
  public void testObtainsLock() throws IOException {
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(null));
    iw.addDocument(iw.newDocument());
    iw.commit();
    
    // keep IW open...
    try {
      new CheckIndex(dir);
      fail("should not have obtained write lock");
    } catch (LockObtainFailedException expected) {
      // ok
    }
    
    iw.close();
    dir.close();
  }
}
