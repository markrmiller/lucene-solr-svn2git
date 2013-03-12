package org.apache.lucene.analysis.standard;

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

import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeSource.AttributeFactory;

import java.io.Reader;
import java.util.Map;

/**
 * Factory for {@link ClassicTokenizer}.
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_clssc" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.ClassicTokenizerFactory" maxTokenLength="120"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 *
 */

public class ClassicTokenizerFactory extends TokenizerFactory {

  private int maxTokenLength;

  @Override
  public void init(Map<String,String> args) {
    super.init(args);
    assureMatchVersion();
    maxTokenLength = getInt("maxTokenLength", 
                            StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH);
  }

  @Override
  public ClassicTokenizer create(Reader input) {
    ClassicTokenizer tokenizer = new ClassicTokenizer(luceneMatchVersion, input); 
    tokenizer.setMaxTokenLength(maxTokenLength);
    return tokenizer;
  }

  @Override
  public ClassicTokenizer create(AttributeFactory factory, Reader input) {
    ClassicTokenizer tokenizer = new ClassicTokenizer(luceneMatchVersion, factory, input); 
    tokenizer.setMaxTokenLength(maxTokenLength);
    return tokenizer;
  }
}
