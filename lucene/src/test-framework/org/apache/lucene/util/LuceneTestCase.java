package org.apache.lucene.util;

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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.LogMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.MockRandomMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SlowMultiReaderWrapper;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldCache.CacheEntry;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.util.FieldCacheSanityChecker.Insanity;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatchman;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * Base class for all Lucene unit tests, Junit3 or Junit4 variant.
 * <p>
 * </p>
 * <p>
 * If you
 * override either <code>setUp()</code> or
 * <code>tearDown()</code> in your unit test, make sure you
 * call <code>super.setUp()</code> and
 * <code>super.tearDown()</code>
 * </p>
 *
 * <code>@After</code> - replaces setup
 * <code>@Before</code> - replaces teardown
 * <code>@Test</code> - any public method with this annotation is a test case, regardless
 * of its name
 * <p>
 * <p>
 * See Junit4 <a href="http://junit.org/junit/javadoc/4.7/">documentation</a> for a complete list of features.
 * <p>
 * Import from org.junit rather than junit.framework.
 * <p>
 * You should be able to use this class anywhere you used LuceneTestCase
 * if you annotate your derived class correctly with the annotations above
 * @see #assertSaneFieldCaches(String)
 */

@RunWith(LuceneTestCase.LuceneTestCaseRunner.class)
public abstract class LuceneTestCase extends Assert {

  /**
   * true iff tests are run in verbose mode. Note: if it is false, tests are not
   * expected to print any messages.
   */
  public static final boolean VERBOSE = Boolean.getBoolean("tests.verbose");

  /** Use this constant when creating Analyzers and any other version-dependent stuff.
   * <p><b>NOTE:</b> Change this when development starts for new Lucene version:
   */
  public static final Version TEST_VERSION_CURRENT = Version.LUCENE_33;

  /**
   * If this is set, it is the only method that should run.
   */
  static final String TEST_METHOD;
  
  /** Create indexes in this directory, optimally use a subdir, named after the test */
  public static final File TEMP_DIR;
  static {
    String method = System.getProperty("testmethod", "").trim();
    TEST_METHOD = method.length() == 0 ? null : method;
    String s = System.getProperty("tempDir", System.getProperty("java.io.tmpdir"));
    if (s == null)
      throw new RuntimeException("To run tests, you need to define system property 'tempDir' or 'java.io.tmpdir'.");
    TEMP_DIR = new File(s);
    TEMP_DIR.mkdirs();
  }
  
  /** set of directories we created, in afterclass we try to clean these up */
  private static final Map<File, StackTraceElement[]> tempDirs = Collections.synchronizedMap(new HashMap<File, StackTraceElement[]>());

  // by default we randomly pick a different codec for
  // each test case (non-J4 tests) and each test class (J4
  // tests)
  /** Gets the locale to run tests with */
  public static final String TEST_LOCALE = System.getProperty("tests.locale", "random");
  /** Gets the timezone to run tests with */
  public static final String TEST_TIMEZONE = System.getProperty("tests.timezone", "random");
  /** Gets the directory to run tests with */
  public static final String TEST_DIRECTORY = System.getProperty("tests.directory", "random");
  /** Get the number of times to run tests */
  public static final int TEST_ITER = Integer.parseInt(System.getProperty("tests.iter", "1"));
  /** Get the minimum number of times to run tests until a failure happens */
  public static final int TEST_ITER_MIN = Integer.parseInt(System.getProperty("tests.iter.min", Integer.toString(TEST_ITER)));
  /** Get the random seed for tests */
  public static final String TEST_SEED = System.getProperty("tests.seed", "random");
  /** whether or not nightly tests should run */
  public static final boolean TEST_NIGHTLY = Boolean.parseBoolean(System.getProperty("tests.nightly", "false"));
  /** the line file used by LineFileDocs */
  public static final String TEST_LINE_DOCS_FILE = System.getProperty("tests.linedocsfile", "europarl.lines.txt.gz");
  /** whether or not to clean threads between test invocations: "false", "perMethod", "perClass" */
  public static final String TEST_CLEAN_THREADS = System.getProperty("tests.cleanthreads", "perClass");

  /**
   * A random multiplier which you should use when writing random tests:
   * multiply it by the number of iterations
   */
  public static final int RANDOM_MULTIPLIER = Integer.parseInt(System.getProperty("tests.multiplier", "1"));
  
  private int savedBoolMaxClauseCount;

  private volatile Thread.UncaughtExceptionHandler savedUncaughtExceptionHandler = null;
  
  /** Used to track if setUp and tearDown are called correctly from subclasses */
  private static State state = State.INITIAL;

  private static enum State {
    INITIAL, // no tests ran yet
    SETUP,   // test has called setUp()
    RANTEST, // test is running
    TEARDOWN // test has called tearDown()
  }
  
  private static class UncaughtExceptionEntry {
    public final Thread thread;
    public final Throwable exception;
    
    public UncaughtExceptionEntry(Thread thread, Throwable exception) {
      this.thread = thread;
      this.exception = exception;
    }
  }
  private List<UncaughtExceptionEntry> uncaughtExceptions = Collections.synchronizedList(new ArrayList<UncaughtExceptionEntry>());
  
  private static Locale locale;
  private static Locale savedLocale;
  private static TimeZone timeZone;
  private static TimeZone savedTimeZone;
  
  private static Map<MockDirectoryWrapper,StackTraceElement[]> stores;
  
  private static class TwoLongs {
    public final long l1, l2;

    public TwoLongs(long l1, long l2) {
      this.l1 = l1;
      this.l2 = l2;
    }

    @Override
    public String toString() {
      return l1 + ":" + l2;
    }

    public static TwoLongs fromString(String s) {
      final int i = s.indexOf(':');
      assert i != -1;
      return new TwoLongs(Long.parseLong(s.substring(0, i)),
                          Long.parseLong(s.substring(1+i)));
    }
  }

  /** @deprecated: until we fix no-fork problems in solr tests */
  @Deprecated
  private static List<String> testClassesRun = new ArrayList<String>();
  
  @BeforeClass
  public static void beforeClassLuceneTestCaseJ4() {
    state = State.INITIAL;
    staticSeed = "random".equals(TEST_SEED) ? seedRand.nextLong() : TwoLongs.fromString(TEST_SEED).l1;
    random.setSeed(staticSeed);
    tempDirs.clear();
    stores = Collections.synchronizedMap(new IdentityHashMap<MockDirectoryWrapper,StackTraceElement[]>());
    // this code consumes randoms where 4.0's lucenetestcase would: to make seeds work across both branches.
    // TODO: doesn't completely work, because what if we get mockrandom codec?!
    if (random.nextInt(4) != 0) {
      random.nextInt(); // consume RandomCodecProvider's seed.
    }
    // end compatibility random-consumption
    savedLocale = Locale.getDefault();
    locale = TEST_LOCALE.equals("random") ? randomLocale(random) : localeForName(TEST_LOCALE);
    Locale.setDefault(locale);
    savedTimeZone = TimeZone.getDefault();
    timeZone = TEST_TIMEZONE.equals("random") ? randomTimeZone(random) : TimeZone.getTimeZone(TEST_TIMEZONE);
    TimeZone.setDefault(timeZone);
    testsFailed = false;
  }
  
  @AfterClass
  public static void afterClassLuceneTestCaseJ4() {
    if (!testsFailed) {
      assertTrue("ensure your setUp() calls super.setUp() and your tearDown() calls super.tearDown()!!!", 
          state == State.INITIAL || state == State.TEARDOWN);
    }
    state = State.INITIAL;
    if (! "false".equals(TEST_CLEAN_THREADS)) {
      int rogueThreads = threadCleanup("test class");
      if (rogueThreads > 0) {
        // TODO: fail here once the leaks are fixed.
        System.err.println("RESOURCE LEAK: test class left " + rogueThreads + " thread(s) running");
      }
    }
    Locale.setDefault(savedLocale);
    TimeZone.setDefault(savedTimeZone);
    System.clearProperty("solr.solr.home");
    System.clearProperty("solr.data.dir");
    // now look for unclosed resources
    if (!testsFailed)
      for (MockDirectoryWrapper d : stores.keySet()) {
        if (d.isOpen()) {
          StackTraceElement elements[] = stores.get(d);
          // Look for the first class that is not LuceneTestCase that requested
          // a Directory. The first two items are of Thread's, so skipping over
          // them.
          StackTraceElement element = null;
          for (int i = 2; i < elements.length; i++) {
            StackTraceElement ste = elements[i];
            if (ste.getClassName().indexOf("LuceneTestCase") == -1) {
              element = ste;
              break;
            }
          }
          fail("directory of test was not closed, opened from: " + element);
        }
      }
    stores = null;
    // if verbose or tests failed, report some information back
    if (VERBOSE || testsFailed)
      System.err.println("NOTE: test params are: " +
        "locale=" + locale + 
        ", timezone=" + (timeZone == null ? "(null)" : timeZone.getID()));
    if (testsFailed) {
      System.err.println("NOTE: all tests run in this JVM:");
      System.err.println(Arrays.toString(testClassesRun.toArray()));
      System.err.println("NOTE: " + System.getProperty("os.name") + " " 
          + System.getProperty("os.version") + " " 
          + System.getProperty("os.arch") + "/"
          + System.getProperty("java.vendor") + " "
          + System.getProperty("java.version") + " "
          + (Constants.JRE_IS_64BIT ? "(64-bit)" : "(32-bit)") + "/"
          + "cpus=" + Runtime.getRuntime().availableProcessors() + ","
          + "threads=" + Thread.activeCount() + ","
          + "free=" + Runtime.getRuntime().freeMemory() + ","
          + "total=" + Runtime.getRuntime().totalMemory());
    }
    // clear out any temp directories if we can
    if (!testsFailed) {
      for (Entry<File, StackTraceElement[]> entry : tempDirs.entrySet()) {
        try {
          _TestUtil.rmDir(entry.getKey());
        } catch (IOException e) {
          e.printStackTrace();
          System.err.println("path " + entry.getKey() + " allocated from");
          // first two STE's are Java's
          StackTraceElement[] elements = entry.getValue();
          for (int i = 2; i < elements.length; i++) {
            StackTraceElement ste = elements[i];            
            // print only our code's stack information
            if (ste.getClassName().indexOf("org.apache.lucene") == -1) break; 
            System.err.println("\t" + ste);
          }
        }
      }
    }
  }

  private static boolean testsFailed; /* true if any tests failed */
  
  // This is how we get control when errors occur.
  // Think of this as start/end/success/failed
  // events.
  @Rule
  public final TestWatchman intercept = new TestWatchman() {

    @Override
    public void failed(Throwable e, FrameworkMethod method) {
      // org.junit.internal.AssumptionViolatedException in older releases
      // org.junit.Assume.AssumptionViolatedException in recent ones
      if (e.getClass().getName().endsWith("AssumptionViolatedException")) {
        if (e.getCause() instanceof TestIgnoredException)
          e = e.getCause();
        System.err.print("NOTE: Assume failed in '" + method.getName() + "' (ignored):");
        if (VERBOSE) {
          System.err.println();
          e.printStackTrace(System.err);
        } else {
          System.err.print(" ");
          System.err.println(e.getMessage());
        }
      } else {
        testsFailed = true;
        reportAdditionalFailureInfo();
      }
      super.failed(e, method);
    }

    @Override
    public void starting(FrameworkMethod method) {
      // set current method name for logging
      LuceneTestCase.this.name = method.getName();
      if (!testsFailed) {
        assertTrue("ensure your setUp() calls super.setUp()!!!", state == State.SETUP);
      }
      state = State.RANTEST;
      super.starting(method);
    }
  };

  @Before
  public void setUp() throws Exception {
    seed = "random".equals(TEST_SEED) ? seedRand.nextLong() : TwoLongs.fromString(TEST_SEED).l2;
    random.setSeed(seed);
    if (!testsFailed) {
      assertTrue("ensure your tearDown() calls super.tearDown()!!!", (state == State.INITIAL || state == State.TEARDOWN));
    }
    state = State.SETUP;
    savedUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        testsFailed = true;
        uncaughtExceptions.add(new UncaughtExceptionEntry(t, e));
        if (savedUncaughtExceptionHandler != null)
          savedUncaughtExceptionHandler.uncaughtException(t, e);
      }
    });
    
    savedBoolMaxClauseCount = BooleanQuery.getMaxClauseCount();
  }


  /**
   * Forcible purges all cache entries from the FieldCache.
   * <p>
   * This method will be called by tearDown to clean up FieldCache.DEFAULT.
   * If a (poorly written) test has some expectation that the FieldCache
   * will persist across test methods (ie: a static IndexReader) this
   * method can be overridden to do nothing.
   * </p>
   *
   * @see FieldCache#purgeAllCaches()
   */
  protected void purgeFieldCache(final FieldCache fc) {
    fc.purgeAllCaches();
  }

  protected String getTestLabel() {
    return getClass().getName() + "." + getName();
  }

  public static void setUseCompoundFile(MergePolicy mp, boolean useCompound) {
    if (mp instanceof LogMergePolicy) {
      ((LogMergePolicy) mp).setUseCompoundFile(useCompound);
    } else if (mp instanceof TieredMergePolicy) {
      ((TieredMergePolicy) mp).setUseCompoundFile(useCompound);
    } else {
      fail("MergePolicy (compound-file) not supported " + mp);
    }
  }

  public static void setMergeFactor(MergePolicy mp, int mergeFactor) {
    if (mp instanceof LogMergePolicy) {
      ((LogMergePolicy) mp).setMergeFactor(mergeFactor);
    } else if (mp instanceof TieredMergePolicy) {
      ((TieredMergePolicy) mp).setMaxMergeAtOnce(mergeFactor);
      ((TieredMergePolicy) mp).setMaxMergeAtOnceExplicit(mergeFactor);
    } else {
      fail("MergePolicy not supported " + mp);
    }
  }

  @After
  public void tearDown() throws Exception {
    if (!testsFailed) {
      // Note: we allow a test to go straight from SETUP -> TEARDOWN (without ever entering the RANTEST state)
      // because if you assume() inside setUp(), it skips the test and the TestWatchman has no way to know...
      assertTrue("ensure your setUp() calls super.setUp()!!!", state == State.RANTEST || state == State.SETUP);
    }
    state = State.TEARDOWN;
    BooleanQuery.setMaxClauseCount(savedBoolMaxClauseCount);
    if ("perMethod".equals(TEST_CLEAN_THREADS)) {
      int rogueThreads = threadCleanup("test method: '" + getName() + "'");
      if (rogueThreads > 0) {
        System.err.println("RESOURCE LEAK: test method: '" + getName() 
            + "' left " + rogueThreads + " thread(s) running");
        // TODO: fail, but print seed for now.
        if (!testsFailed && uncaughtExceptions.isEmpty()) {
          reportAdditionalFailureInfo();
        }
      }
    }
    Thread.setDefaultUncaughtExceptionHandler(savedUncaughtExceptionHandler);
    try {

      if (!uncaughtExceptions.isEmpty()) {
        testsFailed = true;
        reportAdditionalFailureInfo();
        System.err.println("The following exceptions were thrown by threads:");
        for (UncaughtExceptionEntry entry : uncaughtExceptions) {
          System.err.println("*** Thread: " + entry.thread.getName() + " ***");
          entry.exception.printStackTrace(System.err);
        }
        fail("Some threads threw uncaught exceptions!");
      }

      // calling assertSaneFieldCaches here isn't as useful as having test 
      // classes call it directly from the scope where the index readers 
      // are used, because they could be gc'ed just before this tearDown 
      // method is called.
      //
      // But it's better then nothing.
      //
      // If you are testing functionality that you know for a fact 
      // "violates" FieldCache sanity, then you should either explicitly 
      // call purgeFieldCache at the end of your test method, or refactor
      // your Test class so that the inconsistant FieldCache usages are 
      // isolated in distinct test methods  
      assertSaneFieldCaches(getTestLabel());

    } finally {
      purgeFieldCache(FieldCache.DEFAULT);
    }
  }

  private final static int THREAD_STOP_GRACE_MSEC = 50;
  // jvm-wide list of 'rogue threads' we found, so they only get reported once.
  private final static IdentityHashMap<Thread,Boolean> rogueThreads = new IdentityHashMap<Thread,Boolean>();
  
  static {
    // just a hack for things like eclipse test-runner threads
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      rogueThreads.put(t, true);
    }
  }
  
  /**
   * Looks for leftover running threads, trying to kill them off,
   * so they don't fail future tests.
   * returns the number of rogue threads that it found.
   */
  private static int threadCleanup(String context) {
    // educated guess
    Thread[] stillRunning = new Thread[Thread.activeCount()+1];
    int threadCount = 0;
    int rogueCount = 0;
    
    if ((threadCount = Thread.enumerate(stillRunning)) > 1) {
      while (threadCount == stillRunning.length) {
        // truncated response
        stillRunning = new Thread[stillRunning.length*2];
        threadCount = Thread.enumerate(stillRunning);
      }
      
      for (int i = 0; i < threadCount; i++) {
        Thread t = stillRunning[i];
          
        if (t.isAlive() && 
            !rogueThreads.containsKey(t) && 
            t != Thread.currentThread() &&
            // TODO: TimeLimitingCollector starts a thread statically.... WTF?!
            !t.getName().equals("TimeLimitedCollector timer thread") &&
            /* its ok to keep your searcher across test cases */
            (t.getName().startsWith("LuceneTestCase") && context.startsWith("test method")) == false) {
          System.err.println("WARNING: " + context  + " left thread running: " + t);
          rogueThreads.put(t, true);
          rogueCount++;
          if (t.getName().startsWith("LuceneTestCase")) {
            System.err.println("PLEASE CLOSE YOUR INDEXSEARCHERS IN YOUR TEST!!!!");
            continue;
          } else {
            // wait on the thread to die of natural causes
            try {
              t.join(THREAD_STOP_GRACE_MSEC);
            } catch (InterruptedException e) { e.printStackTrace(); }
          }
          // try to stop the thread:
          t.setUncaughtExceptionHandler(null);
          Thread.setDefaultUncaughtExceptionHandler(null);
          t.interrupt();
        }
      }
    }
    return rogueCount;
  }
  
  /**
   * Asserts that FieldCacheSanityChecker does not detect any
   * problems with FieldCache.DEFAULT.
   * <p>
   * If any problems are found, they are logged to System.err
   * (allong with the msg) when the Assertion is thrown.
   * </p>
   * <p>
   * This method is called by tearDown after every test method,
   * however IndexReaders scoped inside test methods may be garbage
   * collected prior to this method being called, causing errors to
   * be overlooked. Tests are encouraged to keep their IndexReaders
   * scoped at the class level, or to explicitly call this method
   * directly in the same scope as the IndexReader.
   * </p>
   *
   * @see org.apache.lucene.util.FieldCacheSanityChecker
   */
  protected void assertSaneFieldCaches(final String msg) {
    final CacheEntry[] entries = FieldCache.DEFAULT.getCacheEntries();
    Insanity[] insanity = null;
    try {
      try {
        insanity = FieldCacheSanityChecker.checkSanity(entries);
      } catch (RuntimeException e) {
        dumpArray(msg + ": FieldCache", entries, System.err);
        throw e;
      }

      assertEquals(msg + ": Insane FieldCache usage(s) found",
              0, insanity.length);
      insanity = null;
    } finally {

      // report this in the event of any exception/failure
      // if no failure, then insanity will be null anyway
      if (null != insanity) {
        dumpArray(msg + ": Insane FieldCache usage(s)", insanity, System.err);
      }

    }
  }
  
  // These deprecated methods should be removed soon, when all tests using no Epsilon are fixed:
  
  @Deprecated
  static public void assertEquals(double expected, double actual) {
    assertEquals(null, expected, actual);
  }
   
  @Deprecated
  static public void assertEquals(String message, double expected, double actual) {
    assertEquals(message, Double.valueOf(expected), Double.valueOf(actual));
  }

  @Deprecated
  static public void assertEquals(float expected, float actual) {
    assertEquals(null, expected, actual);
  }

  @Deprecated
  static public void assertEquals(String message, float expected, float actual) {
    assertEquals(message, Float.valueOf(expected), Float.valueOf(actual));
  }
  
  // Replacement for Assume jUnit class, so we can add a message with explanation:
  
  private static final class TestIgnoredException extends RuntimeException {
    TestIgnoredException(String msg) {
      super(msg);
    }
    
    TestIgnoredException(String msg, Throwable t) {
      super(msg, t);
    }
    
    @Override
    public String getMessage() {
      StringBuilder sb = new StringBuilder(super.getMessage());
      if (getCause() != null)
        sb.append(" - ").append(getCause());
      return sb.toString();
    }
    
    // only this one is called by our code, exception is not used outside this class:
    @Override
    public void printStackTrace(PrintStream s) {
      if (getCause() != null) {
        s.println(super.toString() + " - Caused by:");
        getCause().printStackTrace(s);
      } else {
        super.printStackTrace(s);
      }
    }
  }
  
  public static void assumeTrue(String msg, boolean b) {
    Assume.assumeNoException(b ? null : new TestIgnoredException(msg));
  }
 
  public static void assumeFalse(String msg, boolean b) {
    assumeTrue(msg, !b);
  }
  
  public static void assumeNoException(String msg, Exception e) {
    Assume.assumeNoException(e == null ? null : new TestIgnoredException(msg, e));
  }
 
  /**
   * Convenience method for logging an iterator.
   *
   * @param label  String logged before/after the items in the iterator
   * @param iter   Each next() is toString()ed and logged on it's own line. If iter is null this is logged differnetly then an empty iterator.
   * @param stream Stream to log messages to.
   */
  public static void dumpIterator(String label, Iterator<?> iter,
                                  PrintStream stream) {
    stream.println("*** BEGIN " + label + " ***");
    if (null == iter) {
      stream.println(" ... NULL ...");
    } else {
      while (iter.hasNext()) {
        stream.println(iter.next().toString());
      }
    }
    stream.println("*** END " + label + " ***");
  }

  /**
   * Convenience method for logging an array.  Wraps the array in an iterator and delegates
   *
   * @see #dumpIterator(String,Iterator,PrintStream)
   */
  public static void dumpArray(String label, Object[] objs,
                               PrintStream stream) {
    Iterator<?> iter = (null == objs) ? null : Arrays.asList(objs).iterator();
    dumpIterator(label, iter, stream);
  }

  /** create a new index writer config with random defaults */
  public static IndexWriterConfig newIndexWriterConfig(Version v, Analyzer a) {
    return newIndexWriterConfig(random, v, a);
  }
  
  /** create a new index writer config with random defaults using the specified random */
  public static IndexWriterConfig newIndexWriterConfig(Random r, Version v, Analyzer a) {
    IndexWriterConfig c = new IndexWriterConfig(v, a);
    if (r.nextBoolean()) {
      c.setMergePolicy(newTieredMergePolicy());
    } else if (r.nextBoolean()) {
      c.setMergePolicy(newLogMergePolicy());
    } else {
      c.setMergePolicy(new MockRandomMergePolicy(r));
    }
    
    if (r.nextBoolean()) {
      c.setMergeScheduler(new SerialMergeScheduler());
    }
    if (r.nextBoolean()) {
      if (r.nextInt(20) == 17) {
        c.setMaxBufferedDocs(2);
      } else {
        c.setMaxBufferedDocs(_TestUtil.nextInt(r, 2, 1000));
      }
    }
    if (r.nextBoolean()) {
      c.setTermIndexInterval(_TestUtil.nextInt(r, 1, 1000));
    }
    if (r.nextBoolean()) {
      c.setMaxThreadStates(_TestUtil.nextInt(r, 1, 20));
    }
    
    if (r.nextBoolean()) {
      c.setMergePolicy(new MockRandomMergePolicy(r));
    } else {
      c.setMergePolicy(newLogMergePolicy());
    }
    
    c.setReaderPooling(r.nextBoolean());
    c.setReaderTermsIndexDivisor(_TestUtil.nextInt(r, 1, 4));
    return c;
  }

  public static LogMergePolicy newLogMergePolicy() {
    return newLogMergePolicy(random);
  }

  public static TieredMergePolicy newTieredMergePolicy() {
    return newTieredMergePolicy(random);
  }

  public static LogMergePolicy newLogMergePolicy(Random r) {
    LogMergePolicy logmp = r.nextBoolean() ? new LogDocMergePolicy() : new LogByteSizeMergePolicy();
    logmp.setUseCompoundFile(r.nextBoolean());
    logmp.setCalibrateSizeByDeletes(r.nextBoolean());
    if (r.nextInt(3) == 2) {
      logmp.setMergeFactor(2);
    } else {
      logmp.setMergeFactor(_TestUtil.nextInt(r, 2, 20));
    }
    return logmp;
  }

  public static TieredMergePolicy newTieredMergePolicy(Random r) {
    TieredMergePolicy tmp = new TieredMergePolicy();
    if (r.nextInt(3) == 2) {
      tmp.setMaxMergeAtOnce(2);
      tmp.setMaxMergeAtOnceExplicit(2);
    } else {
      tmp.setMaxMergeAtOnce(_TestUtil.nextInt(r, 2, 20));
      tmp.setMaxMergeAtOnceExplicit(_TestUtil.nextInt(r, 2, 30));
    }
    tmp.setMaxMergedSegmentMB(0.2 + r.nextDouble() * 2.0);
    tmp.setFloorSegmentMB(0.2 + r.nextDouble() * 2.0);
    tmp.setExpungeDeletesPctAllowed(0.0 + r.nextDouble() * 30.0);
    tmp.setSegmentsPerTier(_TestUtil.nextInt(r, 2, 20));
    tmp.setUseCompoundFile(r.nextBoolean());
    tmp.setNoCFSRatio(0.1 + r.nextDouble()*0.8);
    return tmp;
  }

  public static LogMergePolicy newLogMergePolicy(boolean useCFS) {
    LogMergePolicy logmp = newLogMergePolicy();
    logmp.setUseCompoundFile(useCFS);
    return logmp;
  }

  public static LogMergePolicy newLogMergePolicy(boolean useCFS, int mergeFactor) {
    LogMergePolicy logmp = newLogMergePolicy();
    logmp.setUseCompoundFile(useCFS);
    logmp.setMergeFactor(mergeFactor);
    return logmp;
  }

  public static LogMergePolicy newLogMergePolicy(int mergeFactor) {
    LogMergePolicy logmp = newLogMergePolicy();
    logmp.setMergeFactor(mergeFactor);
    return logmp;
  }

  /**
   * Returns a new Directory instance. Use this when the test does not
   * care about the specific Directory implementation (most tests).
   * <p>
   * The Directory is wrapped with {@link MockDirectoryWrapper}.
   * By default this means it will be picky, such as ensuring that you
   * properly close it and all open files in your test. It will emulate
   * some features of Windows, such as not allowing open files to be
   * overwritten.
   */
  public static MockDirectoryWrapper newDirectory() throws IOException {
    return newDirectory(random);
  }
  
  /**
   * Returns a new Directory instance, using the specified random.
   * See {@link #newDirectory()} for more information.
   */
  public static MockDirectoryWrapper newDirectory(Random r) throws IOException {
    Directory impl = newDirectoryImpl(r, TEST_DIRECTORY);
    MockDirectoryWrapper dir = new MockDirectoryWrapper(r, impl);
    stores.put(dir, Thread.currentThread().getStackTrace());
    return dir;
  }
  
  /**
   * Returns a new Directory instance, with contents copied from the
   * provided directory. See {@link #newDirectory()} for more
   * information.
   */
  public static MockDirectoryWrapper newDirectory(Directory d) throws IOException {
    return newDirectory(random, d);
  }
  
  /** Returns a new FSDirectory instance over the given file, which must be a folder. */
  public static MockDirectoryWrapper newFSDirectory(File f) throws IOException {
    return newFSDirectory(f, null);
  }
  
  /** Returns a new FSDirectory instance over the given file, which must be a folder. */
  public static MockDirectoryWrapper newFSDirectory(File f, LockFactory lf) throws IOException {
    String fsdirClass = TEST_DIRECTORY;
    if (fsdirClass.equals("random")) {
      fsdirClass = FS_DIRECTORIES[random.nextInt(FS_DIRECTORIES.length)];
    }
    
    if (fsdirClass.indexOf(".") == -1) {// if not fully qualified, assume .store
      fsdirClass = "org.apache.lucene.store." + fsdirClass;
    }
    
    Class<? extends FSDirectory> clazz;
    try {
      try {
        clazz = Class.forName(fsdirClass).asSubclass(FSDirectory.class);
      } catch (ClassCastException e) {
        // TEST_DIRECTORY is not a sub-class of FSDirectory, so draw one at random
        fsdirClass = FS_DIRECTORIES[random.nextInt(FS_DIRECTORIES.length)];
        
        if (fsdirClass.indexOf(".") == -1) {// if not fully qualified, assume .store
          fsdirClass = "org.apache.lucene.store." + fsdirClass;
        }
        
        clazz = Class.forName(fsdirClass).asSubclass(FSDirectory.class);
      }
      MockDirectoryWrapper dir = new MockDirectoryWrapper(random, newFSDirectoryImpl(clazz, f, lf));
      stores.put(dir, Thread.currentThread().getStackTrace());
      return dir;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Returns a new Directory instance, using the specified random
   * with contents copied from the provided directory. See 
   * {@link #newDirectory()} for more information.
   */
  public static MockDirectoryWrapper newDirectory(Random r, Directory d) throws IOException {
    Directory impl = newDirectoryImpl(r, TEST_DIRECTORY);
    for (String file : d.listAll()) {
     d.copy(impl, file, file);
    }
    MockDirectoryWrapper dir = new MockDirectoryWrapper(r, impl);
    stores.put(dir, Thread.currentThread().getStackTrace());
    return dir;
  }
  
  /** Returns a new field instance. 
   * See {@link #newField(String, String, Field.Store, Field.Index, Field.TermVector)} for more information */
  public static Field newField(String name, String value, Index index) {
    return newField(random, name, value, index);
  }
  
  /** Returns a new field instance. 
   * See {@link #newField(String, String, Field.Store, Field.Index, Field.TermVector)} for more information */
  public static Field newField(String name, String value, Store store, Index index) {
    return newField(random, name, value, store, index);
  }
  
  /**
   * Returns a new Field instance. Use this when the test does not
   * care about some specific field settings (most tests)
   * <ul>
   *  <li>If the store value is set to Store.NO, sometimes the field will be randomly stored.
   *  <li>More term vector data than you ask for might be indexed, for example if you choose YES
   *      it might index term vectors with offsets too.
   * </ul>
   */
  public static Field newField(String name, String value, Store store, Index index, TermVector tv) {
    return newField(random, name, value, store, index, tv);
  }
  
  /** Returns a new field instance, using the specified random. 
   * See {@link #newField(String, String, Field.Store, Field.Index, Field.TermVector)} for more information */
  public static Field newField(Random random, String name, String value, Index index) {
    return newField(random, name, value, Store.NO, index);
  }
  
  /** Returns a new field instance, using the specified random. 
   * See {@link #newField(String, String, Field.Store, Field.Index, Field.TermVector)} for more information */
  public static Field newField(Random random, String name, String value, Store store, Index index) {
    return newField(random, name, value, store, index, TermVector.NO);
  }
  
  /** Returns a new field instance, using the specified random. 
   * See {@link #newField(String, String, Field.Store, Field.Index, Field.TermVector)} for more information */
  public static Field newField(Random random, String name, String value, Store store, Index index, TermVector tv) {
    if (!index.isIndexed())
      return new Field(name, value, store, index);
    
    if (!store.isStored() && random.nextBoolean())
      store = Store.YES; // randomly store it
    
    tv = randomTVSetting(random, tv);
    
    return new Field(name, value, store, index, tv);
  }
  
  static final TermVector tvSettings[] = { 
    TermVector.NO, TermVector.YES, TermVector.WITH_OFFSETS, 
    TermVector.WITH_POSITIONS, TermVector.WITH_POSITIONS_OFFSETS 
  };
  
  private static TermVector randomTVSetting(Random random, TermVector minimum) {
    switch(minimum) {
      case NO: return tvSettings[_TestUtil.nextInt(random, 0, tvSettings.length-1)];
      case YES: return tvSettings[_TestUtil.nextInt(random, 1, tvSettings.length-1)];
      case WITH_OFFSETS: return random.nextBoolean() ? TermVector.WITH_OFFSETS 
          : TermVector.WITH_POSITIONS_OFFSETS;
      case WITH_POSITIONS: return random.nextBoolean() ? TermVector.WITH_POSITIONS 
          : TermVector.WITH_POSITIONS_OFFSETS;
      default: return TermVector.WITH_POSITIONS_OFFSETS;
    }
  }
  
  /** return a random Locale from the available locales on the system */
  public static Locale randomLocale(Random random) {
    Locale locales[] = Locale.getAvailableLocales();
    return locales[random.nextInt(locales.length)];
  }
  
  /** return a random TimeZone from the available timezones on the system */
  public static TimeZone randomTimeZone(Random random) {
    String tzIds[] = TimeZone.getAvailableIDs();
    return TimeZone.getTimeZone(tzIds[random.nextInt(tzIds.length)]);
  }
  
  /** return a Locale object equivalent to its programmatic name */
  public static Locale localeForName(String localeName) {
    String elements[] = localeName.split("\\_");
    switch(elements.length) {
      case 3: return new Locale(elements[0], elements[1], elements[2]);
      case 2: return new Locale(elements[0], elements[1]);
      case 1: return new Locale(elements[0]);
      default: throw new IllegalArgumentException("Invalid Locale: " + localeName);
    }
  }

  private static final String FS_DIRECTORIES[] = {
    "SimpleFSDirectory",
    "NIOFSDirectory",
    "MMapDirectory"
  };

  private static final String CORE_DIRECTORIES[] = {
    "RAMDirectory",
    FS_DIRECTORIES[0], FS_DIRECTORIES[1], FS_DIRECTORIES[2]
  };
  
  public static String randomDirectory(Random random) {
    if (random.nextInt(10) == 0) {
      return CORE_DIRECTORIES[random.nextInt(CORE_DIRECTORIES.length)];
    } else {
      return "RAMDirectory";
    }
  }

  private static Directory newFSDirectoryImpl(
      Class<? extends FSDirectory> clazz, File file, LockFactory lockFactory)
      throws IOException {
    FSDirectory d = null;
    try {
      // Assuming every FSDirectory has a ctor(File), but not all may take a
      // LockFactory too, so setting it afterwards.
      Constructor<? extends FSDirectory> ctor = clazz.getConstructor(File.class);
      d = ctor.newInstance(file);
    } catch (Exception e) {
      d = FSDirectory.open(file);
    }
    if (lockFactory != null) {
      d.setLockFactory(lockFactory);
    }
    return d;
  }
  
  /** Registers a temp file that will be deleted when tests are done. */
  public static void registerTempFile(File tmpFile) {
    tempDirs.put(tmpFile.getAbsoluteFile(), Thread.currentThread().getStackTrace());
  }
  
  static Directory newDirectoryImpl(Random random, String clazzName) {
    if (clazzName.equals("random"))
      clazzName = randomDirectory(random);
    if (clazzName.indexOf(".") == -1) // if not fully qualified, assume .store
      clazzName = "org.apache.lucene.store." + clazzName;
    try {
      final Class<? extends Directory> clazz = Class.forName(clazzName).asSubclass(Directory.class);
      // If it is a FSDirectory type, try its ctor(File)
      if (FSDirectory.class.isAssignableFrom(clazz)) {
        final File tmpFile = File.createTempFile("test", "tmp", TEMP_DIR);
        tmpFile.delete();
        tmpFile.mkdir();
        registerTempFile(tmpFile);
        return newFSDirectoryImpl(clazz.asSubclass(FSDirectory.class), tmpFile, null);
      }

      // try empty ctor
      return clazz.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } 
  }
  
  /** create a new searcher over the reader.
   * This searcher might randomly use threads. */
  public static IndexSearcher newSearcher(IndexReader r) throws IOException {
    return newSearcher(r, true);
  }
  
  /** create a new searcher over the reader.
   * This searcher might randomly use threads.
   * if <code>maybeWrap</code> is true, this searcher might wrap the reader
   * with one that returns null for getSequentialSubReaders.
   */
  public static IndexSearcher newSearcher(IndexReader r, boolean maybeWrap) throws IOException {

    if (random.nextBoolean()) {
      if (maybeWrap && random.nextBoolean()) {
        return new IndexSearcher(new SlowMultiReaderWrapper(r));
      } else {
        return new IndexSearcher(r);
      }
    } else {
      int threads = 0;
      final ExecutorService ex = (random.nextBoolean()) ? null 
          : Executors.newFixedThreadPool(threads = _TestUtil.nextInt(random, 1, 8), 
                      new NamedThreadFactory("LuceneTestCase"));
      if (ex != null && VERBOSE) {
        System.out.println("NOTE: newSearcher using ExecutorService with " + threads + " threads");
      }
      return new IndexSearcher(r, ex) {
        @Override
        public void close() throws IOException {
          super.close();
          if (ex != null) {
            ex.shutdown();
            try {
              ex.awaitTermination(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
      };
    }
  }

  public String getName() {
    return this.name;
  }
  
  /** Gets a resource from the classpath as {@link File}. This method should only be used,
   * if a real file is needed. To get a stream, code should prefer
   * {@link Class#getResourceAsStream} using {@code this.getClass()}.
   */
  
  protected File getDataFile(String name) throws IOException {
    try {
      return new File(this.getClass().getResource(name).toURI());
    } catch (Exception e) {
      throw new IOException("Cannot find resource: " + name);
    }
  }

  // We get here from InterceptTestCaseEvents on the 'failed' event....
  public void reportAdditionalFailureInfo() {
    System.err.println("NOTE: reproduce with: ant test -Dtestcase=" + getClass().getSimpleName() 
        + " -Dtestmethod=" + getName() + " -Dtests.seed=" + new TwoLongs(staticSeed, seed)
        + reproduceWithExtraParams());
  }
  
  // extra params that were overridden needed to reproduce the command
  private String reproduceWithExtraParams() {
    StringBuilder sb = new StringBuilder();
    if (!TEST_LOCALE.equals("random")) sb.append(" -Dtests.locale=").append(TEST_LOCALE);
    if (!TEST_TIMEZONE.equals("random")) sb.append(" -Dtests.timezone=").append(TEST_TIMEZONE);
    if (!TEST_DIRECTORY.equals("random")) sb.append(" -Dtests.directory=").append(TEST_DIRECTORY);
    if (RANDOM_MULTIPLIER > 1) sb.append(" -Dtests.multiplier=").append(RANDOM_MULTIPLIER);
    return sb.toString();
  }

  // recorded seed: for beforeClass
  private static long staticSeed;
  // seed for individual test methods, changed in @before
  private long seed;
  
  private static final Random seedRand = new Random();
  protected static final Random random = new Random(0);

  private String name = "<unknown>";
  
  /**
   * Annotation for tests that should only be run during nightly builds.
   */
  @Documented
  @Inherited
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Nightly {}
  
  /** optionally filters the tests to be run by TEST_METHOD */
  public static class LuceneTestCaseRunner extends BlockJUnit4ClassRunner {
    private List<FrameworkMethod> testMethods;

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
      if (testMethods != null)
        return testMethods;
      testClassesRun.add(getTestClass().getJavaClass().getSimpleName());
      testMethods = new ArrayList<FrameworkMethod>();
      for (Method m : getTestClass().getJavaClass().getMethods()) {
        // check if the current test's class has methods annotated with @Ignore
        final Ignore ignored = m.getAnnotation(Ignore.class);
        if (ignored != null && !m.getName().equals("alwaysIgnoredTestMethod")) {
          System.err.println("NOTE: Ignoring test method '" + m.getName() + "': " + ignored.value());
        }
        // add methods starting with "test"
        final int mod = m.getModifiers();
        if (m.getAnnotation(Test.class) != null ||
            (m.getName().startsWith("test") &&
            !Modifier.isAbstract(mod) &&
            m.getParameterTypes().length == 0 &&
            m.getReturnType() == Void.TYPE))
        {
          if (Modifier.isStatic(mod))
            throw new RuntimeException("Test methods must not be static.");
          testMethods.add(new FrameworkMethod(m));
        }
      }
      
      if (testMethods.isEmpty()) {
        throw new RuntimeException("No runnable methods!");
      }
      
      if (TEST_NIGHTLY == false) {
        if (getTestClass().getJavaClass().isAnnotationPresent(Nightly.class)) {
          /* the test class is annotated with nightly, remove all methods */
          String className = getTestClass().getJavaClass().getSimpleName();
          System.err.println("NOTE: Ignoring nightly-only test class '" + className + "'");
          testMethods.clear();
        } else {
          /* remove all nightly-only methods */
          for (int i = 0; i < testMethods.size(); i++) {
            final FrameworkMethod m = testMethods.get(i);
            if (m.getAnnotation(Nightly.class) != null) {
              System.err.println("NOTE: Ignoring nightly-only test method '" + m.getName() + "'");
              testMethods.remove(i--);
            }
          }
        }
        /* dodge a possible "no-runnable methods" exception by adding a fake ignored test */
        if (testMethods.isEmpty()) {
          try {
            testMethods.add(new FrameworkMethod(LuceneTestCase.class.getMethod("alwaysIgnoredTestMethod")));
          } catch (Exception e) { throw new RuntimeException(e); }
        }
      }
      return testMethods;
    }

    @Override
    protected void runChild(FrameworkMethod arg0, RunNotifier arg1) {
      if (VERBOSE) {
        System.out.println("\nNOTE: running test " + arg0.getName());
      }
      
      // only print iteration info if the user requested more than one iterations
      final boolean verbose = VERBOSE && TEST_ITER > 1;
      
      final int currentIter[] = new int[1];
      arg1.addListener(new RunListener() {
        @Override
        public void testFailure(Failure failure) throws Exception {
          if (verbose) {
            System.out.println("\nNOTE: iteration " + currentIter[0] + " failed! ");
          }
        }
      });
      for (int i = 0; i < TEST_ITER; i++) {
        currentIter[0] = i;
        if (verbose) {
          System.out.println("\nNOTE: running iter=" + (1+i) + " of " + TEST_ITER);
        }
        super.runChild(arg0, arg1);
        if (testsFailed) {
          if (i >= TEST_ITER_MIN - 1) { // XXX is this still off-by-one?
            break;
          }
        }
      }
    }

    public LuceneTestCaseRunner(Class<?> clazz) throws InitializationError {
      super(clazz);
      Filter f = new Filter() {

        @Override
        public String describe() { return "filters according to TEST_METHOD"; }

        @Override
        public boolean shouldRun(Description d) {
          return TEST_METHOD == null || d.getMethodName().equals(TEST_METHOD);
        }     
      };
      
      try {
        f.apply(this);
      } catch (NoTestsRemainException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  @Ignore("just a hack")
  public final void alwaysIgnoredTestMethod() {}
}
