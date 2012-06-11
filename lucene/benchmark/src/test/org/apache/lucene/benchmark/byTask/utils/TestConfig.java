package org.apache.lucene.benchmark.byTask.utils;

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

import java.util.Properties;

import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

public class TestConfig extends LuceneTestCase {

  @Test
  public void testAbsolutePathNamesWindows() throws Exception {
    Properties props = new Properties();
    props.setProperty("work.dir1", "c:\\temp");
    props.setProperty("work.dir2", "c:/temp");
    Config conf = new Config(props);
    assertEquals("c:\\temp", conf.get("work.dir1", ""));
    assertEquals("c:/temp", conf.get("work.dir2", ""));
  }

}
