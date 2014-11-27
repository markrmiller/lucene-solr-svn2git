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

import java.io.Reader;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.feeds.DocMaker;
import org.apache.lucene.document.Document2;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.RAMDirectory;

/**
 * Simple task to test performance of tokenizers.  It just
 * creates a token stream for each field of the document and
 * read all tokens out of that stream.
 */
public class ReadTokensTask extends PerfTask {

  public ReadTokensTask(PerfRunData runData) {
    super(runData);
  }

  private int totalTokenCount = 0;
  
  @Override
  protected String getLogMessage(int recsCount) {
    return "read " + recsCount + " docs; " + totalTokenCount + " tokens";
  }
  
  private IndexWriter privateWriter;

  private IndexWriter getPrivateWriter() throws Exception {
    if (privateWriter == null) {
      RAMDirectory dir = new RAMDirectory();
      privateWriter = new IndexWriter(dir, new IndexWriterConfig(getRunData().getAnalyzer()));
    }
    return privateWriter;
  }

  @Override
  public int doLogic() throws Exception {
    DocMaker docMaker = getRunData().getDocMaker();
    IndexWriter iw = getRunData().getIndexWriter();
    if (iw == null) {
      iw = getPrivateWriter();
    }
    Document2 doc = docMaker.makeDocument(iw);

    List<IndexableField> fields = doc.getFields();
    Analyzer analyzer = iw.getFieldTypes().getIndexAnalyzer();
    int tokenCount = 0;
    for(final IndexableField field : fields) {
      if (field.name().equals(DocMaker.BODY_FIELD) ||
          field.name().equals(DocMaker.DATE_FIELD) ||
          field.name().equals(DocMaker.TITLE_FIELD)) {
      
        final TokenStream stream = field.tokenStream(analyzer, null);
        // reset the TokenStream to the first token
        stream.reset();

        TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
        while(stream.incrementToken()) {
          termAtt.fillBytesRef();
          tokenCount++;
        }
        stream.end();
        stream.close();
      }
    }
    totalTokenCount += tokenCount;
    return tokenCount;
  }

  /* Simple StringReader that can be reset to a new string;
   * we use this when tokenizing the string value from a
   * Field. */
  ReusableStringReader stringReader = new ReusableStringReader();

  private final static class ReusableStringReader extends Reader {
    int upto;
    int left;
    String s;
    void init(String s) {
      this.s = s;
      left = s.length();
      this.upto = 0;
    }
    @Override
    public int read(char[] c) {
      return read(c, 0, c.length);
    }
    @Override
    public int read(char[] c, int off, int len) {
      if (left > len) {
        s.getChars(upto, upto+len, c, off);
        upto += len;
        left -= len;
        return len;
      } else if (0 == left) {
        return -1;
      } else {
        s.getChars(upto, upto+left, c, off);
        int r = left;
        left = 0;
        upto = s.length();
        return r;
      }
    }
    @Override
    public void close() {}
  }
}
