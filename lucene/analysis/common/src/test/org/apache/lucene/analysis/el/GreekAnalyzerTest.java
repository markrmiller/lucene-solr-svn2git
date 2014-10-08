package org.apache.lucene.analysis.el;

/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import org.apache.lucene.util.Version;

/**
 * A unit test class for verifying the correct operation of the GreekAnalyzer.
 *
 */
public class GreekAnalyzerTest extends BaseTokenStreamTestCase {

  /**
   * Test the analysis of various greek strings.
   *
   * @throws Exception in case an error occurs
   */
  public void testAnalyzer() throws Exception {
    Analyzer a = new GreekAnalyzer();
    // Verify the correct analysis of capitals and small accented letters, and
    // stemming
    assertAnalyzesTo(a, "Μία εξαιρετικά καλή και πλούσια σειρά χαρακτήρων της Ελληνικής γλώσσας",
        new String[] { "μια", "εξαιρετ", "καλ", "πλουσ", "σειρ", "χαρακτηρ",
        "ελληνικ", "γλωσσ" });
    // Verify the correct analysis of small letters with diaeresis and the elimination
    // of punctuation marks
    assertAnalyzesTo(a, "Προϊόντα (και)     [πολλαπλές] - ΑΝΑΓΚΕΣ",
        new String[] { "προιοντ", "πολλαπλ", "αναγκ" });
    // Verify the correct analysis of capital accented letters and capital letters with diaeresis,
    // as well as the elimination of stop words
    assertAnalyzesTo(a, "ΠΡΟΫΠΟΘΕΣΕΙΣ  Άψογος, ο μεστός και οι άλλοι",
        new String[] { "προυποθεσ", "αψογ", "μεστ", "αλλ" });
  }

  public void testReusableTokenStream() throws Exception {
    Analyzer a = new GreekAnalyzer();
    // Verify the correct analysis of capitals and small accented letters, and
    // stemming
    assertAnalyzesTo(a, "Μία εξαιρετικά καλή και πλούσια σειρά χαρακτήρων της Ελληνικής γλώσσας",
        new String[] { "μια", "εξαιρετ", "καλ", "πλουσ", "σειρ", "χαρακτηρ",
        "ελληνικ", "γλωσσ" });
    // Verify the correct analysis of small letters with diaeresis and the elimination
    // of punctuation marks
    assertAnalyzesTo(a, "Προϊόντα (και)     [πολλαπλές] - ΑΝΑΓΚΕΣ",
        new String[] { "προιοντ", "πολλαπλ", "αναγκ" });
    // Verify the correct analysis of capital accented letters and capital letters with diaeresis,
    // as well as the elimination of stop words
    assertAnalyzesTo(a, "ΠΡΟΫΠΟΘΕΣΕΙΣ  Άψογος, ο μεστός και οι άλλοι",
        new String[] { "προυποθεσ", "αψογ", "μεστ", "αλλ" });
  }
  
  /** blast some random strings through the analyzer */
  public void testRandomStrings() throws Exception {
    checkRandomData(random(), new GreekAnalyzer(), 1000*RANDOM_MULTIPLIER);
  }

  public void testBackcompat40() throws IOException {
    GreekAnalyzer a = new GreekAnalyzer();
    a.setVersion(Version.LUCENE_4_6_1);
    // this is just a test to see the correct unicode version is being used, not actually testing hebrew
    assertAnalyzesTo(a, "א\"א", new String[] {"א", "א"});
  }
}
