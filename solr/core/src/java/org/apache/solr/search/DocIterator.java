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

package org.apache.solr.search;

import java.util.Iterator;

/**
 * Simple Iterator of document Ids which may include score information.
 *
 * <p>
 * The order of the documents is determined by the context in which the
 * DocIterator instance was retrieved.
 * </p>
 *
 *
 */
public interface DocIterator extends Iterator<Integer> {
  // already declared in superclass, redeclaring prevents javadoc inheritance
  //public boolean hasNext();

  /**
   * Returns the next document id if hasNext()==true
   *
   * <code>
   * This method is equivalent to <code>next()</code>, but avoids the creation
   * of an Integer Object.
   * @see #next()
   */
  public int nextDoc();

  /**
   * Returns the score for the document just returned by <code>nextDoc()</code>
   *
   * <p>
   * The value returned may be meaningless depending on the context
   * in which the DocIterator instance was retrieved.
   */
  public float score();
}
