package org.apache.lucene.queryParser.standard.builders;

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

import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.nodes.FieldQueryNode;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.core.util.StringUtils;
import org.apache.lucene.queryParser.standard.nodes.TermRangeQueryNode;
import org.apache.lucene.queryParser.standard.processors.MultiTermRewriteMethodProcessor;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.TermRangeQuery;

/**
 * Builds a {@link TermRangeQuery} object from a {@link TermRangeQueryNode}
 * object.
 */
public class TermRangeQueryNodeBuilder implements StandardQueryBuilder {
  
  public TermRangeQueryNodeBuilder() {
  // empty constructor
  }
  
  public TermRangeQuery build(QueryNode queryNode) throws QueryNodeException {
    TermRangeQueryNode rangeNode = (TermRangeQueryNode) queryNode;
    FieldQueryNode upper = rangeNode.getUpperBound();
    FieldQueryNode lower = rangeNode.getLowerBound();
    
    String field = StringUtils.toString(rangeNode.getField());
    
    TermRangeQuery rangeQuery = new TermRangeQuery(field, lower
        .getTextAsString(), upper.getTextAsString(), rangeNode
        .isLowerInclusive(), rangeNode.isUpperInclusive());
    
    MultiTermQuery.RewriteMethod method = (MultiTermQuery.RewriteMethod) queryNode
        .getTag(MultiTermRewriteMethodProcessor.TAG_ID);
    if (method != null) {
      rangeQuery.setRewriteMethod(method);
    }
    
    return rangeQuery;
    
  }
  
}
