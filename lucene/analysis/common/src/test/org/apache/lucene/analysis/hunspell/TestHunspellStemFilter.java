package org.apache.lucene.analysis.hunspell;

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
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.HunspellStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestHunspellStemFilter extends BaseTokenStreamTestCase {
  private static Dictionary dictionary;
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    InputStream affixStream = TestStemmer.class.getResourceAsStream("simple.aff");
    InputStream dictStream = TestStemmer.class.getResourceAsStream("simple.dic");
    try {
      dictionary = new Dictionary(affixStream, dictStream);
    } finally {
      IOUtils.closeWhileHandlingException(affixStream, dictStream);
    }
  }
  
  @AfterClass
  public static void afterClass() {
    dictionary = null;
  }
  
  /** Simple test for KeywordAttribute */
  public void testKeywordAttribute() throws IOException {
    MockTokenizer tokenizer = new MockTokenizer(new StringReader("lucene is awesome"));
    tokenizer.setEnableChecks(true);
    HunspellStemFilter filter = new HunspellStemFilter(tokenizer, dictionary);
    assertTokenStreamContents(filter, new String[]{"lucene", "lucen", "is", "awesome"}, new int[] {1, 0, 1, 1});
    
    // assert with keyword marker
    tokenizer = new MockTokenizer(new StringReader("lucene is awesome"));
    CharArraySet set = new CharArraySet(TEST_VERSION_CURRENT, Arrays.asList("Lucene"), true);
    filter = new HunspellStemFilter(new SetKeywordMarkerFilter(tokenizer, set), dictionary);
    assertTokenStreamContents(filter, new String[]{"lucene", "is", "awesome"}, new int[] {1, 1, 1});
  }
  
  /** simple test for longestOnly option */
  public void testLongestOnly() throws IOException {
    MockTokenizer tokenizer = new MockTokenizer(new StringReader("lucene is awesome"));
    tokenizer.setEnableChecks(true);
    HunspellStemFilter filter = new HunspellStemFilter(tokenizer, dictionary, true, true);
    assertTokenStreamContents(filter, new String[]{"lucene", "is", "awesome"}, new int[] {1, 1, 1});
  }
  
  /** blast some random strings through the analyzer */
  public void testRandomStrings() throws Exception {
    Analyzer analyzer = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(tokenizer, new HunspellStemFilter(tokenizer, dictionary));
      }
    };
    checkRandomData(random(), analyzer, 1000*RANDOM_MULTIPLIER);
  }
  
  public void testEmptyTerm() throws IOException {
    Analyzer a = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new KeywordTokenizer(reader);
        return new TokenStreamComponents(tokenizer, new HunspellStemFilter(tokenizer, dictionary));
      }
    };
    checkOneTerm(a, "", "");
  }
}
