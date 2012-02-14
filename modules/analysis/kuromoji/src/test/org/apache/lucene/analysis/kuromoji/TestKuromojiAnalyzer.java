package org.apache.lucene.analysis.kuromoji;

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
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.kuromoji.Segmenter.Mode;

public class TestKuromojiAnalyzer extends BaseTokenStreamTestCase {
  /** This test fails with NPE when the 
   * stopwords file is missing in classpath */
  public void testResourcesAvailable() {
    new KuromojiAnalyzer(TEST_VERSION_CURRENT);
  }
  
  /**
   * An example sentence, test removal of particles, etc by POS,
   * lemmatization with the basic form, and that position increments
   * and offsets are correct.
   */
  public void testBasics() throws IOException {
    assertAnalyzesTo(new KuromojiAnalyzer(TEST_VERSION_CURRENT), "多くの学生が試験に落ちた。",
        new String[] { "多く", "学生", "試験", "落ちる" },
        new int[] { 0, 3, 6,  9 },
        new int[] { 2, 5, 8, 11 },
        new int[] { 1, 2, 2,  2 }
      );
  }

  /**
   * Test that search mode is enabled and working by default
   */
  public void testDecomposition() throws IOException {

    // Senior software engineer:
    assertAnalyzesTo(new KuromojiAnalyzer(TEST_VERSION_CURRENT), "シニアソフトウェアエンジニア",
                     new String[] { "シニア",
                                    "ソフトウェア",
                                    "エンジニア" },
                     new int[] { 1, 1, 1}
                     );
    // Kansai International Airport:
    assertAnalyzesTo(new KuromojiAnalyzer(TEST_VERSION_CURRENT), "関西国際空港",
                     new String[] { "関西",
                                    "国際",
                                    "空港" },
                     new int[] {1, 1, 1}
                     );

    // Konika Minolta Holdings; not quite the right
    // segmentation (see LUCENE-3726):
    assertAnalyzesTo(new KuromojiAnalyzer(TEST_VERSION_CURRENT), "コニカミノルタホールディングス",
                     new String[] { "コニカ",
                                    "ミノルタ", 
                                    "ホールディングス"},
                     new int[] {1, 1, 1}
                     );

    // Narita Airport
    assertAnalyzesTo(new KuromojiAnalyzer(TEST_VERSION_CURRENT), "成田空港",
                     new String[] { "成田",
                                    "空港" },
                     new int[] {1, 1});

    // Kyoto University Baseball Club
    assertAnalyzesTo(new KuromojiAnalyzer(TEST_VERSION_CURRENT), "京都大学硬式野球部",
                     new String[] { "京都",
                                    "大学",
                                    "硬式",
                                    "野球",
                                    "部" },
                     new int[] {1, 1, 1, 1, 1});
  }

  public void testDecompositionWithCompounds() throws IOException {

    final Analyzer a = new KuromojiAnalyzer(TEST_VERSION_CURRENT, null, Mode.SEARCH_WITH_COMPOUNDS,
                                            KuromojiAnalyzer.getDefaultStopSet(),
                                            KuromojiAnalyzer.getDefaultStopTags());

    /*
    TokenStream ts = a.tokenStream("foo", new StringReader("京都大学硬式野球部"));
    while(ts.incrementToken());
    */

    // Senior software engineer:
    assertAnalyzesTo(a, "シニアソフトウェアエンジニア",
                     new String[] { "シニア",
                                    "シニアソフトウェアエンジニア",
                                    "ソフトウェア",
                                    "エンジニア" },
                     new int[] { 1, 0, 1, 1}
                     );

    // Kansai International Airport:
    assertAnalyzesTo(a, "関西国際空港",
                     new String[] { "関西",
                                    "関西国際空港", // zero pos inc
                                    "国際",
                                    "空港" },
                     new int[] {1, 0, 1, 1}
                     );

    // Konika Minolta Holdings; not quite the right
    // segmentation (see LUCENE-3726):
    assertAnalyzesTo(a, "コニカミノルタホールディングス",
                     new String[] { "コニカ",
                                    "コニカミノルタホールディングス", // zero pos inc
                                    "ミノルタ", 
                                    "ホールディングス"},
                     new int[] {1, 0, 1, 1}
                     );

    // Narita Airport
    assertAnalyzesTo(a, "成田空港",
                     new String[] { "成田",
                                    "成田空港",
                                    "空港" },
                     new int[] {1, 0, 1});

  }

  
  /**
   * blast random strings against the analyzer
   */
  public void testRandom() throws IOException {
    final Analyzer a = new KuromojiAnalyzer(TEST_VERSION_CURRENT, null, Mode.SEARCH,
                                            KuromojiAnalyzer.getDefaultStopSet(),
                                            KuromojiAnalyzer.getDefaultStopTags());
    checkRandomData(random, a, atLeast(10000));
  }

  public void testRandomWithCompounds() throws IOException {
    final Analyzer a = new KuromojiAnalyzer(TEST_VERSION_CURRENT, null, Mode.SEARCH_WITH_COMPOUNDS,
                                            KuromojiAnalyzer.getDefaultStopSet(),
                                            KuromojiAnalyzer.getDefaultStopTags());
    checkRandomData(random, a, atLeast(10000));
  }
}
