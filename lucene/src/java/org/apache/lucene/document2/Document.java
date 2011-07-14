package org.apache.lucene.document2;

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

import java.util.*;

import org.apache.lucene.index.IndexReader;  // for javadoc
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;  // for javadoc
import org.apache.lucene.search.ScoreDoc; // for javadoc

/** Documents are the unit of indexing and search.
 *
 * A Document is a set of fields.  Each field has a name and a textual value.
 * A field may be {@link Fieldable#isStored() stored} with the document, in which
 * case it is returned with search hits on the document.  Thus each document
 * should typically contain one or more stored fields which uniquely identify
 * it.
 *
 * <p>Note that fields which are <i>not</i> {@link Fieldable#stored() stored} are
 * <i>not</i> available in documents retrieved from the index, e.g. with {@link
 * ScoreDoc#doc} or {@link IndexReader#document(int)}.
 */

public final class Document implements Iterable<IndexableField> {

  List<IndexableField> fields = new ArrayList<IndexableField>();

  /** Constructs a new document with no fields. */
  public Document() {}

  // @Override not until Java 1.6
  public Iterator<IndexableField> iterator() {

    return new Iterator<IndexableField>() {
      private int fieldUpto = 0;
      
      public boolean hasNext() {
        return fieldUpto < fields.size();
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }

      public IndexableField next() {
        return fields.get(fieldUpto++);
      }
    };
  }

  /**
   * <p>Adds a field to a document.  Several fields may be added with
   * the same name.  In this case, if the fields are indexed, their text is
   * treated as though appended for the purposes of search.</p>
   * <p> Note that add like the removeField(s) methods only makes sense 
   * prior to adding a document to an index. These methods cannot
   * be used to change the content of an existing index! In order to achieve this,
   * a document has to be deleted from an index and a new changed version of that
   * document has to be added.</p>
   */
  public final void add(IndexableField field) {
    fields.add(field);
  }
  
  /**
   * <p>Removes field with the specified name from the document.
   * If multiple fields exist with this name, this method removes the first field that has been added.
   * If there is no field with the specified name, the document remains unchanged.</p>
   * <p> Note that the removeField(s) methods like the add method only make sense 
   * prior to adding a document to an index. These methods cannot
   * be used to change the content of an existing index! In order to achieve this,
   * a document has to be deleted from an index and a new changed version of that
   * document has to be added.</p>
   */
  public final void removeField(String name) {
    Iterator<IndexableField> it = fields.iterator();
    while (it.hasNext()) {
      IndexableField field = it.next();
      if (field.name().equals(name)) {
        it.remove();
        return;
      }
    }
  }
  
  /**
   * <p>Removes all fields with the given name from the document.
   * If there is no field with the specified name, the document remains unchanged.</p>
   * <p> Note that the removeField(s) methods like the add method only make sense 
   * prior to adding a document to an index. These methods cannot
   * be used to change the content of an existing index! In order to achieve this,
   * a document has to be deleted from an index and a new changed version of that
   * document has to be added.</p>
   */
  public final void removeFields(String name) {
    Iterator<IndexableField> it = fields.iterator();
    while (it.hasNext()) {
      IndexableField field = it.next();
      if (field.name().equals(name)) {
        it.remove();
      }
    }
  }

  private final static byte[][] NO_BYTES = new byte[0][];

  /**
  * Returns an array of byte arrays for of the fields that have the name specified
  * as the method parameter.  This method returns an empty
  * array when there are no matching fields.  It never
  * returns null.
  *
  * @param name the name of the field
  * @return a <code>byte[][]</code> of binary field values
  */
  public final byte[][] getBinaryValues(String name) {
    List<byte[]> result = new ArrayList<byte[]>();
    for (IndexableField field : fields) {
      if (field.name().equals(name) && ((Field) field).isBinary())
        result.add(field.binaryValue(null).bytes);
    }
  
    if (result.size() == 0)
      return NO_BYTES;
  
    return result.toArray(new byte[result.size()][]);
  }
  
  /**
  * Returns an array of bytes for the first (or only) field that has the name
  * specified as the method parameter. This method will return <code>null</code>
  * if no binary fields with the specified name are available.
  * There may be non-binary fields with the same name.
  *
  * @param name the name of the field.
  * @return a <code>byte[]</code> containing the binary field value or <code>null</code>
  */
  public final byte[] getBinaryValue(String name) {
    for (IndexableField field : fields) {
      if (field.name().equals(name) && ((Field) field).isBinary())
        return field.binaryValue(null).bytes;
    }
    return null;
  }

  public final IndexableField getField(String name) {
    for (IndexableField field : fields) {
      if (field.name().equals(name))
        return field;
    }
    return null;
  }

  private final static IndexableField[] NO_FIELDS = new IndexableField[0];
  
  public IndexableField[] getFields(String name) {
    List<IndexableField> result = new ArrayList<IndexableField>();
    for (IndexableField field : fields) {
      if (field.name().equals(name)) {
        result.add(field);
      }
    }

    if (result.size() == 0)
      return NO_FIELDS;

    return result.toArray(new IndexableField[result.size()]);
  }
  
  public Integer size() {
    return fields.size();
  }
  
  public final List<IndexableField> getFields() {
    return fields;
  }
  
  public final String get(String name) {
   for (IndexableField field : fields) {
      if (field.name().equals(name) && (field.binaryValue(null) == null))
        return field.stringValue();
    }
    return null;
  }
  
  /** Prints the fields of a document for human consumption. */
  @Override
  public final String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Document<");
    for (int i = 0; i < fields.size(); i++) {
      IndexableField field = fields.get(i);
      buffer.append(field.toString());
      if (i != fields.size()-1)
        buffer.append(" ");
    }
    buffer.append(">");
    return buffer.toString();
  }
}
