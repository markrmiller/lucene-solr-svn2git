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
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.codecs.PostingsFormat;
import org.apache.lucene.index.codecs.CodecProvider;
import org.apache.lucene.index.codecs.preflex.PreFlexPostingsFormat;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/**
 * SegmentCodecs maintains an ordered list of distinct codecs used within a
 * segment. Within a segment on codec is used to write multiple fields while
 * each field could be written by a different codec. To enable codecs per field
 * within a single segment we need to record the distinct codecs and map them to
 * each field present in the segment. SegmentCodecs is created together with
 * {@link SegmentWriteState} for each flush and is maintained in the
 * corresponding {@link SegmentInfo} until it is committed.
 * <p>
 * During indexing {@link FieldInfos} uses {@link SegmentCodecsBuilder} to incrementally
 * build the {@link SegmentCodecs} mapping. Once a segment is flushed
 * DocumentsWriter creates a {@link SegmentCodecs} instance from
 * {@link FieldInfos#buildSegmentCodecs(boolean)} The {@link FieldInfo#codecId}
 * assigned by {@link SegmentCodecsBuilder} refers to the codecs ordinal
 * maintained inside {@link SegmentCodecs}. This ord is later used to get the
 * right codec when the segment is opened in a reader.The {@link PostingsFormat} returned
 * from {@link SegmentCodecs#codec()} in turn uses {@link SegmentCodecs}
 * internal structure to select and initialize the right codec for a fields when
 * it is written.
 * <p>
 * Once a flush succeeded the {@link SegmentCodecs} is maintained inside the
 * {@link SegmentInfo} for the flushed segment it was created for.
 * {@link SegmentInfo} writes the name of each codec in {@link SegmentCodecs}
 * for each segment and maintains the order. Later if a segment is opened by a
 * reader this mapping is deserialized and used to create the codec per field.
 * 
 * 
 * @lucene.internal
 */
public final class SegmentCodecs implements Cloneable {
  /**
   * internal structure to map codecs to fields - don't modify this from outside
   * of this class!
   */
  public final PostingsFormat[] codecs;
  public final CodecProvider provider;
  private final PostingsFormat codec;
  
  public SegmentCodecs(CodecProvider provider, IndexInput input) throws IOException {
    this(provider, read(input, provider));
  }
  
  public SegmentCodecs(CodecProvider provider, PostingsFormat... codecs) {
    this.provider = provider;
    this.codecs = codecs;
    if (codecs.length == 1 && codecs[0] instanceof PreFlexPostingsFormat) {
      this.codec = codecs[0]; // hack for backwards break... don't wrap the codec in preflex
    } else {
      this.codec = new PerFieldPostingsFormat(this);
    }
  }

  public PostingsFormat codec() {
    return codec;
  }

  public void write(IndexOutput out) throws IOException {
    out.writeVInt(codecs.length);
    for (PostingsFormat codec : codecs) {
      out.writeString(codec.name);
    }
  }

  private static PostingsFormat[] read(IndexInput in, CodecProvider provider) throws IOException {
    final int size = in.readVInt();
    final ArrayList<PostingsFormat> list = new ArrayList<PostingsFormat>();
    for (int i = 0; i < size; i++) {
      final String codecName = in.readString();
      final PostingsFormat lookup = provider.lookup(codecName);
      list.add(i, lookup);
    }
    return list.toArray(PostingsFormat.EMPTY);
  }

  public void files(Directory dir, SegmentInfo info, Set<String> files)
      throws IOException {
    final PostingsFormat[] codecArray = codecs;
    for (int i = 0; i < codecArray.length; i++) {
      codecArray[i].files(dir, info, i, files);
    }      
      
  }

  @Override
  public String toString() {
    return "SegmentCodecs [codecs=" + Arrays.toString(codecs) + ", provider=" + provider + "]";
  }
  
  /**
   * Used in {@link FieldInfos} to incrementally build the codec ID mapping for
   * {@link FieldInfo} instances.
   * <p>
   * Note: this class is not thread-safe
   * </p>
   * @see FieldInfo#getCodecId()
   */
  public final static class SegmentCodecsBuilder {
    private final Map<PostingsFormat, Integer> codecRegistry = new IdentityHashMap<PostingsFormat, Integer>();
    private final ArrayList<PostingsFormat> codecs = new ArrayList<PostingsFormat>();
    private final CodecProvider provider;

    private SegmentCodecsBuilder(CodecProvider provider) {
      this.provider = provider;
    }
    
    public static SegmentCodecsBuilder create(CodecProvider provider) {
      return new SegmentCodecsBuilder(provider);
    }
    
    public SegmentCodecsBuilder tryAddAndSet(FieldInfo fi) {
      if (fi.getCodecId() == FieldInfo.UNASSIGNED_CODEC_ID) {
        final PostingsFormat fieldCodec = provider.lookup(provider
            .getFieldCodec(fi.name));
        Integer ord = codecRegistry.get(fieldCodec);
        if (ord == null) {
          ord = Integer.valueOf(codecs.size());
          codecRegistry.put(fieldCodec, ord);
          codecs.add(fieldCodec);
        }
        fi.setCodecId(ord.intValue());
      }
      return this;
    }
    
    public SegmentCodecs build() {
      return new SegmentCodecs(provider, codecs.toArray(PostingsFormat.EMPTY));
    }
    
    public SegmentCodecsBuilder clear() {
      codecRegistry.clear();
      codecs.clear();
      return this;
    }
  }
}