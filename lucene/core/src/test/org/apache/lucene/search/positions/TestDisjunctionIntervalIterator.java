package org.apache.lucene.search.positions;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Weight.PostingFeatures;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;
import java.util.List;

public class TestDisjunctionIntervalIterator extends LuceneTestCase {
  private static final void addDocs(RandomIndexWriter writer)
      throws CorruptIndexException, IOException {
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

  public void testDisjunctionPositionsBooleanQuery() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    addDocs(writer);

    IndexReader reader = writer.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);
    writer.close();
    BooleanQuery query = new BooleanQuery();
    query.add(new BooleanClause(new TermQuery(new Term("field", "porridge")),
        Occur.SHOULD));
    query.add(new BooleanClause(new TermQuery(new Term("field", "pease")),
        Occur.SHOULD));
    query.add(new BooleanClause(new TermQuery(new Term("field", "hot!")),
        Occur.SHOULD));
    {
      IntervalFilterQuery filter = new IntervalFilterQuery(query,
          new RangeIntervalFilter(0, 3));
      TopDocs search = searcher.search(filter, 10);
      ScoreDoc[] scoreDocs = search.scoreDocs;
      assertEquals(2, search.totalHits);
      assertEquals(0, scoreDocs[0].doc);
    }
    {
      IntervalFilterQuery filter = new IntervalFilterQuery(query,
          new WithinIntervalFilter(3));
      TopDocs search = searcher.search(filter, 10);
      ScoreDoc[] scoreDocs = search.scoreDocs;
      assertEquals(2, search.totalHits);
      assertEquals(0, scoreDocs[0].doc);
      assertEquals(1, scoreDocs[1].doc);
    }

    reader.close();
    directory.close();
  }

  public void testBasic() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    {
      Document doc = new Document();
      doc.add(newField("field", "the quick brown fox", TextField.TYPE_STORED));
      writer.addDocument(doc);
    }
    {
      Document doc = new Document();
      doc.add(newField("field", "the brown quick fox", TextField.TYPE_STORED));
      writer.addDocument(doc);
    }

    IndexReader reader = writer.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);
    writer.close();
    
    BooleanQuery query = new BooleanQuery();
    query.add(new BooleanClause(new TermQuery(new Term("field", "quick")),
        Occur.SHOULD));
    query.add(new BooleanClause(new TermQuery(new Term("field", "brown")),
        Occur.SHOULD));
    
    Weight weight = query.createWeight(searcher);
    IndexReaderContext topReaderContext = searcher.getTopReaderContext();
    List<AtomicReaderContext> leaves = topReaderContext.leaves();
    assertEquals(1, leaves.size());
    Scorer scorer = weight.scorer(leaves.get(0),
        true, true, PostingFeatures.POSITIONS, leaves.get(0).reader().getLiveDocs());
    IntervalIterator positions = scorer.intervals(false);
    for (int i = 0; i < 2; i++) {

      int nextDoc = scorer.nextDoc();
      assertEquals(i, nextDoc);
      assertEquals(i, positions.scorerAdvanced(nextDoc));
      Interval interval = positions.next();
      assertEquals(1, interval.begin);
      assertEquals(1, interval.end);

      interval = positions.next();
      assertEquals(2, interval.begin);
      assertEquals(2, interval.end);
      assertNull(positions.next());
    }
    reader.close();
    directory.close();
    
  }

  public void testDisjunctionPositionIterator() throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random())));
    addDocs(writer);

    IndexReader reader = writer.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);
    writer.close();
    BooleanQuery query1 = new BooleanQuery();
    query1.add(new BooleanClause(new TermQuery(new Term("field", "porridge")),
        Occur.MUST));
    query1.add(new BooleanClause(new TermQuery(new Term("field", "pease")),
        Occur.MUST));
    query1.add(new BooleanClause(new TermQuery(new Term("field", "hot!")),
        Occur.MUST));

    BooleanQuery query2 = new BooleanQuery();
    query2.add(new BooleanClause(new TermQuery(new Term("field", "pease")),
        Occur.MUST));
    query2.add(new BooleanClause(new TermQuery(new Term("field", "porridge")),
        Occur.MUST));
    query2.add(new BooleanClause(new TermQuery(new Term("field", "hot!")),
        Occur.MUST));

    BooleanQuery query = new BooleanQuery();
    query.add(new BooleanClause(query1, Occur.SHOULD));
    query.add(new BooleanClause(query2, Occur.SHOULD));
    Weight weight = query.createWeight(searcher);
    IndexReaderContext topReaderContext = searcher.getTopReaderContext();
    List<AtomicReaderContext> leaves = topReaderContext.leaves();
    assertEquals(1, leaves.size());
    for (AtomicReaderContext atomicReaderContext : leaves) {
      Scorer scorer = weight.scorer(atomicReaderContext,
          true, true, PostingFeatures.POSITIONS, atomicReaderContext.reader().getLiveDocs());
      {
        int nextDoc = scorer.nextDoc();
        assertEquals(0, nextDoc);
        IntervalIterator positions = scorer.intervals(false);
        assertEquals(0, positions.scorerAdvanced(nextDoc));
        Interval interval = null;
        int[] start = new int[] { 0, 1, 2, 31, 32, 33 };
        int[] end = new int[] { 2, 3, 4, 33, 34, 35 };
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
        IntervalIterator positions = scorer.intervals(false);
        assertEquals(1, positions.scorerAdvanced(nextDoc));
        Interval interval = null;
        int[] start = new int[] {  3, 4, 5, 34 };
        int[] end = new int[] { 5, 6, 7, 36 };
        // {start}term{end} - end is pos+1
        // {0}Pease {1}porridge cold! {0}Pease {1}porridge {2}hot!{0} Pease{1} porridge{2} in the pot nine days old! Some like it cold, some
        // like it hot, Some like it in the pot nine days old! Pease porridge cold! {4}Pease porridge hot{4}!
        for (int j = 0; j < end.length; j++) {
          interval = positions.next();
          assertNotNull(interval);
          assertEquals(j + "", start[j], interval.begin);
          assertEquals(j + "", end[j], interval.end);
        }
        assertNull(positions.next());
      }
    }
    reader.close();
    directory.close();
  }
}

