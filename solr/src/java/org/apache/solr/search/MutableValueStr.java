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
package org.apache.solr.search;

import org.apache.lucene.util.BytesRef;

public class MutableValueStr extends MutableValue {
  public BytesRef value = new BytesRef();

  @Override
  public Object toObject() {
    return exists ? value.utf8ToString() : null;
  }

  @Override
  public void copy(MutableValue source) {
    MutableValueStr s = (MutableValueStr) source;
    exists = s.exists;
    value.copy(s.value);
  }

  @Override
  public MutableValue duplicate() {
    MutableValueStr v = new MutableValueStr();
    v.value.copy(value);
    v.exists = this.exists;
    return v;
  }

  @Override
  public boolean equalsSameType(Object other) {
    MutableValueStr b = (MutableValueStr)other;
    return value.equals(b.value) && exists == b.exists;
  }

  @Override
  public int compareSameType(Object other) {
    MutableValueStr b = (MutableValueStr)other;
    int c = value.compareTo(b.value);
    if (c != 0) return c;
    if (exists == b.exists) return 0;
    return exists ? 1 : -1;
  }


  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
