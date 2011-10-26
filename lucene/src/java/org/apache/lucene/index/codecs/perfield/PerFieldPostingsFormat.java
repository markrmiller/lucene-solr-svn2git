package org.apache.lucene.index.codecs.perfield;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.FieldsEnum;
import org.apache.lucene.index.PerDocWriteState;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.codecs.PostingsFormat;
import org.apache.lucene.index.codecs.FieldsConsumer;
import org.apache.lucene.index.codecs.FieldsProducer;
import org.apache.lucene.index.codecs.PerDocConsumer;
import org.apache.lucene.index.codecs.PerDocValues;
import org.apache.lucene.index.codecs.TermsConsumer;
import org.apache.lucene.index.codecs.DocValuesConsumer;
import org.apache.lucene.index.values.IndexDocValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.IOUtils;

/**
 * Enables per field format support. This class selects the format used to
 * write a field depending on the provided {@link SegmentFormats}. For each field
 * seen it resolves the format based on the {@link FieldInfo#formatId} which is
 * only valid during a segment merge. See {@link SegmentFormats} javadoc for
 * details.
 * 
 * @lucene.internal
 */
final class PerFieldPostingsFormat extends PostingsFormat {
  private final SegmentFormats segmentFormats;

  PerFieldPostingsFormat(SegmentFormats segmentFormats) {
    super("PerField");
    this.segmentFormats = segmentFormats;
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state)
      throws IOException {
    return new FieldsWriter(state);
  }

  private class FieldsWriter extends FieldsConsumer {
    private final ArrayList<FieldsConsumer> consumers = new ArrayList<FieldsConsumer>();

    public FieldsWriter(SegmentWriteState state) throws IOException {
      assert segmentFormats == state.segmentFormats;
      final PostingsFormat[] formats = segmentFormats.formats;
      for (int i = 0; i < formats.length; i++) {
        boolean success = false;
        try {
          consumers.add(formats[i].fieldsConsumer(new SegmentWriteState(state, i)));
          success = true;
        } finally {
          if (!success) {
            IOUtils.closeWhileHandlingException(consumers);
          }
        }
      }
    }

    @Override
    public TermsConsumer addField(FieldInfo field) throws IOException {
      assert field.getFormatId() != FieldInfo.UNASSIGNED_FORMAT_ID;
      final FieldsConsumer fields = consumers.get(field.getFormatId());
      return fields.addField(field);
    }

    @Override
    public void close() throws IOException {
      IOUtils.close(consumers);
    }
  }

  private class FieldsReader extends FieldsProducer {

    private final Set<String> fields = new TreeSet<String>();
    private final Map<String, FieldsProducer> codecs = new HashMap<String, FieldsProducer>();

    public FieldsReader(Directory dir, FieldInfos fieldInfos, SegmentInfo si,
        IOContext context, int indexDivisor) throws IOException {

      final Map<PostingsFormat, FieldsProducer> producers = new HashMap<PostingsFormat, FieldsProducer>();
      boolean success = false;
      try {
        for (FieldInfo fi : fieldInfos) {
          if (fi.isIndexed) { 
            fields.add(fi.name);
            assert fi.getFormatId() != FieldInfo.UNASSIGNED_FORMAT_ID;
            PostingsFormat format = segmentFormats.formats[fi.getFormatId()];
            if (!producers.containsKey(format)) {
              producers.put(format, format.fieldsProducer(new SegmentReadState(dir,
                                                                             si, fieldInfos, context, indexDivisor, fi.getFormatId())));
            }
            codecs.put(fi.name, producers.get(format));
          }
        }
        success = true;
      } finally {
        if (!success) {
          // If we hit exception (eg, IOE because writer was
          // committing, or, for any other reason) we must
          // go back and close all FieldsProducers we opened:
          IOUtils.closeWhileHandlingException(producers.values());
        }
      }
    }
    

    private final class FieldsIterator extends FieldsEnum {
      private final Iterator<String> it;
      private String current;

      public FieldsIterator() {
        it = fields.iterator();
      }

      @Override
      public String next() {
        if (it.hasNext()) {
          current = it.next();
        } else {
          current = null;
        }

        return current;
      }

      @Override
      public TermsEnum terms() throws IOException {
        Terms terms = codecs.get(current).terms(current);
        if (terms != null) {
          return terms.iterator();
        } else {
          return TermsEnum.EMPTY;
        }
      }
    }

    @Override
    public FieldsEnum iterator() throws IOException {
      return new FieldsIterator();
    }

    @Override
    public Terms terms(String field) throws IOException {
      FieldsProducer fields = codecs.get(field);
      return fields == null ? null : fields.terms(field);
    }
    
    @Override
    public void close() throws IOException {
      IOUtils.close(codecs.values());
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state)
      throws IOException {
    return new FieldsReader(state.dir, state.fieldInfos, state.segmentInfo,
        state.context, state.termsIndexDivisor);
  }

  @Override
  public void files(Directory dir, SegmentInfo info, int formatId, Set<String> files)
      throws IOException {
    // ignore formatid since segmentFormat will assign it per codec
    segmentFormats.files(dir, info, files);
  }

  @Override
  public void getExtensions(Set<String> extensions) {
    for (PostingsFormat format : segmentFormats.formats) {
      format.getExtensions(extensions);
    }
  }

  @Override
  public PerDocConsumer docsConsumer(PerDocWriteState state) throws IOException {
    return new PerDocConsumers(state);
  }

  @Override
  public PerDocValues docsProducer(SegmentReadState state) throws IOException {
    return new PerDocProducers(state.dir, state.fieldInfos, state.segmentInfo,
    state.context, state.termsIndexDivisor);
  }
  
  private final class PerDocProducers extends PerDocValues {
    private final TreeMap<String, PerDocValues> codecs = new TreeMap<String, PerDocValues>();

    public PerDocProducers(Directory dir, FieldInfos fieldInfos, SegmentInfo si,
        IOContext context, int indexDivisor) throws IOException {
      final Map<PostingsFormat, PerDocValues> producers = new HashMap<PostingsFormat, PerDocValues>();
      boolean success = false;
      try {
        for (FieldInfo fi : fieldInfos) {
          if (fi.hasDocValues()) { 
            assert fi.getFormatId() != FieldInfo.UNASSIGNED_FORMAT_ID;
            PostingsFormat codec = segmentFormats.formats[fi.getFormatId()];
            if (!producers.containsKey(codec)) {
              producers.put(codec, codec.docsProducer(new SegmentReadState(dir,
                si, fieldInfos, context, indexDivisor, fi.getFormatId())));
            }
            codecs.put(fi.name, producers.get(codec));
          }
        }
        success = true;
      } finally {
        if (!success) {
          IOUtils.closeWhileHandlingException(producers.values());
        }
      }
    }
    
    @Override
    public Collection<String> fields() {
      return codecs.keySet();
    }
    @Override
    public IndexDocValues docValues(String field) throws IOException {
      final PerDocValues perDocProducer = codecs.get(field);
      if (perDocProducer == null) {
        return null;
      }
      return perDocProducer.docValues(field);
    }
    
    public void close() throws IOException {
      IOUtils.close(codecs.values());
    }
  }
  
  private final class PerDocConsumers extends PerDocConsumer {
    private final PerDocConsumer[] consumers;
    private final PostingsFormat[] formats;
    private final PerDocWriteState state;

    public PerDocConsumers(PerDocWriteState state) throws IOException {
      assert segmentFormats == state.segmentFormats;
      this.state = state;
      formats = segmentFormats.formats;
      consumers = new PerDocConsumer[formats.length];
    }

    public void close() throws IOException {
      IOUtils.close(consumers);
    }

    @Override
    public DocValuesConsumer addValuesField(FieldInfo field) throws IOException {
      final int formatId = field.getFormatId();
      assert formatId != FieldInfo.UNASSIGNED_FORMAT_ID;
      PerDocConsumer perDoc = consumers[formatId];
      if (perDoc == null) {
        perDoc = formats[formatId].docsConsumer(new PerDocWriteState(state, formatId));
        assert perDoc != null;
        consumers[formatId] = perDoc;
      }
      return perDoc.addValuesField(field);
    }
    
  }
}
