package org.apache.lucene.codecs.lucene40;
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
import java.util.IdentityHashMap;
import java.util.Random;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.lucene40.Lucene40PostingsFormat;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Bits.MatchNoBits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LineFileDocs;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;

public class TestReuseDocsEnum extends LuceneTestCase {

  public void testReuseDocsEnumNoReuse() throws IOException {
    Directory dir = newDirectory();
    Codec cp = _TestUtil.alwaysPostingsFormat(new Lucene40PostingsFormat());
    RandomIndexWriter writer = new RandomIndexWriter(random, dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setCodec(cp));
    int numdocs = atLeast(20);
    createRandomIndex(numdocs, writer, random);
    writer.commit();

    IndexReader open = IndexReader.open(dir);
    IndexReader[] sequentialSubReaders = open.getSequentialSubReaders();
    for (IndexReader indexReader : sequentialSubReaders) {
      Terms terms = indexReader.terms("body");
      TermsEnum iterator = terms.iterator(null);
      IdentityHashMap<DocsEnum, Boolean> enums = new IdentityHashMap<DocsEnum, Boolean>();
      MatchNoBits bits = new Bits.MatchNoBits(open.maxDoc());
      while ((iterator.next()) != null) {
        DocsEnum docs = iterator.docs(random.nextBoolean() ? bits : new Bits.MatchNoBits(open.maxDoc()), null, random.nextBoolean());
        enums.put(docs, true);
      }
      
      assertEquals(terms.getUniqueTermCount(), enums.size());  
    }
    IOUtils.close(writer, open, dir);
  }
  
  // tests for reuse only if bits are the same either null or the same instance
  public void testReuseDocsEnumSameBitsOrNull() throws IOException {
    Directory dir = newDirectory();
    Codec cp = _TestUtil.alwaysPostingsFormat(new Lucene40PostingsFormat());
    RandomIndexWriter writer = new RandomIndexWriter(random, dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setCodec(cp));
    int numdocs = atLeast(20);
    createRandomIndex(numdocs, writer, random);
    writer.commit();

    IndexReader open = IndexReader.open(dir);
    IndexReader[] sequentialSubReaders = open.getSequentialSubReaders();
    for (IndexReader indexReader : sequentialSubReaders) {
      Terms terms = indexReader.terms("body");
      TermsEnum iterator = terms.iterator(null);
      IdentityHashMap<DocsEnum, Boolean> enums = new IdentityHashMap<DocsEnum, Boolean>();
      MatchNoBits bits = new Bits.MatchNoBits(open.maxDoc());
      DocsEnum docs = null;
      while ((iterator.next()) != null) {
        docs = iterator.docs(bits, docs, random.nextBoolean());
        enums.put(docs, true);
      }
      
      assertEquals(1, enums.size());
      enums.clear();
      iterator = terms.iterator(null);
      docs = null;
      while ((iterator.next()) != null) {
        docs = iterator.docs(new Bits.MatchNoBits(open.maxDoc()), docs, random.nextBoolean());
        enums.put(docs, true);
      }
      assertEquals(terms.getUniqueTermCount(), enums.size());  
      
      enums.clear();
      iterator = terms.iterator(null);
      docs = null;
      while ((iterator.next()) != null) {
        docs = iterator.docs(null, docs, random.nextBoolean());
        enums.put(docs, true);
      }
      assertEquals(1, enums.size());  
    }
    IOUtils.close(writer, open, dir);
  }
  
  // make sure we never reuse from another reader even if it is the same field & codec etc
  public void testReuseDocsEnumDifferentReader() throws IOException {
    Directory dir = newDirectory();
    Codec cp = _TestUtil.alwaysPostingsFormat(new Lucene40PostingsFormat());
    RandomIndexWriter writer = new RandomIndexWriter(random, dir,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random)).setCodec(cp));
    int numdocs = atLeast(20);
    createRandomIndex(numdocs, writer, random);
    writer.commit();

    IndexReader firstReader = IndexReader.open(dir);
    IndexReader secondReader = IndexReader.open(dir);
    IndexReader[] sequentialSubReaders = firstReader.getSequentialSubReaders();
    IndexReader[] sequentialSubReaders2 = secondReader.getSequentialSubReaders();
    
    for (IndexReader indexReader : sequentialSubReaders) {
      Terms terms = indexReader.terms("body");
      TermsEnum iterator = terms.iterator(null);
      IdentityHashMap<DocsEnum, Boolean> enums = new IdentityHashMap<DocsEnum, Boolean>();
      MatchNoBits bits = new Bits.MatchNoBits(firstReader.maxDoc());
      iterator = terms.iterator(null);
      DocsEnum docs = null;
      BytesRef term = null;
      while ((term = iterator.next()) != null) {
        docs = iterator.docs(null, randomDocsEnum("body", term, sequentialSubReaders2, bits), random.nextBoolean());
        enums.put(docs, true);
      }
      assertEquals(terms.getUniqueTermCount(), enums.size());  
      
      iterator = terms.iterator(null);
      enums.clear();
      docs = null;
      while ((term = iterator.next()) != null) {
        docs = iterator.docs(bits, randomDocsEnum("body", term, sequentialSubReaders2, bits), random.nextBoolean());
        enums.put(docs, true);
      }
      assertEquals(terms.getUniqueTermCount(), enums.size());  
    }
    IOUtils.close(writer, firstReader, secondReader, dir);
  }
  
  public DocsEnum randomDocsEnum(String field, BytesRef term, IndexReader[] readers, Bits bits) throws IOException {
    if (random.nextInt(10) == 0) {
      return null;
    }
    IndexReader indexReader = readers[random.nextInt(readers.length)];
    return indexReader.termDocsEnum(bits, field, term, random.nextBoolean());
  }

  /**
   * populates a writer with random stuff. this must be fully reproducable with
   * the seed!
   */
  public static void createRandomIndex(int numdocs, RandomIndexWriter writer,
      Random random) throws IOException {
    LineFileDocs lineFileDocs = new LineFileDocs(random);

    for (int i = 0; i < numdocs; i++) {
      writer.addDocument(lineFileDocs.nextDoc());
    }
  }

}
