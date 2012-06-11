package org.apache.lucene.analysis.icu.tokenattributes;

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

import org.apache.lucene.util.Attribute;

import com.ibm.icu.lang.UScript; // javadoc @link

/**
 * This attribute stores the UTR #24 script value for a token of text.
 * @lucene.experimental
 */
public interface ScriptAttribute extends Attribute {
  /**
   * Get the numeric code for this script value.
   * This is the constant value from {@link UScript}.
   * @return numeric code
   */
  public int getCode();
  /**
   * Set the numeric code for this script value.
   * This is the constant value from {@link UScript}.
   * @param code numeric code
   */
  public void setCode(int code);
  /**
   * Get the full name.
   * @return UTR #24 full name.
   */
  public String getName();
  /**
   * Get the abbreviated name.
   * @return UTR #24 abbreviated name.
   */
  public String getShortName();
}
