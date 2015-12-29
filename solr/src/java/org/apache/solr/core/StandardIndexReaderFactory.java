package org.apache.solr.core;
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

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.solr.common.util.NamedList;

/**
 * Default IndexReaderFactory implementation. Returns a standard Lucene
 * IndexReader.
 * 
 * @see IndexReader#open(Directory)
 */
public class StandardIndexReaderFactory extends IndexReaderFactory {
  
  /* (non-Javadoc)
   * @see org.apache.solr.core.IndexReaderFactory#newReader(org.apache.lucene.store.Directory, boolean)
   */
  public IndexReader newReader(Directory indexDir, boolean readOnly)
      throws IOException {
    return IndexReader.open(indexDir, null, readOnly, termInfosIndexDivisor);
  }
}
