package org.apache.lucene.document;

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
n * limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.BytesRef;

final class BinaryTokenStream extends TokenStream {
  private final ByteTermAttribute bytesAtt = addAttribute(ByteTermAttribute.class);
  private boolean available = true;
  
  public BinaryTokenStream() {
  }

  public void setValue(BytesRef value) {
    bytesAtt.setBytesRef(value);
  }
  
  @Override
  public boolean incrementToken() {
    if (available) {
      clearAttributes();
      available = false;
      return true;
    }
    return false;
  }
  
  @Override
  public void reset() {
    available = true;
  }
  
  public interface ByteTermAttribute extends TermToBytesRefAttribute {
    public void setBytesRef(BytesRef bytes);
  }
  
  public static class ByteTermAttributeImpl extends AttributeImpl implements ByteTermAttribute, TermToBytesRefAttribute {
    private BytesRef bytes;
    
    @Override
    public void fillBytesRef() {
      // no-op: the bytes was already filled by our owner's incrementToken
    }
    
    @Override
    public BytesRef getBytesRef() {
      return bytes;
    }

    @Override
    public void setBytesRef(BytesRef bytes) {
      this.bytes = bytes;
    }
    
    @Override
    public void clear() {
      // nocommit must null bytes here, and reset should re-instate it
    }
    
    @Override
    public void copyTo(AttributeImpl target) {
      ByteTermAttributeImpl other = (ByteTermAttributeImpl) target;
      other.bytes = bytes;
    }
  }
}
