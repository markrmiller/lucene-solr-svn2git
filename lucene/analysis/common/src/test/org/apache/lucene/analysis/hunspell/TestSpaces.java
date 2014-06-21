package org.apache.lucene.analysis.hunspell;

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

import org.junit.BeforeClass;

public class TestSpaces extends StemmerTestBase {
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    init("spaces.aff", "spaces.dic");
  }
  
  public void testStemming() {
    assertStemsTo("four", "four");
    assertStemsTo("fours", "four");
    assertStemsTo("five", "five");
    assertStemsTo("forty four", "forty four");
    assertStemsTo("forty fours", "forty four");
    assertStemsTo("forty five", "forty five");
    assertStemsTo("fifty", "50");
    assertStemsTo("fiftys", "50");
    assertStemsTo("sixty", "60");
    assertStemsTo("sixty four", "64");
    assertStemsTo("fifty four", "54");
    assertStemsTo("fifty fours", "54");
  }
}
