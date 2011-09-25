package org.apache.lucene.analysis.pt;

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

import static org.apache.lucene.analysis.VocabularyAssert.assertVocabulary;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Simple tests for {@link PortugueseStemFilter}
 */
public class TestPortugueseStemFilter extends BaseTokenStreamTestCase {
  private Analyzer analyzer = new Analyzer() {
    @Override
    protected TokenStreamComponents createComponents(String fieldName,
        Reader reader) {
      Tokenizer source = new StandardTokenizer(TEST_VERSION_CURRENT, reader);
      TokenStream result = new LowerCaseFilter(TEST_VERSION_CURRENT, source);
      return new TokenStreamComponents(source, new PortugueseStemFilter(result));
    }
  };
  
  /**
   * Test the example from the paper "Assessing the impact of stemming accuracy
   * on information retrieval"
   */
  public void testExamples() throws IOException {
    assertAnalyzesTo(
        analyzer,
    "O debate político, pelo menos o que vem a público, parece, de modo nada "
    + "surpreendente, restrito a temas menores. Mas há, evidentemente, "
    + "grandes questões em jogo nas eleições que se aproximam.",
    new String[] { 
      "o", "debat", "politic", "pel", "menos", "o", "que", "vem", "a", 
      "public", "parec", "de", "mod", "nad", "surpreend", "restrit",
      "a", "tem", "men", "mas", "ha", "evid", "grand", "quest",
      "em", "jog", "na", "eleic", "que", "se", "aproxim"
    });
  }
  
  /** Test against a vocabulary from the reference impl */
  public void testVocabulary() throws IOException {
    assertVocabulary(analyzer, getDataFile("ptrslptestdata.zip"), "ptrslp.txt");
  }
  
  /** blast some random strings through the analyzer */
  public void testRandomStrings() throws Exception {
    checkRandomData(random, analyzer, 10000*RANDOM_MULTIPLIER);
  }
}
