package org.apache.lucene.analysis.pl;

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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.KeywordMarkerFilter;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.analysis.stempel.StempelFilter;
import org.apache.lucene.util.Version;
import org.egothor.stemmer.Trie;

/**
 * {@link Analyzer} for Polish.
 */
public final class PolishAnalyzer extends StopwordAnalyzerBase {
  private final Set<?> stemExclusionSet;
  private final Trie stemTable;
  
  /** File containing default Polish stopwords. */
  public final static String DEFAULT_STOPWORD_FILE = "stopwords.txt";
  
  /**
   * Returns an unmodifiable instance of the default stop words set.
   * @return default stop words set.
   */
  public static Set<?> getDefaultStopSet(){
    return DefaultsHolder.DEFAULT_STOP_SET;
  }
  
  /**
   * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class 
   * accesses the static final set the first time.;
   */
  private static class DefaultsHolder {
    static final Set<?> DEFAULT_STOP_SET;
    static final Trie DEFAULT_TABLE;
    
    static {
      try {
        DEFAULT_STOP_SET = WordlistLoader.getWordSet(PolishAnalyzer.class, 
            DEFAULT_STOPWORD_FILE);
      } catch (IOException ex) {
        // default set should always be present as it is part of the
        // distribution (JAR)
        throw new RuntimeException("Unable to load default stopword set", ex);
      }
      
      InputStream stream = PolishAnalyzer.class.getResourceAsStream("stemmer_20000.tbl");
      try {
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream));
        String method = in.readUTF().toUpperCase();
        if (method.indexOf('M') < 0) {
          DEFAULT_TABLE = new org.egothor.stemmer.Trie(in);
        } else {
          DEFAULT_TABLE = new org.egothor.stemmer.MultiTrie2(in);
        }
        in.close();
      } catch (IOException ex) {
        // default set should always be present as it is part of the
        // distribution (JAR)
        throw new RuntimeException("Unable to load default stemming tables", ex);
      }
    }
  }

  /**
   * Builds an analyzer with the default stop words: {@link #DEFAULT_STOPWORD_FILE}.
   */
  public PolishAnalyzer(Version matchVersion) {
    this(matchVersion, DefaultsHolder.DEFAULT_STOP_SET);
  }
  
  /**
   * Builds an analyzer with the given stop words.
   * 
   * @param matchVersion lucene compatibility version
   * @param stopwords a stopword set
   */
  public PolishAnalyzer(Version matchVersion, Set<?> stopwords) {
    this(matchVersion, stopwords, CharArraySet.EMPTY_SET);
  }

  /**
   * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is
   * provided this analyzer will add a {@link KeywordMarkerTokenFilter} before
   * stemming.
   * 
   * @param matchVersion lucene compatibility version
   * @param stopwords a stopword set
   * @param stemExclusionSet a set of terms not to be stemmed
   */
  public PolishAnalyzer(Version matchVersion, Set<?> stopwords, Set<?> stemExclusionSet) {
    super(matchVersion, stopwords);
    this.stemTable = DefaultsHolder.DEFAULT_TABLE;
    this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(
        matchVersion, stemExclusionSet));
  }

  /**
   * Creates a
   * {@link org.apache.lucene.analysis.ReusableAnalyzerBase.TokenStreamComponents}
   * which tokenizes all the text in the provided {@link Reader}.
   * 
   * @return A
   *         {@link org.apache.lucene.analysis.ReusableAnalyzerBase.TokenStreamComponents}
   *         built from an {@link StandardTokenizer} filtered with
   *         {@link StandardFilter}, {@link LowerCaseFilter}, {@link StopFilter}
   *         , {@link KeywordMarkerFilter} if a stem exclusion set is
   *         provided and {@link StempelFilter}.
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName,
      Reader reader) {
    final Tokenizer source = new StandardTokenizer(matchVersion, reader);
    TokenStream result = new StandardFilter(matchVersion, source);
    result = new LowerCaseFilter(matchVersion, result);
    result = new StopFilter(matchVersion, result, stopwords);
    if(!stemExclusionSet.isEmpty())
      result = new KeywordMarkerFilter(result, stemExclusionSet);
    result = new StempelFilter(result, new StempelStemmer(stemTable));
    return new TokenStreamComponents(source, result);
  }
}
