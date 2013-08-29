package org.apache.solr.update.processor;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import org.apache.solr.core.SolrCore;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes duplicate values found in fields matching the specified conditions.  
 * The existing field values are iterated in order, and values are removed when 
 * they are equal to a value that has already been seen for this field.
 * <p>
 * By default this processor matches no fields.
 * </p>
 * 
 * <p>
 * In the example configuration below, if a document initially contains the values 
 * <code>"Steve","Lucy","Jim",Steve","Alice","Bob","Alice"</code> in a field named 
 * <code>foo_uniq</code> then using this processor will result in the final list of 
 * field values being <code>"Steve","Lucy","Jim","Alice","Bob"</code>
 * </p>
 * <pre class="prettyprint">
 *  &lt;processor class="solr.UniqFieldsUpdateProcessorFactory"&gt;
 *    &lt;str name="fieldRegex"&gt;.*_uniq&lt;/str&gt;
 *  &lt;/processor&gt;
 * </pre> 
 */
public class UniqFieldsUpdateProcessorFactory extends FieldValueSubsetUpdateProcessorFactory {

  public final static Logger log = LoggerFactory.getLogger(UniqFieldsUpdateProcessorFactory.class);

  @Override
  public FieldMutatingUpdateProcessor.FieldNameSelector 
    getDefaultSelector(final SolrCore core) {
    
    return FieldMutatingUpdateProcessor.SELECT_NO_FIELDS;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection pickSubset(Collection values) {
    Set<Object> uniqs = new HashSet<Object>();
    List<Object> result = new ArrayList<Object>(values.size());
    for (Object o : values) {
      if (!uniqs.contains(o)) {
        uniqs.add(o);
        result.add(o);
      }
    }
    return result;
  }
}



