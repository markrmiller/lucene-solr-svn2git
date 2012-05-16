package org.apache.lucene.index;

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

import java.util.Iterator;

/** 
 * Collection of {@link FieldInfo}s (accessible by number or by name).
 *  @lucene.experimental
 */
public abstract class FieldInfos implements Cloneable,Iterable<FieldInfo> {
 
  /**
   * Returns a deep clone of this FieldInfos instance.
   */
  @Override
  public abstract FieldInfos clone();

  public abstract FieldInfo fieldInfo(String fieldName);

  /**
   * Return the fieldinfo object referenced by the fieldNumber.
   * @param fieldNumber
   * @return the FieldInfo object or null when the given fieldNumber
   * doesn't exist.
   */  
  public abstract FieldInfo fieldInfo(int fieldNumber);

  public abstract Iterator<FieldInfo> iterator();

  /**
   * @return number of fields
   */
  public abstract int size();

  /** Returns true if any fields have positions */
  // nocommit
  public abstract boolean hasProx();
  
  /** Returns true if any fields have freqs */
  // nocommit
  public abstract boolean hasFreq();
  
  /**
   * @return true if at least one field has any vectors
   */
  // nocommit
  public abstract boolean hasVectors();
  
  /**
   * @return true if at least one field has any norms
   */
  // nocommit
  public abstract boolean hasNorms();

  /**
   * @return true if at least one field has docValues
   */
  // nocommit
  public abstract boolean hasDocValues();
}
