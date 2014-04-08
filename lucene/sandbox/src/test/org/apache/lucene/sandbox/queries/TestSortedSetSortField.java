package org.apache.lucene.sandbox.queries;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/** Simple tests for SortedSetSortField */
public class TestSortedSetSortField extends LuceneTestCase {
  
  public void testForward() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(newStringField("value", "baz", Field.Store.NO));
    doc.add(newStringField("id", "2", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("value", "foo", Field.Store.NO));
    doc.add(newStringField("value", "bar", Field.Store.NO));
    doc.add(newStringField("id", "1", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.shutdown();
    
    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortedSetSortField("value", false));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits);
    // 'bar' comes before 'baz'
    assertEquals("1", searcher.doc(td.scoreDocs[0].doc).get("id"));
    assertEquals("2", searcher.doc(td.scoreDocs[1].doc).get("id"));
    
    ir.close();
    dir.close();
  }
  
  public void testReverse() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(newStringField("value", "foo", Field.Store.NO));
    doc.add(newStringField("value", "bar", Field.Store.NO));
    doc.add(newStringField("id", "1", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("value", "baz", Field.Store.NO));
    doc.add(newStringField("id", "2", Field.Store.YES));
    writer.addDocument(doc);

    IndexReader ir = writer.getReader();
    writer.shutdown();
    
    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortedSetSortField("value", true));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits);
    // 'bar' comes before 'baz'
    assertEquals("2", searcher.doc(td.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(td.scoreDocs[1].doc).get("id"));
    
    ir.close();
    dir.close();
  }
  
  public void testMissingFirst() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(newStringField("value", "baz", Field.Store.NO));
    doc.add(newStringField("id", "2", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("value", "foo", Field.Store.NO));
    doc.add(newStringField("value", "bar", Field.Store.NO));
    doc.add(newStringField("id", "1", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "3", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.shutdown();
    
    IndexSearcher searcher = newSearcher(ir);
    SortField sortField = new SortedSetSortField("value", false);
    sortField.setMissingValue(SortField.STRING_FIRST);
    Sort sort = new Sort(sortField);

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits);
    // 'bar' comes before 'baz'
    // null comes first
    assertEquals("3", searcher.doc(td.scoreDocs[0].doc).get("id"));
    assertEquals("1", searcher.doc(td.scoreDocs[1].doc).get("id"));
    assertEquals("2", searcher.doc(td.scoreDocs[2].doc).get("id"));
    
    ir.close();
    dir.close();
  }
  
  public void testMissingLast() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(newStringField("value", "baz", Field.Store.NO));
    doc.add(newStringField("id", "2", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("value", "foo", Field.Store.NO));
    doc.add(newStringField("value", "bar", Field.Store.NO));
    doc.add(newStringField("id", "1", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("id", "3", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.shutdown();
    
    IndexSearcher searcher = newSearcher(ir);
    SortField sortField = new SortedSetSortField("value", false);
    sortField.setMissingValue(SortField.STRING_LAST);
    Sort sort = new Sort(sortField);

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(3, td.totalHits);
    // 'bar' comes before 'baz'
    assertEquals("1", searcher.doc(td.scoreDocs[0].doc).get("id"));
    assertEquals("2", searcher.doc(td.scoreDocs[1].doc).get("id"));
    // null comes last
    assertEquals("3", searcher.doc(td.scoreDocs[2].doc).get("id"));
    
    ir.close();
    dir.close();
  }
  
  public void testSingleton() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(newStringField("value", "baz", Field.Store.NO));
    doc.add(newStringField("id", "2", Field.Store.YES));
    writer.addDocument(doc);
    doc = new Document();
    doc.add(newStringField("value", "bar", Field.Store.NO));
    doc.add(newStringField("id", "1", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader ir = writer.getReader();
    writer.shutdown();
    
    IndexSearcher searcher = newSearcher(ir);
    Sort sort = new Sort(new SortedSetSortField("value", false));

    TopDocs td = searcher.search(new MatchAllDocsQuery(), 10, sort);
    assertEquals(2, td.totalHits);
    // 'bar' comes before 'baz'
    assertEquals("1", searcher.doc(td.scoreDocs[0].doc).get("id"));
    assertEquals("2", searcher.doc(td.scoreDocs[1].doc).get("id"));
    
    ir.close();
    dir.close();
  }
  
  public void testEmptyIndex() throws Exception {
    IndexSearcher empty = newSearcher(new MultiReader());
    Query query = new TermQuery(new Term("contents", "foo"));
  
    Sort sort = new Sort();
    sort.setSort(new SortedSetSortField("sortedset", false));
    TopDocs td = empty.search(query, null, 10, sort, true, true);
    assertEquals(0, td.totalHits);
    
    // for an empty index, any selector should work
    for (SortedSetSortField.Selector v : SortedSetSortField.Selector.values()) {
      sort.setSort(new SortedSetSortField("sortedset", false, v));
      td = empty.search(query, null, 10, sort, true, true);
      assertEquals(0, td.totalHits);
    }
  }
  
  public void testEquals() throws Exception {
    SortField sf = new SortedSetSortField("a", false);
    assertFalse(sf.equals(null));
    
    assertEquals(sf, sf);
    
    SortField sf2 = new SortedSetSortField("a", false);
    assertEquals(sf, sf2);
    assertEquals(sf.hashCode(), sf2.hashCode());
    
    assertFalse(sf.equals(new SortedSetSortField("a", true)));
    assertFalse(sf.equals(new SortedSetSortField("b", false)));
    assertFalse(sf.equals(new SortedSetSortField("a", false, SortedSetSortField.Selector.MAX)));
    assertFalse(sf.equals("foo"));
  }
}
