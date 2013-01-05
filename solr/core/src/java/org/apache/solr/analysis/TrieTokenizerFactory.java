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
package org.apache.solr.analysis;

import org.apache.lucene.analysis.NumericTokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.solr.common.SolrException;
import org.apache.solr.schema.DateField;
import static org.apache.solr.schema.TrieField.TrieTypes;

import java.io.IOException;
import java.io.Reader;

/**
 * Tokenizer for trie fields. It uses NumericTokenStream to create multiple trie encoded string per number.
 * Each string created by this tokenizer for a given number differs from the previous by the given precisionStep.
 * For query time token streams that only contain the highest precision term, use 32/64 as precisionStep.
 * <p/>
 * Refer to {@link org.apache.lucene.search.NumericRangeQuery} for more details.
 *
 *
 * @see org.apache.lucene.search.NumericRangeQuery
 * @see org.apache.solr.schema.TrieField
 * @since solr 1.4
 */
public class TrieTokenizerFactory extends TokenizerFactory {
  protected final int precisionStep;
  protected final TrieTypes type;

  public TrieTokenizerFactory(TrieTypes type, int precisionStep) {
    this.type = type;
    this.precisionStep = precisionStep;
  }

  @Override
  public TrieTokenizer create(Reader input) {
    return new TrieTokenizer(input, type, TrieTokenizer.getNumericTokenStream(precisionStep));
  }
}

final class TrieTokenizer extends Tokenizer {
  protected static final DateField dateField = new DateField();
  protected final TrieTypes type;
  protected final NumericTokenStream ts;
  
  protected final OffsetAttribute ofsAtt = addAttribute(OffsetAttribute.class);
  protected int startOfs, endOfs;
  protected boolean hasValue;
  protected final char[] buf = new char[32];

  static NumericTokenStream getNumericTokenStream(int precisionStep) {
    return new NumericTokenStream(precisionStep);
  }

  public TrieTokenizer(Reader input, TrieTypes type, NumericTokenStream ts) {
    // must share the attribute source with the NumericTokenStream we delegate to
    super(ts, input);
    this.type = type;
    this.ts = ts;
  }

  @Override
  public void reset() {
   try {
      int upto = 0;
      while (upto < buf.length) {
        final int length = input.read(buf, upto, buf.length - upto);
        if (length == -1) break;
        upto += length;
      }
      // skip remaining data if buffer was too short:
      if (upto == buf.length) {
        input.skip(Long.MAX_VALUE);
      }

      this.startOfs = correctOffset(0);
      this.endOfs = correctOffset(upto);
      
      if (upto == 0) {
        hasValue = false;
        return;
      }

      final String v = new String(buf, 0, upto);
      try {
        switch (type) {
          case INTEGER:
            ts.setIntValue(Integer.parseInt(v));
            break;
          case FLOAT:
            ts.setFloatValue(Float.parseFloat(v));
            break;
          case LONG:
            ts.setLongValue(Long.parseLong(v));
            break;
          case DOUBLE:
            ts.setDoubleValue(Double.parseDouble(v));
            break;
          case DATE:
            ts.setLongValue(dateField.parseMath(null, v).getTime());
            break;
          default:
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unknown type for trie field");
        }
      } catch (NumberFormatException nfe) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, 
                                "Invalid Number: " + v);
      }
      hasValue = true;
      ts.reset();
    } catch (IOException e) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unable to create TrieIndexTokenizer", e);
    }
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (hasValue) {
      ts.close();
    }
  }

  @Override
  public boolean incrementToken() {
    if (hasValue && ts.incrementToken()) {
      ofsAtt.setOffset(startOfs, endOfs);
      return true;
    }
    return false;
  }

  @Override
  public void end() throws IOException {
    if (hasValue) {
      ts.end();
    }
    ofsAtt.setOffset(endOfs, endOfs);
  }
}
