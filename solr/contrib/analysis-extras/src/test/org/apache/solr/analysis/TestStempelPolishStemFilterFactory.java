package org.apache.solr.analysis;

import java.io.StringReader;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.solr.core.SolrResourceLoader;

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
 * Tests for {@link StempelPolishStemFilterFactory}
 */
public class TestStempelPolishStemFilterFactory extends BaseTokenStreamTestCase {
  public void testBasics() throws Exception {
    StringReader document = new StringReader("studenta studenci");
    StempelPolishStemFilterFactory factory = new StempelPolishStemFilterFactory();
    factory.inform(new SolrResourceLoader(null, null));
    TokenStream ts = factory.create(new WhitespaceTokenizer(TEST_VERSION_CURRENT, document));
    assertTokenStreamContents(ts,
        new String[] { "student", "student" });
  }
}
