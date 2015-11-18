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

package org.apache.solr.search;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.parser.QueryParser;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collection of static utilities useful for query parsing.
 *
 *
 */
public class QueryParsing {
  public static final String OP = "q.op";  // the SolrParam used to override the QueryParser "default operator"
  public static final String V = "v";      // value of this parameter
  public static final String F = "f";      // field that a query or command pertains to
  public static final String TYPE = "type";// parser for this query or command
  public static final String DEFTYPE = "defType"; // default parser for any direct subqueries
  public static final String LOCALPARAM_START = "{!";
  public static final char LOCALPARAM_END = '}';
  public static final String DOCID = "_docid_";
  public static final String SCORE = "score";

  // true if the value was specified by the "v" param (i.e. v=myval, or v=$param)
  public static final String VAL_EXPLICIT = "__VAL_EXPLICIT__";


  /**
   * Returns the "preferred" default operator for use by Query Parsers,
   * based on the settings in the IndexSchema which may be overridden using 
   * an optional String override value.
   *
   * @see IndexSchema#getQueryParserDefaultOperator()
   * @see #OP
   */
  public static QueryParser.Operator getQueryParserDefaultOperator(final IndexSchema sch,
                                                       final String override) {
    String val = override;
    if (null == val) val = sch.getQueryParserDefaultOperator();
    return "AND".equals(val) ? QueryParser.Operator.AND : QueryParser.Operator.OR;
  }

  /**
   * Returns the effective default field based on the 'df' param or
   * hardcoded schema default.  May be null if either exists specified.
   * @see org.apache.solr.common.params.CommonParams#DF
   * @see org.apache.solr.schema.IndexSchema#getDefaultSearchFieldName
   */
  public static String getDefaultField(final IndexSchema s, final String df) {
    return df != null ? df : s.getDefaultSearchFieldName();
  }

  // note to self: something needs to detect infinite recursion when parsing queries
  public static int parseLocalParams(String txt, int start, Map<String, String> target, SolrParams params) throws SyntaxError {
    return parseLocalParams(txt, start, target, params, LOCALPARAM_START, LOCALPARAM_END);
  }


  public static int parseLocalParams(String txt, int start, Map<String, String> target, SolrParams params, String startString, char endChar) throws SyntaxError {
    int off = start;
    if (!txt.startsWith(startString, off)) return start;
    StrParser p = new StrParser(txt, start, txt.length());
    p.pos += startString.length(); // skip over "{!"

    for (; ;) {
      /*
      if (p.pos>=txt.length()) {
        throw new SyntaxError("Missing '}' parsing local params '" + txt + '"');
      }
      */
      char ch = p.peek();
      if (ch == endChar) {
        return p.pos + 1;
      }

      String id = p.getId();
      if (id.length() == 0) {
        throw new SyntaxError("Expected ending character '" + endChar + "' parsing local params '" + txt + '"');

      }
      String val = null;

      ch = p.peek();
      if (ch != '=') {
        // single word... treat {!func} as type=func for easy lookup
        val = id;
        id = TYPE;
      } else {
        // saw equals, so read value
        p.pos++;
        ch = p.peek();
        boolean deref = false;
        if (ch == '$') {
          p.pos++;
          ch = p.peek();
          deref = true;  // dereference whatever value is read by treating it as a variable name
        }

        if (ch == '\"' || ch == '\'') {
          val = p.getQuotedString();
        } else {
          // read unquoted literal ended by whitespace or endChar (normally '}')
          // there is no escaping.
          int valStart = p.pos;
          for (; ;) {
            if (p.pos >= p.end) {
              throw new SyntaxError("Missing end to unquoted value starting at " + valStart + " str='" + txt + "'");
            }
            char c = p.val.charAt(p.pos);
            if (c == endChar || Character.isWhitespace(c)) {
              val = p.val.substring(valStart, p.pos);
              break;
            }
            p.pos++;
          }
        }

        if (deref) {  // dereference parameter
          if (params != null) {
            val = params.get(val);
          }
        }
      }
      if (target != null) target.put(id, val);
    }
  }


  public static String encodeLocalParamVal(String val) {
    int len = val.length();
    int i = 0;
    if (len > 0 && val.charAt(0) != '$') {
      for (;i<len; i++) {
        char ch = val.charAt(i);
        if (Character.isWhitespace(ch) || ch=='}') break;
      }
    }

    if (i>=len) return val;

    // We need to enclose in quotes... but now we need to escape
    StringBuilder sb = new StringBuilder(val.length() + 4);
    sb.append('\'');
    for (i=0; i<len; i++) {
      char ch = val.charAt(i);
      if (ch=='\'') {
        sb.append('\\');
      }
      sb.append(ch);
    }
    sb.append('\'');
    return sb.toString();
  }
  

  /**
   * "foo" returns null
   * "{!prefix f=myfield}yes" returns type="prefix",f="myfield",v="yes"
   * "{!prefix f=myfield v=$p}" returns type="prefix",f="myfield",v=params.get("p")
   */
  public static SolrParams getLocalParams(String txt, SolrParams params) throws SyntaxError {
    if (txt == null || !txt.startsWith(LOCALPARAM_START)) {
      return null;
    }
    Map<String, String> localParams = new HashMap<>();
    int start = QueryParsing.parseLocalParams(txt, 0, localParams, params);

    String val = localParams.get(V);
    if (val == null) {
      val = txt.substring(start);
      localParams.put(V, val);
    } else {
      // localParams.put(VAL_EXPLICIT, "true");
    }
    return new MapSolrParams(localParams);
  }

  /**
   * <p>
   * The form of the sort specification string currently parsed is:
   * </p>
   * <pre>
   * SortSpec ::= SingleSort [, SingleSort]*
   * SingleSort ::= &lt;fieldname|function&gt; SortDirection
   * SortDirection ::= top | desc | bottom | asc
   * </pre>
   * Examples:
   * <pre>
   *   score desc               #normal sort by score (will return null)
   *   weight bottom            #sort by weight ascending
   *   weight desc              #sort by weight descending
   *   height desc,weight desc  #sort by height descending, and use weight descending to break any ties
   *   height desc,weight asc   #sort by height descending, using weight ascending as a tiebreaker
   * </pre>
   * @return a SortSpec object populated with the appropriate Sort (which may be null if 
   *         default score sort is used) and SchemaFields (where applicable) using 
   *         hardcoded default count &amp; offset values.
   */
  public static SortSpec parseSortSpec(String sortSpec, SolrQueryRequest req) {
    if (sortSpec == null || sortSpec.length() == 0) return newEmptySortSpec();

    List<SortField> sorts = new ArrayList<>(4);
    List<SchemaField> fields = new ArrayList<>(4);

    try {

      StrParser sp = new StrParser(sortSpec);
      while (sp.pos < sp.end) {
        sp.eatws();

        final int start = sp.pos;

        // short circuit test for a really simple field name
        String field = sp.getId(null);
        Exception qParserException = null;

        if (field == null || !Character.isWhitespace(sp.peekChar())) {
          // let's try it as a function instead
          field = null;
          String funcStr = sp.val.substring(start);

          QParser parser = QParser.getParser(funcStr, FunctionQParserPlugin.NAME, req);
          Query q = null;
          try {
            if (parser instanceof FunctionQParser) {
              FunctionQParser fparser = (FunctionQParser)parser;
              fparser.setParseMultipleSources(false);
              fparser.setParseToEnd(false);
              
              q = fparser.getQuery();
              
              if (fparser.localParams != null) {
                if (fparser.valFollowedParams) {
                  // need to find the end of the function query via the string parser
                  int leftOver = fparser.sp.end - fparser.sp.pos;
                  sp.pos = sp.end - leftOver;   // reset our parser to the same amount of leftover
                } else {
                  // the value was via the "v" param in localParams, so we need to find
                  // the end of the local params themselves to pick up where we left off
                  sp.pos = start + fparser.localParamsEnd;
                }
              } else {
                // need to find the end of the function query via the string parser
                int leftOver = fparser.sp.end - fparser.sp.pos;
                sp.pos = sp.end - leftOver;   // reset our parser to the same amount of leftover
              }
            } else {
              // A QParser that's not for function queries.
              // It must have been specified via local params.
              q = parser.getQuery();

              assert parser.getLocalParams() != null;
              sp.pos = start + parser.localParamsEnd;
            }

            Boolean top = sp.getSortDirection();
            if (null != top) {
              // we have a Query and a valid direction
              if (q instanceof FunctionQuery) {
                sorts.add(((FunctionQuery)q).getValueSource().getSortField(top));
              } else {
                sorts.add((new QueryValueSource(q, 0.0f)).getSortField(top));
              }
              fields.add(null);
              continue;
            }
          } catch (Exception e) {
            // hang onto this in case the string isn't a full field name either
            qParserException = e;
          }
        }

        // if we made it here, we either have a "simple" field name,
        // or there was a problem parsing the string as a complex func/quer

        if (field == null) {
          // try again, simple rules for a field name with no whitespace
          sp.pos = start;
          field = sp.getSimpleString();
        }
        Boolean top = sp.getSortDirection();
        if (null == top) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, 
                                    "Can't determine a Sort Order (asc or desc) in sort spec " + sp);
        }
        
        if (SCORE.equals(field)) {
          if (top) {
            sorts.add(SortField.FIELD_SCORE);
          } else {
            sorts.add(new SortField(null, SortField.Type.SCORE, true));
          }
          fields.add(null);
        } else if (DOCID.equals(field)) {
          sorts.add(new SortField(null, SortField.Type.DOC, top));
          fields.add(null);
        } else {
          // try to find the field
          SchemaField sf = req.getSchema().getFieldOrNull(field);
          if (null == sf) {
            if (null != qParserException) {
              throw new SolrException
                (SolrException.ErrorCode.BAD_REQUEST,
                 "sort param could not be parsed as a query, and is not a "+
                 "field that exists in the index: " + field,
                 qParserException);
            }
            throw new SolrException
              (SolrException.ErrorCode.BAD_REQUEST,
               "sort param field can't be found: " + field);
          }
          sorts.add(sf.getSortField(top));
          fields.add(sf);
        }
      }

    } catch (SyntaxError e) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "error in sort: " + sortSpec, e);
    }


    // normalize a sort on score desc to null
    if (sorts.size()==1 && sorts.get(0) == SortField.FIELD_SCORE) {
      return newEmptySortSpec();
    }

    Sort s = new Sort(sorts.toArray(new SortField[sorts.size()]));
    return new SortSpec(s, fields);
  }

  private static SortSpec newEmptySortSpec() {
    return new SortSpec(null, Collections.<SchemaField>emptyList());
  }


  ///////////////////////////
  ///////////////////////////
  ///////////////////////////

  static FieldType writeFieldName(String name, IndexSchema schema, Appendable out, int flags) throws IOException {
    FieldType ft = null;
    ft = schema.getFieldTypeNoEx(name);
    out.append(name);
    if (ft == null) {
      out.append("(UNKNOWN FIELD " + name + ')');
    }
    out.append(':');
    return ft;
  }

  static void writeFieldVal(String val, FieldType ft, Appendable out, int flags) throws IOException {
    if (ft != null) {
      try {
        out.append(ft.indexedToReadable(val));
      } catch (Exception e) {
        out.append("EXCEPTION(val=");
        out.append(val);
        out.append(")");
      }
    } else {
      out.append(val);
    }
  }

  static void writeFieldVal(BytesRef val, FieldType ft, Appendable out, int flags) throws IOException {
    if (ft != null) {
      try {
        CharsRefBuilder readable = new CharsRefBuilder();
        ft.indexedToReadable(val, readable);
        out.append(readable.get());
      } catch (Exception e) {
        out.append("EXCEPTION(val=");
        out.append(val.utf8ToString());
        out.append(")");
      }
    } else {
      out.append(val.utf8ToString());
    }
  }


  private static int FLAG_BOOSTED=0x01;
  private static int FLAG_IS_CLAUSE=0x02;
  /**
   * @see #toString(Query,IndexSchema)
   */
  public static void toString(Query query, IndexSchema schema, Appendable out, int flags) throws IOException {
    int subflag = flags & ~(FLAG_BOOSTED|FLAG_IS_CLAUSE);  // clear the boosted / is clause flags for recursion

    if (query instanceof TermQuery) {
      TermQuery q = (TermQuery) query;
      Term t = q.getTerm();
      FieldType ft = writeFieldName(t.field(), schema, out, flags);
      writeFieldVal(t.bytes(), ft, out, flags);
    } else if (query instanceof TermRangeQuery) {
      TermRangeQuery q = (TermRangeQuery) query;
      String fname = q.getField();
      FieldType ft = writeFieldName(fname, schema, out, flags);
      out.append(q.includesLower() ? '[' : '{');
      BytesRef lt = q.getLowerTerm();
      BytesRef ut = q.getUpperTerm();
      if (lt == null) {
        out.append('*');
      } else {
        writeFieldVal(lt, ft, out, flags);
      }

      out.append(" TO ");

      if (ut == null) {
        out.append('*');
      } else {
        writeFieldVal(ut, ft, out, flags);
      }

      out.append(q.includesUpper() ? ']' : '}');
    } else if (query instanceof NumericRangeQuery) {
      NumericRangeQuery q = (NumericRangeQuery) query;
      String fname = q.getField();
      FieldType ft = writeFieldName(fname, schema, out, flags);
      out.append(q.includesMin() ? '[' : '{');
      Number lt = q.getMin();
      Number ut = q.getMax();
      if (lt == null) {
        out.append('*');
      } else {
        out.append(lt.toString());
      }

      out.append(" TO ");

      if (ut == null) {
        out.append('*');
      } else {
        out.append(ut.toString());
      }

      out.append(q.includesMax() ? ']' : '}');
    } else if (query instanceof BooleanQuery) {
      BooleanQuery q = (BooleanQuery) query;
      boolean needParens = false;

      if (q.getMinimumNumberShouldMatch() != 0 || q.isCoordDisabled() || (flags & (FLAG_IS_CLAUSE | FLAG_BOOSTED)) != 0 ) {
        needParens = true;
      }
      if (needParens) {
        out.append('(');
      }
      boolean first = true;
      for (BooleanClause c : q.clauses()) {
        if (!first) {
          out.append(' ');
        } else {
          first = false;
        }

        if (c.isProhibited()) {
          out.append('-');
        } else if (c.isRequired()) {
          out.append('+');
        }
        Query subQuery = c.getQuery();

        toString(subQuery, schema, out, subflag | FLAG_IS_CLAUSE);

      }

      if (needParens) {
        out.append(')');
      }
      if (q.getMinimumNumberShouldMatch() > 0) {
        out.append('~');
        out.append(Integer.toString(q.getMinimumNumberShouldMatch()));
      }
      if (q.isCoordDisabled()) {
        out.append("/no_coord");
      }

    } else if (query instanceof PrefixQuery) {
      PrefixQuery q = (PrefixQuery) query;
      Term prefix = q.getPrefix();
      FieldType ft = writeFieldName(prefix.field(), schema, out, flags);
      out.append(prefix.text());
      out.append('*');
    } else if (query instanceof WildcardQuery) {
      out.append(query.toString());
    } else if (query instanceof FuzzyQuery) {
      out.append(query.toString());
    } else if (query instanceof ConstantScoreQuery) {
      out.append(query.toString());
    } else if (query instanceof WrappedQuery) {
      WrappedQuery q = (WrappedQuery)query;
      out.append(q.getOptions());
      toString(q.getWrappedQuery(), schema, out, subflag);
    } else if (query instanceof BoostQuery) {
      BoostQuery q = (BoostQuery)query;
      toString(q.getQuery(), schema, out, subflag | FLAG_BOOSTED);
      out.append("^");
      out.append(Float.toString(q.getBoost()));
    }
    else {
      out.append(query.getClass().getSimpleName()
              + '(' + query.toString() + ')');
    }
  }

  /**
   * Formats a Query for debugging, using the IndexSchema to make
   * complex field types readable.
   * <p>
   * The benefit of using this method instead of calling
   * <code>Query.toString</code> directly is that it knows about the data
   * types of each field, so any field which is encoded in a particularly
   * complex way is still readable. The downside is that it only knows
   * about built in Query types, and will not be able to format custom
   * Query classes.
   * </p>
   */
  public static String toString(Query query, IndexSchema schema) {
    try {
      StringBuilder sb = new StringBuilder();
      toString(query, schema, sb, 0);
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Builds a list of String which are stringified versions of a list of Queries
   */
  public static List<String> toString(List<Query> queries, IndexSchema schema) {
    List<String> out = new ArrayList<>(queries.size());
    for (Query q : queries) {
      out.add(QueryParsing.toString(q, schema));
    }
    return out;
  }

}
