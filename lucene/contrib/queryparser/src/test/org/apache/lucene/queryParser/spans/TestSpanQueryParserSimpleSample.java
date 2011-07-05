package org.apache.lucene.queryParser.spans;

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

import javax.management.Query;

import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.nodes.OrQueryNode;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.core.parser.SyntaxParser;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryParser.standard.parser.StandardSyntaxParser;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.LuceneTestCase;

/**
 * This test case demonstrates how the new query parser can be used.<br/>
 * <br/>
 * 
 * It tests queries likes "term", "field:term" "term1 term2" "term1 OR term2",
 * which are all already supported by the current syntax parser (
 * {@link StandardSyntaxParser}).<br/>
 * <br/>
 * 
 * The goals is to create a new query parser that supports only the pair
 * "field:term" or a list of pairs separated or not by an OR operator, and from
 * this query generate {@link SpanQuery} objects instead of the regular
 * {@link Query} objects. Basically, every pair will be converted to a
 * {@link SpanTermQuery} object and if there are more than one pair they will be
 * grouped by an {@link OrQueryNode}.<br/>
 * <br/>
 * 
 * Another functionality that will be added is the ability to convert every
 * field defined in the query to an unique specific field.<br/>
 * <br/>
 * 
 * The query generation is divided in three different steps: parsing (syntax),
 * processing (semantic) and building.<br/>
 * <br/>
 * 
 * The parsing phase, as already mentioned will be performed by the current
 * query parser: {@link StandardSyntaxParser}.<br/>
 * <br/>
 * 
 * The processing phase will be performed by a processor pipeline which is
 * compound by 2 processors: {@link SpansValidatorQueryNodeProcessor} and
 * {@link UniqueFieldQueryNodeProcessor}.
 * 
 * <pre>
 * 
 *   {@link SpansValidatorQueryNodeProcessor}: as it's going to use the current 
 *   query parser to parse the syntax, it will support more features than we want,
 *   this processor basically validates the query node tree generated by the parser
 *   and just let got through the elements we want, all the other elements as 
 *   wildcards, range queries, etc...if found, an exception is thrown.
 *   
 *   {@link UniqueFieldQueryNodeProcessor}: this processor will take care of reading
 *   what is the &quot;unique field&quot; from the configuration and convert every field defined
 *   in every pair to this &quot;unique field&quot;. For that, a {@link SpansQueryConfigHandler} is
 *   used, which has the {@link UniqueFieldAttribute} defined in it.
 * </pre>
 * 
 * The building phase is performed by the {@link SpansQueryTreeBuilder}, which
 * basically contains a map that defines which builder will be used to generate
 * {@link SpanQuery} objects from {@link QueryNode} objects.<br/>
 * <br/>
 * 
 * @see TestSpanQueryParser for a more advanced example
 * 
 * @see SpansQueryConfigHandler
 * @see SpansQueryTreeBuilder
 * @see SpansValidatorQueryNodeProcessor
 * @see SpanOrQueryNodeBuilder
 * @see SpanTermQueryNodeBuilder
 * @see StandardSyntaxParser
 * @see UniqueFieldQueryNodeProcessor
 * @see UniqueFieldAttribute
 * 
 */
public class TestSpanQueryParserSimpleSample extends LuceneTestCase {

  public void testBasicDemo() throws Exception {
    SyntaxParser queryParser = new StandardSyntaxParser();

    // convert the CharSequence into a QueryNode tree
    QueryNode queryTree = queryParser.parse("body:text", null);

    // create a config handler with a attribute used in
    // UniqueFieldQueryNodeProcessor
    QueryConfigHandler spanQueryConfigHandler = new SpansQueryConfigHandler();
    spanQueryConfigHandler.set(SpansQueryConfigHandler.UNIQUE_FIELD, "index");

    // set up the processor pipeline with the ConfigHandler
    // and create the pipeline for this simple demo
    QueryNodeProcessorPipeline spanProcessorPipeline = new QueryNodeProcessorPipeline(
        spanQueryConfigHandler);
    // @see SpansValidatorQueryNodeProcessor
    spanProcessorPipeline.add(new SpansValidatorQueryNodeProcessor());
    // @see UniqueFieldQueryNodeProcessor
    spanProcessorPipeline.add(new UniqueFieldQueryNodeProcessor());

    // print to show out the QueryNode tree before being processed
    if (VERBOSE) System.out.println(queryTree);

    // Process the QueryTree using our new Processors
    queryTree = spanProcessorPipeline.process(queryTree);

    // print to show out the QueryNode tree after being processed
    if (VERBOSE) System.out.println(queryTree);

    // create a instance off the Builder
    SpansQueryTreeBuilder spansQueryTreeBuilder = new SpansQueryTreeBuilder();

    // convert QueryNode tree to span query Objects
    SpanQuery spanquery = spansQueryTreeBuilder.build(queryTree);

    assertTrue(spanquery instanceof SpanTermQuery);
    assertEquals(spanquery.toString(), "index:text");

  }

}
