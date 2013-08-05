package org.apache.solr.rest.schema;
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

import com.google.inject.Inject;
import org.apache.solr.request.SolrQueryRequestDecoder;
import org.apache.solr.schema.IndexSchema;
import org.restlet.representation.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class responds to requests at /solr/(corename)/schema/solrqueryparser/defaultoperator
 */
public class SolrQueryParserDefaultOperatorSR extends BaseSchemaResource implements SolrQueryParserDefaultOperatorResource {
  private static final Logger log = LoggerFactory.getLogger(SolrQueryParserDefaultOperatorSR.class);

  @Inject
  public SolrQueryParserDefaultOperatorSR(SolrQueryRequestDecoder requestDecoder) {
    super(requestDecoder);
  }


  @Override
  public Representation get() {
    try {
      getSolrResponse().add(IndexSchema.DEFAULT_OPERATOR, getSchema().getQueryParserDefaultOperator());
    } catch (Exception e) {
      getSolrResponse().setException(e);
    }
    handlePostExecution(log);

    return new SolrOutputRepresentation();
  }
}
