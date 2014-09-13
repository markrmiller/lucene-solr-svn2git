package org.apache.lucene.analysis;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.junit.Assert;

/** Utility class for doing vocabulary-based stemming tests */
public class VocabularyAssert {
  /** Run a vocabulary test against two data files. */
  public static void assertVocabulary(Analyzer a, InputStream voc, InputStream out)
  throws IOException {
    BufferedReader vocReader = new BufferedReader(
        new InputStreamReader(voc, StandardCharsets.UTF_8));
    BufferedReader outputReader = new BufferedReader(
        new InputStreamReader(out, StandardCharsets.UTF_8));
    String inputWord = null;
    while ((inputWord = vocReader.readLine()) != null) {
      String expectedWord = outputReader.readLine();
      Assert.assertNotNull(expectedWord);
      BaseTokenStreamTestCase.checkOneTerm(a, inputWord, expectedWord);
    }
  }
  
  /** Run a vocabulary test against one file: tab separated. */
  public static void assertVocabulary(Analyzer a, InputStream vocOut)
  throws IOException {
    BufferedReader vocReader = new BufferedReader(
        new InputStreamReader(vocOut, StandardCharsets.UTF_8));
    String inputLine = null;
    while ((inputLine = vocReader.readLine()) != null) {
      if (inputLine.startsWith("#") || inputLine.trim().length() == 0)
        continue; /* comment */
      String words[] = inputLine.split("\t");
      BaseTokenStreamTestCase.checkOneTerm(a, words[0], words[1]);
    }
  }
  
  /** Run a vocabulary test against two data files inside a zip file */
  public static void assertVocabulary(Analyzer a, Path zipFile, String voc, String out) throws IOException {
    ZipFile zip = new ZipFile(zipFile.toFile());
    InputStream v = zip.getInputStream(zip.getEntry(voc));
    InputStream o = zip.getInputStream(zip.getEntry(out));
    assertVocabulary(a, v, o);
    v.close();
    o.close();
    zip.close();
  }
  
  /** Run a vocabulary test against a tab-separated data file inside a zip file */
  public static void assertVocabulary(Analyzer a, Path zipFile, String vocOut) throws IOException {
    ZipFile zip = new ZipFile(zipFile.toFile());
    InputStream vo = zip.getInputStream(zip.getEntry(vocOut));
    assertVocabulary(a, vo);
    vo.close();
    zip.close();
  }
}
