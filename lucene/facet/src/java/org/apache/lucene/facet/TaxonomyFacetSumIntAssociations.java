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
import java.util.List;

import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

/** Aggregates sum of int values previously indexed with
 *  {@link IntAssociationFacetField}, assuming the default
 *  encoding.
 *
 *  @lucene.experimental */
public class TaxonomyFacetSumIntAssociations extends IntTaxonomyFacets {

  /** Create {@code TaxonomyFacetSumIntAssociations} against
   *  the default index field. */
  public TaxonomyFacetSumIntAssociations(TaxonomyReader taxoReader, FacetsConfig config, FacetsCollector fc) throws IOException {
    this(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, taxoReader, config, fc);
  }

  /** Create {@code TaxonomyFacetSumIntAssociations} against
   *  the specified index field. */
  public TaxonomyFacetSumIntAssociations(String indexFieldName, TaxonomyReader taxoReader, FacetsConfig config, FacetsCollector fc) throws IOException {
    super(indexFieldName, taxoReader, config);
    sumValues(fc.getMatchingDocs());
  }

  private final void sumValues(List<MatchingDocs> matchingDocs) throws IOException {
    //System.out.println("count matchingDocs=" + matchingDocs + " facetsField=" + facetsFieldName);
    for(MatchingDocs hits : matchingDocs) {
      BinaryDocValues dv = hits.context.reader().getBinaryDocValues(indexFieldName);
      if (dv == null) { // this reader does not have DocValues for the requested category list
        continue;
      }
      FixedBitSet bits = hits.bits;
    
      final int length = hits.bits.length();
      int doc = 0;
      BytesRef scratch = new BytesRef();
      //System.out.println("count seg=" + hits.context.reader());
      while (doc < length && (doc = bits.nextSetBit(doc)) != -1) {
        //System.out.println("  doc=" + doc);
        // nocommit use OrdinalsReader?  but, add a
        // BytesRef getAssociation()?
        dv.get(doc, scratch);
        byte[] bytes = scratch.bytes;
        int end = scratch.offset + scratch.length;
        int offset = scratch.offset;
        while (offset < end) {
          int ord = ((bytes[offset]&0xFF) << 24) |
            ((bytes[offset+1]&0xFF) << 16) |
            ((bytes[offset+2]&0xFF) << 8) |
            (bytes[offset+3]&0xFF);
          offset += 4;
          int value = ((bytes[offset]&0xFF) << 24) |
            ((bytes[offset+1]&0xFF) << 16) |
            ((bytes[offset+2]&0xFF) << 8) |
            (bytes[offset+3]&0xFF);
          offset += 4;
          values[ord] += value;
        }
        ++doc;
      }
    }

    rollup();
  }
}
