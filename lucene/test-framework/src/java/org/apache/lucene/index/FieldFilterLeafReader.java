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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.lucene.document.FieldTypes;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FilterIterator;

/**
 * A {@link FilterLeafReader} that exposes only a subset
 * of fields from the underlying wrapped reader.
 */
public final class FieldFilterLeafReader extends FilterLeafReader {
  
  private final Set<String> fields;
  private final boolean negate;
  private final FieldInfos fieldInfos;
  private final FieldTypes fieldTypes;

  public FieldFilterLeafReader(LeafReader in, Set<String> fields, boolean negate) {
    super(in);
    this.fields = fields;
    this.negate = negate;
    ArrayList<FieldInfo> filteredInfos = new ArrayList<>();
    Set<String> actualFields = new HashSet<>();
    for (FieldInfo fi : in.getFieldInfos()) {
      if (hasField(fi.name)) {
        filteredInfos.add(fi);  
        actualFields.add(fi.name);
      }
    }
    fieldInfos = new FieldInfos(filteredInfos.toArray(new FieldInfo[filteredInfos.size()]));
    fieldTypes = new FieldTypes(in.getFieldTypes(), actualFields);
  }
  
  boolean hasField(String field) {
    return negate ^ fields.contains(field);
  }

  @Override
  public FieldInfos getFieldInfos() {
    return fieldInfos;
  }

  @Override
  public FieldTypes getFieldTypes() {
    return fieldTypes;
  }

  @Override
  public Fields getTermVectors(int docID) throws IOException {
    Fields f = super.getTermVectors(docID);
    if (f == null) {
      return null;
    }
    f = new FieldFilterFields(f);
    // we need to check for emptyness, so we can return
    // null:
    return f.iterator().hasNext() ? f : null;
  }

  @Override
  public void document(final int docID, final StoredFieldVisitor visitor) throws IOException {
    super.document(docID, new StoredFieldVisitor() {
      @Override
      public void binaryField(FieldInfo fieldInfo, byte[] value) throws IOException {
        visitor.binaryField(fieldInfo, value);
      }

      @Override
      public void stringField(FieldInfo fieldInfo, String value) throws IOException {
        visitor.stringField(fieldInfo, value);
      }

      @Override
      public void intField(FieldInfo fieldInfo, int value) throws IOException {
        visitor.intField(fieldInfo, value);
      }

      @Override
      public void longField(FieldInfo fieldInfo, long value) throws IOException {
        visitor.longField(fieldInfo, value);
      }

      @Override
      public void floatField(FieldInfo fieldInfo, float value) throws IOException {
        visitor.floatField(fieldInfo, value);
      }

      @Override
      public void doubleField(FieldInfo fieldInfo, double value) throws IOException {
        visitor.doubleField(fieldInfo, value);
      }

      @Override
      public Status needsField(FieldInfo fieldInfo) throws IOException {
        return hasField(fieldInfo.name) ? visitor.needsField(fieldInfo) : Status.NO;
      }
    });
  }

  @Override
  public Fields fields() throws IOException {
    final Fields f = super.fields();
    return (f == null) ? null : new FieldFilterFields(f);
  }
  
  

  @Override
  public NumericDocValues getNumericDocValues(String field) throws IOException {
    return hasField(field) ? super.getNumericDocValues(field) : null;
  }

  @Override
  public BinaryDocValues getBinaryDocValues(String field) throws IOException {
    return hasField(field) ? super.getBinaryDocValues(field) : null;
  }

  @Override
  public SortedDocValues getSortedDocValues(String field) throws IOException {
    return hasField(field) ? super.getSortedDocValues(field) : null;
  }
  
  @Override
  public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
    return hasField(field) ? super.getSortedNumericDocValues(field) : null;
  }
  
  @Override
  public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
    return hasField(field) ? super.getSortedSetDocValues(field) : null;
  }

  @Override
  public NumericDocValues getNormValues(String field) throws IOException {
    return hasField(field) ? super.getNormValues(field) : null;
  }

  @Override
  public Bits getDocsWithField(String field) throws IOException {
    return hasField(field) ? super.getDocsWithField(field) : null;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("FieldFilterLeafReader(reader=");
    sb.append(in).append(", fields=");
    if (negate) sb.append('!');
    return sb.append(fields).append(')').toString();
  }
  
  private class FieldFilterFields extends FilterFields {

    public FieldFilterFields(Fields in) {
      super(in);
    }

    @Override
    public int size() {
      // this information is not cheap, return -1 like MultiFields does:
      return -1;
    }

    @Override
    public Iterator<String> iterator() {
      return new FilterIterator<String, String>(super.iterator()) {
        @Override
        protected boolean predicateFunction(String field) {
          return hasField(field);
        }
      };
    }

    @Override
    public Terms terms(String field) throws IOException {
      return hasField(field) ? super.terms(field) : null;
    }
    
  }
  
}
