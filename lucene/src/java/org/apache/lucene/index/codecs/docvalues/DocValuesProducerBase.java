package org.apache.lucene.index.codecs.docvalues;

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
import java.io.IOException;
import java.util.Collection;
import java.util.TreeMap;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.values.Bytes;
import org.apache.lucene.index.values.DocValues;
import org.apache.lucene.index.values.Floats;
import org.apache.lucene.index.values.Ints;
import org.apache.lucene.index.values.Type;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IntsRef;

/**
 * Abstract base class for FieldsProducer implementations supporting
 * {@link DocValues}.
 * 
 * @lucene.experimental
 */
public abstract class DocValuesProducerBase extends FieldsProducer {

  protected final TreeMap<String, DocValues> docValues = new TreeMap<String, DocValues>();
  private final DocValuesCodecInfo info = new DocValuesCodecInfo();

  /**
   * Creates a new {@link DocValuesProducerBase} instance and loads all
   * {@link DocValues} instances for this segment and codec.
   * 
   * @param si
   *          the segment info to load the {@link DocValues} for.
   * @param dir
   *          the directory to load the {@link DocValues} from.
   * @param fieldInfo
   *          the {@link FieldInfos}
   * @param codecId
   *          the codec ID
   * @throws IOException
   *           if an {@link IOException} occurs
   */
  protected DocValuesProducerBase(SegmentInfo si, Directory dir,
      FieldInfos fieldInfo, String codecId) throws IOException {
    info.read(dir, si, codecId);
    load(fieldInfo, si.name, si.docCount, dir, codecId);
  }

  /**
   * Returns a {@link DocValues} instance for the given field name or
   * <code>null</code> if this field has no {@link DocValues}.
   */
  @Override
  public DocValues docValues(String field) throws IOException {
    return docValues.get(field);
  }

  // Only opens files... doesn't actually load any values
  protected void load(FieldInfos fieldInfos, String segment, int docCount,
      Directory dir, String codecId) throws IOException {
    final IntsRef valueFields = info.fieldIDs();
    for (int i = valueFields.offset; i < valueFields.length; i++) {
      final int fieldNumber = valueFields.ints[i];
      final FieldInfo fieldInfo = fieldInfos.fieldInfo(fieldNumber);
      assert fieldInfo.hasDocValues();
      final String field = fieldInfo.name;
      // TODO can we have a compound file per segment and codec for docvalues?
      final String id = info.docValuesId(segment, codecId, fieldNumber + "");
      docValues.put(field, loadDocValues(docCount, dir, id, fieldInfo
          .getDocValues()));
    }
  }

  /**
   * Loads a {@link DocValues} instance depending on the given {@link Type}.
   * Codecs that use different implementations for a certain {@link Type} can
   * simply override this method and return their custom implementations.
   * 
   * @param docCount
   *          number of documents in the segment
   * @param dir
   *          the {@link Directory} to load the {@link DocValues} from
   * @param id
   *          the unique file ID within the segment
   * @param type
   *          the type to load
   * @return a {@link DocValues} instance for the given type
   * @throws IOException
   *           if an {@link IOException} occurs
   * @throws IllegalArgumentException
   *           if the given {@link Type} is not supported
   */
  protected DocValues loadDocValues(int docCount, Directory dir, String id,
      Type type) throws IOException {
    switch (type) {
    case PACKED_INTS:
      return Ints.getValues(dir, id, false);
    case SIMPLE_FLOAT_4BYTE:
      return Floats.getValues(dir, id, docCount);
    case SIMPLE_FLOAT_8BYTE:
      return Floats.getValues(dir, id, docCount);
    case BYTES_FIXED_STRAIGHT:
      return Bytes.getValues(dir, id, Bytes.Mode.STRAIGHT, true, docCount);
    case BYTES_FIXED_DEREF:
      return Bytes.getValues(dir, id, Bytes.Mode.DEREF, true, docCount);
    case BYTES_FIXED_SORTED:
      return Bytes.getValues(dir, id, Bytes.Mode.SORTED, true, docCount);
    case BYTES_VAR_STRAIGHT:
      return Bytes.getValues(dir, id, Bytes.Mode.STRAIGHT, false, docCount);
    case BYTES_VAR_DEREF:
      return Bytes.getValues(dir, id, Bytes.Mode.DEREF, false, docCount);
    case BYTES_VAR_SORTED:
      return Bytes.getValues(dir, id, Bytes.Mode.SORTED, false, docCount);
    default:
      throw new IllegalStateException("unrecognized index values mode " + type);
    }
  }

  @Override
  public void close() throws IOException {
    Collection<DocValues> values = docValues.values();
    IOException ex = null;
    for (DocValues docValues : values) {
      try {
        docValues.close();
      } catch (IOException e) {
        ex = e;
      }
    }
    if (ex != null) {
      throw ex;
    }
  }
}
