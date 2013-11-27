package org.apache.lucene.facet;

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

import org.apache.lucene.document.Document; // javadocs
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.util.BytesRef;

/** Add an instance of this to your {@link Document} to add
 *  a facet label associated with an arbitrary byte[].
 *  This will require a custom {@link Facets}
 *  implementation at search time; see {@link
 *  IntAssociationFacetField} and {@link
 *  FloatAssociationFacetField} to use existing {@link
 *  Facets} implementations.
 * 
 *  @lucene.experimental */
public class AssociationFacetField extends Field {
  static final FieldType TYPE = new FieldType();
  static {
    TYPE.setIndexed(true);
    TYPE.freeze();
  }
  protected final String dim;
  protected final String[] path;
  protected final BytesRef assoc;

  /** Creates this from {@code dim} and {@code path} and an
   *  association */
  public AssociationFacetField(BytesRef assoc, String dim, String... path) {
    super("dummy", TYPE);
    this.dim = dim;
    this.assoc = assoc;
    if (path.length == 0) {
      throw new IllegalArgumentException("path must have at least one element");
    }
    this.path = path;
  }

  private static BytesRef intToBytesRef(int v) {
    byte[] bytes = new byte[4];
    // big-endian:
    bytes[0] = (byte) (v >> 24);
    bytes[1] = (byte) (v >> 16);
    bytes[2] = (byte) (v >> 8);
    bytes[3] = (byte) v;
    return new BytesRef(bytes);
  }

  @Override
  public String toString() {
    return "AssociationFacetField(dim=" + dim + " path=" + Arrays.toString(path) + " bytes=" + assoc + ")";
  }
}
