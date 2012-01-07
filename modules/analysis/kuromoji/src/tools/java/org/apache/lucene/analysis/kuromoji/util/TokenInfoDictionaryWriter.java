package org.apache.lucene.analysis.kuromoji.util;

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
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.lucene.analysis.kuromoji.dict.TokenInfoDictionary;
import org.apache.lucene.analysis.kuromoji.trie.DoubleArrayTrie;

public class TokenInfoDictionaryWriter extends BinaryDictionaryWriter {

  private DoubleArrayTrie trie;

  public TokenInfoDictionaryWriter(int size) {
    super(TokenInfoDictionary.class, size);
    this.trie = trie;
  }
  
  public void setTrie(DoubleArrayTrie trie) {
    this.trie = trie;
  }
  
  @Override
  public void write(String baseDir) throws IOException {
    super.write(baseDir);
    writeDoubleArrayTrie(getBaseFileName(baseDir) + TokenInfoDictionary.TRIE_FILENAME_SUFFIX);
  }
  
  protected void writeDoubleArrayTrie(String filename) throws IOException  {
    new File(filename).getParentFile().mkdirs();
    final FileOutputStream os = new FileOutputStream(filename);
    try {
      trie.write(os);
    } finally {
      os.close();
    }
  }
  
}
