package org.apache.solr.spelling;

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
import java.io.Reader;
import java.util.Collection;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CannedTokenStream;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.solr.analysis.TrimFilter;
import org.apache.solr.analysis.PatternReplaceFilter;

public class TestSuggestSpellingConverter extends BaseTokenStreamTestCase {
  SuggestQueryConverter converter = new SuggestQueryConverter();
  
  public void testSimple() throws Exception {
    // lowercases only!
    converter.setAnalyzer(new MockAnalyzer(random, MockTokenizer.KEYWORD, true));
    assertConvertsTo("This is a test", new String[] { "this is a test" });
  }
  
  public void testComplicated() throws Exception {
    // lowercases, removes field names, other syntax, collapses runs of whitespace, etc.
    converter.setAnalyzer(new ReusableAnalyzerBase() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new KeywordTokenizer(reader);
        TokenStream filter = new PatternReplaceFilter(tokenizer, 
            Pattern.compile("([^\\p{L}\\p{M}\\p{N}\\p{Cs}]*[\\p{L}\\p{M}\\p{N}\\p{Cs}\\_]+:)|([^\\p{L}\\p{M}\\p{N}\\p{Cs}])+"), " ", true);
        filter = new LowerCaseFilter(TEST_VERSION_CURRENT, filter);
        filter = new TrimFilter(filter, false);
        return new TokenStreamComponents(tokenizer, filter);
      }
    });
    assertConvertsTo("test1 +test2", new String[] { "test1 test2" });
    assertConvertsTo("test~", new String[] { "test" });
    assertConvertsTo("field:test", new String[] { "test" });
    assertConvertsTo("This is a test", new String[] { "this is a test" });
    assertConvertsTo(" This is  a test", new String[] { "this is a test" });
    assertConvertsTo("Foo (field:bar) text_hi:हिन्दी    ", new String[] { "foo bar हिन्दी" });
  }
  
  public void assertConvertsTo(String text, String expected[]) throws IOException {
    Collection<Token> tokens = converter.convert(text);
    TokenStream ts = new CannedTokenStream(tokens.toArray(new Token[0]));
    assertTokenStreamContents(ts, expected);
  }
}
