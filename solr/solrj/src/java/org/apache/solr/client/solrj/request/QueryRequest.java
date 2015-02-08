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

package org.apache.solr.client.solrj.request;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 
 *
 * @since solr 1.3
 */
public class QueryRequest extends SolrRequest
{
  private SolrParams query;
  
  public QueryRequest()
  {
    super( METHOD.GET, null );
  }

  public QueryRequest( SolrParams q )
  {
    super( METHOD.GET, null );
    query = q;
  }
  
  public QueryRequest( SolrParams q, METHOD method )
  {
    super( method, null );
    query = q;
  }

  /**
   * Use the params 'QT' parameter if it exists
   */
  @Override
  public String getPath() {
    String qt = query == null ? null : query.get( CommonParams.QT );
    if( qt == null ) {
      qt = super.getPath();
    }
    if( qt != null && qt.startsWith( "/" ) ) {
      return qt;
    }
    return "/select";
  }
  
  //---------------------------------------------------------------------------------
  //---------------------------------------------------------------------------------
  
  @Override
  public Collection<ContentStream> getContentStreams() {
    return null;
  }

  @Override
  public SolrParams getParams() {
    return query;
  }

  @Override
  public QueryResponse process( SolrClient client ) throws SolrServerException
  {
    try {
      long startTime = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
      QueryResponse res = new QueryResponse( client.request( this ), client );
      long endTime = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
      res.setElapsedTime(endTime - startTime);
      return res;
    } catch (SolrServerException | SolrException e){
      throw e;
    } catch (Exception e) {
      throw new SolrServerException("Error executing query", e);
    }
  }
}

