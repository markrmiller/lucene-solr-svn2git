package org.apache.lucene.codecs.blocktree;

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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.blocktree.Lucene40FieldReader;
import org.apache.lucene.codecs.blocktree.Lucene40Stats;
import org.apache.lucene.codecs.lucene41.Lucene41RWCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.BasePostingsFormatTestCase;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;

/**
 * Tests BlockPostingsFormat
 */
public class TestLucene40BlockFormat extends BasePostingsFormatTestCase {
  private final Codec codec = new Lucene41RWCodec();

  @Override
  protected Codec getCodec() {
    return codec;
  }
  
  /** Make sure the final sub-block(s) are not skipped. */
  public void testFinalBlock() throws Exception {
    Directory d = newDirectory();
    IndexWriter w = new IndexWriter(d, new IndexWriterConfig(new MockAnalyzer(random())));
    for(int i=0;i<25;i++) {
      Document doc = new Document();
      doc.add(newStringField("field", Character.toString((char) (97+i)), Field.Store.NO));
      doc.add(newStringField("field", "z" + Character.toString((char) (97+i)), Field.Store.NO));
      w.addDocument(doc);
    }
    w.forceMerge(1);

    DirectoryReader r = DirectoryReader.open(w, true);
    assertEquals(1, r.leaves().size());
    Lucene40FieldReader field = (Lucene40FieldReader) r.leaves().get(0).reader().fields().terms("field");
    // We should see exactly two blocks: one root block (prefix empty string) and one block for z* terms (prefix z):
    Lucene40Stats stats = field.getStats();
    assertEquals(0, stats.floorBlockCount);
    assertEquals(2, stats.nonFloorBlockCount);
    r.close();
    w.close();
    d.close();
  }
}
