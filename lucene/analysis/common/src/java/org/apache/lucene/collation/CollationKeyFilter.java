package org.apache.lucene.collation;

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


import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.IndexableBinaryStringTools;

import java.io.IOException;
import java.text.Collator;


/**
 * <p>
 *   Converts each token into its {@link java.text.CollationKey}, and then
 *   encodes the CollationKey with {@link IndexableBinaryStringTools}, to allow 
 *   it to be stored as an index term.
 * </p>
 * <p>
 *   <strong>WARNING:</strong> Make sure you use exactly the same Collator at
 *   index and query time -- CollationKeys are only comparable when produced by
 *   the same Collator.  Since {@link java.text.RuleBasedCollator}s are not
 *   independently versioned, it is unsafe to search against stored
 *   CollationKeys unless the following are exactly the same (best practice is
 *   to store this information with the index and check that they remain the
 *   same at query time):
 * </p>
 * <ol>
 *   <li>JVM vendor</li>
 *   <li>JVM version, including patch version</li>
 *   <li>
 *     The language (and country and variant, if specified) of the Locale
 *     used when constructing the collator via
 *     {@link Collator#getInstance(java.util.Locale)}.
 *   </li>
 *   <li>
 *     The collation strength used - see {@link Collator#setStrength(int)}
 *   </li>
 * </ol> 
 * <p>
 *   The <code>ICUCollationKeyFilter</code> in the analysis-icu package 
 *   uses ICU4J's Collator, which makes its
 *   version available, thus allowing collation to be versioned independently
 *   from the JVM.  ICUCollationKeyFilter is also significantly faster and
 *   generates significantly shorter keys than CollationKeyFilter.  See
 *   <a href="http://site.icu-project.org/charts/collation-icu4j-sun"
 *   >http://site.icu-project.org/charts/collation-icu4j-sun</a> for key
 *   generation timing and key length comparisons between ICU4J and
 *   java.text.Collator over several languages.
 * </p>
 * <p>
 *   CollationKeys generated by java.text.Collators are not compatible
 *   with those those generated by ICU Collators.  Specifically, if you use 
 *   CollationKeyFilter to generate index terms, do not use
 *   ICUCollationKeyFilter on the query side, or vice versa.
 * </p>
 * @deprecated Use {@link CollationAttributeFactory} instead, which encodes
 *  terms directly as bytes. This filter will be removed in Lucene 5.0
 */
@Deprecated
public final class CollationKeyFilter extends TokenFilter {
  private final Collator collator;
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

  /**
   * @param input Source token stream
   * @param collator CollationKey generator
   */
  public CollationKeyFilter(TokenStream input, Collator collator) {
    super(input);
    // clone in case JRE doesnt properly sync,
    // or to reduce contention in case they do
    this.collator = (Collator) collator.clone();
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      byte[] collationKey = collator.getCollationKey(termAtt.toString()).toByteArray();
      int encodedLength = IndexableBinaryStringTools.getEncodedLength(
          collationKey, 0, collationKey.length);
      termAtt.resizeBuffer(encodedLength);
      termAtt.setLength(encodedLength);
      IndexableBinaryStringTools.encode(collationKey, 0, collationKey.length,
          termAtt.buffer(), 0, encodedLength);
      return true;
    } else {
      return false;
    }
  }
}
