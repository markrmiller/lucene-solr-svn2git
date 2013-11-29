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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.index.IndexDocument;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexableFieldType;
import org.apache.lucene.index.StorableField;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;

/** Records per-dimension configuration.  By default a
 *  dimension is flat, single valued and does
 *  not require count for the dimension; use
 *  the setters in this class to change these settings for
 *  each dim.
 *
 *  <p><b>NOTE</b>: this configuration is not saved into the
 *  index, but it's vital, and up to the application to
 *  ensure, that at search time the provided {@code
 *  FacetsConfig} matches what was used during indexing.
 *
 *  @lucene.experimental */
public class FacetsConfig {

  public static final String DEFAULT_INDEX_FIELD_NAME = "$facets";

  private final Map<String,DimConfig> fieldTypes = new ConcurrentHashMap<String,DimConfig>();

  // Used only for best-effort detection of app mixing
  // int/float/bytes in a single indexed field:
  private final Map<String,String> assocDimTypes = new ConcurrentHashMap<String,String>();

  /** Holds the configuration for one dimension
   *
   * @lucene.experimental */
  public static final class DimConfig {
    /** True if this dimension is hierarchical. */
    public boolean hierarchical;

    /** True if this dimension is multi-valued. */
    public boolean multiValued;

    /** True if the count/aggregate for the entire dimension
     *  is required, which is unusual (default is false). */
    public boolean requireDimCount;

    /** Actual field where this dimension's facet labels
     *  should be indexed */
    public String indexFieldName = DEFAULT_INDEX_FIELD_NAME;
  }

  /** Default per-dimension configuration. */
  public final static DimConfig DEFAULT_DIM_CONFIG = new DimConfig();

  /** Default constructor. */
  public FacetsConfig() {
  }

  /** Get the current configuration for a dimension. */
  public synchronized DimConfig getDimConfig(String dimName) {
    DimConfig ft = fieldTypes.get(dimName);
    if (ft == null) {
      ft = DEFAULT_DIM_CONFIG;
    }
    return ft;
  }

  /** Pass {@code true} if this dimension is hierarchical
   *  (has depth > 1 paths). */
  public synchronized void setHierarchical(String dimName, boolean v) {
    DimConfig ft = fieldTypes.get(dimName);
    if (ft == null) {
      ft = new DimConfig();
      fieldTypes.put(dimName, ft);
    }
    ft.hierarchical = v;
  }

  /** Pass {@code true} if this dimension may have more than
   *  one value per document. */
  public synchronized void setMultiValued(String dimName, boolean v) {
    DimConfig ft = fieldTypes.get(dimName);
    if (ft == null) {
      ft = new DimConfig();
      fieldTypes.put(dimName, ft);
    }
    ft.multiValued = v;
  }

  /** Pass {@code true} if at search time you require
   *  accurate counts of the dimension, i.e. how many
   *  hits have this dimension. */
  public synchronized void setRequireDimCount(String dimName, boolean v) {
    DimConfig ft = fieldTypes.get(dimName);
    if (ft == null) {
      ft = new DimConfig();
      fieldTypes.put(dimName, ft);
    }
    ft.requireDimCount = v;
  }

  /** Specify which index field name should hold the
   *  ordinals for this dimension; this is only used by the
   *  taxonomy based facet methods. */
  public synchronized void setIndexFieldName(String dimName, String indexFieldName) {
    DimConfig ft = fieldTypes.get(dimName);
    if (ft == null) {
      ft = new DimConfig();
      fieldTypes.put(dimName, ft);
    }
    ft.indexFieldName = indexFieldName;
  }

  public Map<String,DimConfig> getDimConfigs() {
    return fieldTypes;
  }

  private static void checkSeen(Set<String> seenDims, String dim) {
    if (seenDims.contains(dim)) {
      throw new IllegalArgumentException("dimension \"" + dim + "\" is not multiValued, but it appears more than once in this document");
    }
    seenDims.add(dim);
  }

  /** Translates any added {@link FacetField}s into normal
   *  fields for indexing; only use this version if you
   *  did not add any taxonomy-based fields ({@link
   *  FacetField} or {@link AssociationFacetField}) */
  public IndexDocument build(IndexDocument doc) throws IOException {
    return build(null, doc);
  }

  /** Translates any added {@link FacetField}s into normal
   *  fields for indexing. */
  public IndexDocument build(TaxonomyWriter taxoWriter, IndexDocument doc) throws IOException {
    // Find all FacetFields, collated by the actual field:
    Map<String,List<FacetField>> byField = new HashMap<String,List<FacetField>>();

    // ... and also all SortedSetDocValuesFacetFields:
    Map<String,List<SortedSetDocValuesFacetField>> dvByField = new HashMap<String,List<SortedSetDocValuesFacetField>>();

    // ... and also all AssociationFacetFields
    Map<String,List<AssociationFacetField>> assocByField = new HashMap<String,List<AssociationFacetField>>();

    Set<String> seenDims = new HashSet<String>();

    for(IndexableField field : doc.indexableFields()) {
      if (field.fieldType() == FacetField.TYPE) {
        FacetField facetField = (FacetField) field;
        FacetsConfig.DimConfig dimConfig = getDimConfig(facetField.dim);
        if (dimConfig.multiValued == false) {
          checkSeen(seenDims, facetField.dim);
        }
        String indexFieldName = dimConfig.indexFieldName;
        List<FacetField> fields = byField.get(indexFieldName);
        if (fields == null) {
          fields = new ArrayList<FacetField>();
          byField.put(indexFieldName, fields);
        }
        fields.add(facetField);
      }

      if (field.fieldType() == SortedSetDocValuesFacetField.TYPE) {
        SortedSetDocValuesFacetField facetField = (SortedSetDocValuesFacetField) field;
        FacetsConfig.DimConfig dimConfig = getDimConfig(facetField.dim);
        if (dimConfig.multiValued == false) {
          checkSeen(seenDims, facetField.dim);
        }
        String indexFieldName = dimConfig.indexFieldName;
        List<SortedSetDocValuesFacetField> fields = dvByField.get(indexFieldName);
        if (fields == null) {
          fields = new ArrayList<SortedSetDocValuesFacetField>();
          dvByField.put(indexFieldName, fields);
        }
        fields.add(facetField);
      }

      if (field.fieldType() == AssociationFacetField.TYPE) {
        AssociationFacetField facetField = (AssociationFacetField) field;
        FacetsConfig.DimConfig dimConfig = getDimConfig(facetField.dim);
        if (dimConfig.multiValued == false) {
          checkSeen(seenDims, facetField.dim);
        }
        if (dimConfig.hierarchical) {
          throw new IllegalArgumentException("AssociationFacetField cannot be hierarchical (dim=\"" + facetField.dim + "\")");
        }
        if (dimConfig.requireDimCount) {
          throw new IllegalArgumentException("AssociationFacetField cannot requireDimCount (dim=\"" + facetField.dim + "\")");
        }

        String indexFieldName = dimConfig.indexFieldName;
        List<AssociationFacetField> fields = assocByField.get(indexFieldName);
        if (fields == null) {
          fields = new ArrayList<AssociationFacetField>();
          assocByField.put(indexFieldName, fields);
        }
        fields.add(facetField);

        // Best effort: detect mis-matched types in same
        // indexed field:
        String type;
        if (facetField instanceof IntAssociationFacetField) {
          type = "int";
        } else if (facetField instanceof FloatAssociationFacetField) {
          type = "float";
        } else {
          type = "bytes";
        }
        // NOTE: not thread safe, but this is just best effort:
        String curType = assocDimTypes.get(indexFieldName);
        if (curType == null) {
          assocDimTypes.put(indexFieldName, type);
        } else if (!curType.equals(type)) {
          throw new IllegalArgumentException("mixing incompatible types of AssocationFacetField (" + curType + " and " + type + ") in indexed field \"" + indexFieldName + "\"; use FacetsConfig to change the indexFieldName for each dimension");
        }
      }
    }

    List<Field> addedIndexedFields = new ArrayList<Field>();
    List<Field> addedStoredFields = new ArrayList<Field>();

    processFacetFields(taxoWriter, byField, addedIndexedFields, addedStoredFields);
    processSSDVFacetFields(dvByField, addedIndexedFields, addedStoredFields);
    processAssocFacetFields(taxoWriter, assocByField, addedIndexedFields, addedStoredFields);

    //System.out.println("add stored: " + addedStoredFields);

    final List<IndexableField> allIndexedFields = new ArrayList<IndexableField>();
    for(IndexableField field : doc.indexableFields()) {
      IndexableFieldType ft = field.fieldType();
      if (ft != FacetField.TYPE && ft != SortedSetDocValuesFacetField.TYPE && ft != AssociationFacetField.TYPE) {
        allIndexedFields.add(field);
      }
    }
    allIndexedFields.addAll(addedIndexedFields);

    final List<StorableField> allStoredFields = new ArrayList<StorableField>();
    for(StorableField field : doc.storableFields()) {
      allStoredFields.add(field);
    }
    allStoredFields.addAll(addedStoredFields);

    //System.out.println("all indexed: " + allIndexedFields);
    //System.out.println("all stored: " + allStoredFields);

    return new IndexDocument() {
        @Override
        public Iterable<IndexableField> indexableFields() {
          return allIndexedFields;
        }

        @Override
        public Iterable<StorableField> storableFields() {
          return allStoredFields;
        }
      };
  }

  private void processFacetFields(TaxonomyWriter taxoWriter, Map<String,List<FacetField>> byField, List<Field> addedIndexedFields, List<Field> addedStoredFields) throws IOException {

    for(Map.Entry<String,List<FacetField>> ent : byField.entrySet()) {

      String indexFieldName = ent.getKey();
      //System.out.println("  fields=" + ent.getValue());

      IntsRef ordinals = new IntsRef(32);
      for(FacetField facetField : ent.getValue()) {

        FacetsConfig.DimConfig ft = getDimConfig(facetField.dim);
        if (facetField.path.length > 1 && ft.hierarchical == false) {
          throw new IllegalArgumentException("dimension \"" + facetField.dim + "\" is not hierarchical yet has " + facetField.path.length + " components");
        }
      
        FacetLabel cp = new FacetLabel(facetField.dim, facetField.path);

        checkTaxoWriter(taxoWriter);
        int ordinal = taxoWriter.addCategory(cp);
        if (ordinals.length == ordinals.ints.length) {
          ordinals.grow(ordinals.length+1);
        }
        ordinals.ints[ordinals.length++] = ordinal;
        //System.out.println("  add cp=" + cp);

        if (ft.multiValued && (ft.hierarchical || ft.requireDimCount)) {
          // Add all parents too:
          int parent = taxoWriter.getParent(ordinal);
          while (parent > 0) {
            if (ordinals.ints.length == ordinals.length) {
              ordinals.grow(ordinals.length+1);
            }
            ordinals.ints[ordinals.length++] = parent;
            parent = taxoWriter.getParent(parent);
          }

          if (ft.requireDimCount == false) {
            // Remove last (dimension) ord:
            ordinals.length--;
          }
        }

        // Drill down:
        for(int i=1;i<=cp.length;i++) {
          addedIndexedFields.add(new StringField(indexFieldName, pathToString(cp.components, i), Field.Store.NO));
        }
      }

      // Facet counts:
      // DocValues are considered stored fields:
      addedStoredFields.add(new BinaryDocValuesField(indexFieldName, dedupAndEncode(ordinals)));
    }
  }

  private void processSSDVFacetFields(Map<String,List<SortedSetDocValuesFacetField>> byField, List<Field> addedIndexedFields, List<Field> addedStoredFields) throws IOException {
    //System.out.println("process SSDV: " + byField);
    for(Map.Entry<String,List<SortedSetDocValuesFacetField>> ent : byField.entrySet()) {

      String indexFieldName = ent.getKey();
      //System.out.println("  field=" + indexFieldName);

      for(SortedSetDocValuesFacetField facetField : ent.getValue()) {
        FacetLabel cp = new FacetLabel(facetField.dim, facetField.label);
        String fullPath = pathToString(cp.components, cp.length);
        //System.out.println("add " + fullPath);

        // For facet counts:
        addedStoredFields.add(new SortedSetDocValuesField(indexFieldName, new BytesRef(fullPath)));

        // For drill-down:
        addedIndexedFields.add(new StringField(indexFieldName, fullPath, Field.Store.NO));
        addedIndexedFields.add(new StringField(indexFieldName, facetField.dim, Field.Store.NO));
      }
    }
  }

  private void processAssocFacetFields(TaxonomyWriter taxoWriter, Map<String,List<AssociationFacetField>> byField,
                                       List<Field> addedIndexedFields, List<Field> addedStoredFields) throws IOException {
    for(Map.Entry<String,List<AssociationFacetField>> ent : byField.entrySet()) {
      byte[] bytes = new byte[16];
      int upto = 0;
      String indexFieldName = ent.getKey();
      for(AssociationFacetField field : ent.getValue()) {
        // NOTE: we don't add parents for associations
        // nocommit is that right?  maybe we are supposed to
        // add to taxo writer, and just not index the parent
        // ords?
        checkTaxoWriter(taxoWriter);
        int ordinal = taxoWriter.addCategory(new FacetLabel(field.dim, field.path));
        if (upto + 4 > bytes.length) {
          bytes = ArrayUtil.grow(bytes, upto+4);
        }
        // big-endian:
        bytes[upto++] = (byte) (ordinal >> 24);
        bytes[upto++] = (byte) (ordinal >> 16);
        bytes[upto++] = (byte) (ordinal >> 8);
        bytes[upto++] = (byte) ordinal;
        if (upto + field.assoc.length > bytes.length) {
          bytes = ArrayUtil.grow(bytes, upto+field.assoc.length);
        }
        System.arraycopy(field.assoc.bytes, field.assoc.offset, bytes, upto, field.assoc.length);
        upto += field.assoc.length;
      }
      addedStoredFields.add(new BinaryDocValuesField(indexFieldName, new BytesRef(bytes, 0, upto)));
    }
  }

  /** Encodes ordinals into a BytesRef; expert: subclass can
   *  override this to change encoding. */
  protected BytesRef dedupAndEncode(IntsRef ordinals) {
    Arrays.sort(ordinals.ints, ordinals.offset, ordinals.length);
    byte[] bytes = new byte[5*ordinals.length];
    int lastOrd = -1;
    int upto = 0;
    for(int i=0;i<ordinals.length;i++) {
      int ord = ordinals.ints[ordinals.offset+i];
      // ord could be == lastOrd, so we must dedup:
      if (ord > lastOrd) {
        int delta;
        if (lastOrd == -1) {
          delta = ord;
        } else {
          delta = ord - lastOrd;
        }
        if ((delta & ~0x7F) == 0) {
          bytes[upto] = (byte) delta;
          upto++;
        } else if ((delta & ~0x3FFF) == 0) {
          bytes[upto] = (byte) (0x80 | ((delta & 0x3F80) >> 7));
          bytes[upto + 1] = (byte) (delta & 0x7F);
          upto += 2;
        } else if ((delta & ~0x1FFFFF) == 0) {
          bytes[upto] = (byte) (0x80 | ((delta & 0x1FC000) >> 14));
          bytes[upto + 1] = (byte) (0x80 | ((delta & 0x3F80) >> 7));
          bytes[upto + 2] = (byte) (delta & 0x7F);
          upto += 3;
        } else if ((delta & ~0xFFFFFFF) == 0) {
          bytes[upto] = (byte) (0x80 | ((delta & 0xFE00000) >> 21));
          bytes[upto + 1] = (byte) (0x80 | ((delta & 0x1FC000) >> 14));
          bytes[upto + 2] = (byte) (0x80 | ((delta & 0x3F80) >> 7));
          bytes[upto + 3] = (byte) (delta & 0x7F);
          upto += 4;
        } else {
          bytes[upto] = (byte) (0x80 | ((delta & 0xF0000000) >> 28));
          bytes[upto + 1] = (byte) (0x80 | ((delta & 0xFE00000) >> 21));
          bytes[upto + 2] = (byte) (0x80 | ((delta & 0x1FC000) >> 14));
          bytes[upto + 3] = (byte) (0x80 | ((delta & 0x3F80) >> 7));
          bytes[upto + 4] = (byte) (delta & 0x7F);
          upto += 5;
        }
        lastOrd = ord;
      }
    }
    return new BytesRef(bytes, 0, upto);
  }

  private void checkTaxoWriter(TaxonomyWriter taxoWriter) {
    if (taxoWriter == null) {
      throw new IllegalStateException("a non-null TaxonomyWriter must be provided when indexing FacetField or AssociationFacetField");
    }
  }

  // Joins the path components together:
  private static final char DELIM_CHAR = '\u001F';

  // Escapes any occurrence of the path component inside the label:
  private static final char ESCAPE_CHAR = '\u001E';

  /** Turns a dim + path into an encoded string. */
  public static String pathToString(String dim, String[] path) {
    String[] fullPath = new String[1+path.length];
    fullPath[0] = dim;
    System.arraycopy(path, 0, fullPath, 1, path.length);
    return pathToString(fullPath, fullPath.length);
  }

  /** Turns a dim + path into an encoded string. */
  public static String pathToString(String[] path) {
    return pathToString(path, path.length);
  }

  /** Turns the first {@code} length elements of {@code
   * path} into an encoded string. */
  public static String pathToString(String[] path, int length) {
    if (length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for(int i=0;i<length;i++) {
      String s = path[i];
      if (s.length() == 0) {
        throw new IllegalArgumentException("each path component must have length > 0 (got: \"\")");
      }
      int numChars = s.length();
      for(int j=0;j<numChars;j++) {
        char ch = s.charAt(j);
        if (ch == DELIM_CHAR || ch == ESCAPE_CHAR) {
          sb.append(ESCAPE_CHAR);
        }
        sb.append(ch);
      }
      sb.append(DELIM_CHAR);
    }

    // Trim off last DELIM_CHAR:
    sb.setLength(sb.length()-1);
    return sb.toString();
  }

  /** Turns an encoded string (from a previous call to {@link
   *  #pathToString}) back into the original {@code
   *  String[]}. */
  public static String[] stringToPath(String s) {
    List<String> parts = new ArrayList<String>();
    int length = s.length();
    if (length == 0) {
      return new String[0];
    }
    char[] buffer = new char[length];

    int upto = 0;
    boolean lastEscape = false;
    for(int i=0;i<length;i++) {
      char ch = s.charAt(i);
      if (lastEscape) {
        buffer[upto++] = ch;
        lastEscape = false;
      } else if (ch == ESCAPE_CHAR) {
        lastEscape = true;
      } else if (ch == DELIM_CHAR) {
        parts.add(new String(buffer, 0, upto));
        upto = 0;
      } else {
        buffer[upto++] = ch;
      }
    }
    parts.add(new String(buffer, 0, upto));
    assert !lastEscape;
    return parts.toArray(new String[parts.size()]);
  }
}
