package org.apache.lucene.benchmark.byTask.tasks;

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

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.feeds.DocMaker;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

/**
 * Update a document, using IndexWriter.updateDocument,
 * optionally with of a certain size.
 * <br>Other side effects: none.
 * <br>Takes optional param: document size. 
 */
public class UpdateDocTask extends PerfTask {

  public UpdateDocTask(PerfRunData runData) {
    super(runData);
  }

  private int docSize = 0;
  
  @Override
  public int doLogic() throws Exception {
    Document doc;
    DocMaker docMaker = getRunData().getDocMaker();
    IndexWriter iw = getRunData().getIndexWriter();
    if (docSize > 0) {
      doc = docMaker.makeDocument(iw, docSize);
    } else {
      doc = docMaker.makeDocument(iw);
    }
    final String docID = doc.getString(DocMaker.ID_FIELD);
    if (docID == null) {
      throw new IllegalStateException("document must define the docid field");
    }
    iw.updateDocument(new Term(DocMaker.ID_FIELD, docID), doc);
    return 1;
  }

  @Override
  protected String getLogMessage(int recsCount) {
    return "updated " + recsCount + " docs";
  }
  
  /**
   * Set the params (docSize only)
   * @param params docSize, or 0 for no limit.
   */
  @Override
  public void setParams(String params) {
    super.setParams(params);
    docSize = (int) Float.parseFloat(params); 
  }

  /* (non-Javadoc)
   * @see org.apache.lucene.benchmark.byTask.tasks.PerfTask#supportsParams()
   */
  @Override
  public boolean supportsParams() {
    return true;
  }
  
}
