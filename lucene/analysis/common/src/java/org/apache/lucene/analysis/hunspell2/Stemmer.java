package org.apache.lucene.analysis.hunspell2;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/**
 * Stemmer uses the affix rules declared in the Dictionary to generate one or more stems for a word.  It
 * conforms to the algorithm in the original hunspell algorithm, including recursive suffix stripping.
 */
final class Stemmer {
  private final int recursionCap;
  private final Dictionary dictionary;
  private BytesRef scratch = new BytesRef();
  private final StringBuilder segment = new StringBuilder();

  /**
   * Constructs a new Stemmer which will use the provided Dictionary to create its stems. Uses the 
   * default recursion cap of <code>2</code> (based on Hunspell documentation). 
   *
   * @param dictionary Dictionary that will be used to create the stems
   */
  public Stemmer(Dictionary dictionary) {
    this(dictionary, 2);
  }

  /**
   * Constructs a new Stemmer which will use the provided Dictionary to create its stems.
   *
   * @param dictionary Dictionary that will be used to create the stems
   * @param recursionCap maximum level of recursion stemmer can go into
   */
  public Stemmer(Dictionary dictionary, int recursionCap) {
    this.dictionary = dictionary;
    this.recursionCap = recursionCap;
  } 
  
  /**
   * Find the stem(s) of the provided word.
   * 
   * @param word Word to find the stems for
   * @return List of stems for the word
   */
  public List<Stem> stem(String word) {
    return stem(word.toCharArray(), word.length());
  }

  /**
   * Find the stem(s) of the provided word
   * 
   * @param word Word to find the stems for
   * @return List of stems for the word
   */
  public List<Stem> stem(char word[], int length) {
    List<Stem> stems = new ArrayList<Stem>();
    if (dictionary.lookupWord(word, 0, length, scratch) != null) {
      stems.add(new Stem(word, length));
    }
    stems.addAll(stem(word, length, null, 0));
    return stems;
  }
  
  /**
   * Find the unique stem(s) of the provided word
   * 
   * @param word Word to find the stems for
   * @return List of stems for the word
   */
  public List<Stem> uniqueStems(char word[], int length) {
    List<Stem> stems = new ArrayList<Stem>();
    CharArraySet terms = new CharArraySet(Version.LUCENE_CURRENT, 8, false);
    if (dictionary.lookupWord(word, 0, length, scratch) != null) {
      stems.add(new Stem(word, length));
      terms.add(word);
    }
    List<Stem> otherStems = stem(word, length, null, 0);
    for (Stem s : otherStems) {
      if (!terms.contains(s.stem)) {
        stems.add(s);
        terms.add(s.stem);
      }
    }
    return stems;
  }

  // ================================================= Helper Methods ================================================

  /**
   * Generates a list of stems for the provided word
   *
   * @param word Word to generate the stems for
   * @param flags Flags from a previous stemming step that need to be cross-checked with any affixes in this recursive step
   * @param recursionDepth Level of recursion this stemming step is at
   * @return List of stems, or empty list if no stems are found
   */
  private List<Stem> stem(char word[], int length, char[] flags, int recursionDepth) {
    List<Stem> stems = new ArrayList<Stem>();

    for (int i = 0; i < length; i++) {
      List<Affix> suffixes = dictionary.lookupSuffix(word, i, length - i);
      if (suffixes == null) {
        continue;
      }

      for (Affix suffix : suffixes) {
        if (hasCrossCheckedFlag(suffix.getFlag(), flags)) {
          int appendLength = length - i;
          int deAffixedLength = length - appendLength;
          // TODO: can we do this in-place?
          String strippedWord = new StringBuilder().append(word, 0, deAffixedLength).append(suffix.getStrip()).toString();

          List<Stem> stemList = applyAffix(strippedWord.toCharArray(), strippedWord.length(), suffix, recursionDepth);
          for (Stem stem : stemList) {
            stem.addSuffix(suffix);
          }

          stems.addAll(stemList);
        }
      }
    }

    for (int i = length - 1; i >= 0; i--) {
      List<Affix> prefixes = dictionary.lookupPrefix(word, 0, i);
      if (prefixes == null) {
        continue;
      }

      for (Affix prefix : prefixes) {
        if (hasCrossCheckedFlag(prefix.getFlag(), flags)) {
          int deAffixedStart = i;
          int deAffixedLength = length - deAffixedStart;

          String strippedWord = new StringBuilder().append(prefix.getStrip())
              .append(word, deAffixedStart, deAffixedLength)
              .toString();

          List<Stem> stemList = applyAffix(strippedWord.toCharArray(), strippedWord.length(), prefix, recursionDepth);
          for (Stem stem : stemList) {
            stem.addPrefix(prefix);
          }

          stems.addAll(stemList);
        }
      }
    }

    return stems;
  }

  /**
   * Applies the affix rule to the given word, producing a list of stems if any are found
   *
   * @param strippedWord Word the affix has been removed and the strip added
   * @param affix HunspellAffix representing the affix rule itself
   * @param recursionDepth Level of recursion this stemming step is at
   * @return List of stems for the word, or an empty list if none are found
   */
  public List<Stem> applyAffix(char strippedWord[], int length, Affix affix, int recursionDepth) {
    segment.setLength(0);
    segment.append(strippedWord, 0, length);
    if (!affix.checkCondition(segment)) {
      return Collections.emptyList();
    }

    List<Stem> stems = new ArrayList<Stem>();

    char wordFlags[] = dictionary.lookupWord(strippedWord, 0, length, scratch);
    if (wordFlags != null && Dictionary.hasFlag(wordFlags, affix.getFlag())) {
      stems.add(new Stem(strippedWord, length));
    }

    if (affix.isCrossProduct() && recursionDepth < recursionCap) {
      stems.addAll(stem(strippedWord, length, affix.getAppendFlags(), ++recursionDepth));
    }

    return stems;
  }

  /**
   * Checks if the given flag cross checks with the given array of flags
   *
   * @param flag Flag to cross check with the array of flags
   * @param flags Array of flags to cross check against.  Can be {@code null}
   * @return {@code true} if the flag is found in the array or the array is {@code null}, {@code false} otherwise
   */
  private boolean hasCrossCheckedFlag(char flag, char[] flags) {
    return flags == null || Arrays.binarySearch(flags, flag) >= 0;
  }
}
