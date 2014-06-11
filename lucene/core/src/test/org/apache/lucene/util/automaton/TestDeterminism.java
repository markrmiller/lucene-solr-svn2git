package org.apache.lucene.util.automaton;

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

import org.apache.lucene.util.LuceneTestCase;

/**
 * Not completely thorough, but tries to test determinism correctness
 * somewhat randomly.
 */
public class TestDeterminism extends LuceneTestCase {
  
  /** test a bunch of random regular expressions */
  public void testRegexps() throws Exception {
      int num = atLeast(500);
      for (int i = 0; i < num; i++) {
        assertAutomaton(new RegExp(AutomatonTestUtil.randomRegexp(random()), RegExp.NONE).toLightAutomaton());
      }
  }
  
  /** test against a simple, unoptimized det */
  public void testAgainstSimple() throws Exception {
    int num = atLeast(200);
    for (int i = 0; i < num; i++) {
      LightAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      a = AutomatonTestUtil.determinizeSimpleLight(a);
      LightAutomaton b = BasicOperations.determinize(a);
      // TODO: more verifications possible?
      assertTrue(BasicOperations.sameLanguage(a, b));
    }
  }
  
  private static void assertAutomaton(LightAutomaton a) {
    a = BasicOperations.determinize(a);

    // complement(complement(a)) = a
    LightAutomaton equivalent = BasicOperations.complementLight(BasicOperations.complementLight(a));
    assertTrue(BasicOperations.sameLanguage(a, equivalent));
    
    // a union a = a
    equivalent = BasicOperations.determinize(BasicOperations.unionLight(a, a));
    assertTrue(BasicOperations.sameLanguage(a, equivalent));
    
    // a intersect a = a
    equivalent = BasicOperations.determinize(BasicOperations.intersectionLight(a, a));
    assertTrue(BasicOperations.sameLanguage(a, equivalent));
    
    // a minus a = empty
    LightAutomaton empty = BasicOperations.minusLight(a, a);
    assertTrue(BasicOperations.isEmpty(empty));
    
    // as long as don't accept the empty string
    // then optional(a) - empty = a
    if (!BasicOperations.run(a, "")) {
      //System.out.println("test " + a);
      LightAutomaton optional = BasicOperations.optionalLight(a);
      //System.out.println("optional " + optional);
      equivalent = BasicOperations.minusLight(optional, BasicAutomata.makeEmptyStringLight());
      //System.out.println("equiv " + equivalent);
      assertTrue(BasicOperations.sameLanguage(a, equivalent));
    }
  } 
}
