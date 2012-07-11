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

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;

/**
 * Simple tests to ensure the Greek lowercase filter factory is working.
 */
public class TestGreekLowerCaseFilterFactory extends BaseTokenStreamTestCase {
  /**
   * Ensure the filter actually lowercases (and a bit more) greek text.
   */
  public void testNormalization() throws Exception {
    Reader reader = new StringReader("Μάϊος ΜΆΪΟΣ");
    Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, false);
    GreekLowerCaseFilterFactory factory = new GreekLowerCaseFilterFactory();
    factory.setLuceneMatchVersion(TEST_VERSION_CURRENT);
    Map<String, String> args = Collections.emptyMap();
    factory.init(args);
    TokenStream stream = factory.create(tokenizer);
    assertTokenStreamContents(stream, new String[] { "μαιοσ", "μαιοσ" });
  }
}
