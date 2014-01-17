package org.apache.lucene.analysis.uima;

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

import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeSource.AttributeFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link org.apache.lucene.analysis.util.TokenizerFactory} for {@link UIMAAnnotationsTokenizer}
 */
public class UIMAAnnotationsTokenizerFactory extends TokenizerFactory {

  private String descriptorPath;
  private String tokenType;
  private final Map<String,Object> configurationParameters = new HashMap<String,Object>();

  /** Creates a new UIMAAnnotationsTokenizerFactory */
  public UIMAAnnotationsTokenizerFactory(Map<String,String> args) {
    super(args);
    tokenType = require(args, "tokenType");
    descriptorPath = require(args, "descriptorPath");
    configurationParameters.putAll(args);
  }

  @Override
  public UIMAAnnotationsTokenizer create(AttributeFactory factory) {
    return new UIMAAnnotationsTokenizer(descriptorPath, tokenType, configurationParameters, factory);
  }
}
