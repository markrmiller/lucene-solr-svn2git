package org.apache.solr.spelling;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.index.IndexReader;
import org.apache.solr.common.params.SolrParams;

import java.util.Collection;
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


/**
 *
 *
 **/
public class SpellingOptions {

  /**
   * The tokens to spell check
   */
  public Collection<Token> tokens;
  /**
   * An optional {@link org.apache.lucene.index.IndexReader}
   */
  public IndexReader reader;
  /**
   * The number of suggestions to return, if there are any.  Defaults to 1.
   */
  public int count = 1;
  /**
   * Return only those results that are more popular, as defined by the implementation
   */
  public boolean onlyMorePopular;
  /**
   * Provide additional, per implementation, information about the results
   */
  public boolean extendedResults;

  /**
   * Optionally restrict the results to have a minimum accuracy level.  Per Implementation.
   * By default set to Float.MIN_VALUE.
   */
  public float accuracy = Float.MIN_VALUE;

  /**
   * Any other custom params can be passed through.  May be null and is null by default.
   */
  public SolrParams customParams;

  public SpellingOptions() {
  }

  //A couple of convenience ones
  public SpellingOptions(Collection<Token> tokens, int count) {
    this.tokens = tokens;
    this.count = count;
  }

  public SpellingOptions(Collection<Token> tokens, IndexReader reader) {
    this.tokens = tokens;
    this.reader = reader;
  }

  public SpellingOptions(Collection<Token> tokens, IndexReader reader, int count) {
    this.tokens = tokens;
    this.reader = reader;
    this.count = count;
  }


  public SpellingOptions(Collection<Token> tokens, IndexReader reader, int count, boolean onlyMorePopular, boolean extendedResults, float accuracy, SolrParams customParams) {
    this.tokens = tokens;
    this.reader = reader;
    this.count = count;
    this.onlyMorePopular = onlyMorePopular;
    this.extendedResults = extendedResults;
    this.accuracy = accuracy;
    this.customParams = customParams;
  }
}
