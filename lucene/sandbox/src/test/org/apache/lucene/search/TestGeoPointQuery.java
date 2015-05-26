package org.apache.lucene.search;

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
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.GeoPointField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.GeoUtils;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit testing for basic GeoPoint query logic
 *
 * @lucene.experimental
 */
public class TestGeoPointQuery extends LuceneTestCase {
  private static Directory directory = null;
  private static IndexReader reader = null;
  private static IndexSearcher searcher = null;

  private static final String FIELD_NAME = "geoField";

  @BeforeClass
  public static void beforeClass() throws Exception {
    directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
            newIndexWriterConfig(new MockAnalyzer(random()))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
                    .setMergePolicy(newLogMergePolicy()));

    // create some simple geo points
    final FieldType storedPoint = new FieldType(GeoPointField.TYPE_STORED);
    // this is a simple systematic test
    GeoPointField[] pts = new GeoPointField[] {
         new GeoPointField(FIELD_NAME, -96.4538113027811, 32.94823588839368, storedPoint),
         new GeoPointField(FIELD_NAME, -96.7759895324707, 32.7559529921407, storedPoint),
         new GeoPointField(FIELD_NAME, -96.77701950073242, 32.77866942010977, storedPoint),
         new GeoPointField(FIELD_NAME, -96.7706036567688, 32.7756745755423, storedPoint),
         new GeoPointField(FIELD_NAME, -139.73458170890808, 27.703618681345585, storedPoint),
         new GeoPointField(FIELD_NAME, -96.65084838867188, 33.06047141970814, storedPoint),
         new GeoPointField(FIELD_NAME, -96.7772, 32.778650, storedPoint)};

    for (GeoPointField p : pts) {
        Document doc = new Document();
        doc.add(p);
        writer.addDocument(doc);
    }
    reader = writer.getReader();
    searcher = newSearcher(reader);
    writer.close();
  }

  @AfterClass
  public static void afterClass() throws Exception {
    searcher = null;
    reader.close();
    reader = null;
    directory.close();
    directory = null;
  }

  private TopDocs bboxQuery(double minLon, double minLat, double maxLon, double maxLat, int limit) throws Exception {
    GeoPointInBBoxQuery q = new GeoPointInBBoxQuery(FIELD_NAME, minLon, minLat, maxLon, maxLat);
    return searcher.search(q, limit);
  }

  private TopDocs polygonQuery(double[] lon, double[] lat, int limit) throws Exception {
    GeoPointInPolygonQuery q = GeoPointInPolygonQuery.newPolygonQuery(FIELD_NAME, lon, lat);
    return searcher.search(q, limit);
  }

  @Test
  public void testBBoxQuery() throws Exception {
    TopDocs td = bboxQuery(-96.7772, 32.778650, -96.77690000, 32.778950, 5);
    assertEquals("GeoBoundingBoxQuery failed", 2, td.totalHits);
  }

  @Test
  public void testPolyQuery() throws Exception {
    TopDocs td = polygonQuery( new double[] { -96.7682647, -96.8280029, -96.6288757, -96.4929199,
        -96.6041564, -96.7449188, -96.76826477}, new double[] { 33.073130, 32.9942669, 32.938386, 33.0374494,
        33.1369762, 33.1162747, 33.073130}, 5);
    assertEquals("GeoPolygonQuery failed", td.totalHits, 1);
  }

  public void testRandomTiny() throws Exception {
    // Make sure single-leaf-node case is OK:
    doTestRandom(10);
  }

  public void testRandom() throws Exception {
    doTestRandom(10000);
  }

  @Nightly
  public void testRandomBig() throws Exception {
    doTestRandom(1000000);
  }

  private void doTestRandom(int count) throws Exception {

    int numPoints = atLeast(count);

    if (VERBOSE) {
      System.out.println("TEST: numPoints=" + numPoints);
    }

    double[] lats = new double[numPoints];
    double[] lons = new double[numPoints];

    boolean haveRealDoc = false;

    for (int docID=0;docID<numPoints;docID++) {
      int x = random().nextInt(20);
      if (x == 17) {
        // Some docs don't have a point:
        lats[docID] = Double.NaN;
        if (VERBOSE) {
          //System.out.println("  doc=" + docID + " is missing");
        }
        continue;
      }

      if (docID > 0 && x < 3 && haveRealDoc) {
        int oldDocID;
        while (true) {
          oldDocID = random().nextInt(docID);
          if (Double.isNaN(lats[oldDocID]) == false) {
            break;
          }
        }

        if (x == 0) {
          // Identical lat to old point
          lats[docID] = lats[oldDocID];
          lons[docID] = randomLon();
          if (VERBOSE) {
            //System.out.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID] + " (same lat as doc=" + oldDocID + ")");
          }
        } else if (x == 1) {
          // Identical lon to old point
          lats[docID] = randomLat();
          lons[docID] = lons[oldDocID];
          if (VERBOSE) {
            //System.out.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID] + " (same lon as doc=" + oldDocID + ")");
          }
        } else {
          assert x == 2;
          // Fully identical point:
          lats[docID] = lats[oldDocID];
          lons[docID] = lons[oldDocID];
          if (VERBOSE) {
            //System.out.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID] + " (same lat/lon as doc=" + oldDocID + ")");
          }
        }
      } else {
        lats[docID] = randomLat();
        lons[docID] = randomLon();
        haveRealDoc = true;
        if (VERBOSE) {
          //System.out.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID]);
        }
      }
    }

    verify(lats, lons);
  }

  private static void verify(double[] lats, double[] lons) throws IOException {
    IndexWriterConfig iwc = newIndexWriterConfig();
    Directory dir;
    if (lats.length > 100000) {
      dir = newFSDirectory(createTempDir("TestGeoPointQuery"));
    } else {
      dir = newDirectory();
    }
    Set<Integer> deleted = new HashSet<>();
    // RandomIndexWriter is too slow here:
    IndexWriter w = new IndexWriter(dir, iwc);
    for(int id=0;id<lats.length;id++) {
      Document doc = new Document();
      doc.add(newStringField("id", ""+id, Field.Store.NO));
      doc.add(new NumericDocValuesField("id", id));
      if (Double.isNaN(lats[id]) == false) {
        if (VERBOSE) {
          System.out.println("  id=" + id + " lat=" + lats[id] + " lon=" + lons[id]);
        }
        doc.add(new GeoPointField("point", lons[id], lats[id], Field.Store.NO));
      } else if (VERBOSE) {
        System.out.println("  id=" + id + " skipped");
      }
      w.addDocument(doc);
      if (id > 0 && random().nextInt(100) == 42) {
        int idToDelete = random().nextInt(id);
        w.deleteDocuments(new Term("id", ""+idToDelete));
        deleted.add(idToDelete);
        if (VERBOSE) {
          System.out.println("  delete id=" + idToDelete);
        }
      }
    }
    if (random().nextBoolean()) {
      w.forceMerge(1);
    }
    IndexReader r = DirectoryReader.open(w, true);
    w.close();

    // We can't wrap with "exotic" readers because the BKD query must see the BKDDVFormat:
    IndexSearcher s = newSearcher(r, false);

    NumericDocValues docIDToID = MultiDocValues.getNumericValues(r, "id");

    int iters = atLeast(100);
    for (int iter=0;iter<iters;iter++) {
      double lat0 = randomLat();
      double lat1 = randomLat();
      double lon0 = randomLon();
      double lon1 = randomLon();

      if (lat1 < lat0) {
        double x = lat0;
        lat0 = lat1;
        lat1 = x;
      }

      if (lon1 < lon0) {
        double x = lon0;
        lon0 = lon1;
        lon1 = x;
      }

      if (VERBOSE) {
        System.out.println("\nTEST: iter=" + iter + " lat=" + lat0 + " TO " + lat1 + " lon=" + lon0 + " TO " + lon1);
      }

      // nocommit test "in polygon" query too!
      Query query = new GeoPointInBBoxQuery("point", lon0, lat0, lon1, lat1);

      final FixedBitSet hits = new FixedBitSet(r.maxDoc());
      s.search(query, new SimpleCollector() {

          private int docBase;

          @Override
          public boolean needsScores() {
            return false;
          }

          @Override
          protected void doSetNextReader(LeafReaderContext context) throws IOException {
            docBase = context.docBase;
          }

          @Override
          public void collect(int doc) {
            hits.set(docBase+doc);
          }
        });

      for(int docID=0;docID<r.maxDoc();docID++) {
        int id = (int) docIDToID.get(docID);
        boolean expected = deleted.contains(id) == false && rectContainsPointEnc(lat0, lat1, lon0, lon1, lats[id], lons[id]);
        if (hits.get(docID) != expected) {
          fail("id=" + id + " docID=" + docID + " lat=" + lats[id] + " lon=" + lons[id] + " expected " + expected + " but got: " + hits.get(docID) + " deleted?=" + deleted.contains(id));
        }
      }
    }
    IOUtils.close(r, dir);
  }

  private static boolean rectContainsPointEnc(double rectLatMin, double rectLatMax,
                                              double rectLonMin, double rectLonMax,
                                              double pointLat, double pointLon) {
    if (Double.isNaN(pointLat)) {
      return false;
    }
    /*
    int rectLatMinEnc = BKDTreeWriter.encodeLat(rectLatMin);
    int rectLatMaxEnc = BKDTreeWriter.encodeLat(rectLatMax);
    int rectLonMinEnc = BKDTreeWriter.encodeLon(rectLonMin);
    int rectLonMaxEnc = BKDTreeWriter.encodeLon(rectLonMax);
    int pointLatEnc = BKDTreeWriter.encodeLat(pointLat);
    int pointLonEnc = BKDTreeWriter.encodeLon(pointLon);

    return pointLatEnc >= rectLatMinEnc &&
      pointLatEnc < rectLatMaxEnc &&
      pointLonEnc >= rectLonMinEnc &&
      pointLonEnc < rectLonMaxEnc;
    */
    return GeoUtils.bboxContains(pointLon, pointLat, rectLonMin, rectLatMin, rectLonMax, rectLatMax);
  }

  private static double randomLat() {
    return -90 + 180.0 * random().nextDouble();
  }

  private static double randomLon() {
    return -180 + 360.0 * random().nextDouble();
  }

}
