package org.apache.lucene.queryParser.standard.config;

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

import org.apache.lucene.queryParser.core.config.AbstractQueryConfig;
import org.apache.lucene.queryParser.core.config.ConfigAttribute;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.standard.config.StandardQueryConfigHandler.ConfigurationKeys;
import org.apache.lucene.queryParser.standard.processors.ParametricRangeQueryNodeProcessor;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.util.AttributeImpl;

/**
 * This attribute is used by {@link ParametricRangeQueryNodeProcessor} processor
 * and should be defined in the {@link QueryConfigHandler} used by this
 * processor. It basically tells the processor which {@link RewriteMethod} to
 * use. <br/>
 * 
 * @see MultiTermRewriteMethodAttribute
 * 
 * @deprecated
 * 
 */
@Deprecated
public class MultiTermRewriteMethodAttributeImpl extends AttributeImpl
    implements MultiTermRewriteMethodAttribute, ConfigAttribute {

  private static final long serialVersionUID = -2104763012723049527L;
  
  private AbstractQueryConfig config;
  
  { enableBackwards = false; }
  
  public MultiTermRewriteMethodAttributeImpl() {
    // empty constructor
  }

  public void setMultiTermRewriteMethod(MultiTermQuery.RewriteMethod method) {
   config.set(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD, method);
  }

  public MultiTermQuery.RewriteMethod getMultiTermRewriteMethod() {
    return config.get(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD);
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copyTo(AttributeImpl target) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object other) {

    if (other instanceof MultiTermRewriteMethodAttributeImpl
        && ((MultiTermRewriteMethodAttributeImpl) other).getMultiTermRewriteMethod() == getMultiTermRewriteMethod()) {

      return true;

    }

    return false;

  }

  @Override
  public int hashCode() {
    return getMultiTermRewriteMethod().hashCode();
  }

  @Override
  public String toString() {
    return "<multiTermRewriteMethod multiTermRewriteMethod="
        + getMultiTermRewriteMethod() + "/>";
  }
  
  public void setQueryConfigHandler(AbstractQueryConfig config) {
    this.config = config;
    
    if (!config.has(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD)) {
      config.set(ConfigurationKeys.MULTI_TERM_REWRITE_METHOD, MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
    }
    
  }

}
