package org.apache.lucene.search.positions;
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
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;
import java.util.List;

public class TestConjunctionIntervalIterator extends LuceneTestCase {
  
  private static final void addDocs(RandomIndexWriter writer) throws CorruptIndexException, IOException {
    {
      Document doc = new Document();
      doc.add(newField(
          "field",
          "Pease porridge hot! Pease porridge cold! Pease porridge in the pot nine days old! Some like it hot, some"
              + " like it cold, Some like it in the pot nine days old! Pease porridge hot! Pease porridge cold!",
              TextField.TYPE_STORED));
      writer.addDocument(doc);
    }
    
    {
      Document doc = new Document();
      doc.add(newField(
          "field",
          "Pease porridge cold! Pease porridge hot! Pease porridge in the pot nine days old! Some like it cold, some"
              + " like it hot, Some like it in the pot nine days old! Pease porridge cold! Pease porridge hot!",
              TextField.TYPE_STORED));
      writer.addDocument(doc);
    }
  }
  public void testConjunctionPositionsBooleanQuery() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    addDocs(writer);
    
    IndexReader reader = writer.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);
    writer.close();
    BooleanQuery query = new BooleanQuery();
    query.add(new BooleanClause(new TermQuery(new Term("field", "porridge")), Occur.MUST));
    query.add(new BooleanClause(new TermQuery(new Term("field", "pease")), Occur.MUST));
    query.add(new BooleanClause(new TermQuery(new Term("field", "hot!")), Occur.MUST));
    {
      IntervalFilterQuery filter = new IntervalFilterQuery(query, new RangeIntervalIterator(0,3));
      TopDocs search = searcher.search(filter, 10);
      ScoreDoc[] scoreDocs = search.scoreDocs;
      assertEquals(1, search.totalHits);
      assertEquals(0, scoreDocs[0].doc);
    }
    {
      IntervalFilterQuery filter = new IntervalFilterQuery(query, new WithinIntervalIterator(3));
      TopDocs search = searcher.search(filter, 10);
      ScoreDoc[] scoreDocs = search.scoreDocs;
      assertEquals(2, search.totalHits);
      assertEquals(0, scoreDocs[0].doc);
      assertEquals(1, scoreDocs[1].doc);
    }
    
    reader.close();
    directory.close();
  }
  
  public void testConjuctionPositionIterator() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    addDocs(writer);
    
    IndexReader reader = writer.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);
    writer.close();
    BooleanQuery query = new BooleanQuery();
    query.add(new BooleanClause(new TermQuery(new Term("field", "porridge")), Occur.MUST));
    query.add(new BooleanClause(new TermQuery(new Term("field", "pease")), Occur.MUST));
    query.add(new BooleanClause(new TermQuery(new Term("field", "hot!")), Occur.MUST));
    Weight weight = query.createWeight(searcher);
    IndexReaderContext topReaderContext = searcher.getTopReaderContext();
    List<AtomicReaderContext> leaves = topReaderContext.leaves();
    assertEquals(1, leaves.size());
    for (AtomicReaderContext atomicReaderContext : leaves) {
      Scorer scorer = weight.scorer(atomicReaderContext, true, true, true, false, false, atomicReaderContext.reader().getLiveDocs());
      {
        int nextDoc = scorer.nextDoc();
        assertEquals(0, nextDoc);
        IntervalIterator positions = scorer.positions();
        assertEquals(0, positions.scorerAdvanced(nextDoc));
        Interval interval = null;
        int[] start = new int[] {0, 1, 2, 31, 32, 33};
        int[] end = new int[] {2, 3, 4, 33, 34, 35};
        // {start}term{end} - end is pos+1 
        // {0}Pease {1}porridge {2}hot!{0} Pease{1} porridge{2} cold! Pease porridge in the pot nine days old! Some like it hot, some"
        // like it cold, Some like it in the pot nine days old! {3}Pease {4}porridge {5}hot!{3} Pease{4} porridge{5} cold!",
        for (int j = 0; j < end.length; j++) {
          interval = positions.next();
          assertNotNull("" + j, interval);
          assertEquals(start[j], interval.begin);
          assertEquals(end[j], interval.end);
        }
        assertNull(positions.next());
      }
      {
        int nextDoc = scorer.nextDoc();
        assertEquals(1, nextDoc);
        IntervalIterator positions = scorer.positions();
        assertEquals(1, positions.scorerAdvanced(nextDoc));
        Interval interval = null;
        int[] start = new int[] {3, 4, 5, 34 };
        int[] end = new int[] {5, 6, 7, 36 };
        // {start}term{end} - end is pos+1
        // {0}Pease {1}porridge cold! {0}Pease {1}porridge {2}hot!{0} Pease{1} porridge{2} in the pot nine days old! Some like it cold, some
        // like it hot, Some like it in the pot nine days old! Pease porridge cold! {4}Pease porridge hot{4}!
        for (int j = 0; j < end.length; j++) {
          interval = positions.next();
          assertNotNull(interval);
          assertEquals(j + "", start[j], interval.begin);
          assertEquals(j+ "", end[j], interval.end);
        }
        assertNull(positions.next());
      }
    }
    reader.close();
    directory.close();
  }
}
