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

package org.apache.lucene.analysis.miscellaneous;

import java.io.StringReader;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;

/**
 * HyphenatedWordsFilter test
 */
public class TestHyphenatedWordsFilter extends BaseTokenStreamTestCase {
	public void testHyphenatedWords() throws Exception {
		String input = "ecologi-\r\ncal devel-\r\n\r\nop compre-\u0009hensive-hands-on and ecologi-\ncal";
		// first test
		TokenStream ts = new MockTokenizer(new StringReader(input), MockTokenizer.WHITESPACE, false);
		ts = new HyphenatedWordsFilter(ts);
		assertTokenStreamContents(ts, 
		    new String[] { "ecological", "develop", "comprehensive-hands-on", "and", "ecological" });
	}
	
	/**
	 * Test that HyphenatedWordsFilter behaves correctly with a final hyphen
	 */
	public void testHyphenAtEnd() throws Exception {
	    String input = "ecologi-\r\ncal devel-\r\n\r\nop compre-\u0009hensive-hands-on and ecology-";
	    // first test
	    TokenStream ts = new MockTokenizer(new StringReader(input), MockTokenizer.WHITESPACE, false);
	    ts = new HyphenatedWordsFilter(ts);
	    assertTokenStreamContents(ts, 
	        new String[] { "ecological", "develop", "comprehensive-hands-on", "and", "ecology-" });
	  }
}
