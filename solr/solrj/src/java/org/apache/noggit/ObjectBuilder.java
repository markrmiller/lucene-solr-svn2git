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

package org.apache.noggit;

import java.util.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author yonik
 */
public class ObjectBuilder {

  public static Object fromJSON(String json) throws IOException {
    JSONParser p = new JSONParser(json);
    return getVal(p);
  }

  public static Object getVal(JSONParser parser) throws IOException {
    return new ObjectBuilder(parser).getVal();
  }

  final JSONParser parser;
  
  public ObjectBuilder(JSONParser parser) throws IOException {
    this.parser = parser;
    if (parser.lastEvent()==0) parser.nextEvent();
  }


  public Object getVal() throws IOException {
    int ev = parser.lastEvent();
    switch(ev) {
      case JSONParser.STRING: return getString();
      case JSONParser.LONG: return getLong();
      case JSONParser.NUMBER: return getNumber();
      case JSONParser.BIGNUMBER: return getBigNumber();
      case JSONParser.BOOLEAN: return getBoolean();
      case JSONParser.NULL: return getNull();
      case JSONParser.OBJECT_START: return getObject();
      case JSONParser.OBJECT_END: return null; // OR ERROR?
      case JSONParser.ARRAY_START: return getArray();
      case JSONParser.ARRAY_END: return  null; // OR ERROR?
      case JSONParser.EOF: return null; // OR ERROR?
      default: return null; // OR ERROR?
    }
  }


  public Object getString() throws IOException {
    return parser.getString();    
  }

  public Object getLong() throws IOException {
    return Long.valueOf(parser.getLong());    
  }

  public Object getNumber() throws IOException {
    CharArr num = parser.getNumberChars();
    String numstr = num.toString();
    double d = Double.parseDouble(numstr);
    if (!Double.isInfinite(d)) return Double.valueOf(d);
    // TODO: use more efficient constructor in Java5
    return new BigDecimal(numstr);
  }

  public Object getBigNumber() throws IOException {
    CharArr num = parser.getNumberChars();
    String numstr = num.toString();
    for(int ch; (ch=num.read())!=-1;) {
      if (ch=='.' || ch=='e' || ch=='E') return new BigDecimal(numstr);
    }
    return new BigInteger(numstr);
  }

  public Object getBoolean() throws IOException {
    return parser.getBoolean();
  }

  public Object getNull() throws IOException {
    parser.getNull();
    return null;
  }

  public Object newObject() throws IOException {
    return new LinkedHashMap();
  }

  public Object getKey() throws IOException {
    return parser.getString();
  }

  public void addKeyVal(Object map, Object key, Object val) throws IOException {
    Object prev = ((Map)map).put(key,val);
    // TODO: test for repeated value?
  }

  public Object objectEnd(Object obj) {
    return obj;
  }


  public Object getObject() throws IOException {
    Object m = newObject();
    for(;;) {
      int ev = parser.nextEvent();
      if (ev==JSONParser.OBJECT_END) return objectEnd(m);
      Object key = getKey();
      ev = parser.nextEvent();      
      Object val = getVal();
      addKeyVal(m, key, val);
    }
  }

  public Object newArray() {
    return new ArrayList();
  }

  public void addArrayVal(Object arr, Object val) throws IOException {
    ((List)arr).add(val);
  }

  public Object endArray(Object arr) {
    return arr;
  }
  
  public Object getArray() throws IOException {
    Object arr = newArray();
    for(;;) {
      int ev = parser.nextEvent();
      if (ev==JSONParser.ARRAY_END) return endArray(arr);
      Object val = getVal();
      addArrayVal(arr, val);
    }
  }

}
