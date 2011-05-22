package org.apache.lucene.analysis;
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


import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.index.Payload;

import java.io.IOException;
import java.io.Reader;


/**
 *
 *
 **/
public final class MockPayloadAnalyzer extends Analyzer {

  @Override
  public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream result = new MockTokenizer(reader, MockTokenizer.WHITESPACE, true);
    return new MockPayloadFilter(result, fieldName);
  }
}


/**
 *
 *
 **/
final class MockPayloadFilter extends TokenFilter {
  String fieldName;

  int pos;

  int i;

  final PositionIncrementAttribute posIncrAttr;
  final PayloadAttribute payloadAttr;
  final CharTermAttribute termAttr;

  public MockPayloadFilter(TokenStream input, String fieldName) {
    super(input);
    this.fieldName = fieldName;
    pos = 0;
    i = 0;
    posIncrAttr = input.addAttribute(PositionIncrementAttribute.class);
    payloadAttr = input.addAttribute(PayloadAttribute.class);
    termAttr = input.addAttribute(CharTermAttribute.class);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      payloadAttr.setPayload(new Payload(("pos: " + pos).getBytes()));
      int posIncr;
      if (i % 2 == 1) {
        posIncr = 1;
      } else {
        posIncr = 0;
      }
      posIncrAttr.setPositionIncrement(posIncr);
      pos += posIncr;
      i++;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    i = 0;
    pos = 0;
  }
}

