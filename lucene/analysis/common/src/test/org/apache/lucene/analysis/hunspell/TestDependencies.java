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

public class TestDependencies extends StemmerTestBase {
  
  @BeforeClass
  public static void beforeClass() throws Exception {
    init("dependencies.aff", "dependencies.dic");
  }
  
  public void testDependencies() {
    assertStemsTo("drink", "drink", "drink");
    assertStemsTo("drinks", "drink", "drink");
    assertStemsTo("drinkable", "drink");
    // TODO: BUG! assertStemsTo("drinkables", "drink");
    assertStemsTo("undrinkable", "drink");
    // TODO: BUG! assertStemsTo("undrinkables", "drink");
    assertStemsTo("undrink");
    // TODO: BUG! assertStemsTo("undrinks");
  }
}
