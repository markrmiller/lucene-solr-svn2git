package org.apache.lucene.analysis.kuromoji;

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

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import org.apache.lucene.analysis.kuromoji.Token;
import org.apache.lucene.analysis.kuromoji.Tokenizer;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TokenizerTest extends LuceneTestCase {
  
  private static Tokenizer tokenizer;
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    tokenizer = Tokenizer.builder().build();
  }
  
  @AfterClass
  public static void afterClass() throws Exception {
    tokenizer = null;
  }
  
  @Test
  public void testSegmentation() {
    // Skip tests for Michelle Kwan -- UniDic segments Kwan as ク ワン
    //		String input = "ミシェル・クワンが優勝しました。スペースステーションに行きます。うたがわしい。";
    //		String[] surfaceForms = {
    //				"ミシェル", "・", "クワン", "が", "優勝", "し", "まし", "た", "。",
    //				"スペース", "ステーション", "に", "行き", "ます", "。",
    //				"うたがわしい", "。"
    //		};
    String input = "スペースステーションに行きます。うたがわしい。";
    String[] surfaceForms = {
        "スペース", "ステーション", "に", "行き", "ます", "。",
        "うたがわしい", "。"
    };
    List<Token> tokens = tokenizer.tokenize(input);
    assertTrue(tokens.size() == surfaceForms.length);
    for (int i = 0; i < tokens.size(); i++) {
      assertEquals(surfaceForms[i], tokens.get(i).getSurfaceForm());
    }
  }
  
  
  @Test
  public void testReadings() {
    List<Token> tokens = tokenizer.tokenize("寿司が食べたいです。");
    assertTrue(tokens.size() == 6);
    assertEquals(tokens.get(0).getReading(), "スシ");
    assertEquals(tokens.get(1).getReading(), "ガ");
    assertEquals(tokens.get(2).getReading(), "タベ");
    assertEquals(tokens.get(3).getReading(), "タイ");
    assertEquals(tokens.get(4).getReading(), "デス");
    assertEquals(tokens.get(5).getReading(), "。");
  }
  
  @Test
  public void testBasicForms() {
    List<Token> tokens = tokenizer.tokenize("それはまだ実験段階にあります。");
    assertEquals(9, tokens.size());
    assertNull(tokens.get(0).getBaseForm());
    assertNull(tokens.get(1).getBaseForm());
    assertNull(tokens.get(2).getBaseForm());
    assertNull(tokens.get(3).getBaseForm());
    assertNull(tokens.get(4).getBaseForm());
    assertNull(tokens.get(5).getBaseForm());
    assertEquals(tokens.get(6).getBaseForm(), "ある");
    assertNull(tokens.get(7).getBaseForm());
    assertNull(tokens.get(8).getBaseForm());
  }
  
  @Test
  public void testPartOfSpeech() {
    List<Token> tokens = tokenizer.tokenize("それはまだ実験段階にあります。");
    assertEquals(9, tokens.size());
    assertEquals("名詞,代名詞,一般,*",  tokens.get(0).getPartOfSpeech());
    assertEquals("助詞,係助詞,*,*",    tokens.get(1).getPartOfSpeech());
    assertEquals("副詞,助詞類接続,*,*", tokens.get(2).getPartOfSpeech());
    assertEquals("名詞,サ変接続,*,*",   tokens.get(3).getPartOfSpeech());
    assertEquals("名詞,一般,*,*",      tokens.get(4).getPartOfSpeech());
    assertEquals("助詞,格助詞,一般,*",  tokens.get(5).getPartOfSpeech());
    assertEquals("動詞,自立,*,*",      tokens.get(6).getPartOfSpeech());
    assertEquals("助動詞,*,*,*",       tokens.get(7).getPartOfSpeech());
    assertEquals("記号,句点,*,*",      tokens.get(8).getPartOfSpeech());
  }
  
  public void testBocchan() throws Exception {
    doTestBocchan(1);
  }
  
  @Test @Nightly
  public void testBocchanBig() throws Exception {
    doTestBocchan(100);
  }
  
  private void doTestBocchan(int numIterations) throws Exception {
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        this.getClass().getResourceAsStream("bocchan.utf-8")));
    
    String line = reader.readLine();
    reader.close();
    
    if (VERBOSE) {
      System.out.println("Test for Bocchan without pre-splitting sentences");
    }
    long totalStart = System.currentTimeMillis();
    for (int i = 0; i < numIterations; i++){
      tokenizer.tokenize(line);
    }
    if (VERBOSE) {
      System.out.println("Total time : " + (System.currentTimeMillis() - totalStart));
      System.out.println("Test for Bocchan with pre-splitting sentences");
    }
    String[] sentences = line.split("、|。");
    totalStart = System.currentTimeMillis();
    for (int i = 0; i < numIterations; i++) {
      for (String sentence: sentences) {
        tokenizer.tokenize(sentence);       
      }
    }
    if (VERBOSE) {
      System.out.println("Total time : " + (System.currentTimeMillis() - totalStart));
    }
  }
}
