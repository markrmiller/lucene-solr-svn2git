package org.apache.lucene.analysis.core;

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

import org.apache.lucene.analysis.util.AbstractAnalysisFactory;
import org.apache.lucene.analysis.util.MultiTermAwareComponent;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for {@link LowerCaseTokenizer}. 
 * <pre class="prettyprint">
 * &lt;fieldType name="text_lwrcase" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.LowerCaseTokenizerFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
public class LowerCaseTokenizerFactory extends TokenizerFactory implements MultiTermAwareComponent {
  
  /** Creates a new LowerCaseTokenizerFactory */
  public LowerCaseTokenizerFactory(Map<String,String> args) {
    super(args);
    assureMatchVersion();
    if (!args.isEmpty()) {
      throw new IllegalArgumentException("Unknown parameters: " + args);
    }
  }

  @Override
  public LowerCaseTokenizer create(AttributeFactory factory) {
    return new LowerCaseTokenizer(luceneMatchVersion, factory);
  }

  @Override
  public AbstractAnalysisFactory getMultiTermComponent() {
    return new LowerCaseFilterFactory(new HashMap<>(getOriginalArgs()));
  }
}
