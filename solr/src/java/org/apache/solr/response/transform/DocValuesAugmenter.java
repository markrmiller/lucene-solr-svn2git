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
package org.apache.solr.response.transform;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.search.function.DocValues;

/**
 * Add values from a ValueSource (function query etc)
 *
 * @version $Id: JSONResponseWriter.java 1065304 2011-01-30 15:10:15Z rmuir $
 * @since solr 4.0
 */
public class DocValuesAugmenter extends DocTransformer
{
  final String name;
  final DocValues values;

  public DocValuesAugmenter( String name, DocValues values )
  {
    this.name = name;
    this.values = values;
  }

  @Override
  public void transform(SolrDocument doc, int docid) {
    // TODO, should know what the real type is -- not always string
    Object v = values.strVal( docid );
    doc.setField( name, v );
  }
}
