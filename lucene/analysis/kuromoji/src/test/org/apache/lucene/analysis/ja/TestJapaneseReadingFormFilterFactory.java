package org.apache.lucene.analysis.ja;

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

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;

/**
 * Simple tests for {@link JapaneseReadingFormFilterFactory}
 */
public class TestJapaneseReadingFormFilterFactory extends BaseTokenStreamTestCase {
  public void testReadings() throws IOException {
    JapaneseTokenizerFactory tokenizerFactory = new JapaneseTokenizerFactory();
    Map<String, String> args = Collections.emptyMap();
    tokenizerFactory.init(args);
    tokenizerFactory.inform(new StringMockResourceLoader(""));
    TokenStream tokenStream = tokenizerFactory.create(new StringReader("先ほどベルリンから来ました。"));
    JapaneseReadingFormFilterFactory filterFactory = new JapaneseReadingFormFilterFactory();
    assertTokenStreamContents(filterFactory.create(tokenStream),
        new String[] { "サキ", "ホド", "ベルリン", "カラ", "キ", "マシ", "タ" }
    );
  }
}
