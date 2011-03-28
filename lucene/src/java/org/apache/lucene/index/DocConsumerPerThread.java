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

import java.io.IOException;

abstract class DocConsumerPerThread {

  /** Process the document. If there is
   *  something for this document to be done in docID order,
   *  you should encapsulate that as a
   *  DocumentsWriter.DocWriter and return it.
   *  DocumentsWriter then calls finish() on this object
   *  when it's its turn. */
  abstract DocumentsWriter.DocWriter processDocument(FieldInfos fieldInfos) throws IOException;

  abstract void doAfterFlush();
  abstract void abort();
}
