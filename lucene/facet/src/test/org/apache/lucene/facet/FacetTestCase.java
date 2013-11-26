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
import java.util.Random;

import org.apache.lucene.facet.simple.CachedOrdinalsReader;
import org.apache.lucene.facet.simple.DocValuesOrdinalsReader;
import org.apache.lucene.facet.simple.Facets;
import org.apache.lucene.facet.simple.FacetsConfig;
import org.apache.lucene.facet.simple.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.simple.OrdinalsReader;
import org.apache.lucene.facet.simple.SimpleFacetsCollector;
import org.apache.lucene.facet.simple.TaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.util.LuceneTestCase;

public abstract class FacetTestCase extends LuceneTestCase {
  
  public Facets getTaxonomyFacetCounts(TaxonomyReader taxoReader, FacetsConfig config, SimpleFacetsCollector c) throws IOException {
    return getTaxonomyFacetCounts(taxoReader, config, c, FacetsConfig.DEFAULT_INDEX_FIELD_NAME);
  }

  public Facets getTaxonomyFacetCounts(TaxonomyReader taxoReader, FacetsConfig config, SimpleFacetsCollector c, String indexFieldName) throws IOException {
    Facets facets;
    if (random().nextBoolean()) {
      facets = new FastTaxonomyFacetCounts(indexFieldName, taxoReader, config, c);
    } else {
      OrdinalsReader ordsReader = new DocValuesOrdinalsReader(indexFieldName);
      if (random().nextBoolean()) {
        ordsReader = new CachedOrdinalsReader(ordsReader);
      }
      facets = new TaxonomyFacetCounts(ordsReader, taxoReader, config, c);
    }

    return facets;
  }
}
