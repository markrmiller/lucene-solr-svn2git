package org.apache.lucene.util.junitcompat;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

// LUCENE-4456: Test that we fail if there are unreferenced files
public class TestFailIfUnreferencedFiles extends WithNestedTests {
  public TestFailIfUnreferencedFiles() {
    super(true);
  }
  
  public static class Nested1 extends WithNestedTests.AbstractNestedTest {
    public void testDummy() throws Exception {
      Directory dir = newMockDirectory();
      IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig(TEST_VERSION_CURRENT, null));
      iw.addDocument(new Document());
      iw.close();
      IndexOutput output = dir.createOutput("TEST_THE_TESTER_FILE_NEVER_DELETED_ON_CRASH", IOContext.DEFAULT);
      output.writeString("i am unreferenced!");
      output.close();
      dir.close();
    }
  }

  @Test
  public void testFailIfUnreferencedFiles() {
    Result r = JUnitCore.runClasses(Nested1.class);
    Assert.assertEquals(1, r.getFailureCount());
  }
}
