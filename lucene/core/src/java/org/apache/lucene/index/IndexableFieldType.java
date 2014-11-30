package org.apache.lucene.index;

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

import org.apache.lucene.analysis.Analyzer; // javadocs

/** 
 * Describes the properties of a field.
 * @lucene.experimental 
 */
public interface IndexableFieldType {

  /** True if the field's value should be stored */
  default public boolean stored() {
    return false;
  }
  
  /** 
   * True if this field's indexed form should be also stored 
   * into term vectors.
   * <p>
   * This builds a miniature inverted-index for this field which
   * can be accessed in a document-oriented way from 
   * {@link IndexReader#getTermVector(int,String)}.
   * <p>
   * This option is illegal if {@link #indexOptions()} returns
   * IndexOptions.NONE.
   */
  default public boolean storeTermVectors() {
    return false;
  }

  /** 
   * True if this field's token character offsets should also
   * be stored into term vectors.
   * <p>
   * This option is illegal if term vectors are not enabled for the field
   * ({@link #storeTermVectors()} is false)
   */
  default public boolean storeTermVectorOffsets() {
    return false;
  }

  /** 
   * True if this field's token positions should also be stored
   * into the term vectors.
   * <p>
   * This option is illegal if term vectors are not enabled for the field
   * ({@link #storeTermVectors()} is false). 
   */
  default public boolean storeTermVectorPositions() {
    return false;
  }
  
  /** 
   * True if this field's token payloads should also be stored
   * into the term vectors.
   * <p>
   * This option is illegal if term vector positions are not enabled 
   * for the field ({@link #storeTermVectors()} is false).
   */
  default public boolean storeTermVectorPayloads() {
    return false;
  }

  /**
   * True if normalization values should be omitted for the field.
   * <p>
   * This saves memory, but at the expense of scoring quality (length normalization
   * will be disabled), and if you omit norms, you cannot use index-time boosts. 
   */
  default public boolean omitNorms() {
    return false;
  }

  /** {@link IndexOptions}, describing what should be
   *  recorded into the inverted index */
  default public IndexOptions indexOptions() {
    return IndexOptions.NONE;
  }

  /** 
   * DocValues {@link DocValuesType}: how the field's value will be indexed
   * into docValues.
   */
  default public DocValuesType docValuesType() {
    return DocValuesType.NONE;
  }

  /** Returns the gap to insert between multi-valued, tokenized fields */
  default public int getPositionGap() {
    return 1;
  }

  /** Returns the gap offset to insert between multi-valued, tokenized fields */
  default public int getOffsetGap() {
    return 0;
  }
}
