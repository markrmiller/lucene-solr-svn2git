package org.apache.solr.spelling.suggest.fst;

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

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.store.FSDirectory;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.FieldType;
import org.apache.solr.spelling.suggest.LookupFactory;

/**
 * Factory for {@link AnalyzingInfixSuggester}
 * @lucene.experimental
 */
public class AnalyzingInfixLookupFactory extends LookupFactory {
  /**
   * The analyzer used at "query-time" and "build-time" to analyze suggestions.
   */
  protected static final String QUERY_ANALYZER = "suggestAnalyzerFieldType";

  /**
   * The path where the underlying index is stored
   * if no index is found, it will be generated by
   * the AnalyzingInfixSuggester
   */
  protected static final String INDEX_PATH = "indexPath";

  /**
   * Minimum number of leading characters before PrefixQuery is used (default 4). 
   * Prefixes shorter than this are indexed as character ngrams 
   * (increasing index size but making lookups faster)
   */
  protected static final String MIN_PREFIX_CHARS = "minPrefixChars";
  
  /** 
   * Default path where the index for the suggester is stored/loaded from
   * */
  private static final String DEFAULT_INDEX_PATH = "analyzingInfixSuggesterIndexDir";

  /**
   * File name for the automaton.
   */
  private static final String FILENAME = "iwfsta.bin";
  
  
  @Override
  public Lookup create(NamedList params, SolrCore core) {
    // mandatory parameter
    Object fieldTypeName = params.get(QUERY_ANALYZER);
    if (fieldTypeName == null) {
      throw new IllegalArgumentException("Error in configuration: " + QUERY_ANALYZER + " parameter is mandatory");
    }
    FieldType ft = core.getLatestSchema().getFieldTypeByName(fieldTypeName.toString());
    if (ft == null) {
      throw new IllegalArgumentException("Error in configuration: " + fieldTypeName.toString() + " is not defined in the schema");
    }
    Analyzer indexAnalyzer = ft.getIndexAnalyzer();
    Analyzer queryAnalyzer = ft.getQueryAnalyzer();
    
    // optional parameters
    
    String indexPath = params.get(INDEX_PATH) != null
    ? params.get(INDEX_PATH).toString()
    : DEFAULT_INDEX_PATH;
    
    int minPrefixChars = params.get(MIN_PREFIX_CHARS) != null
    ? Integer.parseInt(params.get(MIN_PREFIX_CHARS).toString())
    : AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS;

    try {
      return new AnalyzingInfixSuggester(core.getSolrConfig().luceneMatchVersion, 
                                         FSDirectory.open(new File(indexPath)), indexAnalyzer,
                                         queryAnalyzer, minPrefixChars, true);
    } catch (IOException e) {
      throw new RuntimeException();
    }
  }

  @Override
  public String storeFileName() {
    return FILENAME;
  }
}
