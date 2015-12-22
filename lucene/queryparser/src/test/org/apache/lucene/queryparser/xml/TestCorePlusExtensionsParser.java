package org.apache.lucene.queryparser.xml;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.search.Query;

public class TestCorePlusExtensionsParser extends TestCorePlusQueriesParser {

  private CoreParser corePlusExtensionsParser;

  public void testFuzzyLikeThisQueryXML() throws Exception {
    Query q = parse("FuzzyLikeThisQuery.xml");
    //show rewritten fuzzyLikeThisQuery - see what is being matched on
    if (VERBOSE) {
      System.out.println(rewrite(q));
    }
    dumpResults("FuzzyLikeThis", q, 5);
  }

  //================= Helper methods ===================================

  @Override
  protected CoreParser coreParser() {
    if (corePlusExtensionsParser == null) {
      corePlusExtensionsParser = new CorePlusExtensionsParser(
          super.defaultField(),
          super.analyzer());
    }
    return corePlusExtensionsParser;
  }

}
