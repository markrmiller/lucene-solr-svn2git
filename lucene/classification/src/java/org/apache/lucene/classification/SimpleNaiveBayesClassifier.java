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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;

/**
 * A simplistic Lucene based NaiveBayes classifier, see <code>http://en.wikipedia.org/wiki/Naive_Bayes_classifier</code>
 *
 * @lucene.experimental
 */
public class SimpleNaiveBayesClassifier implements Classifier<BytesRef> {

  /**
   * {@link org.apache.lucene.index.LeafReader} used to access the {@link org.apache.lucene.classification.Classifier}'s
   * index
   */
  protected final LeafReader leafReader;

  /**
   * names of the fields to be used as input text
   */
  protected final String[] textFieldNames;

  /**
   * name of the field to be used as a class / category output
   */
  protected final String classFieldName;

  /**
   * {@link org.apache.lucene.analysis.Analyzer} to be used for tokenizing unseen input text
   */
  protected final Analyzer analyzer;

  /**
   * {@link org.apache.lucene.search.IndexSearcher} to run searches on the index for retrieving frequencies
   */
  protected final IndexSearcher indexSearcher;

  /**
   * {@link org.apache.lucene.search.Query} used to eventually filter the document set to be used to classify
   */
  protected final Query query;

  /**
   * Creates a new NaiveBayes classifier.
   *
   * @param leafReader     the reader on the index to be used for classification
   * @param analyzer       an {@link Analyzer} used to analyze unseen text
   * @param query          a {@link Query} to eventually filter the docs used for training the classifier, or {@code null}
   *                       if all the indexed docs should be used
   * @param classFieldName the name of the field used as the output for the classifier NOTE: must not be havely analyzed
   *                       as the returned class will be a token indexed for this field
   * @param textFieldNames the name of the fields used as the inputs for the classifier, NO boosting supported per field
   */
  public SimpleNaiveBayesClassifier(LeafReader leafReader, Analyzer analyzer, Query query, String classFieldName, String... textFieldNames) {
    this.leafReader = leafReader;
    this.indexSearcher = new IndexSearcher(this.leafReader);
    this.textFieldNames = textFieldNames;
    this.classFieldName = classFieldName;
    this.analyzer = analyzer;
    this.query = query;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ClassificationResult<BytesRef> assignClass(String inputDocument) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = assignClassNormalizedList(inputDocument);
    ClassificationResult<BytesRef> assignedClass = null;
    double maxscore = -Double.MAX_VALUE;
    for (ClassificationResult<BytesRef> c : assignedClasses) {
      if (c.getScore() > maxscore) {
        assignedClass = c;
        maxscore = c.getScore();
      }
    }
    return assignedClass;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ClassificationResult<BytesRef>> getClasses(String text) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = assignClassNormalizedList(text);
    Collections.sort(assignedClasses);
    return assignedClasses;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ClassificationResult<BytesRef>> getClasses(String text, int max) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = assignClassNormalizedList(text);
    Collections.sort(assignedClasses);
    return assignedClasses.subList(0, max);
  }

  /**
   * Calculate probabilities for all classes for a given input text
   * @param inputDocument the input text as a {@code String}
   * @return a {@code List} of {@code ClassificationResult}, one for each existing class
   * @throws IOException if assigning probabilities fails
   */
  protected List<ClassificationResult<BytesRef>> assignClassNormalizedList(String inputDocument) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = new ArrayList<>();

    Terms classes = MultiFields.getTerms(leafReader, classFieldName);
    TermsEnum classesEnum = classes.iterator();
    BytesRef next;
    String[] tokenizedText = tokenize(inputDocument);
    int docsWithClassSize = countDocsWithClass();
    while ((next = classesEnum.next()) != null) {
      if (next.length > 0) {
        // We are passing the term to IndexSearcher so we need to make sure it will not change over time
        next = BytesRef.deepCopyOf(next);
        double clVal = calculateLogPrior(next, docsWithClassSize) + calculateLogLikelihood(tokenizedText, next, docsWithClassSize);
        assignedClasses.add(new ClassificationResult<>(next, clVal));
      }
    }

    // normalization; the values transforms to a 0-1 range
    ArrayList<ClassificationResult<BytesRef>> assignedClassesNorm = normClassificationResults(assignedClasses);

    return assignedClassesNorm;
  }

  /**
   * count the number of documents in the index having at least a value for the 'class' field
   *
   * @return the no. of documents having a value for the 'class' field
   * @throws IOException if accessing to term vectors or search fails
   */
  protected int countDocsWithClass() throws IOException {
    int docCount = MultiFields.getTerms(this.leafReader, this.classFieldName).getDocCount();
    if (docCount == -1) { // in case codec doesn't support getDocCount
      TotalHitCountCollector classQueryCountCollector = new TotalHitCountCollector();
      BooleanQuery.Builder q = new BooleanQuery.Builder();
      q.add(new BooleanClause(new WildcardQuery(new Term(classFieldName, String.valueOf(WildcardQuery.WILDCARD_STRING))), BooleanClause.Occur.MUST));
      if (query != null) {
        q.add(query, BooleanClause.Occur.MUST);
      }
      indexSearcher.search(q.build(),
          classQueryCountCollector);
      docCount = classQueryCountCollector.getTotalHits();
    }
    return docCount;
  }

  /**
   * tokenize a <code>String</code> on this classifier's text fields and analyzer
   *
   * @param text the <code>String</code> representing an input text (to be classified)
   * @return a <code>String</code> array of the resulting tokens
   * @throws IOException if tokenization fails
   */
  protected String[] tokenize(String text) throws IOException {
    Collection<String> result = new LinkedList<>();
    for (String textFieldName : textFieldNames) {
      try (TokenStream tokenStream = analyzer.tokenStream(textFieldName, text)) {
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();
        while (tokenStream.incrementToken()) {
          result.add(charTermAttribute.toString());
        }
        tokenStream.end();
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private double calculateLogLikelihood(String[] tokenizedText, BytesRef c, int docsWithClass) throws IOException {
    // for each word
    double result = 0d;
    for (String word : tokenizedText) {
      // search with text:word AND class:c
      int hits = getWordFreqForClass(word,c);

      // num : count the no of times the word appears in documents of class c (+1)
      double num = hits + 1; // +1 is added because of add 1 smoothing

      // den : for the whole dictionary, count the no of times a word appears in documents of class c (+|V|)
      double den = getTextTermFreqForClass(c) + docsWithClass;

      // P(w|c) = num/den
      double wordProbability = num / den;
      result += Math.log(wordProbability);
    }

    // log(P(d|c)) = log(P(w1|c))+...+log(P(wn|c))
    return result;
  }

  /**
   * Returns the average number of unique terms times the number of docs belonging to the input class
   * @param c the class
   * @return the average number of unique terms
   * @throws IOException if a low level I/O problem happens
   */
  private double getTextTermFreqForClass(BytesRef c) throws IOException {
    double avgNumberOfUniqueTerms = 0;
    for (String textFieldName : textFieldNames) {
      Terms terms = MultiFields.getTerms(leafReader, textFieldName);
      long numPostings = terms.getSumDocFreq(); // number of term/doc pairs
      avgNumberOfUniqueTerms += numPostings / (double) terms.getDocCount(); // avg # of unique terms per doc
    }
    int docsWithC = leafReader.docFreq(new Term(classFieldName, c));
    return avgNumberOfUniqueTerms * docsWithC; // avg # of unique terms in text fields per doc * # docs with c
  }

  /**
   * Returns the number of documents of the input class ( from the whole index or from a subset)
   * that contains the word ( in a specific field or in all the fields if no one selected)
   * @param word the token produced by the analyzer
   * @param c the class
   * @return the number of documents of the input class
   * @throws IOException if a low level I/O problem happens
   */
  private int getWordFreqForClass(String word, BytesRef c) throws IOException {
    BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
    BooleanQuery.Builder subQuery = new BooleanQuery.Builder();
    for (String textFieldName : textFieldNames) {
      subQuery.add(new BooleanClause(new TermQuery(new Term(textFieldName, word)), BooleanClause.Occur.SHOULD));
    }
    booleanQuery.add(new BooleanClause(subQuery.build(), BooleanClause.Occur.MUST));
    booleanQuery.add(new BooleanClause(new TermQuery(new Term(classFieldName, c)), BooleanClause.Occur.MUST));
    if (query != null) {
      booleanQuery.add(query, BooleanClause.Occur.MUST);
    }
    TotalHitCountCollector totalHitCountCollector = new TotalHitCountCollector();
    indexSearcher.search(booleanQuery.build(), totalHitCountCollector);
    return totalHitCountCollector.getTotalHits();
  }

  private double calculateLogPrior(BytesRef currentClass, int docsWithClassSize) throws IOException {
    return Math.log((double) docCount(currentClass)) - Math.log(docsWithClassSize);
  }

  private int docCount(BytesRef countedClass) throws IOException {
    return leafReader.docFreq(new Term(classFieldName, countedClass));
  }

  /**
   * Normalize the classification results based on the max score available
   * @param assignedClasses the list of assigned classes
   * @return the normalized results
   */
  protected ArrayList<ClassificationResult<BytesRef>> normClassificationResults(List<ClassificationResult<BytesRef>> assignedClasses) {
    // normalization; the values transforms to a 0-1 range
    ArrayList<ClassificationResult<BytesRef>> returnList = new ArrayList<>();
    if (!assignedClasses.isEmpty()) {
      Collections.sort(assignedClasses);
      // this is a negative number closest to 0 = a
      double smax = assignedClasses.get(0).getScore();

      double sumLog = 0;
      // log(sum(exp(x_n-a)))
      for (ClassificationResult<BytesRef> cr : assignedClasses) {
        // getScore-smax <=0 (both negative, smax is the smallest abs()
        sumLog += Math.exp(cr.getScore() - smax);
      }
      // loga=a+log(sum(exp(x_n-a))) = log(sum(exp(x_n)))
      double loga = smax;
      loga += Math.log(sumLog);

      // 1/sum*x = exp(log(x))*1/sum = exp(log(x)-log(sum))
      for (ClassificationResult<BytesRef> cr : assignedClasses) {
        double scoreDiff = cr.getScore() - loga;
        returnList.add(new ClassificationResult<>(cr.getAssignedClass(), Math.exp(scoreDiff)));
      }
    }
    return returnList;
  }
}
