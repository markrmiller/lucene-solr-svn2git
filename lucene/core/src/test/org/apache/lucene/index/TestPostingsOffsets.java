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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CannedTokenStream;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockPayloadAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldTypes;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.English;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

// TODO: we really need to test indexingoffsets, but then getting only docs / docs + freqs.
// not all codecs store prx separate...
// TODO: fix sep codec to index offsets so we can greatly reduce this list!
public class TestPostingsOffsets extends LuceneTestCase {
  IndexWriterConfig iwc;
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    iwc = newIndexWriterConfig(new MockAnalyzer(random()));
  }

  public void testBasic() throws Exception {
    Directory dir = newDirectory();
    
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    FieldTypes fieldTypes = w.getFieldTypes();

    Document doc = w.newDocument();

    if (random().nextBoolean()) {
      fieldTypes.enableTermVectors("content");
      if (random().nextBoolean()) {
        fieldTypes.enableTermVectorPositions("content");
      }
      if (random().nextBoolean()) {
        fieldTypes.enableTermVectorOffsets("content");
      }
    }

    Token[] tokens = new Token[] {
      makeToken("a", 1, 0, 6),
      makeToken("b", 1, 8, 9),
      makeToken("a", 1, 9, 17),
      makeToken("c", 1, 19, 50),
    };
    doc.addLargeText("content", new CannedTokenStream(tokens));

    w.addDocument(doc);
    IndexReader r = w.getReader();
    w.close();

    DocsAndPositionsEnum dp = MultiFields.getTermPositionsEnum(r, null, "content", new BytesRef("a"));
    assertNotNull(dp);
    assertEquals(0, dp.nextDoc());
    assertEquals(2, dp.freq());
    assertEquals(0, dp.nextPosition());
    assertEquals(0, dp.startOffset());
    assertEquals(6, dp.endOffset());
    assertEquals(2, dp.nextPosition());
    assertEquals(9, dp.startOffset());
    assertEquals(17, dp.endOffset());
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc());

    dp = MultiFields.getTermPositionsEnum(r, null, "content", new BytesRef("b"));
    assertNotNull(dp);
    assertEquals(0, dp.nextDoc());
    assertEquals(1, dp.freq());
    assertEquals(1, dp.nextPosition());
    assertEquals(8, dp.startOffset());
    assertEquals(9, dp.endOffset());
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc());

    dp = MultiFields.getTermPositionsEnum(r, null, "content", new BytesRef("c"));
    assertNotNull(dp);
    assertEquals(0, dp.nextDoc());
    assertEquals(1, dp.freq());
    assertEquals(3, dp.nextPosition());
    assertEquals(19, dp.startOffset());
    assertEquals(50, dp.endOffset());
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc());

    r.close();
    dir.close();
  }
  
  public void testSkipping() throws Exception {
    doTestNumbers(false);
  }
  
  public void testPayloads() throws Exception {
    doTestNumbers(true);
  }
  
  public void doTestNumbers(boolean withPayloads) throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = withPayloads ? new MockPayloadAnalyzer() : new MockAnalyzer(random());
    iwc = newIndexWriterConfig(analyzer);
    iwc.setMergePolicy(newLogMergePolicy()); // will rely on docids a bit for skipping
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    FieldTypes fieldTypes = w.getFieldTypes();
    
    if (random().nextBoolean()) {
      fieldTypes.enableTermVectors("numbers");
      fieldTypes.enableTermVectors("oddeven");
      if (random().nextBoolean()) {
        fieldTypes.enableTermVectorOffsets("numbers");
        fieldTypes.enableTermVectorOffsets("oddeven");
      }
      if (random().nextBoolean()) {
        fieldTypes.enableTermVectorPositions("numbers");
        fieldTypes.enableTermVectorPositions("oddeven");
      }
    }
    
    int numDocs = atLeast(500);
    for (int i = 0; i < numDocs; i++) {
      Document doc = w.newDocument();
      doc.addLargeText("numbers", English.intToEnglish(i));
      doc.addLargeText("oddeven", (i % 2) == 0 ? "even" : "odd");
      doc.addAtom("id", "" + i);
      w.addDocument(doc);
    }
    
    IndexReader reader = w.getReader();
    w.close();
    
    String terms[] = { "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "hundred" };
    
    for (String term : terms) {
      DocsAndPositionsEnum dp = MultiFields.getTermPositionsEnum(reader, null, "numbers", new BytesRef(term));
      int doc;
      while((doc = dp.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        String storedNumbers = reader.document(doc).getString("numbers");
        int freq = dp.freq();
        for (int i = 0; i < freq; i++) {
          dp.nextPosition();
          int start = dp.startOffset();
          assert start >= 0;
          int end = dp.endOffset();
          assert end >= 0 && end >= start;
          // check that the offsets correspond to the term in the src text
          assertTrue(storedNumbers.substring(start, end).equals(term));
          if (withPayloads) {
            // check that we have a payload and it starts with "pos"
            assertNotNull(dp.getPayload());
            BytesRef payload = dp.getPayload();
            assertTrue(payload.utf8ToString().startsWith("pos:"));
          } // note: withPayloads=false doesnt necessarily mean we dont have them from MockAnalyzer!
        }
      }
    }
    
    // check we can skip correctly
    int numSkippingTests = atLeast(50);
    
    for (int j = 0; j < numSkippingTests; j++) {
      int num = TestUtil.nextInt(random(), 100, Math.min(numDocs - 1, 999));
      DocsAndPositionsEnum dp = MultiFields.getTermPositionsEnum(reader, null, "numbers", new BytesRef("hundred"));
      int doc = dp.advance(num);
      assertEquals(num, doc);
      int freq = dp.freq();
      for (int i = 0; i < freq; i++) {
        String storedNumbers = reader.document(doc).getString("numbers");
        dp.nextPosition();
        int start = dp.startOffset();
        assert start >= 0;
        int end = dp.endOffset();
        assert end >= 0 && end >= start;
        // check that the offsets correspond to the term in the src text
        assertTrue(storedNumbers.substring(start, end).equals("hundred"));
        if (withPayloads) {
          // check that we have a payload and it starts with "pos"
          assertNotNull(dp.getPayload());
          BytesRef payload = dp.getPayload();
          assertTrue(payload.utf8ToString().startsWith("pos:"));
        } // note: withPayloads=false doesnt necessarily mean we dont have them from MockAnalyzer!
      }
    }
    
    // check that other fields (without offsets) work correctly
    
    for (int i = 0; i < numDocs; i++) {
      DocsEnum dp = MultiFields.getTermDocsEnum(reader, null, "id", new BytesRef("" + i), 0);
      assertEquals(i, dp.nextDoc());
      assertEquals(DocIdSetIterator.NO_MORE_DOCS, dp.nextDoc());
    }
    
    reader.close();
    dir.close();
  }

  public void testRandom() throws Exception {
    // token -> docID -> tokens
    final Map<String,Map<Integer,List<Token>>> actualTokens = new HashMap<>();

    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    FieldTypes fieldTypes = w.getFieldTypes();

    final int numDocs = atLeast(20);
    //final int numDocs = atLeast(5);

    // TODO: randomize what IndexOptions we use; also test
    // changing this up in one IW buffered segment...:
    if (random().nextBoolean()) {
      fieldTypes.enableTermVectors("content");
      if (random().nextBoolean()) {
        fieldTypes.enableTermVectorOffsets("content");
      }
      if (random().nextBoolean()) {
        fieldTypes.enableTermVectorPositions("content");
      }
    }

    for(int docCount=0;docCount<numDocs;docCount++) {
      Document doc = w.newDocument();
      doc.addInt("id", docCount);
      List<Token> tokens = new ArrayList<>();
      final int numTokens = atLeast(100);
      //final int numTokens = atLeast(20);
      int pos = -1;
      int offset = 0;
      //System.out.println("doc id=" + docCount);
      for(int tokenCount=0;tokenCount<numTokens;tokenCount++) {
        final String text;
        if (random().nextBoolean()) {
          text = "a";
        } else if (random().nextBoolean()) {
          text = "b";
        } else if (random().nextBoolean()) {
          text = "c";
        } else {
          text = "d";
        }       
        
        int posIncr = random().nextBoolean() ? 1 : random().nextInt(5);
        if (tokenCount == 0 && posIncr == 0) {
          posIncr = 1;
        }
        final int offIncr = random().nextBoolean() ? 0 : random().nextInt(5);
        final int tokenOffset = random().nextInt(5);

        final Token token = makeToken(text, posIncr, offset+offIncr, offset+offIncr+tokenOffset);
        if (!actualTokens.containsKey(text)) {
          actualTokens.put(text, new HashMap<Integer,List<Token>>());
        }
        final Map<Integer,List<Token>> postingsByDoc = actualTokens.get(text);
        if (!postingsByDoc.containsKey(docCount)) {
          postingsByDoc.put(docCount, new ArrayList<Token>());
        }
        postingsByDoc.get(docCount).add(token);
        tokens.add(token);
        pos += posIncr;
        // stuff abs position into type:
        token.setType(""+pos);
        offset += offIncr + tokenOffset;
        //System.out.println("  " + token + " posIncr=" + token.getPositionIncrement() + " pos=" + pos + " off=" + token.startOffset() + "/" + token.endOffset() + " (freq=" + postingsByDoc.get(docCount).size() + ")");
      }
      doc.addLargeText("content", new CannedTokenStream(tokens.toArray(new Token[tokens.size()])));
      w.addDocument(doc);
    }
    final DirectoryReader r = w.getReader();
    w.close();

    final String[] terms = new String[] {"a", "b", "c", "d"};
    for(LeafReaderContext ctx : r.leaves()) {
      // TODO: improve this
      LeafReader sub = ctx.reader();
      //System.out.println("\nsub=" + sub);
      final TermsEnum termsEnum = sub.fields().terms("content").iterator(null);
      DocsEnum docs = null;
      DocsAndPositionsEnum docsAndPositions = null;
      DocsAndPositionsEnum docsAndPositionsAndOffsets = null;
      final NumericDocValues docIDToID = DocValues.getNumeric(sub, "id");
      for(String term : terms) {
        //System.out.println("  term=" + term);
        if (termsEnum.seekExact(new BytesRef(term))) {
          docs = termsEnum.docs(null, docs);
          assertNotNull(docs);
          int doc;
          //System.out.println("    doc/freq");
          while((doc = docs.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            final List<Token> expected = actualTokens.get(term).get((int) docIDToID.get(doc));
            //System.out.println("      doc=" + docIDToID.get(doc) + " docID=" + doc + " " + expected.size() + " freq");
            assertNotNull(expected);
            assertEquals(expected.size(), docs.freq());
          }

          // explicitly exclude offsets here
          docsAndPositions = termsEnum.docsAndPositions(null, docsAndPositions, DocsAndPositionsEnum.FLAG_PAYLOADS);
          assertNotNull(docsAndPositions);
          //System.out.println("    doc/freq/pos");
          while((doc = docsAndPositions.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            final List<Token> expected = actualTokens.get(term).get((int) docIDToID.get(doc));
            //System.out.println("      doc=" + docIDToID.get(doc) + " " + expected.size() + " freq");
            assertNotNull(expected);
            assertEquals(expected.size(), docsAndPositions.freq());
            for(Token token : expected) {
              int pos = Integer.parseInt(token.type());
              //System.out.println("        pos=" + pos);
              assertEquals(pos, docsAndPositions.nextPosition());
            }
          }

          docsAndPositionsAndOffsets = termsEnum.docsAndPositions(null, docsAndPositions);
          assertNotNull(docsAndPositionsAndOffsets);
          //System.out.println("    doc/freq/pos/offs");
          while((doc = docsAndPositionsAndOffsets.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            final List<Token> expected = actualTokens.get(term).get((int) docIDToID.get(doc));
            //System.out.println("      doc=" + docIDToID.get(doc) + " " + expected.size() + " freq");
            assertNotNull(expected);
            assertEquals(expected.size(), docsAndPositionsAndOffsets.freq());
            for(Token token : expected) {
              int pos = Integer.parseInt(token.type());
              //System.out.println("        pos=" + pos);
              assertEquals(pos, docsAndPositionsAndOffsets.nextPosition());
              assertEquals(token.startOffset(), docsAndPositionsAndOffsets.startOffset());
              assertEquals(token.endOffset(), docsAndPositionsAndOffsets.endOffset());
            }
          }
        }
      }        
      // TODO: test advance:
    }
    r.close();
    dir.close();
  }
  
  public void testAddFieldTwice() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    FieldTypes fieldTypes = iw.getFieldTypes();
    fieldTypes.enableTermVectors("content3");
    fieldTypes.enableTermVectorPositions("content3");
    fieldTypes.enableTermVectorOffsets("content3");
    fieldTypes.setMultiValued("content3");

    Document doc = iw.newDocument();
    doc.addLargeText("content3", "here is more content with aaa aaa aaa");
    doc.addLargeText("content3", "here is more content with aaa aaa aaa");
    iw.addDocument(doc);
    iw.close();
    dir.close(); // checkindex
  }
  
  // NOTE: the next two tests aren't that good as we need an EvilToken...
  public void testNegativeOffsets() throws Exception {
    try {
      checkTokens(new Token[] { 
          makeToken("foo", 1, -1, -1)
      });
      fail();
    } catch (IllegalArgumentException expected) {
      //expected
    }
  }
  
  public void testIllegalOffsets() throws Exception {
    try {
      checkTokens(new Token[] { 
          makeToken("foo", 1, 1, 0)
      });
      fail();
    } catch (IllegalArgumentException expected) {
      //expected
    }
  }
  
  public void testIllegalOffsetsAcrossFieldInstances() throws Exception {
    try {
      checkTokens(new Token[] { makeToken("use", 1, 150, 160) }, 
                  new Token[] { makeToken("use", 1, 50, 60) });
      fail();
    } catch (IllegalArgumentException expected) {
      //expected
    }
  }
   
  public void testBackwardsOffsets() throws Exception {
    try {
      checkTokens(new Token[] { 
         makeToken("foo", 1, 0, 3),
         makeToken("foo", 1, 4, 7),
         makeToken("foo", 0, 3, 6)
      });
      fail();
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }
  
  public void testStackedTokens() throws Exception {
    checkTokens(new Token[] { 
        makeToken("foo", 1, 0, 3),
        makeToken("foo", 0, 0, 3),
        makeToken("foo", 0, 0, 3)
      });
  }
  
  public void testCrazyOffsetGap() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MockTokenizer(MockTokenizer.KEYWORD, false));
      }

      @Override
      public int getOffsetGap(String fieldName) {
        return -10;
      }
    };
    IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig(analyzer));
    // add good document
    Document doc = iw.newDocument();
    iw.addDocument(doc);
    try {
      doc.addLargeText("foo", "bar");
      doc.addLargeText("foo", "bar");
      iw.addDocument(doc);
      fail("didn't get expected exception");
    } catch (IllegalArgumentException expected) {}
    iw.close();

    // make sure we see our good doc
    DirectoryReader r = DirectoryReader.open(dir);   
    assertEquals(1, r.numDocs());
    r.close();
    dir.close();
  }

  public void testLegalbutVeryLargeOffsets() throws Exception {
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(null));
    Document doc = iw.newDocument();
    Token t1 = new Token("foo", 0, Integer.MAX_VALUE-500);
    if (random().nextBoolean()) {
      t1.setPayload(new BytesRef("test"));
    }
    Token t2 = new Token("foo", Integer.MAX_VALUE-500, Integer.MAX_VALUE);
    TokenStream tokenStream = new CannedTokenStream(
        new Token[] { t1, t2 }
    );
    FieldTypes fieldTypes = iw.getFieldTypes();
    // store some term vectors for the checkindex cross-check
    fieldTypes.enableTermVectors("foo");
    fieldTypes.enableTermVectorPositions("foo");
    fieldTypes.enableTermVectorOffsets("foo");
    doc.addLargeText("foo", tokenStream);
    iw.addDocument(doc);
    iw.close();
    dir.close();
  }
  // TODO: more tests with other possibilities
  
  private void checkTokens(Token[] field1, Token[] field2) throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter riw = new RandomIndexWriter(random(), dir, iwc);
    FieldTypes fieldTypes = riw.getFieldTypes();

    boolean success = false;
    try {
      // store some term vectors for the checkindex cross-check
      fieldTypes.enableTermVectors("body");
      fieldTypes.enableTermVectorPositions("body");
      fieldTypes.enableTermVectorOffsets("body");
     
      Document doc = riw.newDocument();
      doc.addLargeText("body", new CannedTokenStream(field1));
      doc.addLargeText("body", new CannedTokenStream(field2));
      riw.addDocument(doc);
      riw.close();
      success = true;
    } finally {
      if (success) {
        IOUtils.close(dir);
      } else {
        IOUtils.closeWhileHandlingException(riw, dir);
      }
    }
  }
  
  private void checkTokens(Token[] tokens) throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter riw = new RandomIndexWriter(random(), dir, iwc);
    FieldTypes fieldTypes = riw.getFieldTypes();
    boolean success = false;
    try {
      // store some term vectors for the checkindex cross-check
      fieldTypes.enableTermVectors("body");
      fieldTypes.enableTermVectorPositions("body");
      fieldTypes.enableTermVectorOffsets("body");
     
      Document doc = riw.newDocument();
      doc.addLargeText("body", new CannedTokenStream(tokens));
      riw.addDocument(doc);
      riw.close();
      success = true;
    } finally {
      if (success) {
        IOUtils.close(dir);
      } else {
        IOUtils.closeWhileHandlingException(riw, dir);
      }
    }
  }

  private Token makeToken(String text, int posIncr, int startOffset, int endOffset) {
    final Token t = new Token();
    t.append(text);
    t.setPositionIncrement(posIncr);
    t.setOffset(startOffset, endOffset);
    return t;
  }
}
