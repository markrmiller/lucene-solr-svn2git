package org.apache.lucene.analysis.no;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.util.CharArraySet;

import static org.apache.lucene.analysis.VocabularyAssert.*;
import static org.apache.lucene.analysis.no.NorwegianLightStemmer.BOKMAAL;
import static org.apache.lucene.analysis.no.NorwegianLightStemmer.NYNORSK;

/**
 * Simple tests for {@link NorwegianMinimalStemFilter}
 */
public class TestNorwegianMinimalStemFilter extends BaseTokenStreamTestCase {
  private Analyzer analyzer = new Analyzer() {
    @Override
    protected TokenStreamComponents createComponents(String fieldName,
        Reader reader) {
      Tokenizer source = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
      return new TokenStreamComponents(source, new NorwegianMinimalStemFilter(source, BOKMAAL));
    }
  };
  
  /** Test against a Bokmål vocabulary file */
  public void testVocabulary() throws IOException {
    assertVocabulary(analyzer, new FileInputStream(getDataFile("nb_minimal.txt")));
  }
  
  /** Test against a Nynorsk vocabulary file */
  public void testNynorskVocabulary() throws IOException {  
    Analyzer analyzer = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer source = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(source, new NorwegianMinimalStemFilter(source, NYNORSK));
      }
    };
    assertVocabulary(analyzer, new FileInputStream(getDataFile("nn_minimal.txt")));
  }
  
  public void testKeyword() throws IOException {
    final CharArraySet exclusionSet = new CharArraySet( asSet("sekretæren"), false);
    Analyzer a = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer source = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
        TokenStream sink = new SetKeywordMarkerFilter(source, exclusionSet);
        return new TokenStreamComponents(source, new NorwegianMinimalStemFilter(sink));
      }
    };
    checkOneTerm(a, "sekretæren", "sekretæren");
  }

  /** blast some random strings through the analyzer */
  public void testRandomStrings() throws Exception {
    Random random = random();
    checkRandomData(random, analyzer, 1000*RANDOM_MULTIPLIER);
  }
  
  public void testEmptyTerm() throws IOException {
    Analyzer a = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new KeywordTokenizer(reader);
        return new TokenStreamComponents(tokenizer, new NorwegianMinimalStemFilter(tokenizer));
      }
    };
    checkOneTerm(a, "", "");
  }
}
