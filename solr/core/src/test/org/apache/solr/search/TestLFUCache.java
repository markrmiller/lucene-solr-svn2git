package org.apache.solr.search;

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

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.util.ConcurrentLFUCache;
import org.apache.solr.util.RefCounted;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * Test for LFUCache
 *
 * @see org.apache.solr.search.LFUCache
 * @since solr 3.6
 */
public class TestLFUCache extends SolrTestCaseJ4 {

  private class LFURegenerator implements CacheRegenerator {
    public boolean regenerateItem(SolrIndexSearcher newSearcher, SolrCache newCache,
                                  SolrCache oldCache, Object oldKey, Object oldVal) throws IOException {
      newCache.put(oldKey, oldVal);
      return true;
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig-caching.xml", "schema.xml");
  }

  @Test
  public void testTimeDecayParams() throws IOException {
    RefCounted<SolrIndexSearcher> holder = h.getCore().getSearcher();
    try {
      SolrIndexSearcher searcher = holder.get();
      LFUCache cacheDecayTrue = (LFUCache) searcher.getCache("lfuCacheDecayTrue");
      assertNotNull(cacheDecayTrue);
      NamedList stats = cacheDecayTrue.getStatistics();
      assertTrue((Boolean) stats.get("timeDecay"));
      addCache(cacheDecayTrue, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      for (int idx = 0; idx < 64; ++idx) {
        assertCache(cacheDecayTrue, 1, 2, 3, 4, 5);
      }
      addCache(cacheDecayTrue, 11, 12, 13, 14, 15);
      assertCache(cacheDecayTrue, 1, 2, 3, 4, 5, 12, 13, 14, 15);

      LFUCache cacheDecayDefault = (LFUCache) searcher.getCache("lfuCacheDecayDefault");
      assertNotNull(cacheDecayDefault);
      stats = cacheDecayDefault.getStatistics();
      assertTrue((Boolean) stats.get("timeDecay"));
      addCache(cacheDecayDefault, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      assertCache(cacheDecayDefault, 1, 2, 3, 4, 5);
      for (int idx = 0; idx < 64; ++idx) {
        assertCache(cacheDecayDefault, 1, 2, 3, 4, 5);
      }
      addCache(cacheDecayDefault, 11, 12, 13, 14, 15);
      assertCache(cacheDecayDefault, 1, 2, 3, 4, 5, 12, 13, 14, 15);
      addCache(cacheDecayDefault, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
      assertCache(cacheDecayDefault, 1, 2, 3, 4, 5, 17, 18, 19, 20, 21);

      LFUCache cacheDecayFalse = (LFUCache) searcher.getCache("lfuCacheDecayFalse");
      assertNotNull(cacheDecayFalse);
      stats = cacheDecayFalse.getStatistics();
      assertFalse((Boolean) stats.get("timeDecay"));
      addCache(cacheDecayFalse, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      assertCache(cacheDecayFalse, 1, 2, 3, 4, 5);
      for (int idx = 0; idx < 16; ++idx) {
        assertCache(cacheDecayFalse, 1, 2, 3, 4, 5);
      }
      addCache(cacheDecayFalse, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

      assertCache(cacheDecayFalse, 1, 2, 3, 4, 5);
      assertNotCache(cacheDecayFalse, 6, 7, 8, 9, 10);
      for (int idx = 22; idx < 256; ++idx) {
        addCache(cacheDecayFalse, idx);
      }
      assertCache(cacheDecayFalse, 1, 2, 3, 4, 5);

    } finally {
      holder.decref();
    }
  }

  private void addCache(LFUCache cache, int... inserts) {
    for (int idx : inserts) {
      cache.put(idx, Integer.toString(idx));
    }
  }

  private void assertCache(LFUCache cache, int... gets) {
    for (int idx : gets) {
      if (cache.get(idx) == null) {
        log.error(String.format("Expected entry %d not in cache", idx));
        assertTrue(false);
      }
    }
  }
  private void assertNotCache(LFUCache cache, int... gets) {
    for (int idx : gets) {
      if (cache.get(idx) != null) {
        log.error(String.format("Unexpected entry %d in cache", idx));
        assertTrue(false);
      }
    }
  }


  @Test
  public void testSimple() throws IOException {
    LFUCache lfuCache = new LFUCache();
    LFUCache newLFUCache = new LFUCache();
    LFUCache noWarmLFUCache = new LFUCache();
    try {
      Map params = new HashMap();
      params.put("size", "100");
      params.put("initialSize", "10");
      params.put("autowarmCount", "25");
      LFURegenerator regenerator = new LFURegenerator();
      Object initObj = lfuCache.init(params, null, regenerator);
      lfuCache.setState(SolrCache.State.LIVE);
      for (int i = 0; i < 101; i++) {
        lfuCache.put(i + 1, "" + (i + 1));
      }
      assertEquals("15", lfuCache.get(15));
      assertEquals("75", lfuCache.get(75));
      assertEquals(null, lfuCache.get(110));
      NamedList nl = lfuCache.getStatistics();
      assertEquals(3L, nl.get("lookups"));
      assertEquals(2L, nl.get("hits"));
      assertEquals(101L, nl.get("inserts"));

      assertEquals(null, lfuCache.get(1));  // first item put in should be the first out

      // Test autowarming
      newLFUCache.init(params, initObj, regenerator);
      newLFUCache.warm(null, lfuCache);
      newLFUCache.setState(SolrCache.State.LIVE);

      newLFUCache.put(103, "103");
      assertEquals("15", newLFUCache.get(15));
      assertEquals("75", newLFUCache.get(75));
      assertEquals(null, newLFUCache.get(50));
      nl = newLFUCache.getStatistics();
      assertEquals(3L, nl.get("lookups"));
      assertEquals(2L, nl.get("hits"));
      assertEquals(1L, nl.get("inserts"));
      assertEquals(0L, nl.get("evictions"));

      assertEquals(7L, nl.get("cumulative_lookups"));
      assertEquals(4L, nl.get("cumulative_hits"));
      assertEquals(102L, nl.get("cumulative_inserts"));
      newLFUCache.close();

      // Test no autowarming

      params.put("autowarmCount", "0");
      noWarmLFUCache.init(params, initObj, regenerator);
      noWarmLFUCache.warm(null, lfuCache);
      noWarmLFUCache.setState(SolrCache.State.LIVE);

      noWarmLFUCache.put(103, "103");
      assertNull(noWarmLFUCache.get(15));
      assertNull(noWarmLFUCache.get(75));
      assertEquals("103", noWarmLFUCache.get(103));
    } finally {
      if (newLFUCache != null) newLFUCache.close();
      if (noWarmLFUCache != null) noWarmLFUCache.close();
      if (lfuCache != null) lfuCache.close();
    }
  }

  @Test
  public void testItemOrdering() {
    ConcurrentLFUCache<Integer, String> cache = new ConcurrentLFUCache<Integer, String>(100, 90);
    try {
      for (int i = 0; i < 50; i++) {
        cache.put(i + 1, "" + (i + 1));
      }
      for (int i = 0; i < 44; i++) {
        cache.get(i + 1);
        cache.get(i + 1);
      }
      cache.get(1);
      cache.get(1);
      cache.get(1);
      cache.get(3);
      cache.get(3);
      cache.get(3);
      cache.get(5);
      cache.get(5);
      cache.get(5);
      cache.get(7);
      cache.get(7);
      cache.get(7);
      cache.get(9);
      cache.get(9);
      cache.get(9);
      cache.get(48);
      cache.get(48);
      cache.get(48);
      cache.get(50);
      cache.get(50);
      cache.get(50);
      cache.get(50);
      cache.get(50);

      Map<Integer, String> m;

      m = cache.getMostUsedItems(5);
      //System.out.println(m);
      // 50 9 7 5 3 1
      assertNotNull(m.get(50));
      assertNotNull(m.get(9));
      assertNotNull(m.get(7));
      assertNotNull(m.get(5));
      assertNotNull(m.get(3));

      m = cache.getLeastUsedItems(5);
      //System.out.println(m);
      // 49 47 46 45 2
      assertNotNull(m.get(49));
      assertNotNull(m.get(47));
      assertNotNull(m.get(46));
      assertNotNull(m.get(45));
      assertNotNull(m.get(2));

      m = cache.getLeastUsedItems(0);
      assertTrue(m.isEmpty());

      //test this too
      m = cache.getMostUsedItems(0);
      assertTrue(m.isEmpty());
    } finally {
      cache.destroy();
    }
  }

  @Test
  public void testTimeDecay() {
    ConcurrentLFUCache<Integer, String> cacheDecay = new ConcurrentLFUCache<Integer, String>(10, 9);
    try {
      for (int i = 1; i < 21; i++) {
        cacheDecay.put(i, Integer.toString(i));
      }
      Map<Integer, String> itemsDecay;

      //11-20 now in cache.
      itemsDecay = cacheDecay.getMostUsedItems(10);
      for (int i = 11; i < 21; ++i) {
        assertNotNull(itemsDecay.get(i));
      }

      // Now increase the freq count for 5 items
      for (int i = 0; i < 5; ++i) {
        for (int jdx = 0; jdx < 63; ++jdx) {
          cacheDecay.get(i + 13);
        }
      }
      // OK, 13 - 17 should have larger counts and should stick past next few collections. One collection should
      // be triggered for each two insertions
      cacheDecay.put(22, "22");
      cacheDecay.put(23, "23"); // Surplus count at 32
      cacheDecay.put(24, "24");
      cacheDecay.put(25, "25"); // Surplus count at 16
      itemsDecay = cacheDecay.getMostUsedItems(10);
      // 13 - 17 should be in cache, but 11 and 18 (among others) should not Testing that elements before and
      // after the ones with increased counts are removed, and all the increased count ones are still in the cache
      assertNull(itemsDecay.get(11));
      assertNull(itemsDecay.get(18));
      assertNotNull(itemsDecay.get(13));
      assertNotNull(itemsDecay.get(14));
      assertNotNull(itemsDecay.get(15));
      assertNotNull(itemsDecay.get(16));
      assertNotNull(itemsDecay.get(17));


      // Testing that all the elements in front of the ones with increased counts are gone
      for (int idx = 26; idx < 32; ++idx) {
        cacheDecay.put(idx, Integer.toString(idx));
      }
      //Surplus count should be at 0
      itemsDecay = cacheDecay.getMostUsedItems(10);
      assertNull(itemsDecay.get(20));
      assertNull(itemsDecay.get(24));
      assertNotNull(itemsDecay.get(13));
      assertNotNull(itemsDecay.get(14));
      assertNotNull(itemsDecay.get(15));
      assertNotNull(itemsDecay.get(16));
      assertNotNull(itemsDecay.get(17));

      for (int idx = 32; idx < 40; ++idx) {
        cacheDecay.put(idx, Integer.toString(idx));
      }

      // All the entries with increased counts should be gone.
      itemsDecay = cacheDecay.getMostUsedItems(10);
      System.out.println(itemsDecay);
      assertNull(itemsDecay.get(13));
      assertNull(itemsDecay.get(14));
      assertNull(itemsDecay.get(15));
      assertNull(itemsDecay.get(16));
      assertNull(itemsDecay.get(17));
      for (int idx = 30; idx < 40; ++idx) {
        assertNotNull(itemsDecay.get(idx));
      }
    } finally {
      cacheDecay.destroy();
    }
  }

  @Test
  public void testTimeNoDecay() {

    ConcurrentLFUCache<Integer, String> cacheNoDecay = new ConcurrentLFUCache<Integer, String>(10, 9,
        (int) Math.floor((9 + 10) / 2), (int) Math.ceil(0.75 * 10), false, false, null, false);
    try {
      for (int i = 1; i < 21; i++) {
        cacheNoDecay.put(i, Integer.toString(i));
      }
      Map<Integer, String> itemsNoDecay;

      //11-20 now in cache.
      itemsNoDecay = cacheNoDecay.getMostUsedItems(10);
      for (int i = 11; i < 21; ++i) {
        assertNotNull(itemsNoDecay.get(i));
      }

      // Now increase the freq count for 5 items
      for (int i = 0; i < 5; ++i) {
        for (int jdx = 0; jdx < 10; ++jdx) {
          cacheNoDecay.get(i + 13);
        }
      }
      // OK, 13 - 17 should have larger counts but that shouldn't matter since timeDecay=false
      cacheNoDecay.put(22, "22");
      cacheNoDecay.put(23, "23");
      cacheNoDecay.put(24, "24");
      cacheNoDecay.put(25, "25");
      itemsNoDecay = cacheNoDecay.getMostUsedItems(10);
      for (int idx = 15; idx < 25; ++idx) {
        assertNotNull(itemsNoDecay.get(15));
      }
    } finally {
      cacheNoDecay.destroy();
    }
  }

// From the original LRU cache tests, they're commented out there too because they take a while.
//  void doPerfTest(int iter, int cacheSize, int maxKey) {
//    long start = System.currentTimeMillis();
//
//    int lowerWaterMark = cacheSize;
//    int upperWaterMark = (int) (lowerWaterMark * 1.1);
//
//    Random r = random;
//    ConcurrentLFUCache cache = new ConcurrentLFUCache(upperWaterMark, lowerWaterMark,
//        (upperWaterMark + lowerWaterMark) / 2, upperWaterMark, false, false, null, true);
//    boolean getSize = false;
//    int minSize = 0, maxSize = 0;
//    for (int i = 0; i < iter; i++) {
//      cache.put(r.nextInt(maxKey), "TheValue");
//      int sz = cache.size();
//      if (!getSize && sz >= cacheSize) {
//        getSize = true;
//        minSize = sz;
//      } else {
//        if (sz < minSize) minSize = sz;
//        else if (sz > maxSize) maxSize = sz;
//      }
//    }
//    cache.destroy();
//
//    long end = System.currentTimeMillis();
//    System.out.println("time=" + (end - start) + ", minSize=" + minSize + ",maxSize=" + maxSize);
//  }
//
//
//  @Test
//  public void testPerf() {
//    doPerfTest(1000000, 100000, 200000); // big cache, warmup
//    doPerfTest(2000000, 100000, 200000); // big cache
//    doPerfTest(2000000, 100000, 120000);  // smaller key space increases distance between oldest, newest and makes the first passes less effective.
//    doPerfTest(6000000, 1000, 2000);    // small cache, smaller hit rate
//    doPerfTest(6000000, 1000, 1200);    // small cache, bigger hit rate
//  }
//
//
//  // returns number of puts
//  int useCache(SolrCache sc, int numGets, int maxKey, int seed) {
//    int ret = 0;
//    Random r = new Random(seed);
//
//    // use like a cache... gets and a put if not found
//    for (int i = 0; i < numGets; i++) {
//      Integer k = r.nextInt(maxKey);
//      Integer v = (Integer) sc.get(k);
//      if (v == null) {
//        sc.put(k, k);
//        ret++;
//      }
//    }
//
//    return ret;
//  }
//
//  void fillCache(SolrCache sc, int cacheSize, int maxKey) {
//    for (int i = 0; i < cacheSize; i++) {
//      Integer kv = random.nextInt(maxKey);
//      sc.put(kv, kv);
//    }
//  }
//
//
//  void cachePerfTest(final SolrCache sc, final int nThreads, final int numGets, int cacheSize, final int maxKey) {
//    Map l = new HashMap();
//    l.put("size", "" + cacheSize);
//    l.put("initialSize", "" + cacheSize);
//
//    Object o = sc.init(l, null, null);
//    sc.setState(SolrCache.State.LIVE);
//
//    fillCache(sc, cacheSize, maxKey);
//
//    long start = System.currentTimeMillis();
//
//    Thread[] threads = new Thread[nThreads];
//    final AtomicInteger puts = new AtomicInteger(0);
//    for (int i = 0; i < threads.length; i++) {
//      final int seed = random.nextInt();
//      threads[i] = new Thread() {
//        @Override
//        public void run() {
//          int ret = useCache(sc, numGets / nThreads, maxKey, seed);
//          puts.addAndGet(ret);
//        }
//      };
//    }
//
//    for (Thread thread : threads) {
//      try {
//        thread.start();
//      } catch (Exception e) {
//        e.printStackTrace();
//      }
//    }
//
//    for (Thread thread : threads) {
//      try {
//        thread.join();
//      } catch (Exception e) {
//        e.printStackTrace();
//      }
//    }
//
//    long end = System.currentTimeMillis();
//    System.out.println("time=" + (end - start) + " impl=" + sc.getClass().getSimpleName()
//        + " nThreads= " + nThreads + " size=" + cacheSize + " maxKey=" + maxKey + " gets=" + numGets
//        + " hitRatio=" + (1 - (((double) puts.get()) / numGets)));
//  }
//
//  void perfTestBoth(int nThreads, int numGets, int cacheSize, int maxKey) {
//    cachePerfTest(new LFUCache(), nThreads, numGets, cacheSize, maxKey);
//  }
//
//
//  public void testCachePerf() {
//    // warmup
//    perfTestBoth(2, 100000, 100000, 120000);
//    perfTestBoth(1, 2000000, 100000, 100000); // big cache, 100% hit ratio
//    perfTestBoth(2, 2000000, 100000, 100000); // big cache, 100% hit ratio
//    perfTestBoth(1, 2000000, 100000, 120000); // big cache, bigger hit ratio
//    perfTestBoth(2, 2000000, 100000, 120000); // big cache, bigger hit ratio
//    perfTestBoth(1, 2000000, 100000, 200000); // big cache, ~50% hit ratio
//    perfTestBoth(2, 2000000, 100000, 200000); // big cache, ~50% hit ratio
//    perfTestBoth(1, 2000000, 100000, 1000000); // big cache, ~10% hit ratio
//    perfTestBoth(2, 2000000, 100000, 1000000); // big cache, ~10% hit ratio
//
//    perfTestBoth(1, 2000000, 1000, 1000); // small cache, ~100% hit ratio
//    perfTestBoth(2, 2000000, 1000, 1000); // small cache, ~100% hit ratio
//    perfTestBoth(1, 2000000, 1000, 1200); // small cache, bigger hit ratio
//    perfTestBoth(2, 2000000, 1000, 1200); // small cache, bigger hit ratio
//    perfTestBoth(1, 2000000, 1000, 2000); // small cache, ~50% hit ratio
//    perfTestBoth(2, 2000000, 1000, 2000); // small cache, ~50% hit ratio
//    perfTestBoth(1, 2000000, 1000, 10000); // small cache, ~10% hit ratio
//    perfTestBoth(2, 2000000, 1000, 10000); // small cache, ~10% hit ratio
//  }

}
