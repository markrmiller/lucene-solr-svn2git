package org.apache.lucene.spatial;

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

import java.util.ArrayList;
import java.util.Collection;

import org.apache.lucene.document.Document2;
import org.apache.lucene.document.FieldTypes;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.TermQueryPrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;
import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.shape.Shape;

public class QueryEqualsHashCodeTest extends LuceneTestCase {

  private final SpatialContext ctx = SpatialContext.GEO;

  @Test
  public void testEqualsHashCode() throws Exception {

    final SpatialPrefixTree gridQuad = new QuadPrefixTree(ctx,10);
    final SpatialPrefixTree gridGeohash = new GeohashPrefixTree(ctx,10);

    Collection<SpatialStrategy> strategies = new ArrayList<>();
    strategies.add(new RecursivePrefixTreeStrategy(gridGeohash, "recursive_geohash"));
    strategies.add(new TermQueryPrefixTreeStrategy(gridQuad, "termquery_quad"));
    strategies.add(new PointVectorStrategy(ctx, "pointvector"));
    strategies.add(new BBoxStrategy(ctx, "bbox"));
    strategies.add(new SerializedDVStrategy(ctx, "serialized"));
    for (SpatialStrategy strategy : strategies) {
      testEqualsHashcode(strategy);
    }
  }

  private void testEqualsHashcode(final SpatialStrategy strategy) throws Exception {
    final SpatialArgs args1 = makeArgs1();
    final SpatialArgs args2 = makeArgs2();
    IndexWriterConfig iwConfig = new IndexWriterConfig(null);
    Directory dir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, iwConfig);
    Document2 doc = writer.newDocument();
    strategy.addFields(doc, SpatialContext.GEO.makePoint(0, 0));
    writer.addDocument(doc);
    final FieldTypes fieldTypes = writer.getFieldTypes();
    testEqualsHashcode(args1, args2, new ObjGenerator() {
      @Override
      public Object gen(SpatialArgs args) {
        return strategy.makeQuery(fieldTypes, args);
      }
    });
    testEqualsHashcode(args1, args2, new ObjGenerator() {
      @Override
      public Object gen(SpatialArgs args) {
        return strategy.makeFilter(fieldTypes, args);
      }
    });
    testEqualsHashcode(args1, args2, new ObjGenerator() {
      @Override
      public Object gen(SpatialArgs args) {
        return strategy.makeDistanceValueSource(args.getShape().getCenter());
      }
    });
    writer.close();
    dir.close();
  }

  private void testEqualsHashcode(SpatialArgs args1, SpatialArgs args2, ObjGenerator generator) {
    Object first;
    try {
      first = generator.gen(args1);
    } catch (UnsupportedOperationException e) {
      return;
    }
    if (first == null)
      return;//unsupported op?
    Object second = generator.gen(args1);//should be the same
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotSame(args1, args2);
    second = generator.gen(args2);//now should be different
    assertNotSame(first, second);
    assertNotSame(first.hashCode(), second.hashCode());
  }

  private SpatialArgs makeArgs1() {
    final Shape shape1 = ctx.makeRectangle(0, 0, 10, 10);
    return new SpatialArgs(SpatialOperation.Intersects, shape1);
  }

  private SpatialArgs makeArgs2() {
    final Shape shape2 = ctx.makeRectangle(0, 0, 20, 20);
    return new SpatialArgs(SpatialOperation.Intersects, shape2);
  }

  interface ObjGenerator {
    Object gen(SpatialArgs args);
  }

}
