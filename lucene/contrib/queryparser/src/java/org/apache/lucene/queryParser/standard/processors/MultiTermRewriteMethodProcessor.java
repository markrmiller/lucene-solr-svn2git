package org.apache.lucene.queryParser.standard.processors;

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

import java.util.List;

import org.apache.lucene.queryParser.core.nodes.ParametricRangeQueryNode;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessorImpl;
import org.apache.lucene.queryParser.standard.config.StandardQueryConfigHandler.ConfigurationKeys;
import org.apache.lucene.queryParser.standard.nodes.WildcardQueryNode;
import org.apache.lucene.search.MultiTermQuery;

/**
 * This processor instates the default
 * {@link org.apache.lucene.search.MultiTermQuery.RewriteMethod},
 * {@link MultiTermQuery#CONSTANT_SCORE_AUTO_REWRITE_DEFAULT}, for multi-term
 * query nodes.
 */
public class MultiTermRewriteMethodProcessor extends QueryNodeProcessorImpl {

  public static final String TAG_ID = "MultiTermRewriteMethodConfiguration";

  @Override
  protected QueryNode postProcessNode(QueryNode node) {

    // set setMultiTermRewriteMethod for WildcardQueryNode and
    // PrefixWildcardQueryNode
    if (node instanceof WildcardQueryNode
        || node instanceof ParametricRangeQueryNode) {


      // read the attribute value and use a TAG to take the value to the Builder
      MultiTermQuery.RewriteMethod rewriteMethod = getQueryConfigHandler()
          .get(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD);
      
      if (rewriteMethod == null) {
        // This should not happen, this attribute is created in the
        // StandardQueryConfigHandler
        throw new IllegalArgumentException(
            "StandardQueryConfigHandler.ConfigurationKeys.MULTI_TERM_REWRITE_METHOD should be set on the QueryConfigHandler");
      }


      node.setTag(MultiTermRewriteMethodProcessor.TAG_ID, rewriteMethod);

    }

    return node;
  }

  @Override
  protected QueryNode preProcessNode(QueryNode node) {
    return node;
  }

  @Override
  protected List<QueryNode> setChildrenOrder(List<QueryNode> children) {
    return children;
  }
}
