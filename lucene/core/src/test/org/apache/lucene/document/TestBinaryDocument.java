package org.apache.lucene.document;

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

import java.nio.charset.StandardCharsets;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Tests {@link Document} class.
 */
public class TestBinaryDocument extends LuceneTestCase {

  String binaryValStored = "this text will be stored as a byte array in the index";
  String binaryValCompressed = "this text will be also stored and compressed as a byte array in the index";
  
  public void testBinaryFieldInIndex()
    throws Exception
  {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    Document2 doc = writer.newDocument();
    
    doc.addStored("binaryStored", binaryValStored.getBytes(StandardCharsets.UTF_8));
    doc.addStored("stringStored", binaryValStored);

    /** test for field count */
    assertEquals(2, doc.getFields().size());
    
    /** add the doc to a ram index */
    writer.addDocument(doc);
    
    /** open a reader and fetch the document */ 
    IndexReader reader = writer.getReader();
    Document2 docFromReader = reader.document(0);
    assertTrue(docFromReader != null);
    
    /** fetch the binary stored field and compare it's content with the original one */
    BytesRef bytes = docFromReader.getBinary("binaryStored");
    assertNotNull(bytes);
    String binaryFldStoredTest = new String(bytes.bytes, bytes.offset, bytes.length, StandardCharsets.UTF_8);
    assertTrue(binaryFldStoredTest.equals(binaryValStored));
    
    /** fetch the string field and compare it's content with the original one */
    String stringFldStoredTest = docFromReader.getString("stringStored");
    assertTrue(stringFldStoredTest.equals(binaryValStored));
    
    writer.close();
    reader.close();
    dir.close();
  }
  
  public void testCompressionTools() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    Document2 doc = writer.newDocument();
    
    doc.addStored("binaryCompressed", CompressionTools.compress(binaryValCompressed.getBytes(StandardCharsets.UTF_8)));
    doc.addStored("stringCompressed", CompressionTools.compressString(binaryValCompressed));
    
    /** add the doc to a ram index */
    writer.addDocument(doc);
    
    /** open a reader and fetch the document */ 
    IndexReader reader = writer.getReader();
    Document2 docFromReader = reader.document(0);
    assertTrue(docFromReader != null);
    
    /** fetch the binary compressed field and compare it's content with the original one */
    String binaryFldCompressedTest = new String(CompressionTools.decompress(docFromReader.getBinary("binaryCompressed")), StandardCharsets.UTF_8);
    assertTrue(binaryFldCompressedTest.equals(binaryValCompressed));
    assertTrue(CompressionTools.decompressString(docFromReader.getBinary("stringCompressed")).equals(binaryValCompressed));

    writer.close();
    reader.close();
    dir.close();
  }
}
