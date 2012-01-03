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

import org.apache.lucene.analysis.kuromoji.trie.Trie;
import org.apache.lucene.analysis.kuromoji.trie.Trie.Node;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

public class TrieTest extends LuceneTestCase {
  
  @Test
  public void testGetRoot() {
    Trie trie = new Trie();
    Node rootNode = trie.getRoot();
    assertNotNull(rootNode);
  }
  
  @Test
  public void testAdd() {
    Trie trie = new Trie();
    trie.add("aa");
    trie.add("ab");
    trie.add("bb");
    
    Node rootNode = trie.getRoot();
    assertEquals(2, rootNode.getChildren().length);
    assertEquals(2, rootNode.getChildren()[0].getChildren().length);
    assertEquals(1, rootNode.getChildren()[1].getChildren().length);
  }
  
  @Test
  public void testGetChildren() {
    Trie trie = new Trie();
    trie.add("aa");
    trie.add("ab");
    trie.add("bb");
    
    Node rootNode = trie.getRoot();
    assertEquals(2, rootNode.getChildren().length);
    assertEquals(2, rootNode.getChildren()[0].getChildren().length);
    assertEquals(1, rootNode.getChildren()[1].getChildren().length);
  }
  
  @Test
  public void testSinglePath() {
    Trie trie = new Trie();
    assertTrue(trie.getRoot().hasSinglePath());
    trie.add("abcdef");
    assertTrue(trie.getRoot().hasSinglePath());
    trie.add("abdfg");
    Node rootNode = trie.getRoot();
    assertEquals(2, rootNode.getChildren()[0].getChildren()[0].getChildren().length);
    assertTrue(rootNode.getChildren()[0].getChildren()[0].getChildren()[0].hasSinglePath());
    assertTrue(rootNode.getChildren()[0].getChildren()[0].getChildren()[1].hasSinglePath());
  }
}
