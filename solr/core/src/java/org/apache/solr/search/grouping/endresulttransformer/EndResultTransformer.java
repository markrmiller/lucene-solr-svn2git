package org.apache.solr.search.grouping.endresulttransformer;

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

import org.apache.lucene.search.ScoreDoc;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.grouping.GroupingSpecification;

import java.util.Map;

/**
 * Responsible for transforming the grouped result into the final format for displaying purposes.
 */
public interface EndResultTransformer {

  /**
   * Transforms the specified result into its final form and puts it into the specified response.
   *
   * @param result The map containing the grouping result (for grouping by field and query)
   * @param response The response that will be rendered to the client
   * @param groupingSpecification The grouping specification
   * @param solrDocumentSource The source of {@link SolrDocument} instances
   */
  void transform(Map<String, ?> result, SolrQueryResponse response, GroupingSpecification groupingSpecification, SolrDocumentSource solrDocumentSource);

  /**
   * Abstracts the source for {@link SolrDocument} instances.
   * The source of documents is different for a distributed search than local search
   */
  public interface SolrDocumentSource {

    SolrDocument retrieve(ScoreDoc doc);

  }

}
