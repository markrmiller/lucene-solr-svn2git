package org.apache.solr.analysis;

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

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.*;
import org.apache.lucene.collation.ICUCollationKeyFilter;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.util.ULocale;

/**
 * <!-- see LUCENE-4015 for why we cannot link -->
 * Factory for <code>ICUCollationKeyFilter</code>.
 * <p>
 * This factory can be created in two ways: 
 * <ul>
 *  <li>Based upon a system collator associated with a Locale.
 *  <li>Based upon a tailored ruleset.
 * </ul>
 * <p>
 * Using a System collator:
 * <ul>
 *  <li>locale: RFC 3066 locale ID (mandatory)
 *  <li>strength: 'primary','secondary','tertiary', 'quaternary', or 'identical' (optional)
 *  <li>decomposition: 'no', or 'canonical' (optional)
 * </ul>
 * <p>
 * Using a Tailored ruleset:
 * <ul>
 *  <li>custom: UTF-8 text file containing rules supported by RuleBasedCollator (mandatory)
 *  <li>strength: 'primary','secondary','tertiary', 'quaternary', or 'identical' (optional)
 *  <li>decomposition: 'no' or 'canonical' (optional)
 * </ul>
 * <p>
 * Expert options:
 * <ul>
 *  <li>alternate: 'shifted' or 'non-ignorable'. Can be used to ignore punctuation/whitespace.
 *  <li>caseLevel: 'true' or 'false'. Useful with strength=primary to ignore accents but not case.
 *  <li>caseFirst: 'lower' or 'upper'. Useful to control which is sorted first when case is not ignored.
 *  <li>numeric: 'true' or 'false'. Digits are sorted according to numeric value, e.g. foobar-9 sorts before foobar-10
 *  <li>variableTop: single character or contraction. Controls what is variable for 'alternate'
 * </ul>
 *
 * @see Collator
 * @see ULocale
 * @see RuleBasedCollator
 * @deprecated use {@link org.apache.solr.schema.ICUCollationField} instead.
 */
@Deprecated
public class ICUCollationKeyFilterFactory extends TokenFilterFactory implements MultiTermAwareComponent, ResourceLoaderAware {
  private Collator collator;

  public void inform(ResourceLoader loader) {
    String custom = args.get("custom");
    String localeID = args.get("locale");
    String strength = args.get("strength");
    String decomposition = args.get("decomposition");

    String alternate = args.get("alternate");
    String caseLevel = args.get("caseLevel");
    String caseFirst = args.get("caseFirst");
    String numeric = args.get("numeric");
    String variableTop = args.get("variableTop");
    
    if (custom == null && localeID == null)
      throw new SolrException(ErrorCode.SERVER_ERROR, "Either custom or locale is required.");
    
    if (custom != null && localeID != null)
      throw new SolrException(ErrorCode.SERVER_ERROR, "Cannot specify both locale and custom. "
          + "To tailor rules for a built-in language, see the javadocs for RuleBasedCollator. "
          + "Then save the entire customized ruleset to a file, and use with the custom parameter");
    
    if (localeID != null) { 
      // create from a system collator, based on Locale.
      collator = createFromLocale(localeID);
    } else { 
      // create from a custom ruleset
      collator = createFromRules(custom, loader);
    }
    
    // set the strength flag, otherwise it will be the default.
    if (strength != null) {
      if (strength.equalsIgnoreCase("primary"))
        collator.setStrength(Collator.PRIMARY);
      else if (strength.equalsIgnoreCase("secondary"))
        collator.setStrength(Collator.SECONDARY);
      else if (strength.equalsIgnoreCase("tertiary"))
        collator.setStrength(Collator.TERTIARY);
      else if (strength.equalsIgnoreCase("quaternary"))
        collator.setStrength(Collator.QUATERNARY);
      else if (strength.equalsIgnoreCase("identical"))
        collator.setStrength(Collator.IDENTICAL);
      else
        throw new SolrException(ErrorCode.SERVER_ERROR, "Invalid strength: " + strength);
    }
    
    // set the decomposition flag, otherwise it will be the default.
    if (decomposition != null) {
      if (decomposition.equalsIgnoreCase("no"))
        collator.setDecomposition(Collator.NO_DECOMPOSITION);
      else if (decomposition.equalsIgnoreCase("canonical"))
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
      else
        throw new SolrException(ErrorCode.SERVER_ERROR, "Invalid decomposition: " + decomposition);
    }
    
    // expert options: concrete subclasses are always a RuleBasedCollator
    RuleBasedCollator rbc = (RuleBasedCollator) collator;
    if (alternate != null) {
      if (alternate.equalsIgnoreCase("shifted")) {
        rbc.setAlternateHandlingShifted(true);
      } else if (alternate.equalsIgnoreCase("non-ignorable")) {
        rbc.setAlternateHandlingShifted(false);
      } else {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Invalid alternate: " + alternate);
      }
    }
    if (caseLevel != null) {
      rbc.setCaseLevel(Boolean.parseBoolean(caseLevel));
    }
    if (caseFirst != null) {
      if (caseFirst.equalsIgnoreCase("lower")) {
        rbc.setLowerCaseFirst(true);
      } else if (caseFirst.equalsIgnoreCase("upper")) {
        rbc.setUpperCaseFirst(true);
      } else {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Invalid caseFirst: " + caseFirst);
      }
    }
    if (numeric != null) {
      rbc.setNumericCollation(Boolean.parseBoolean(numeric));
    }
    if (variableTop != null) {
      rbc.setVariableTop(variableTop);
    }
  }
  
  public TokenStream create(TokenStream input) {
    return new ICUCollationKeyFilter(input, collator);
  }
  
  /*
   * Create a locale from localeID.
   * Then return the appropriate collator for the locale.
   */
  private Collator createFromLocale(String localeID) {
    return Collator.getInstance(new ULocale(localeID));
  }
  
  /*
   * Read custom rules from a file, and create a RuleBasedCollator
   * The file cannot support comments, as # might be in the rules!
   */
  private Collator createFromRules(String fileName, ResourceLoader loader) {
    InputStream input = null;
    try {
     input = loader.openResource(fileName);
     String rules = IOUtils.toString(input, "UTF-8");
     return new RuleBasedCollator(rules);
    } catch (Exception e) {
      // io error or invalid rules
      throw new RuntimeException(e);
    } finally {
      IOUtils.closeQuietly(input);
    }
  }
  
  @Override
  public AbstractAnalysisFactory getMultiTermComponent() {
    return this;
  }
}
