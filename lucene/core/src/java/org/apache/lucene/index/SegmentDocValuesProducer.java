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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.Version;

/** Encapsulates multiple producers when there are docvalues updates as one producer */
// TODO: try to clean up close? no-op?
// TODO: add shared base class (also used by per-field-pf?) to allow "punching thru" to low level producer?
class SegmentDocValuesProducer extends DocValuesProducer {
  
  private static final long LONG_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(Long.class);
  private static final long BASE_RAM_BYTES_USED =
      RamUsageEstimator.shallowSizeOfInstance(SegmentDocValuesProducer.class);

  final Map<String,DocValuesProducer> dvProducersByField = new HashMap<>();
  final Set<DocValuesProducer> dvProducers = Collections.newSetFromMap(new IdentityHashMap<DocValuesProducer,Boolean>());
  final List<Long> dvGens = new ArrayList<>();
  
  SegmentDocValuesProducer(SegmentCommitInfo si, Directory dir, FieldInfos fieldInfos, SegmentDocValues segDocValues, DocValuesFormat dvFormat) throws IOException {
    boolean success = false;
    try {
      Version ver = si.info.getVersion();
      if (ver != null && ver.onOrAfter(Version.LUCENE_4_9_0)) {
        DocValuesProducer baseProducer = null;
        for (FieldInfo fi : fieldInfos) {
          if (!fi.hasDocValues()) {
            continue;
          }
          long docValuesGen = fi.getDocValuesGen();
          if (docValuesGen == -1) {
            if (baseProducer == null) {
              // the base producer gets all the fields, so the Codec can validate properly
              baseProducer = segDocValues.getDocValuesProducer(docValuesGen, si, IOContext.READ, dir, dvFormat, fieldInfos);
              dvGens.add(docValuesGen);
              dvProducers.add(baseProducer);
            }
            dvProducersByField.put(fi.name, baseProducer);
          } else {
            assert !dvGens.contains(docValuesGen);
            final DocValuesProducer dvp = segDocValues.getDocValuesProducer(docValuesGen, si, IOContext.READ, dir, dvFormat, new FieldInfos(new FieldInfo[] { fi }));
            dvGens.add(docValuesGen);
            dvProducers.add(dvp);
            dvProducersByField.put(fi.name, dvp);
          }
        }
      } else {
        // For pre-4.9 indexes, especially with doc-values updates, multiple
        // FieldInfos could belong to the same dvGen. Therefore need to make sure
        // we initialize each DocValuesProducer once per gen.
        Map<Long,List<FieldInfo>> genInfos = new HashMap<>();
        for (FieldInfo fi : fieldInfos) {
          if (!fi.hasDocValues()) {
            continue;
          }
          List<FieldInfo> genFieldInfos = genInfos.get(fi.getDocValuesGen());
          if (genFieldInfos == null) {
            genFieldInfos = new ArrayList<>();
            genInfos.put(fi.getDocValuesGen(), genFieldInfos);
          }
          genFieldInfos.add(fi);
        }
      
        for (Map.Entry<Long,List<FieldInfo>> e : genInfos.entrySet()) {
          long docValuesGen = e.getKey();
          List<FieldInfo> infos = e.getValue();
          final DocValuesProducer dvp;
          if (docValuesGen == -1) {
            // we need to send all FieldInfos to gen=-1, but later we need to
            // record the DVP only for the "true" gen=-1 fields (not updated)
            dvp = segDocValues.getDocValuesProducer(docValuesGen, si, IOContext.READ, dir, dvFormat, fieldInfos);
          } else {
            dvp = segDocValues.getDocValuesProducer(docValuesGen, si, IOContext.READ, dir, dvFormat, new FieldInfos(infos.toArray(new FieldInfo[infos.size()])));
          }
          dvGens.add(docValuesGen);
          dvProducers.add(dvp);
          for (FieldInfo fi : infos) {
            dvProducersByField.put(fi.name, dvp);
          }
        }
      }
      success = true;
    } finally {
      if (success == false) {
        try {
          segDocValues.decRef(dvGens);
        } catch (Throwable t) {
          // Ignore so we keep throwing first exception
        }
      }
    }
  }

  @Override
  public NumericDocValues getNumeric(FieldInfo field) throws IOException {
    DocValuesProducer dvProducer = dvProducersByField.get(field.name);
    assert dvProducer != null;
    return dvProducer.getNumeric(field);
  }

  @Override
  public BinaryDocValues getBinary(FieldInfo field) throws IOException {
    DocValuesProducer dvProducer = dvProducersByField.get(field.name);
    assert dvProducer != null;
    return dvProducer.getBinary(field);
  }

  @Override
  public SortedDocValues getSorted(FieldInfo field) throws IOException {
    DocValuesProducer dvProducer = dvProducersByField.get(field.name);
    assert dvProducer != null;
    return dvProducer.getSorted(field);
  }

  @Override
  public SortedNumericDocValues getSortedNumeric(FieldInfo field) throws IOException {
    DocValuesProducer dvProducer = dvProducersByField.get(field.name);
    assert dvProducer != null;
    return dvProducer.getSortedNumeric(field);
  }

  @Override
  public SortedSetDocValues getSortedSet(FieldInfo field) throws IOException {
    DocValuesProducer dvProducer = dvProducersByField.get(field.name);
    assert dvProducer != null;
    return dvProducer.getSortedSet(field);
  }

  @Override
  public Bits getDocsWithField(FieldInfo field) throws IOException {
    DocValuesProducer dvProducer = dvProducersByField.get(field.name);
    assert dvProducer != null;
    return dvProducer.getDocsWithField(field);
  }

  @Override
  public void checkIntegrity() throws IOException {
    for (DocValuesProducer producer : dvProducers) {
      producer.checkIntegrity();
    }
  }
  
  @Override
  public void close() throws IOException {
    throw new UnsupportedOperationException(); // there is separate ref tracking
  }

  @Override
  public long ramBytesUsed() {
    long ramBytesUsed = BASE_RAM_BYTES_USED;
    ramBytesUsed += dvGens.size() * LONG_RAM_BYTES_USED;
    ramBytesUsed += dvProducers.size() * RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    ramBytesUsed += dvProducersByField.size() * 2 * RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    for (DocValuesProducer producer : dvProducers) {
      ramBytesUsed += producer.ramBytesUsed();
    }
    return ramBytesUsed;
  }

  @Override
  public Iterable<? extends Accountable> getChildResources() {
    List<Accountable> resources = new ArrayList<>();
    for (Accountable producer : dvProducers) {
      resources.add(Accountables.namedAccountable("delegate", producer));
    }
    return Collections.unmodifiableList(resources);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(producers=" + dvProducers.size() + ")";
  }
}
