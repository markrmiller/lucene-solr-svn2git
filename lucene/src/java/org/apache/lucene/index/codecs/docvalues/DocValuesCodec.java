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
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.FieldsEnum;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.index.codecs.FieldsConsumer;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.codecs.TermsConsumer;
import org.apache.lucene.index.values.DocValues;
import org.apache.lucene.index.values.Writer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;

/**
 * A codec that adds DocValues support to a given codec transparently.
 * @lucene.experimental
 */
public class DocValuesCodec extends Codec {
  private final Codec other;
  private final Comparator<BytesRef> comparator;

  public DocValuesCodec(Codec other, Comparator<BytesRef> comparator) {
    this.name = other.name;
    this.other = other;
    this.comparator = comparator;
  }

  public DocValuesCodec(Codec other) {
    this(other, null);
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state)
      throws IOException {
    final WrappingFieldsConsumer consumer;
      consumer = new WrappingFieldsConsumer(other, comparator, state);
    // nocommit this is a hack and only necessary since
    // we want to initialized the wrapped
    // fieldsConsumer lazily with a SegmentWriteState created after the docvalue
    // ones is. We should fix this in DocumentWriter I guess. See
    // DocFieldProcessor too!
    return consumer;
  }

  private static class WrappingFieldsConsumer extends FieldsConsumer {
    private final SegmentWriteState state;
    private FieldsConsumer wrappedConsumer;
    private final Codec other;
    private final Comparator<BytesRef> comparator;

    public WrappingFieldsConsumer(Codec other, Comparator<BytesRef> comparator, SegmentWriteState state) {
      this.other = other;
      this.comparator = comparator;
      this.state = state;
    }

    @Override
    public void close() throws IOException {
      synchronized (this) {
        if (wrappedConsumer != null) {
          wrappedConsumer.close();
        } 
      }
    
    }

    @Override
    public synchronized DocValuesConsumer addValuesField(FieldInfo field)
        throws IOException {
      final DocValuesConsumer consumer = Writer.create(field.getDocValues(), docValuesId(state.segmentName, state.codecId, field.number),
      // TODO can we have a compound file per segment and codec for
          // docvalues?
          state.directory, comparator, state.bytesUsed);
      return consumer;
    }

    @Override
    public TermsConsumer addField(FieldInfo field) throws IOException {
      synchronized (this) {
        if (wrappedConsumer == null)
          wrappedConsumer = other.fieldsConsumer(state);
      }
      return wrappedConsumer.addField(field);
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state)
      throws IOException {
    final FieldInfos fieldInfos = state.fieldInfos;
    boolean indexed = false;
    boolean docValues = false;
    for (FieldInfo fieldInfo : fieldInfos) {
      if (fieldInfo.getCodecId() == state.codecId) {
        indexed |= fieldInfo.isIndexed;
        docValues |= fieldInfo.hasDocValues();
        if (indexed && docValues)
          break;
      }
    }
    final FieldsProducer indexedProducer = indexed ? other.fieldsProducer(state) : FieldsProducer.EMPTY;
    if (docValues) {
      return new WrappingFielsdProducer(state, indexedProducer);
    } else {
      return FieldsProducer.EMPTY;
    }
  }
  
  static String docValuesId(String segmentsName, int codecID, int fieldId) {
    return segmentsName + "_" + codecID + "-" + fieldId;
  }

  @Override
  public void files(Directory dir, SegmentInfo segmentInfo, int codecId,
      Set<String> files) throws IOException {
    FieldInfos fieldInfos = segmentInfo.getFieldInfos();
    boolean indexed = false;
    for (FieldInfo fieldInfo : fieldInfos) {
      if (fieldInfo.getCodecId() == codecId) {
        indexed |= fieldInfo.isIndexed;
        if (fieldInfo.hasDocValues()) {
          String filename = docValuesId(segmentInfo.name, codecId, fieldInfo.number);
          switch (fieldInfo.getDocValues()) {
          case BYTES_FIXED_DEREF:
          case BYTES_VAR_DEREF:
          case BYTES_VAR_SORTED:
          case BYTES_FIXED_SORTED:
          case BYTES_VAR_STRAIGHT:
            files.add(IndexFileNames.segmentFileName(filename, "",
                Writer.INDEX_EXTENSION));
            assert dir.fileExists(IndexFileNames.segmentFileName(filename, "",
                Writer.INDEX_EXTENSION));
          case BYTES_FIXED_STRAIGHT:
          case FLOAT_32:
          case FLOAT_64:
          case INTS:
            files.add(IndexFileNames.segmentFileName(filename, "",
                Writer.DATA_EXTENSION));
            assert dir.fileExists(IndexFileNames.segmentFileName(filename, "",
                Writer.DATA_EXTENSION));
            break;
           default:
             assert false;
          }
        }

      }
    }
    if (indexed) {
      other.files(dir, segmentInfo, codecId, files);
    }
  }

  @Override
  public void getExtensions(Set<String> extensions) {
    other.getExtensions(extensions);
    extensions.add(Writer.DATA_EXTENSION);
    extensions.add(Writer.INDEX_EXTENSION);
  }

  static class WrappingFielsdProducer extends DocValuesProducerBase {

    private final FieldsProducer other;

    WrappingFielsdProducer(SegmentReadState state, FieldsProducer other)
        throws IOException {
      super(state.segmentInfo, state.dir, state.fieldInfos, state.codecId);
      this.other = other;
    }

    @Override
    public void close() throws IOException {
      try {
        other.close();
      } finally {
        super.close();
      }
    }

    @Override
    public void loadTermsIndex(int indexDivisor) throws IOException {
      other.loadTermsIndex(indexDivisor);
    }

    @Override
    public FieldsEnum iterator() throws IOException {
      return new WrappingFieldsEnum(other.iterator(), docValues.entrySet()
          .iterator());
    }

    @Override
    public Terms terms(String field) throws IOException {
      return other.terms(field);
    }
  }

  static abstract class NameValue<V> {
    String name;
    V value;

    NameValue<?> smaller(NameValue<?> other) throws IOException {
      if (other.name == null) {
        if (this.name == null) {
          return null;
        }
        return this;
      } else if (this.name == null) {
        return other;
      }
      final int res = this.name.compareTo(other.name);
      if (res < 0)
        return this;
      if (res == 0)
        other.name = this.name;
      return other;
    }

    abstract NameValue<V> next() throws IOException;
  }

  static class FieldsEnumNameValue extends NameValue<FieldsEnum> {
    @Override
    NameValue<FieldsEnum> next() throws IOException {
      name = value.next();
      return this;
    }
  }

  static class DocValueNameValue extends NameValue<DocValues> {
    Iterator<Entry<String, DocValues>> iter;

    @Override
    NameValue<DocValues> next() {
      if (iter.hasNext()) {
        Entry<String, DocValues> next = iter.next();
        value = next.getValue();
        name = next.getKey();
      } else {
        name = null;
      }
      return this;
    }
  }

  static class WrappingFieldsEnum extends FieldsEnum {
    private final DocValueNameValue docValues = new DocValueNameValue();
    private final NameValue<FieldsEnum> fieldsEnum = new FieldsEnumNameValue();
    private NameValue<?> coordinator;

    @Override
    public AttributeSource attributes() {
      return fieldsEnum.value.attributes();
    }

    public WrappingFieldsEnum(FieldsEnum wrapped,
        Iterator<Entry<String, DocValues>> docValues) {
      this.docValues.iter = docValues;
      this.fieldsEnum.value = wrapped;
      coordinator = null;
    }

    @Override
    public DocValues docValues() throws IOException {
      if (docValues.name == coordinator.name)
        return docValues.value;
      return null;
    }

    @Override
    public String next() throws IOException {
      if (coordinator == null) {
        coordinator = fieldsEnum.next().smaller(docValues.next());
      } else {
        String current = coordinator.name;
        if (current == docValues.name) {
          docValues.next();
        }
        if (current == fieldsEnum.name) {
          fieldsEnum.next();
        }
        coordinator = docValues.smaller(fieldsEnum);

      }
      return coordinator == null ? null : coordinator.name;
    }

    @Override
    public TermsEnum terms() throws IOException {
      if (fieldsEnum.name == coordinator.name) {
        return fieldsEnum.value.terms();
      }
      return null;
    }
  }

}
