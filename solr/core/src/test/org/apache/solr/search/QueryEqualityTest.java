package org.apache.solr.search;
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

import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryUtils;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.SolrQueryResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * Sanity checks that queries (generated by the QParser and ValueSourceParser 
 * framework) are appropriately {@link Object#equals} and 
 * {@link Object#hashCode()} equivalent.  If you are adding a new default 
 * QParser or ValueSourceParser, you will most likely get a failure from
 * {@link #testParserCoverage} until you add a new test method to this class.
 *
 * @see ValueSourceParser#standardValueSourceParsers
 * @see QParserPlugin#standardPlugins
 * @see QueryUtils
 **/
public class QueryEqualityTest extends SolrTestCaseJ4 {

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml","schema15.xml");
  }

  /** @see #testParserCoverage */
  @AfterClass
  public static void afterClassParserCoverageTest() {

    if ( ! doAssertParserCoverage) return;
    for (String name : QParserPlugin.standardPlugins.keySet()) {
      assertTrue("testParserCoverage was run w/o any other method explicitly testing qparser: " + name, qParsersTested.contains(name));
    }

    for (final String name : ValueSourceParser.standardValueSourceParsers.keySet()) {
      assertTrue("testParserCoverage was run w/o any other method explicitly testing val parser: " + name, valParsersTested.contains(name));
    }

  }

  /** @see #testParserCoverage */
  private static boolean doAssertParserCoverage = false;
  /** @see #testParserCoverage */
  private static final Set<String> qParsersTested = new HashSet<>();
  /** @see #testParserCoverage */
  private static final Set<String> valParsersTested = new HashSet<>();


  public void testDateMathParsingEquality() throws Exception {
    // regardless of parser, these should all be equivalent queries
    assertQueryEquals
      (null
       ,"{!lucene}f_tdt:2013-09-11T00\\:00\\:00Z"
       ,"{!lucene}f_tdt:2013-03-08T00\\:46\\:15Z/DAY+6MONTHS+3DAYS"
       ,"{!lucene}f_tdt:\"2013-03-08T00:46:15Z/DAY+6MONTHS+3DAYS\""
       ,"{!field f=f_tdt}2013-03-08T00:46:15Z/DAY+6MONTHS+3DAYS"
       ,"{!field f=f_tdt}2013-09-11T00:00:00Z"
       ,"{!term f=f_tdt}2013-03-08T00:46:15Z/DAY+6MONTHS+3DAYS"
       ,"{!term f=f_tdt}2013-09-11T00:00:00Z"
       );

  }
  public void testQueryLucene() throws Exception {
    assertQueryEquals("lucene", "{!lucene}apache solr", 
                      "apache  solr", "apache solr ");
    assertQueryEquals("lucene", "+apache +solr", "apache AND solr", 
                      " +apache +solr");
  }

  public void testQueryLucenePlusSort() throws Exception {
    assertQueryEquals("lucenePlusSort", 
                      "apache solr", "apache  solr", "apache solr ; score desc");
    assertQueryEquals("lucenePlusSort", 
                      "+apache +solr", "apache AND solr", " +apache +solr; score desc");
  }

  public void testQueryPrefix() throws Exception {
    SolrQueryRequest req = req("myField","foo_s");
    try {
      assertQueryEquals("prefix", req, 
                        "{!prefix f=$myField}asdf", 
                        "{!prefix f=foo_s}asdf");
    } finally {
      req.close();
    }
  }
  
  public void testQueryBoost() throws Exception {
    SolrQueryRequest req = req("df","foo_s","myBoost","sum(3,foo_i)");
    try {
      assertQueryEquals("boost", req, 
                        "{!boost b=$myBoost}asdf", 
                        "{!boost b=$myBoost v=asdf}", 
                        "{!boost b=sum(3,foo_i)}foo_s:asdf");
    } finally {
      req.close();
    }
  }

  public void testReRankQuery() throws Exception {
    SolrQueryRequest req = req("q", "*:*",
                               "rqq", "{!edismax}hello",
                               "rdocs", "20",
                               "rweight", "2",
                               "rows", "10",
                               "start", "0");
    try {
      assertQueryEquals("rerank", req,
          "{!rerank reRankQuery=$rqq reRankDocs=$rdocs reRankWeight=$rweight}",
          "{!rerank reRankQuery=$rqq reRankDocs=20 reRankWeight=2}");

    } finally {
      req.close();
    }


    req = req("qq", "*:*",
        "rqq", "{!edismax}hello",
        "rdocs", "20",
        "rweight", "2",
        "rows", "100",
        "start", "50");
    try {
      assertQueryEquals("rerank", req,
          "{!rerank mainQuery=$qq reRankQuery=$rqq reRankDocs=$rdocs reRankWeight=$rweight}",
          "{!rerank mainQuery=$qq reRankQuery=$rqq reRankDocs=20 reRankWeight=2}");

    } finally {
      req.close();
    }
  }

  public void testExportQuery() throws Exception {
    SolrQueryRequest req = req("q", "*:*");
    try {
      assertQueryEquals("xport", req, "{!xport}");
    } finally {
      req.close();
    }
  }

  public void testQuerySwitch() throws Exception {
    SolrQueryRequest req = req("myXXX", "XXX", 
                               "myField", "foo_s",
                               "myQ", "{!prefix f=$myField}asdf");
    try {
      assertQueryEquals("switch", req, 
                        "{!switch case.foo=XXX case.bar=zzz case.yak=qqq}foo",
                        "{!switch case.foo=qqq case.bar=XXX case.yak=zzz} bar ",
                        "{!switch case.foo=qqq case.bar=XXX case.yak=zzz v='  bar '}",
                        "{!switch default=XXX case.foo=qqq case.bar=zzz}asdf",
                        "{!switch default=$myXXX case.foo=qqq case.bar=zzz}asdf",
                        "{!switch case=XXX case.bar=zzz case.yak=qqq v=''}",
                        "{!switch case.bar=zzz case=XXX case.yak=qqq v=''}",
                        "{!switch case=XXX case.bar=zzz case.yak=qqq}",
                        "{!switch case=XXX case.bar=zzz case.yak=qqq}   ",
                        "{!switch case=$myXXX case.bar=zzz case.yak=qqq}   ");

      assertQueryEquals("switch", req, 
                        "{!switch case.foo=$myQ case.bar=zzz case.yak=qqq}foo",
                        "{!query v=$myQ}");
    } finally {
      req.close();
    }
  }

  public void testQueryDismax() throws Exception {
    for (final String type : new String[]{"dismax","edismax"}) {
      assertQueryEquals(type, "{!"+type+"}apache solr",
                        "apache solr", "apache  solr", "apache solr ");
      assertQueryEquals(type, "+apache +solr", "apache AND solr", 
                        " +apache +solr");
    }
  }
  public void testField() throws Exception {
    SolrQueryRequest req = req("myField","foo_s");
    try {
      assertQueryEquals("field", req, 
                        "{!field f=$myField}asdf", 
                        "{!field f=$myField v=asdf}", 
                        "{!field f=foo_s}asdf");
    } finally {
      req.close();
    }
  }

  public void testQueryRaw() throws Exception {
    SolrQueryRequest req = req("myField","foo_s");
    try {
      assertQueryEquals("raw", req, 
                        "{!raw f=$myField}asdf", 
                        "{!raw f=$myField v=asdf}", 
                        "{!raw f=foo_s}asdf");
    } finally {
      req.close();
    }
  }

  public void testQueryTerm() throws Exception {
    SolrQueryRequest req = req("myField","foo_s");
    try {
      assertQueryEquals("term", req, 
                        "{!term f=$myField}asdf", 
                        "{!term f=$myField v=asdf}", 
                        "{!term f=foo_s}asdf");
    } finally {
      req.close();
    }
  }

  public void testQueryCollapse() throws Exception {
    SolrQueryRequest req = req("myField","foo_s",
                               "g_sort","foo_s1 asc, foo_i desc");

    try {
      assertQueryEquals("collapse", req,
          "{!collapse field=$myField}");

      assertQueryEquals("collapse", req,
          "{!collapse field=$myField max=a}");

      assertQueryEquals("collapse", req,
                        "{!collapse field=$myField min=a}",
                        "{!collapse field=$myField min=a nullPolicy=ignore}");
      
      assertQueryEquals("collapse", req,
                        "{!collapse field=$myField sort=$g_sort}",
                        "{!collapse field=$myField sort='foo_s1 asc, foo_i desc'}",
                        "{!collapse field=$myField sort=$g_sort nullPolicy=ignore}");

      assertQueryEquals("collapse", req,
          "{!collapse field=$myField max=a nullPolicy=expand}");

      //Add boosted documents to the request context.
      Map context = req.getContext();
      Set boosted = new HashSet();
      boosted.add("doc1");
      boosted.add("doc2");
      context.put("BOOSTED", boosted);

      assertQueryEquals("collapse", req,
          "{!collapse field=$myField min=a}",
          "{!collapse field=$myField min=a nullPolicy=ignore}");


    } finally {
      req.close();
    }
  }


  public void testHash() throws Exception {
    SolrQueryRequest req = req("partitionKeys","foo_s");

    try {
      assertQueryEquals("hash", req,
          "{!hash workers=3 worker=0}");

    } finally {
      req.close();
    }
  }

  public void testQueryNested() throws Exception {
    SolrQueryRequest req = req("df", "foo_s");
    try {
      assertQueryEquals("query", req, 
                        "{!query defType=lucene}asdf", 
                        "{!query v='foo_s:asdf'}", 
                        "{!query}foo_s:asdf", 
                        "{!query}asdf");
    } finally {
      req.close();
    }
  }

  public void testQueryFunc() throws Exception {
    // more involved tests of specific functions in other methods
    SolrQueryRequest req = req("myVar", "5",
                               "myField","foo_i",
                               "myInner","product(4,foo_i)");
    try {
      assertQueryEquals("func", req, 
                        "{!func}sum(4,5)",
                        "{!func}sum(4,$myVar)",
                        "sum(4,5)");
      assertQueryEquals("func", req, 
                        "{!func}sum(1,2,3,4,5)",
                        "{!func}sum(1,2,3,4,$myVar)",
                        "sum(1,2,3,4,5)");
      assertQueryEquals("func", req,
                        "{!func}sum(4,$myInner)",
                        "{!func}sum(4,product(4,foo_i))",
                        "{!func}sum(4,product(4,$myField))",
                        "{!func}sum(4,product(4,field(foo_i)))");
    } finally {
      req.close();
    }
  }

  public void testQueryFrange() throws Exception {
    SolrQueryRequest req = req("myVar", "5",
                               "low","0.2",
                               "high", "20.4",
                               "myField","foo_i",
                               "myInner","product(4,foo_i)");
    try {
      assertQueryEquals("frange", req, 
                        "{!frange l=0.2 h=20.4}sum(4,5)",
                        "{!frange l=$low h=$high}sum(4,$myVar)");
    } finally {
      req.close();
    }
  }

  public void testQueryGeofilt() throws Exception {
    checkQuerySpatial("geofilt");
  }
  public void testQueryBbox() throws Exception {
    checkQuerySpatial("bbox");
  }

  private void checkQuerySpatial(final String type) throws Exception {
    SolrQueryRequest req = req("myVar", "5",
                               "d","109",
                               "pt","10.312,-20.556",
                               "sfield","store");
    try {
      assertQueryEquals(type, req, 
                        "{!"+type+" d=109}",
                        "{!"+type+" sfield=$sfield}",
                        "{!"+type+" sfield=store d=109}",
                        "{!"+type+" sfield=store d=$d pt=$pt}",
                        "{!"+type+" sfield=store d=$d pt=10.312,-20.556}",
                        "{!"+type+"}");
      // diff SpatialQueryable FieldTypes matter for determining final query
      assertQueryEquals(type, req, 
                        "{!"+type+" sfield=point_hash}",
                        "{!"+type+" sfield=point_hash d=109}",
                        "{!"+type+" sfield=point_hash d=$d pt=$pt}",
                        "{!"+type+" sfield=point_hash d=$d pt=10.312,-20.556}");
      assertQueryEquals(type, req, 
                        "{!"+type+" sfield=point}",
                        "{!"+type+" sfield=point d=109}",
                        "{!"+type+" sfield=point d=$d pt=$pt}",
                        "{!"+type+" sfield=point d=$d pt=10.312,-20.556}");
    } finally {
      req.close();
    }
  }
  public void testQueryJoin() throws Exception {
    SolrQueryRequest req = req("myVar", "5",
                               "df","text",
                               "ff","foo_s",
                               "tt", "bar_s");

    try {
      assertQueryEquals("join", req, 
                        "{!join from=foo_s to=bar_s}asdf",
                        "{!join from=$ff to=$tt}asdf",
                        "{!join from=$ff to='bar_s'}text:asdf");
    } finally {
      req.close();
    }
  }

  public void testQueryScoreJoin() throws Exception {
    SolrQueryRequest req = req("myVar", "5",
        "df", "text",
        "ff", "foo_s",
        "tt", "bar_s",
        "scoreavg","avg");

    try {
      assertQueryEquals("join", req,
          "{!join from=foo_s to=bar_s score=avg}asdf",
          "{!join from=$ff to=$tt score=Avg}asdf",
          "{!join from=$ff to='bar_s' score=$scoreavg}text:asdf");
    } finally {
      req.close();
    }
  }

  public void testTerms() throws Exception {
    assertQueryEquals("terms", "{!terms f=foo_i}10,20,30,-10,-20,-30", "{!terms f=foo_i}10,20,30,-10,-20,-30");
  }

  public void testBlockJoin() throws Exception {
    assertQueryEquals("parent", "{!parent which=foo_s:parent}dude",
        "{!parent which=foo_s:parent}dude");
    assertQueryEquals("child", "{!child of=foo_s:parent}dude",
        "{!child of=foo_s:parent}dude");
  }

  public void testGraphQuery() throws Exception {
    SolrQueryRequest req = req("from", "node_s",
        "to","edge_s",
        "traversalFilter","foo",
        "returnOnlyLeaf","true",
        "returnRoot","false",
        "maxDepth","2",
        "useAutn","false"
        );
    // make sure all param subsitution works for all args to graph query.
    assertQueryEquals("graph", req, 
        "{!graph from=node_s to=edge_s}*:*",
        "{!graph from=$from to=$to}*:*");
    
    assertQueryEquals("graph", req,
        "{!graph from=node_s to=edge_s traversalFilter=foo}*:*",
        "{!graph from=$from to=$to traversalFilter=$traversalFilter}*:*");
    
    assertQueryEquals("graph", req,
        "{!graph from=node_s to=edge_s traversalFilter=foo returnOnlyLeaf=true}*:*",
        "{!graph from=$from to=$to traversalFilter=$traversalFilter returnOnlyLeaf=$returnOnlyLeaf}*:*");
    
    assertQueryEquals("graph", req,
        "{!graph from=node_s to=edge_s traversalFilter=foo returnOnlyLeaf=true returnRoot=false}*:*",
        "{!graph from=$from to=$to traversalFilter=$traversalFilter returnOnlyLeaf=$returnOnlyLeaf returnRoot=$returnRoot}*:*");
    
    assertQueryEquals("graph", req,
        "{!graph from=node_s to=edge_s traversalFilter=foo returnOnlyLeaf=true returnRoot=false maxDepth=2}*:*",
        "{!graph from=$from to=$to traversalFilter=$traversalFilter returnOnlyLeaf=$returnOnlyLeaf returnRoot=$returnRoot maxDepth=$maxDepth}*:*");
    
    assertQueryEquals("graph", req,
        "{!graph from=node_s to=edge_s traversalFilter=foo returnOnlyLeaf=true returnRoot=false maxDepth=2 useAutn=false}*:*",
        "{!graph from=$from to=$to traversalFilter=$traversalFilter returnOnlyLeaf=$returnOnlyLeaf returnRoot=$returnRoot maxDepth=$maxDepth useAutn=$useAutn}*:*");
    
  }

  public void testQuerySurround() throws Exception {
    assertQueryEquals("surround", "{!surround}and(apache,solr)", 
                      "and(apache,solr)", "apache AND solr");
  }

  public void testQueryComplexPhrase() throws Exception {
    assertQueryEquals("complexphrase", "{!complexphrase df=text}\"jo* smith\"",
        "text:\"jo* smith\"");
    assertQueryEquals("complexphrase", "{!complexphrase df=title}\"jo* smith\"",
        "title:\"jo* smith\"");
  }

  public void testFuncTestfunc() throws Exception {
    assertFuncEquals("testfunc(foo_i)","testfunc(field(foo_i))"); 
    assertFuncEquals("testfunc(23)"); 
    assertFuncEquals("testfunc(sum(23,foo_i))",
                     "testfunc(sum(23,field(foo_i)))"); 
  }
  public void testFuncOrd() throws Exception {
    assertFuncEquals("ord(foo_s)","ord(foo_s    )"); 
  }

  public void testFuncLiteral() throws Exception {
    SolrQueryRequest req = req("someVar","a string");
    try {
      assertFuncEquals(req, 
                       "literal('a string')","literal(\"a string\")",
                       "literal($someVar)"); 
    } finally {
      req.close();
    }
  }
  public void testFuncRord() throws Exception {
    assertFuncEquals("rord(foo_s)","rord(foo_s    )"); 
  }

  public void testFuncCscore() throws Exception {
    assertFuncEquals("cscore()", "cscore(  )");
  }

  public void testFuncTop() throws Exception {
    assertFuncEquals("top(sum(3,foo_i))");
  }
  public void testFuncLinear() throws Exception {
    SolrQueryRequest req = req("someVar","27");
    try {
      assertFuncEquals(req, 
                       "linear(foo_i,$someVar,42)",
                       "linear(foo_i,   27,   42)");
    } finally {
      req.close();
    }
  }
  public void testFuncRecip() throws Exception {
    SolrQueryRequest req = req("someVar","27");
    try {
      assertFuncEquals(req, 
                       "recip(foo_i,$someVar,42,   27   )",
                       "recip(foo_i,   27,   42,$someVar)");
    } finally {
      req.close();
    }
  }
  public void testFuncScale() throws Exception {
    SolrQueryRequest req = req("someVar","27");
    try {
      assertFuncEquals(req, 
                       "scale(field(foo_i),$someVar,42)",
                       "scale(foo_i, 27, 42)");
    } finally {
      req.close();
    }
  }
  public void testFuncDiv() throws Exception {
    assertFuncEquals("div(5,4)", "div(5, 4)");
    assertFuncEquals("div(foo_i,4)", "div(foo_i, 4)", 
                     "div(field('foo_i'), 4)");
    assertFuncEquals("div(foo_i,sub(4,field('bar_i')))", 
                     "div(field(foo_i), sub(4,bar_i))");

  }
  public void testFuncMod() throws Exception {
    assertFuncEquals("mod(5,4)", "mod(5, 4)");
    assertFuncEquals("mod(foo_i,4)", "mod(foo_i, 4)", 
                     "mod(field('foo_i'), 4)");
    assertFuncEquals("mod(foo_i,sub(4,field('bar_i')))", 
                     "mod(field(foo_i), sub(4,bar_i))");
  }
  public void testFuncMap() throws Exception {
    assertFuncEquals("map(field(foo_i), 0, 45, 100)",
                     "map(foo_i, 0.0, 45, 100)");
  }

  public void testFuncSum() throws Exception {
    assertFuncEquals("sum(5,4)", "add(5, 4)");
    assertFuncEquals("sum(5,4,3,2,1)", "add(5, 4, 3, 2, 1)");
    assertFuncEquals("sum(foo_i,4)", "sum(foo_i, 4)", 
                     "sum(field('foo_i'), 4)");
    assertFuncEquals("add(foo_i,sub(4,field('bar_i')))", 
                     "sum(field(foo_i), sub(4,bar_i))");

  }

  public void testFuncProduct() throws Exception {
    assertFuncEquals("product(5,4,3,2,1)", "mul(5, 4, 3, 2, 1)");
    assertFuncEquals("product(5,4)", "mul(5, 4)");
    assertFuncEquals("product(foo_i,4)", "product(foo_i, 4)", 
                     "product(field('foo_i'), 4)");
    assertFuncEquals("mul(foo_i,sub(4,field('bar_i')))", 
                     "product(field(foo_i), sub(4,bar_i))");

  }
  public void testFuncSub() throws Exception {
    assertFuncEquals("sub(5,4)", "sub(5, 4)");
    assertFuncEquals("sub(foo_i,4)", "sub(foo_i, 4)");
    assertFuncEquals("sub(foo_i,sum(4,bar_i))", "sub(foo_i, sum(4,bar_i))");
  }
  public void testFuncVector() throws Exception {
    assertFuncEquals("vector(5,4, field(foo_i))", "vector(5, 4, foo_i)");
    assertFuncEquals("vector(foo_i,4)", "vector(foo_i, 4)");
    assertFuncEquals("vector(foo_i,sum(4,bar_i))", "vector(foo_i, sum(4,bar_i))");
  }
  public void testFuncQuery() throws Exception {
    SolrQueryRequest req = req("myQ","asdf");
    try {
      assertFuncEquals(req,
                       "query($myQ)",
                       "query($myQ,0)",
                       "query({!lucene v=$myQ},0)");
    } finally {
      req.close();
    }
  }
  public void testFuncBoost() throws Exception {
    SolrQueryRequest req = req("myQ","asdf");
    try {
      assertFuncEquals(req,
                       "boost($myQ,sum(4,5))",
                       "boost({!lucene v=$myQ},sum(4,5))");
    } finally {
      req.close();
    }
  }
  public void testFuncJoindf() throws Exception {
    assertFuncEquals("joindf(foo,bar)");
  }

  public void testFuncGeodist() throws Exception {
    SolrQueryRequest req = req("pt","10.312,-20.556",
                               "sfield","store");
    try {
      assertFuncEquals(req, 
                       "geodist()",
                       "geodist($sfield,$pt)",
                       "geodist(store,$pt)",
                       "geodist(field(store),$pt)",
                       "geodist(store,10.312,-20.556)");
    } finally {
      req.close();
    }
  }

  public void testFuncHsin() throws Exception {
    assertFuncEquals("hsin(45,true,0,0,45,45)");
  }
  public void testFuncGhhsin() throws Exception {
    assertFuncEquals("ghhsin(45,point_hash,'asdf')",
                     "ghhsin(45,field(point_hash),'asdf')");
  }
  public void testFuncGeohash() throws Exception {
    assertFuncEquals("geohash(45,99)");
  }
  public void testFuncDist() throws Exception {
    assertFuncEquals("dist(2,45,99,101,111)",
                     "dist(2,vector(45,99),vector(101,111))");
  }
  public void testFuncSqedist() throws Exception {
    assertFuncEquals("sqedist(45,99,101,111)",
                     "sqedist(vector(45,99),vector(101,111))");
  }
  public void testFuncMin() throws Exception {
    assertFuncEquals("min(5,4,3,2,1)", "min(5, 4, 3, 2, 1)");
    assertFuncEquals("min(foo_i,4)", "min(field('foo_i'), 4)");
    assertFuncEquals("min(foo_i,sub(4,field('bar_i')))", 
                     "min(field(foo_i), sub(4,bar_i))");
  }
  public void testFuncMax() throws Exception {
    assertFuncEquals("max(5,4,3,2,1)", "max(5, 4, 3, 2, 1)");
    assertFuncEquals("max(foo_i,4)", "max(field('foo_i'), 4)");
    assertFuncEquals("max(foo_i,sub(4,field('bar_i')))", 
                     "max(field(foo_i), sub(4,bar_i))");
  }

  public void testFuncMs() throws Exception {
    // Note ms() takes in field name, not field(...)
    assertFuncEquals("ms()", "ms(NOW)");
    assertFuncEquals("ms(2000-01-01T00:00:00Z)",
                     "ms('2000-01-01T00:00:00Z')");
    assertFuncEquals("ms(myDateField_dt)",
                     "ms('myDateField_dt')");
    assertFuncEquals("ms(2000-01-01T00:00:00Z,myDateField_dt)",
                     "ms('2000-01-01T00:00:00Z','myDateField_dt')");
    assertFuncEquals("ms(myDateField_dt, NOW)",
                     "ms('myDateField_dt', NOW)");
  }
  public void testFuncMathConsts() throws Exception {
    assertFuncEquals("pi()");
    assertFuncEquals("e()");
  }
  public void testFuncTerms() throws Exception {
    SolrQueryRequest req = req("myField","field_t","myTerm","my term");
    try {
      for (final String type : new String[]{"docfreq","termfreq",
                                            "totaltermfreq","ttf",
                                            "idf","tf"}) {
        // NOTE: these functions takes a field *name* not a field(..) source
        assertFuncEquals(req,
                         type + "('field_t','my term')",
                         type + "(field_t,'my term')",
                         type + "(field_t,$myTerm)",
                         type + "(field_t,$myTerm)",
                         type + "($myField,$myTerm)");
      }

      // ttf is an alias for totaltermfreq
      assertFuncEquals(req, 
                       "ttf(field_t,'my term')", "ttf('field_t','my term')", 
                       "totaltermfreq(field_t,'my term')");

    } finally {
      req.close();
    }
  }
  public void testFuncSttf() throws Exception {
    // sttf is an alias for sumtotaltermfreq
    assertFuncEquals("sttf(foo_t)", "sttf('foo_t')",
                     "sumtotaltermfreq(foo_t)", "sumtotaltermfreq('foo_t')");
    assertFuncEquals("sumtotaltermfreq('foo_t')");
  }
  public void testFuncNorm() throws Exception {
    assertFuncEquals("norm(foo_t)","norm('foo_t')");
  }
  public void testFuncMaxdoc() throws Exception {
    assertFuncEquals("maxdoc()");
  }
  public void testFuncNumdocs() throws Exception {
    assertFuncEquals("numdocs()");
  }

  public void testFuncBools() throws Exception {
    SolrQueryRequest req = req("myTrue","true","myFalse","false");
    try {
      assertFuncEquals(req, "true","$myTrue");
      assertFuncEquals(req, "false","$myFalse");
    } finally {
      req.close();
    }
  }

  public void testFuncExists() throws Exception {
    SolrQueryRequest req = req("myField","field_t","myQ","asdf");
    try {
      assertFuncEquals(req, 
                       "exists(field_t)",
                       "exists($myField)",
                       "exists(field('field_t'))",
                       "exists(field($myField))");
      assertFuncEquals(req, 
                       "exists(query($myQ))",
                       "exists(query({!lucene v=$myQ}))");
    } finally {
      req.close();
    }
  }

  public void testFuncNot() throws Exception {
    SolrQueryRequest req = req("myField","field_b", "myTrue","true");
    try {
      assertFuncEquals(req, "not(true)", "not($myTrue)"); 
      assertFuncEquals(req, "not(not(true))", "not(not($myTrue))"); 
      assertFuncEquals(req, 
                       "not(field_b)",
                       "not($myField)",
                       "not(field('field_b'))",
                       "not(field($myField))");
      assertFuncEquals(req, 
                       "not(exists(field_b))",
                       "not(exists($myField))",
                       "not(exists(field('field_b')))",
                       "not(exists(field($myField)))");
      
    } finally {
      req.close();
    }
  }
  public void testFuncDoubleValueBools() throws Exception {
    SolrQueryRequest req = req("myField","field_b","myTrue","true");
    try {
      for (final String type : new String[]{"and","or","xor"}) {
        assertFuncEquals(req,
                         type + "(field_b,true)",
                         type + "(field_b,$myTrue)",
                         type + "(field('field_b'),true)",
                         type + "(field($myField),$myTrue)",
                         type + "($myField,$myTrue)");
      }
    } finally {
      req.close();
    }
  }

  public void testFuncIf() throws Exception {
    SolrQueryRequest req = req("myBoolField","foo_b",
                               "myIntField","bar_i",
                               "myTrue","true");
    try {
      assertFuncEquals(req, 
                       "if(foo_b,bar_i,25)",
                       "if($myBoolField,bar_i,25)",
                       "if(field('foo_b'),$myIntField,25)",
                       "if(field($myBoolField),field('bar_i'),25)");
      assertFuncEquals(req, 
                       "if(true,37,field($myIntField))",
                       "if($myTrue,37,$myIntField)");
    } finally {
      req.close();
    }
  }

  public void testFuncDef() throws Exception {
    SolrQueryRequest req = req("myField","bar_f");

    try {
      assertFuncEquals(req, 
                       "def(bar_f,25)",
                       "def($myField,25)",
                       "def(field('bar_f'),25)");
      assertFuncEquals(req, 
                       "def(ceil(bar_f),25)",
                       "def(ceil($myField),25)",
                       "def(ceil(field('bar_f')),25)");
    } finally {
      req.close();
    }
  }

  public void testFuncSingleValueMathFuncs() throws Exception {
    SolrQueryRequest req = req("myVal","45", "myField","foo_i");
    for (final String func : new String[] {"abs","rad","deg","sqrt","cbrt",
                                           "log","ln","exp","sin","cos","tan",
                                           "asin","acos","atan",
                                           "sinh","cosh","tanh",
                                           "ceil","floor","rint"}) {
      try {
        assertFuncEquals(req,
                         func + "(field(foo_i))", func + "(foo_i)", 
                         func + "($myField)");
        assertFuncEquals(req, func + "(45)", func+ "($myVal)");
      } finally {
        req.close();
      }
    }
  }

  public void testFuncDoubleValueMathFuncs() throws Exception {
    SolrQueryRequest req = req("myVal","45", "myOtherVal", "27",
                               "myField","foo_i");
    for (final String func : new String[] {"pow","hypot","atan2"}) {
      try {
        assertFuncEquals(req,
                         func + "(field(foo_i),$myVal)", func+"(foo_i,$myVal)", 
                         func + "($myField,45)");
        assertFuncEquals(req, 
                         func+"(45,$myOtherVal)", func+"($myVal,27)",
                         func+"($myVal,$myOtherVal)");
                         
      } finally {
        req.close();
      }
    }
  }

  public void testFuncStrdist() throws Exception {
    SolrQueryRequest req = req("myVal","zot", "myOtherVal", "yak",
                               "myField","foo_s1");
    try {
      assertFuncEquals(req,
                       "strdist(\"zot\",literal('yak'),edit)", 
                       "strdist(literal(\"zot\"),'yak',   edit  )", 
                       "strdist(literal($myVal),literal($myOtherVal),edit)");
      assertFuncEquals(req,
                       "strdist(\"zot\",literal($myOtherVal),ngram)", 
                       "strdist(\"zot\",'yak', ngram, 2)");
      assertFuncEquals(req,
                       "strdist(field('foo_s1'),literal($myOtherVal),jw)", 
                       "strdist(field($myField),\"yak\",jw)", 
                       "strdist($myField,'yak', jw)");
    } finally {
      req.close();
    }
  }
  public void testFuncField() throws Exception {
    assertFuncEquals("field(\"foo_i\")", 
                     "field('foo_i\')", 
                     "foo_i");
    
    // simple VS of single valued field should be same as asking for min/max on that field
    assertFuncEquals("field(\"foo_i\")", 
                     "field('foo_i',min)", 
                     "field(foo_i,'min')", 
                     "field('foo_i',max)", 
                     "field(foo_i,'max')", 
                     "foo_i");

    // multivalued field with selector
    String multif = "multi_int_with_docvals";
    SolrQueryRequest req = req("my_field", multif);
    // this test is only viable if it's a multivalued field, sanity check the schema
    assertTrue(multif + " is no longer multivalued, who broke this schema?",
               req.getSchema().getField(multif).multiValued());
    assertFuncEquals(req,
                     "field($my_field,'MIN')", 
                     "field('"+multif+"',min)");
    assertFuncEquals(req,
                     "field($my_field,'max')", 
                     "field('"+multif+"',Max)"); 
    
  }
  public void testFuncCurrency() throws Exception {
    assertFuncEquals("currency(\"amount\")", 
                     "currency('amount\')",
                     "currency(amount)",
                     "currency(amount,USD)",
                     "currency('amount',USD)");
  }

  public void testTestFuncs() throws Exception {
    assertFuncEquals("sleep(1,5)", "sleep(1,5)");
    assertFuncEquals("threadid()", "threadid()");
  }
  
  // TODO: more tests
  public void testQueryMaxScore() throws Exception {
    assertQueryEquals("maxscore", "{!maxscore}A OR B OR C",
                      "A OR B OR C");
    assertQueryEquals("maxscore", "{!maxscore}A AND B",
                      "A AND B");
    assertQueryEquals("maxscore", "{!maxscore}apache -solr",
        "apache  -solr", "apache -solr ");
    assertQueryEquals("maxscore", "+apache +solr", "apache AND solr",
        "+apache +solr");
  }

  /**
   * this test does not assert anything itself, it simply toggles a static 
   * boolean informing an @AfterClass method to assert that every default 
   * qparser and valuesource parser configured was recorded by 
   * assertQueryEquals and assertFuncEquals.
   */
  public void testParserCoverage() {
    doAssertParserCoverage = true;
  }

  public void testQuerySimple() throws Exception {
    SolrQueryRequest req = req("myField","foo_s");
    try {
      assertQueryEquals("simple", req,
          "{!simple f=$myField}asdf",
          "{!simple f=$myField v=asdf}",
          "{!simple f=foo_s}asdf");
    } finally {
      req.close();
    }
  }

  public void testQueryMLT() throws Exception {
    assertU(adoc("id", "1", "lowerfilt", "sample data"));
    assertU(commit());
    try {
      assertQueryEquals("mlt", "{!mlt qf=lowerfilt}1",
          "{!mlt qf=lowerfilt v=1}");
    } finally {
      delQ("*:*");
      assertU(commit());
    }
  }


  /**
   * NOTE: defType is not only used to pick the parser, but also to record 
   * the parser being tested for coverage sanity checking
   * @see #testParserCoverage
   * @see #assertQueryEquals
   */
  protected void assertQueryEquals(final String defType,
                                   final String... inputs) throws Exception {
    SolrQueryRequest req = req();
    try {
      assertQueryEquals(defType, req, inputs);
    } finally {
      req.close();
    }
  }

  /**
   * NOTE: defType is not only used to pick the parser, but, if non-null it is 
   * also to record the parser being tested for coverage sanity checking
   *
   * @see QueryUtils#check
   * @see QueryUtils#checkEqual
   * @see #testParserCoverage
   */
  protected void assertQueryEquals(final String defType,
                                   final SolrQueryRequest req,
                                   final String... inputs) throws Exception {

    if (null != defType) qParsersTested.add(defType);

    final Query[] queries = new Query[inputs.length];

    try {
      SolrQueryResponse rsp = new SolrQueryResponse();
      SolrRequestInfo.setRequestInfo(new SolrRequestInfo(req,rsp));
      for (int i = 0; i < inputs.length; i++) {
        queries[i] = (QParser.getParser(inputs[i], defType, req).getQuery());
      }
    } finally {
      SolrRequestInfo.clearRequestInfo();
    }

    for (int i = 0; i < queries.length; i++) {
      QueryUtils.check(queries[i]);
      // yes starting j=0 is redundent, we're making sure every query 
      // is equal to itself, and that the quality checks work regardless 
      // of which caller/callee is used.
      for (int j = 0; j < queries.length; j++) {
        QueryUtils.checkEqual(queries[i], queries[j]);
      }
    }
  }

  /**
   * the function name for val parser coverage checking is extracted from
   * the first input
   * @see #assertQueryEquals
   * @see #testParserCoverage
   */
  protected void assertFuncEquals(final String... inputs) throws Exception {
    SolrQueryRequest req = req();
    try {
      assertFuncEquals(req, inputs);
    } finally {
      req.close();
    }
  }

  /**
   * the function name for val parser coverage checking is extracted from
   * the first input
   * @see #assertQueryEquals
   * @see #testParserCoverage
   */
  protected void assertFuncEquals(final SolrQueryRequest req,
                                  final String... inputs) throws Exception {
    // pull out the function name
    final String funcName = (new StrParser(inputs[0])).getId();
    valParsersTested.add(funcName);

    assertQueryEquals(FunctionQParserPlugin.NAME, req, inputs);
  }


  public void testAggs() throws Exception {
    assertFuncEquals("agg(avg(foo_i))", "agg(avg(foo_i))");
    assertFuncEquals("agg(avg(foo_i))", "agg_avg(foo_i)");
    assertFuncEquals("agg_min(foo_i)", "agg(min(foo_i))");
    assertFuncEquals("agg_max(foo_i)", "agg(max(foo_i))");

    assertFuncEquals("agg_avg(foo_i)", "agg_avg(foo_i)");
    assertFuncEquals("agg_sum(foo_i)", "agg_sum(foo_i)");
    assertFuncEquals("agg_count()", "agg_count()");
    assertFuncEquals("agg_unique(foo_i)", "agg_unique(foo_i)");
    assertFuncEquals("agg_hll(foo_i)", "agg_hll(foo_i)");
    assertFuncEquals("agg_sumsq(foo_i)", "agg_sumsq(foo_i)");
    assertFuncEquals("agg_percentile(foo_i,50)", "agg_percentile(foo_i,50)");
    // assertFuncEquals("agg_stdev(foo_i)", "agg_stdev(foo_i)");
    // assertFuncEquals("agg_multistat(foo_i)", "agg_multistat(foo_i)");
  }

}
