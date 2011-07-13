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

import java.io.Reader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.StringHelper;

/**
 * A field is a section of a Document. Each field has two parts, a name and a
 * value. Values may be free text, provided as a String or as a Reader, or they
 * may be atomic keywords, which are not further processed. Such keywords may be
 * used to represent dates, urls, etc. Fields are optionally stored in the
 * index, so that they may be returned with hits on the document.
 */

public class Field implements IndexableField {
  
  protected FieldType type;
  protected String name = "body";
  // the data object for all different kind of field values
  protected Object fieldsData = null;
  // pre-analyzed tokenStream for indexed fields
  protected TokenStream tokenStream;
  protected boolean isBinary = false;
  // length/offset for all primitive types
  protected int binaryLength;
  protected int binaryOffset;
  
  protected float boost = 1.0f;

  public Field(String name, FieldType type) {
    this.name = name;
    this.type = type;
  }
  
  public Field(String name, FieldType type, Reader reader) {
    if (name == null)
      throw new NullPointerException("name cannot be null");
    if (reader == null)
      throw new NullPointerException("reader cannot be null");
    
    this.name = StringHelper.intern(name);        // field names are interned
    this.fieldsData = reader;
    this.type = type;
  }
  
  public Field(String name, FieldType type, TokenStream tokenStream) {
    if (name == null)
      throw new NullPointerException("name cannot be null");
    if (tokenStream == null)
      throw new NullPointerException("tokenStream cannot be null");
    
    this.name = StringHelper.intern(name);        // field names are interned
    this.fieldsData = null;
    this.tokenStream = tokenStream;
    this.type = type;
  }
  
  public Field(String name, FieldType type, byte[] value) {
    this(name, type, value, 0, value.length);
  }
  
  public Field(String name, FieldType type, byte[] value, int offset, int length) {
    this.isBinary = true;
    this.fieldsData = value;
    this.type = type;
    this.binaryOffset = offset;
    this.binaryLength = length;
    this.name = StringHelper.intern(name);
  }
  
  public Field(String name, FieldType type, String value) {
    this(name, true, type, value);
  }
  
  public Field(String name, boolean internName, FieldType type, String value) {
    if (name == null)
      throw new IllegalArgumentException("name cannot be null");
    if (value == null)
      throw new IllegalArgumentException("value cannot be null");
    
    this.type = type;
    this.name = name;
    this.fieldsData = value;
    
    if (internName) // field names are optionally interned
      name = StringHelper.intern(name);
  }

  public boolean isNumeric() {
    return false;
  }
  
  /**
   * The value of the field as a String, or null. If null, the Reader value or
   * binary value is used. Exactly one of stringValue(), readerValue(), and
   * getBinaryValue() must be set.
   */
  public String stringValue() {
    return fieldsData instanceof String ? (String) fieldsData : null;
  }
  
  /**
   * The value of the field as a Reader, or null. If null, the String value or
   * binary value is used. Exactly one of stringValue(), readerValue(), and
   * getBinaryValue() must be set.
   */
  public Reader readerValue() {
    return fieldsData instanceof Reader ? (Reader) fieldsData : null;
  }
  
  /**
   * The TokesStream for this field to be used when indexing, or null. If null,
   * the Reader value or String value is analyzed to produce the indexed tokens.
   */
  public TokenStream tokenStreamValue() {
    return tokenStream;
  }
  
  /**
   * <p>
   * Expert: change the value of this field. This can be used during indexing to
   * re-use a single Field instance to improve indexing speed by avoiding GC
   * cost of new'ing and reclaiming Field instances. Typically a single
   * {@link Document} instance is re-used as well. This helps most on small
   * documents.
   * </p>
   * 
   * <p>
   * Each Field instance should only be used once within a single
   * {@link Document} instance. See <a
   * href="http://wiki.apache.org/lucene-java/ImproveIndexingSpeed"
   * >ImproveIndexingSpeed</a> for details.
   * </p>
   */
  public void setValue(String value) {
    if (isBinary) {
      throw new IllegalArgumentException(
          "cannot set a String value on a binary field");
    }
    fieldsData = value;
  }
  
  /**
   * Expert: change the value of this field. See <a
   * href="#setValue(java.lang.String)">setValue(String)</a>.
   */
  public void setValue(Reader value) {
    if (isBinary) {
      throw new IllegalArgumentException(
          "cannot set a Reader value on a binary field");
    }
    if (stored()) {
      throw new IllegalArgumentException(
          "cannot set a Reader value on a stored field");
    }
    fieldsData = value;
  }
  
  /**
   * Expert: change the value of this field. See <a
   * href="#setValue(java.lang.String)">setValue(String)</a>.
   */
  public void setValue(byte[] value) {
    if (!isBinary) {
      throw new IllegalArgumentException(
          "cannot set a byte[] value on a non-binary field");
    }
    fieldsData = value;
    binaryLength = value.length;
    binaryOffset = 0;
  }
  
  /**
   * Expert: change the value of this field. See <a
   * href="#setValue(java.lang.String)">setValue(String)</a>.
   */
  public void setValue(byte[] value, int offset, int length) {
    if (!isBinary) {
      throw new IllegalArgumentException(
          "cannot set a byte[] value on a non-binary field");
    }
    fieldsData = value;
    binaryLength = length;
    binaryOffset = offset;
  }
  
  /**
   * Expert: sets the token stream to be used for indexing and causes
   * isIndexed() and isTokenized() to return true. May be combined with stored
   * values from stringValue() or getBinaryValue()
   */
  public void setTokenStream(TokenStream tokenStream) {
    if (!indexed() || !tokenized()) {
      throw new IllegalArgumentException(
          "cannot set token stream on non indexed and tokenized field");
    }
    this.tokenStream = tokenStream;
  }
  
  public String name() {
    return name;
  }
  
  public float boost() {
    return boost;
  }
  
  /**
   * Sets the boost factor hits on this field. This value will be multiplied
   * into the score of all hits on this this field of this document.
   * 
   * <p>
   * The boost is multiplied by
   * {@link org.apache.lucene.document.Document#getBoost()} of the document
   * containing this field. If a document has multiple fields with the same
   * name, all such values are multiplied together. This product is then used to
   * compute the norm factor for the field. By default, in the
   * {@link org.apache.lucene.search.Similarity#computeNorm(FieldInvertState)}
   * method, the boost value is multiplied by the length normalization factor
   * and then rounded by
   * {@link org.apache.lucene.search.Similarity#encodeNormValue(float)} before
   * it is stored in the index. One should attempt to ensure that this product
   * does not overflow the range of that encoding.
   * 
   * @see org.apache.lucene.document.Document#setBoost(float)
   * @see org.apache.lucene.search.Similarity#computeNorm(FieldInvertState)
   * @see org.apache.lucene.search.Similarity#encodeNormValue(float)
   */
  public void setBoost(float boost) {
    this.boost = boost;
  }
  
  public boolean numeric() {
    return false;
  }

  public Number numericValue() {
    return null;
  }

  public NumericField.DataType numericDataType() {
    return null;
  }
  
  private byte[] getBinaryValue(byte[] result /* unused */) {
    if (isBinary || fieldsData instanceof byte[]) return (byte[]) fieldsData;
    else return null;
  }
  
  private byte[] getBinaryValue() {
    return getBinaryValue(null);
  }
  
  public BytesRef binaryValue(BytesRef reuse) {
    final byte[] bytes = getBinaryValue();
    if (bytes != null) {
      if (reuse == null) {
        return new BytesRef(bytes, getBinaryOffset(), getBinaryLength());
      } else {
        reuse.bytes = bytes;
        reuse.offset = getBinaryOffset();
        reuse.length = getBinaryLength();
        return reuse;
      }
    } else {
      return null;
    }
  }
  
  /**
   * Returns length of byte[] segment that is used as value, if Field is not
   * binary returned value is undefined
   * 
   * @return length of byte[] segment that represents this Field value
   */
  private int getBinaryLength() {
    if (isBinary) {
      return binaryLength;
    } else if (fieldsData instanceof byte[]) return ((byte[]) fieldsData).length;
    else return 0;
  }
  
  /**
   * Returns offset into byte[] segment that is used as value, if Field is not
   * binary returned value is undefined
   * 
   * @return index of the first character in byte[] segment that represents this
   *         Field value
   */
  public int getBinaryOffset() {
    return binaryOffset;
  }
  
  public boolean isBinary() {
    return isBinary;
  }
  
  /** methods from inner FieldType */
  
  public boolean stored() {
    return type.stored();
  }
  
  public boolean indexed() {
    return type.indexed();
  }
  
  public boolean tokenized() {
    return type.tokenized();
  }
  
  public boolean omitNorms() {
    return type.omitNorms();
  }
  
  public boolean omitTermFreqAndPositions() {
    return type.omitTermFreqAndPositions();
  }
  
  public boolean storeTermVectors() {
    return type.storeTermVectors();
  }
  
  public boolean storeTermVectorOffsets() {
    return type.storeTermVectorOffsets();
  }
  
  public boolean storeTermVectorPositions() {
    return type.storeTermVectorPositions();
  }
  
  public boolean lazy() {
    return type.lazy();
  }
  
  /** Prints a Field for human consumption. */
  @Override
  public final String toString() {
    StringBuilder result = new StringBuilder();
    result.append(type.toString());
    result.append('<');
    result.append(name);
    result.append(':');

    if (fieldsData != null && type.lazy() == false) {
      result.append(fieldsData);
    }

    result.append('>');
    return result.toString();
  }
}
