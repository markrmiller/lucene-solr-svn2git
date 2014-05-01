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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CrankyTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.cranky.CrankyCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.LuceneTestCase.AwaitsFix;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.Rethrow;

/** 
 * Causes a bunch of non-aborting and aborting exceptions and checks that
 * no index corruption is ever created
 */
// TODO: not sure which fails are test bugs or real bugs yet...
// also sometimes when it fails, the exception-stream printing doesnt seem to be working yet
// 
@AwaitsFix(bugUrl = "https://issues.apache.org/jira/browse/LUCENE-5635")
public class TestIndexWriterExceptions2 extends LuceneTestCase {
  
  // just one thread, serial merge policy, hopefully debuggable
  public void testSimple() throws Exception {
    Directory dir = newDirectory();
    
    // log all exceptions we hit, in case we fail (for debugging)
    ByteArrayOutputStream exceptionLog = new ByteArrayOutputStream();
    PrintStream exceptionStream = new PrintStream(exceptionLog, true, "UTF-8");
    
    // create lots of non-aborting exceptions with a broken analyzer
    final long analyzerSeed = random().nextLong();
    Analyzer analyzer = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        MockTokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, false);
        tokenizer.setEnableChecks(false); // TODO: can we turn this on? our filter is probably too evil
        TokenStream stream = new CrankyTokenFilter(tokenizer, new Random(analyzerSeed));
        return new TokenStreamComponents(tokenizer, stream);
      }
    };
    
    // create lots of aborting exceptions with a broken codec
    Codec codec = new CrankyCodec(Codec.getDefault(), new Random(random().nextLong()));
    
    IndexWriterConfig conf = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    // just for now, try to keep this test reproducible
    conf.setMergeScheduler(new SerialMergeScheduler());
    conf.setCodec(codec);
    
    // TODO: too much?
    int numDocs = RANDOM_MULTIPLIER * 1000;
    
    IndexWriter iw = new IndexWriter(dir, conf);
    try {
      for (int i = 0; i < numDocs; i++) {
        // TODO: add crankyDocValuesFields, etc
        Document doc = new Document();
        doc.add(newStringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new NumericDocValuesField("dv", i));
        doc.add(newTextField("text1", TestUtil.randomAnalysisString(random(), 20, true), Field.Store.NO));
        // TODO: sometimes update dv
        try {
          iw.addDocument(doc);
        } catch (Exception e) {
          if (e.getMessage() != null && e.getMessage().startsWith("Fake IOException")) {
            System.out.println("\nTEST: got expected fake exc:" + e.getMessage());
            e.printStackTrace(exceptionStream);
          } else {
            Rethrow.rethrow(e);
          }
        }
        if (random().nextInt(10) == 0) {
          // trigger flush: TODO: sometimes reopen
          try {
            iw.commit();
            if (DirectoryReader.indexExists(dir)) {
              TestUtil.checkIndex(dir);
            }
          } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Fake IOException")) {
              System.out.println("\nTEST: got expected fake exc:" + e.getMessage());
              e.printStackTrace(exceptionStream);
            } else {
              Rethrow.rethrow(e);
            }
          }
        }
      }
      
      iw.close();
      dir.close();
    } catch (Throwable t) {
      System.out.println("Unexpected exception: dumping fake-exception-log:...");
      exceptionStream.flush();
      System.out.println(exceptionLog.toString("UTF-8"));
      System.out.flush();
      Rethrow.rethrow(t);
    }
    
    if (VERBOSE) {
      System.out.println("TEST PASSED: dumping fake-exception-log:...");
      System.out.println(exceptionLog.toString("UTF-8"));
    }
  }
}
