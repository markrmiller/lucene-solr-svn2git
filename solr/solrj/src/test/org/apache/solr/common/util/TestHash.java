package org.apache.solr.common.util;

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

import java.util.Random;

/** Tests for lookup3ycs hash functions
 */
public class TestHash extends LuceneTestCase {

  // Test that the java version produces the same output as the C version
  public void testEqualsLOOKUP3() {
    int[] hashes = new int[] {0xc4c20dd5,0x3ab04cc3,0xebe874a3,0x0e770ef3,0xec321498,0x73845e86,0x8a2db728,0x03c313bb,0xfe5b9199,0x95965125,0xcbc4e7c2};
    /*** the hash values were generated by adding the following to lookup3.c
     *
     * char* s = "hello world";
     * int len = strlen(s);
     * uint32_t a[len];
     * for (int i=0; i<len; i++) {
     *   a[i]=s[i];
     *   uint32_t result = hashword(a, i+1, i*12345);
     *   printf("0x%.8x\n", result);
     * }
     *
     */

    String s = "hello world";
    int[] a = new int[s.length()];
    for (int i=0; i<s.length(); i++) {
      a[i] = s.charAt(i);
      int len = i+1;
      int hash = Hash.lookup3(a, 0, len, i*12345);
      assertEquals(hashes[i], hash);
      int hash2 = Hash.lookup3ycs(a, 0, len, i*12345+(len<<2));
      assertEquals(hashes[i], hash2);
      int hash3 = Hash.lookup3ycs(s, 0, len, i*12345+(len<<2));
      assertEquals(hashes[i], hash3);
    }
  }


  // test that the hash of the UTF-16 encoded Java String is equal to the hash of the unicode code points
  void tstEquiv(int[] utf32, int len) {
    int seed=100;
    StringBuilder sb = new StringBuilder();
    for (int i=0; i<len; i++) sb.appendCodePoint(utf32[i]);
    int hash = Hash.lookup3(utf32, 0, len, seed -(len<<2));
    int hash2 = Hash.lookup3ycs(utf32, 0, len, seed);
    assertEquals(hash, hash2);
    int hash3 = Hash.lookup3ycs(sb, 0, sb.length(), seed);
    assertEquals(hash, hash3);
    long hash4 = Hash.lookup3ycs64(sb, 0, sb.length(), seed);
    assertEquals((int)hash4, hash);
  }


  public void testHash() {
    Random r = random();
    int[] utf32 = new int[20];
    tstEquiv(utf32,0);

    utf32[0]=0x10000;
    tstEquiv(utf32,1);
    utf32[0]=0x8000;
    tstEquiv(utf32,1);
    utf32[0]=Character.MAX_CODE_POINT;
    tstEquiv(utf32,1);

    for (int iter=0; iter<10000; iter++) {
      int len = r.nextInt(utf32.length+1);
      for (int i=0; i<len; i++) {
        int codePoint;
        do  {
          codePoint = r.nextInt(Character.MAX_CODE_POINT+1);
        } while((codePoint & 0xF800) == 0xD800);  // avoid surrogate code points
        utf32[i] = codePoint;
      }
      // System.out.println("len="+len + ","+utf32[0]+","+utf32[1]);
      tstEquiv(utf32, len);
    }
  }


}
