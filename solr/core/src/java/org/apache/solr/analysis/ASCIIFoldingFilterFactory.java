
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


package org.apache.solr.analysis;
import org.apache.lucene.analysis.ASCIIFoldingFilter;
import org.apache.lucene.analysis.TokenStream;

/** 
 * Factory for {@link ASCIIFoldingFilter}.
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_ascii" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ASCIIFoldingFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 * @version $Id$
 */
public class ASCIIFoldingFilterFactory extends BaseTokenFilterFactory implements MultiTermAwareComponent {
  public ASCIIFoldingFilter create(TokenStream input) {
    return new ASCIIFoldingFilter(input);
  }

  public Object getMultiTermComponent() {
    return this;
  }
}

