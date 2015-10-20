package org.apache.lucene.classification.document;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.SimpleNaiveBayesClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;

/**
 * A simplistic Lucene based NaiveBayes classifier, see {@code http://en.wikipedia.org/wiki/Naive_Bayes_classifier}
 *
 * @lucene.experimental
 */
public class SimpleNaiveBayesDocumentClassifier extends SimpleNaiveBayesClassifier implements DocumentClassifier<BytesRef> {
  /**
   * {@link org.apache.lucene.analysis.Analyzer} to be used for tokenizing document fields
   */
  protected Map<String, Analyzer> field2analyzer;

  /**
   * Creates a new NaiveBayes classifier.
   *
   * @param leafReader     the reader on the index to be used for classification
   * @param query          a {@link org.apache.lucene.search.Query} to eventually filter the docs used for training the classifier, or {@code null}
   *                       if all the indexed docs should be used
   * @param classFieldName the name of the field used as the output for the classifier NOTE: must not be havely analyzed
   *                       as the returned class will be a token indexed for this field
   * @param textFieldNames the name of the fields used as the inputs for the classifier, they can contain boosting indication e.g. title^10
   */
  public SimpleNaiveBayesDocumentClassifier(LeafReader leafReader, Query query, String classFieldName, Map<String, Analyzer> field2analyzer, String... textFieldNames) {
    super(leafReader, null, query, classFieldName, textFieldNames);
    this.field2analyzer = field2analyzer;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ClassificationResult<BytesRef> assignClass(Document document) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = assignNormClasses(document);
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
  public List<ClassificationResult<BytesRef>> getClasses(Document document) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = assignNormClasses(document);
    Collections.sort(assignedClasses);
    return assignedClasses;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ClassificationResult<BytesRef>> getClasses(Document document, int max) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = assignNormClasses(document);
    Collections.sort(assignedClasses);
    return assignedClasses.subList(0, max);
  }

  private List<ClassificationResult<BytesRef>> assignNormClasses(Document inputDocument) throws IOException {
    List<ClassificationResult<BytesRef>> assignedClasses = new ArrayList<>();
    Map<String, List<String[]>> fieldName2tokensArray = new LinkedHashMap<>();
    Map<String, Float> fieldName2boost = new LinkedHashMap<>();
    Terms classes = MultiFields.getTerms(leafReader, classFieldName);
    TermsEnum classesEnum = classes.iterator();
    BytesRef c;

    analyzeSeedDocument(inputDocument, fieldName2tokensArray, fieldName2boost);

    int docsWithClassSize = countDocsWithClass();
    while ((c = classesEnum.next()) != null) {
      double classScore = 0;
      for (String fieldName : textFieldNames) {
        List<String[]> tokensArrays = fieldName2tokensArray.get(fieldName);
        double fieldScore = 0;
        for (String[] fieldTokensArray : tokensArrays) {
          fieldScore += calculateLogPrior(c, docsWithClassSize) + calculateLogLikelihood(fieldTokensArray, fieldName, c, docsWithClassSize) * fieldName2boost.get(fieldName);
        }
        classScore += fieldScore;
      }
      assignedClasses.add(new ClassificationResult<>(BytesRef.deepCopyOf(c), classScore));
    }
    ArrayList<ClassificationResult<BytesRef>> assignedClassesNorm = normClassificationResults(assignedClasses);
    return assignedClassesNorm;
  }

  /**
   * This methods performs the analysis for the seed document and extract the boosts if present.
   * This is done only one time for the Seed Document.
   *
   * @param inputDocument         the seed unseen document
   * @param fieldName2tokensArray a map that associated to a field name the list of token arrays for all its values
   * @param fieldName2boost       a map that associates the boost to the field
   * @throws IOException If there is a low-level I/O error
   */
  private void analyzeSeedDocument(Document inputDocument, Map<String, List<String[]>> fieldName2tokensArray, Map<String, Float> fieldName2boost) throws IOException {
    for (int i = 0; i < textFieldNames.length; i++) {
      String fieldName = textFieldNames[i];
      float boost = 1;
      List<String[]> tokenizedValues = new LinkedList<>();
      if (fieldName.contains("^")) {
        String[] field2boost = fieldName.split("\\^");
        fieldName = field2boost[0];
        boost = Float.parseFloat(field2boost[1]);
      }
      Field[] fieldValues = inputDocument.getFields(fieldName);
      for (Field fieldValue : fieldValues) {
        TokenStream fieldTokens = fieldValue.tokenStream(field2analyzer.get(fieldName), null);
        String[] fieldTokensArray = getTokenArray(fieldTokens);
        tokenizedValues.add(fieldTokensArray);
      }
      fieldName2tokensArray.put(fieldName, tokenizedValues);
      fieldName2boost.put(fieldName, boost);
      textFieldNames[i] = fieldName;
    }
  }

  /**
   * Counts the number of documents in the index having at least a value for the 'class' field
   *
   * @return the no. of documents having a value for the 'class' field
   * @throws java.io.IOException If accessing to term vectors or search fails
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
   * Returns a token array from the {@link org.apache.lucene.analysis.TokenStream} in input
   *
   * @param tokenizedText the tokenized content of a field
   * @return a {@code String} array of the resulting tokens
   * @throws java.io.IOException If tokenization fails because there is a low-level I/O error
   */
  protected String[] getTokenArray(TokenStream tokenizedText) throws IOException {
    Collection<String> tokens = new LinkedList<>();
    CharTermAttribute charTermAttribute = tokenizedText.addAttribute(CharTermAttribute.class);
    tokenizedText.reset();
    while (tokenizedText.incrementToken()) {
      tokens.add(charTermAttribute.toString());
    }
    tokenizedText.end();
    tokenizedText.close();
    return tokens.toArray(new String[tokens.size()]);
  }

  /**
   * @param tokenizedText the tokenized content of a field
   * @param fieldName     the input field name
   * @param c             the class to calculate the score of
   * @param docsWithClass the total number of docs that have a class
   * @return a normalized score for the class
   * @throws IOException If there is a low-level I/O error
   */
  private double calculateLogLikelihood(String[] tokenizedText, String fieldName, BytesRef c, int docsWithClass) throws IOException {
    // for each word
    double result = 0d;
    for (String word : tokenizedText) {
      // search with text:word AND class:c
      int hits = getWordFreqForClass(word, fieldName, c);

      // num : count the no of times the word appears in documents of class c (+1)
      double num = hits + 1; // +1 is added because of add 1 smoothing

      // den : for the whole dictionary, count the no of times a word appears in documents of class c (+|V|)
      double den = getTextTermFreqForClass(c, fieldName) + docsWithClass;

      // P(w|c) = num/den
      double wordProbability = num / den;
      result += Math.log(wordProbability);
    }

    // log(P(d|c)) = log(P(w1|c))+...+log(P(wn|c))
    double normScore = result / (tokenizedText.length); // this is normalized because if not, long text fields will always be more important than short fields
    return normScore;
  }

  /**
   * Returns the average number of unique terms times the number of docs belonging to the input class
   *
   * @param c the class
   * @return the average number of unique terms
   * @throws java.io.IOException If there is a low-level I/O error
   */
  private double getTextTermFreqForClass(BytesRef c, String fieldName) throws IOException {
    double avgNumberOfUniqueTerms;
    Terms terms = MultiFields.getTerms(leafReader, fieldName);
    long numPostings = terms.getSumDocFreq(); // number of term/doc pairs
    avgNumberOfUniqueTerms = numPostings / (double) terms.getDocCount(); // avg # of unique terms per doc
    int docsWithC = leafReader.docFreq(new Term(classFieldName, c));
    return avgNumberOfUniqueTerms * docsWithC; // avg # of unique terms in text fields per doc * # docs with c
  }

  /**
   * Returns the number of documents of the input class ( from the whole index or from a subset)
   * that contains the word ( in a specific field or in all the fields if no one selected)
   *
   * @param word      the token produced by the analyzer
   * @param fieldName the field the word is coming from
   * @param c         the class
   * @return number of documents of the input class
   * @throws java.io.IOException If there is a low-level I/O error
   */
  private int getWordFreqForClass(String word, String fieldName, BytesRef c) throws IOException {
    BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
    BooleanQuery.Builder subQuery = new BooleanQuery.Builder();
    subQuery.add(new BooleanClause(new TermQuery(new Term(fieldName, word)), BooleanClause.Occur.SHOULD));
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
}