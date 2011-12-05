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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;

/**
 * 
 * @lucene.experimental
 */
public class TestOmitPositions extends LuceneTestCase {

  public void testBasic() throws Exception {   
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random, dir);
    Document doc = new Document();
    FieldType ft = new FieldType(TextField.TYPE_UNSTORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    Field f = newField("foo", "this is a test test", ft);
    doc.add(f);
    for (int i = 0; i < 100; i++) {
      w.addDocument(doc);
    }
    
    IndexReader reader = w.getReader();
    w.close();
    
    assertNull(MultiFields.getTermPositionsEnum(reader, null, "foo", new BytesRef("test")));
    
    DocsEnum de = _TestUtil.docs(random, reader, "foo", new BytesRef("test"), null, null, true);
    while (de.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      assertEquals(2, de.freq());
    }
    
    reader.close();
    dir.close();
  }
  
  // Tests whether the DocumentWriter correctly enable the
  // omitTermFreqAndPositions bit in the FieldInfo
  public void testPositions() throws Exception {
    Directory ram = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random);
    IndexWriter writer = new IndexWriter(ram, newIndexWriterConfig( TEST_VERSION_CURRENT, analyzer));
    Document d = new Document();
        
    // f1,f2,f3: docs only
    FieldType ft = new FieldType(TextField.TYPE_UNSTORED);
    ft.setIndexOptions(IndexOptions.DOCS_ONLY);
    
    Field f1 = newField("f1", "This field has docs only", ft);
    d.add(f1);
       
    Field f2 = newField("f2", "This field has docs only", ft);
    d.add(f2);
    
    Field f3 = newField("f3", "This field has docs only", ft);
    d.add(f3);

    FieldType ft2 = new FieldType(TextField.TYPE_UNSTORED);
    ft2.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    
    // f4,f5,f6 docs and freqs
    Field f4 = newField("f4", "This field has docs and freqs", ft2);
    d.add(f4);
       
    Field f5 = newField("f5", "This field has docs and freqs", ft2);
    d.add(f5);
    
    Field f6 = newField("f6", "This field has docs and freqs", ft2);
    d.add(f6);
    
    FieldType ft3 = new FieldType(TextField.TYPE_UNSTORED);
    ft3.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    
    // f7,f8,f9 docs/freqs/positions
    Field f7 = newField("f7", "This field has docs and freqs and positions", ft3);
    d.add(f7);
       
    Field f8 = newField("f8", "This field has docs and freqs and positions", ft3);
    d.add(f8);
    
    Field f9 = newField("f9", "This field has docs and freqs and positions", ft3);
    d.add(f9);
        
    writer.addDocument(d);
    writer.forceMerge(1);

    // now we add another document which has docs-only for f1, f4, f7, docs/freqs for f2, f5, f8, 
    // and docs/freqs/positions for f3, f6, f9
    d = new Document();
    
    // f1,f4,f7: docs only
    f1 = newField("f1", "This field has docs only", ft);
    d.add(f1);
    
    f4 = newField("f4", "This field has docs only", ft);
    d.add(f4);
    
    f7 = newField("f7", "This field has docs only", ft);
    d.add(f7);

    // f2, f5, f8: docs and freqs
    f2 = newField("f2", "This field has docs and freqs", ft2);
    d.add(f2);
    
    f5 = newField("f5", "This field has docs and freqs", ft2);
    d.add(f5);
    
    f8 = newField("f8", "This field has docs and freqs", ft2);
    d.add(f8);
    
    // f3, f6, f9: docs and freqs and positions
    f3 = newField("f3", "This field has docs and freqs and positions", ft3);
    d.add(f3);     
    
    f6 = newField("f6", "This field has docs and freqs and positions", ft3);
    d.add(f6);
    
    f9 = newField("f9", "This field has docs and freqs and positions", ft3);
    d.add(f9);
        
    writer.addDocument(d);

    // force merge
    writer.forceMerge(1);
    // flush
    writer.close();

    SegmentReader reader = getOnlySegmentReader(IndexReader.open(ram));
    FieldInfos fi = reader.fieldInfos();
    // docs + docs = docs
    assertEquals(IndexOptions.DOCS_ONLY, fi.fieldInfo("f1").indexOptions);
    // docs + docs/freqs = docs
    assertEquals(IndexOptions.DOCS_ONLY, fi.fieldInfo("f2").indexOptions);
    // docs + docs/freqs/pos = docs
    assertEquals(IndexOptions.DOCS_ONLY, fi.fieldInfo("f3").indexOptions);
    // docs/freqs + docs = docs
    assertEquals(IndexOptions.DOCS_ONLY, fi.fieldInfo("f4").indexOptions);
    // docs/freqs + docs/freqs = docs/freqs
    assertEquals(IndexOptions.DOCS_AND_FREQS, fi.fieldInfo("f5").indexOptions);
    // docs/freqs + docs/freqs/pos = docs/freqs
    assertEquals(IndexOptions.DOCS_AND_FREQS, fi.fieldInfo("f6").indexOptions);
    // docs/freqs/pos + docs = docs
    assertEquals(IndexOptions.DOCS_ONLY, fi.fieldInfo("f7").indexOptions);
    // docs/freqs/pos + docs/freqs = docs/freqs
    assertEquals(IndexOptions.DOCS_AND_FREQS, fi.fieldInfo("f8").indexOptions);
    // docs/freqs/pos + docs/freqs/pos = docs/freqs/pos
    assertEquals(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, fi.fieldInfo("f9").indexOptions);
    
    reader.close();
    ram.close();
  }
  
  private void assertNoPrx(Directory dir) throws Throwable {
    final String[] files = dir.listAll();
    for(int i=0;i<files.length;i++) {
      assertFalse(files[i].endsWith(".prx"));
      assertFalse(files[i].endsWith(".pos"));
    }
  }

  // Verifies no *.prx exists when all fields omit term positions:
  public void testNoPrxFile() throws Throwable {
    Directory ram = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random);
    IndexWriter writer = new IndexWriter(ram, newIndexWriterConfig(
                                                                   TEST_VERSION_CURRENT, analyzer).setMaxBufferedDocs(3).setMergePolicy(newLogMergePolicy()));
    LogMergePolicy lmp = (LogMergePolicy) writer.getConfig().getMergePolicy();
    lmp.setMergeFactor(2);
    lmp.setUseCompoundFile(false);
    Document d = new Document();

    FieldType ft = new FieldType(TextField.TYPE_UNSTORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    Field f1 = newField("f1", "This field has term freqs", ft);
    d.add(f1);

    for(int i=0;i<30;i++)
      writer.addDocument(d);

    writer.commit();

    assertNoPrx(ram);
    
    // now add some documents with positions, and check there is no prox after optimization
    d = new Document();
    f1 = newField("f1", "This field has positions", TextField.TYPE_UNSTORED);
    d.add(f1);
    
    for(int i=0;i<30;i++)
      writer.addDocument(d);

    // force merge
    writer.forceMerge(1);
    // flush
    writer.close();

    assertNoPrx(ram);
    ram.close();
  }
}
