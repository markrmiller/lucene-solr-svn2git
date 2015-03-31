package org.apache.solr.client.solrj.impl;

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
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;

/**
 * Simply puts the InputStream into an entry in a NamedList named "stream".
 */
public class InputStreamResponseParser extends ResponseParser {

  private final String writerType;

  public InputStreamResponseParser(String writerType) {
    this.writerType = writerType;
  }

  @Override
  public String getWriterType() {
    return writerType;
  }

  @Override
  public NamedList<Object> processResponse(Reader reader) {
    throw new UnsupportedOperationException();
  }

  @Override
  public NamedList<Object> processResponse(InputStream body, String encoding) {
    throw new UnsupportedOperationException();
  }

}

