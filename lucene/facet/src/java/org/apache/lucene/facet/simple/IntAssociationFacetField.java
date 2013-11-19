package org.apache.lucene.facet.simple;

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

import java.util.Arrays;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.util.BytesRef;

/** Associates an arbitrary int with the added facet
 *  path, encoding the int into a 4-byte BytesRef. */
public class IntAssociationFacetField extends AssociationFacetField {

  /** Utility ctor: associates an int value (translates it
   *  to 4-byte BytesRef). */
  public IntAssociationFacetField(int assoc, String dim, String... path) {
    super(intToBytesRef(assoc), dim, path);
  }

  public static BytesRef intToBytesRef(int v) {
    byte[] bytes = new byte[4];
    // big-endian:
    bytes[0] = (byte) (v >> 24);
    bytes[1] = (byte) (v >> 16);
    bytes[2] = (byte) (v >> 8);
    bytes[3] = (byte) v;
    return new BytesRef(bytes);
  }

  public static int bytesRefToInt(BytesRef b) {
    return ((b.bytes[b.offset]&0xFF) << 24) |
      ((b.bytes[b.offset+1]&0xFF) << 16) |
      ((b.bytes[b.offset+2]&0xFF) << 8) |
      (b.bytes[b.offset+3]&0xFF);
  }

  @Override
  public String toString() {
    return "IntAssociationFacetField(dim=" + dim + " path=" + Arrays.toString(path) + " value=" + bytesRefToInt(assoc) + ")";
  }
}
