package org.apache.lucene.analysis.bg;

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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;

/**
 * Test the Bulgarian analyzer
 */
public class TestBulgarianAnalyzer extends BaseTokenStreamTestCase {
  
  /**
   * This test fails with NPE when the stopwords file is missing in classpath
   */
  public void testResourcesAvailable() {
    new BulgarianAnalyzer();
  }
  
  public void testStopwords() throws IOException {
    Analyzer a = new BulgarianAnalyzer();
    assertAnalyzesTo(a, "Как се казваш?", new String[] {"казваш"});
  }
  
  public void testCustomStopwords() throws IOException {
    Analyzer a = new BulgarianAnalyzer(CharArraySet.EMPTY_SET);
    assertAnalyzesTo(a, "Как се казваш?", 
        new String[] {"как", "се", "казваш"});
  }
  
  public void testReusableTokenStream() throws IOException {
    Analyzer a = new BulgarianAnalyzer();
    assertAnalyzesTo(a, "документи", new String[] {"документ"});
    assertAnalyzesTo(a, "документ", new String[] {"документ"});
  }
  
  /**
   * Test some examples from the paper
   */
  public void testBasicExamples() throws IOException {
    Analyzer a = new BulgarianAnalyzer();
    assertAnalyzesTo(a, "енергийни кризи", new String[] {"енергийн", "криз"});
    assertAnalyzesTo(a, "Атомната енергия", new String[] {"атомн", "енерг"});
    
    assertAnalyzesTo(a, "компютри", new String[] {"компютр"});
    assertAnalyzesTo(a, "компютър", new String[] {"компютр"});
    
    assertAnalyzesTo(a, "градове", new String[] {"град"});
  }
  
  public void testWithStemExclusionSet() throws IOException {
    CharArraySet set = new CharArraySet(1, true);
    set.add("строеве");
    Analyzer a = new BulgarianAnalyzer(CharArraySet.EMPTY_SET, set);
    assertAnalyzesTo(a, "строевете строеве", new String[] { "строй", "строеве" });
  }
  
  /** blast some random strings through the analyzer */
  public void testRandomStrings() throws Exception {
    checkRandomData(random(), new BulgarianAnalyzer(), 1000*RANDOM_MULTIPLIER);
  }

  public void testBackcompat40() throws IOException {
    BulgarianAnalyzer a = new BulgarianAnalyzer();
    a.setVersion(Version.LUCENE_4_6_1);
    // this is just a test to see the correct unicode version is being used, not actually testing hebrew
    assertAnalyzesTo(a, "א\"א", new String[] {"א", "א"});
  }
}
