package org.apache.solr.analysis;

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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ga.IrishLowerCaseFilter;

/** 
 * Factory for {@link IrishLowerCaseFilter}. 
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_ga" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.StandardTokenizerFactory"/&gt;
 *     &lt;filter class="solr.IrishLowerCaseFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 */
public class IrishLowerCaseFilterFactory extends BaseTokenFilterFactory implements MultiTermAwareComponent {

  //@Override
  public TokenStream create(TokenStream input) {
    return new IrishLowerCaseFilter(input);
  }

  // this will 'mostly work', except for special cases, just like most other filters
  //@Override
  public Object getMultiTermComponent() {
    return this;
  }
}
