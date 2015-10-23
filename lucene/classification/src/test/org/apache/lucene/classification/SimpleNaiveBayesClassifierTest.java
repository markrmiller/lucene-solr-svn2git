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
package org.apache.lucene.classification;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.reverse.ReverseStringFilter;
import org.apache.lucene.classification.utils.ConfusionMatrixGenerator;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Testcase for {@link SimpleNaiveBayesClassifier}
 */
public class SimpleNaiveBayesClassifierTest extends ClassificationTestBase<BytesRef> {

  @Test
  public void testBasicUsage() throws Exception {
    LeafReader leafReader = null;
    try {
      MockAnalyzer analyzer = new MockAnalyzer(random());
      leafReader = getSampleIndex(analyzer);
      SimpleNaiveBayesClassifier classifier = new SimpleNaiveBayesClassifier(leafReader, analyzer, null, categoryFieldName, textFieldName);
      checkCorrectClassification(classifier, TECHNOLOGY_INPUT, TECHNOLOGY_RESULT);
      checkCorrectClassification(classifier, POLITICS_INPUT, POLITICS_RESULT);
    } finally {
      if (leafReader != null) {
        leafReader.close();
      }
    }
  }

  @Test
  public void testBasicUsageWithQuery() throws Exception {
    LeafReader leafReader = null;
    try {
      MockAnalyzer analyzer = new MockAnalyzer(random());
      leafReader = getSampleIndex(analyzer);
      TermQuery query = new TermQuery(new Term(textFieldName, "it"));
      checkCorrectClassification(new SimpleNaiveBayesClassifier(leafReader, analyzer, query, categoryFieldName, textFieldName), TECHNOLOGY_INPUT, TECHNOLOGY_RESULT);
    } finally {
      if (leafReader != null) {
        leafReader.close();
      }
    }
  }

  @Test
  public void testNGramUsage() throws Exception {
    LeafReader leafReader = null;
    try {
      Analyzer analyzer = new NGramAnalyzer();
      leafReader = getSampleIndex(analyzer);
      checkCorrectClassification(new CachingNaiveBayesClassifier(leafReader, analyzer, null, categoryFieldName, textFieldName), TECHNOLOGY_INPUT, TECHNOLOGY_RESULT);
    } finally {
      if (leafReader != null) {
        leafReader.close();
      }
    }
  }

  private class NGramAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      final Tokenizer tokenizer = new KeywordTokenizer();
      return new TokenStreamComponents(tokenizer, new ReverseStringFilter(new EdgeNGramTokenFilter(new ReverseStringFilter(tokenizer), 10, 20)));
    }
  }

  @Test
  public void testPerformance() throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    LeafReader leafReader = getRandomIndex(analyzer, 100);
    try {
      long trainStart = System.currentTimeMillis();
      SimpleNaiveBayesClassifier simpleNaiveBayesClassifier = new SimpleNaiveBayesClassifier(leafReader,
          analyzer, null, categoryFieldName, textFieldName);
      long trainEnd = System.currentTimeMillis();
      long trainTime = trainEnd - trainStart;
      assertTrue("training took more than 10s: " + trainTime / 1000 + "s", trainTime < 10000);

      long evaluationStart = System.currentTimeMillis();
      ConfusionMatrixGenerator.ConfusionMatrix confusionMatrix = ConfusionMatrixGenerator.getConfusionMatrix(leafReader,
          simpleNaiveBayesClassifier, categoryFieldName, textFieldName);
      assertNotNull(confusionMatrix);
      long evaluationEnd = System.currentTimeMillis();
      long evaluationTime = evaluationEnd - evaluationStart;
      assertTrue("evaluation took more than 2m: " + evaluationTime / 1000 + "s", evaluationTime < 120000);
      double avgClassificationTime = confusionMatrix.getAvgClassificationTime();
      assertTrue("avg classification time: " + avgClassificationTime, 5000 > avgClassificationTime);
      double accuracy = confusionMatrix.getAccuracy();
      assertTrue(accuracy > 0d);
    } finally {
      leafReader.close();
    }

  }

}
