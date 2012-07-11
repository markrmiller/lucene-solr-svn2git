package org.apache.lucene.queryparser.flexible.standard.builders;

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

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.FuzzyQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.search.FuzzyQuery;

/**
 * Builds a {@link FuzzyQuery} object from a {@link FuzzyQueryNode} object.
 */
public class FuzzyQueryNodeBuilder implements StandardQueryBuilder {

  public FuzzyQueryNodeBuilder() {
    // empty constructor
  }

  public FuzzyQuery build(QueryNode queryNode) throws QueryNodeException {
    FuzzyQueryNode fuzzyNode = (FuzzyQueryNode) queryNode;
    String text = fuzzyNode.getTextAsString();
    
    int numEdits = FuzzyQuery.floatToEdits(fuzzyNode.getSimilarity(), 
        text.codePointCount(0, text.length()));
    
    return new FuzzyQuery(new Term(fuzzyNode.getFieldAsString(), fuzzyNode
        .getTextAsString()), numEdits, fuzzyNode
        .getPrefixLength());

  }

}
