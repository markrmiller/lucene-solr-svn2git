package org.apache.lucene.analysis.cjk;

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

import org.apache.lucene.analysis.cjk.CJKTokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;

import java.io.Reader;

/** 
 * Factory for {@link CJKTokenizer}. 
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_cjk" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.CJKTokenizerFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 * @deprecated Use {@link CJKBigramFilterFactory} instead.
 */
@Deprecated
public class CJKTokenizerFactory extends TokenizerFactory {
  public CJKTokenizer create(Reader in) {
    return new CJKTokenizer(in);
  }
}

