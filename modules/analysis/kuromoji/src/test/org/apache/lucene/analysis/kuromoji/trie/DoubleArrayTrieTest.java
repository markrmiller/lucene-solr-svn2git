package org.apache.lucene.analysis.kuromoji.trie;

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
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.lucene.analysis.kuromoji.trie.DoubleArrayTrie;
import org.apache.lucene.analysis.kuromoji.trie.Trie;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

public class DoubleArrayTrieTest extends LuceneTestCase {
  
  @Test
  public void testBuild() {		
    Trie trie = getTrie();
    DoubleArrayTrie doubleArrayTrie = new DoubleArrayTrie();
    doubleArrayTrie.build(trie);
  }
  
  @Test
  public void testWrite() throws IOException {
    Trie trie = getTrie();
    
    DoubleArrayTrie doubleArrayTrie = new DoubleArrayTrie();
    doubleArrayTrie.build(trie);
    
    try{
      doubleArrayTrie.write("/some/path/which/is/not/exist");
      fail();
    }catch(IOException e){
      
    }
    
    // nocommit: lets use TEMPDIR here
    String tmpDir = System.getProperty("java.io.tmpdir");
    File dir = new File(tmpDir + File.separator + "datmp");
    dir.mkdir();
    doubleArrayTrie.write(dir.getCanonicalPath());
    dir.deleteOnExit();
    for(File file : dir.listFiles()) {
      file.deleteOnExit();
    }
    
    assertTrue(dir.length() > 0);
    
  }
  
  @Test
  public void testLookup() throws IOException {
    Trie trie = getTrie();
    
    DoubleArrayTrie doubleArrayTrie = new DoubleArrayTrie();
    doubleArrayTrie.build(trie);
    
    // nocommit: lets use TEMPDIR here
    String tmpDir = System.getProperty("java.io.tmpdir");
    File dir = new File(tmpDir + File.separator + "datmp");
    dir.mkdir();
    doubleArrayTrie.write(dir.getCanonicalPath());
    dir.deleteOnExit();
    for(File file : dir.listFiles()) {
      file.deleteOnExit();
    }
    
    doubleArrayTrie = DoubleArrayTrie.read(new FileInputStream(dir.getCanonicalPath() + File.separator + DoubleArrayTrie.FILENAME));
    
    assertEquals(0, doubleArrayTrie.lookup("a"));
    assertTrue(doubleArrayTrie.lookup("abc") > 0);
    assertTrue(doubleArrayTrie.lookup("あいう") > 0);
    assertTrue(doubleArrayTrie.lookup("xyz") < 0);
    
  }
  
  private Trie getTrie() {
    Trie trie = new Trie();
    trie.add("abc");
    trie.add("abd");
    trie.add("あああ");
    trie.add("あいう");
    return trie;
  }
  
  
}
