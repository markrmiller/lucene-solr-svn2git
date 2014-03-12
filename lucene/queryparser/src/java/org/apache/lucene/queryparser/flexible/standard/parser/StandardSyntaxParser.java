/* Generated By:JavaCC: Do not edit this line. StandardSyntaxParser.java */
package org.apache.lucene.queryparser.flexible.standard.parser;

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

import java.io.StringReader;
import java.util.Vector;

import org.apache.lucene.queryparser.flexible.messages.Message;
import org.apache.lucene.queryparser.flexible.messages.MessageImpl;
import org.apache.lucene.queryparser.flexible.core.QueryNodeParseException;
import org.apache.lucene.queryparser.flexible.core.messages.QueryParserMessages;
import org.apache.lucene.queryparser.flexible.core.nodes.AndQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.BooleanQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.BoostQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.FuzzyQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.ModifierQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.GroupQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.OrQueryNode;
import org.apache.lucene.queryparser.flexible.standard.nodes.RegexpQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.SlopQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QuotedFieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.parser.SyntaxParser;
import org.apache.lucene.queryparser.flexible.standard.nodes.TermRangeQueryNode;

/**
 * Parser for the standard Lucene syntax
 */
public class StandardSyntaxParser implements SyntaxParser, StandardSyntaxParserConstants {

  private static final int CONJ_NONE =0;
  private static final int CONJ_AND =2;
  private static final int CONJ_OR =2;


   // syntax parser constructor
   public StandardSyntaxParser() {
     this(new FastCharStream(new StringReader("")));
  }
     /** Parses a query string, returning a {@link org.apache.lucene.queryparser.flexible.core.nodes.QueryNode}.
     *  @param query  the query string to be parsed.
     *  @throws ParseException if the parsing fails
     */
    public QueryNode parse(CharSequence query, CharSequence field) throws QueryNodeParseException {
      ReInit(new FastCharStream(new StringReader(query.toString())));
      try {
        // TopLevelQuery is a Query followed by the end-of-input (EOF)
        QueryNode querynode = TopLevelQuery(field);
        return querynode;
      }
      catch (ParseException tme) {
            tme.setQuery(query);
            throw tme;
      }
      catch (Error tme) {
          Message message = new MessageImpl(QueryParserMessages.INVALID_SYNTAX_CANNOT_PARSE, query, tme.getMessage());
          QueryNodeParseException e = new QueryNodeParseException(tme);
            e.setQuery(query);
            e.setNonLocalizedMessage(message);
            throw e;
      }
    }

// *   Query  ::= ( Clause )*
// *   Clause ::= ["+", "-"] [<TERM> ":"] ( <TERM> | "(" Query ")" )
  final public int Conjunction() throws ParseException {
  int ret = CONJ_NONE;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case AND:
    case OR:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        jj_consume_token(AND);
            ret = CONJ_AND;
        break;
      case OR:
        jj_consume_token(OR);
              ret = CONJ_OR;
        break;
      default:
        jj_la1[0] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[1] = jj_gen;
      ;
    }
    {if (true) return ret;}
    throw new Error("Missing return statement in function");
  }

  final public ModifierQueryNode.Modifier Modifiers() throws ParseException {
  ModifierQueryNode.Modifier ret = ModifierQueryNode.Modifier.MOD_NONE;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NOT:
    case PLUS:
    case MINUS:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
              ret = ModifierQueryNode.Modifier.MOD_REQ;
        break;
      case MINUS:
        jj_consume_token(MINUS);
                 ret = ModifierQueryNode.Modifier.MOD_NOT;
        break;
      case NOT:
        jj_consume_token(NOT);
               ret = ModifierQueryNode.Modifier.MOD_NOT;
        break;
      default:
        jj_la1[2] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[3] = jj_gen;
      ;
    }
    {if (true) return ret;}
    throw new Error("Missing return statement in function");
  }

// This makes sure that there is no garbage after the query string
  final public QueryNode TopLevelQuery(CharSequence field) throws ParseException {
  QueryNode q;
    q = Query(field);
    jj_consume_token(0);
    {if (true) return q;}
    throw new Error("Missing return statement in function");
  }

// These changes were made to introduce operator precedence:
// - Clause() now returns a QueryNode. 
// - The modifiers are consumed by Clause() and returned as part of the QueryNode Object
// - Query does not consume conjunctions (AND, OR) anymore. 
// - This is now done by two new non-terminals: ConjClause and DisjClause
// The parse tree looks similar to this:
//       Query ::= DisjQuery ( DisjQuery )*
//   DisjQuery ::= ConjQuery ( OR ConjQuery )* 
//   ConjQuery ::= Clause ( AND Clause )*
//      Clause ::= [ Modifier ] ... 
  final public QueryNode Query(CharSequence field) throws ParseException {
  Vector<QueryNode> clauses = null;
  QueryNode c, first=null;
    first = DisjQuery(field);
    label_1:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case NOT:
      case PLUS:
      case MINUS:
      case LPAREN:
      case QUOTED:
      case TERM:
      case REGEXPTERM:
      case RANGEIN_START:
      case RANGEEX_START:
      case NUMBER:
        ;
        break;
      default:
        jj_la1[4] = jj_gen;
        break label_1;
      }
      c = DisjQuery(field);
       if (clauses == null) {
           clauses = new Vector<>();
           clauses.addElement(first);
       }
       clauses.addElement(c);
    }
        if (clauses != null) {
        {if (true) return new BooleanQueryNode(clauses);}
      } else {
          {if (true) return first;}
      }
    throw new Error("Missing return statement in function");
  }

  final public QueryNode DisjQuery(CharSequence field) throws ParseException {
  QueryNode first, c;
  Vector<QueryNode> clauses = null;
    first = ConjQuery(field);
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OR:
        ;
        break;
      default:
        jj_la1[5] = jj_gen;
        break label_2;
      }
      jj_consume_token(OR);
      c = ConjQuery(field);
     if (clauses == null) {
         clauses = new Vector<>();
         clauses.addElement(first);
     }
     clauses.addElement(c);
    }
    if (clauses != null) {
      {if (true) return new OrQueryNode(clauses);}
    } else {
        {if (true) return first;}
    }
    throw new Error("Missing return statement in function");
  }

  final public QueryNode ConjQuery(CharSequence field) throws ParseException {
  QueryNode first, c;
  Vector<QueryNode> clauses = null;
    first = ModClause(field);
    label_3:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        ;
        break;
      default:
        jj_la1[6] = jj_gen;
        break label_3;
      }
      jj_consume_token(AND);
      c = ModClause(field);
     if (clauses == null) {
         clauses = new Vector<>();
         clauses.addElement(first);
     }
     clauses.addElement(c);
    }
    if (clauses != null) {
      {if (true) return new AndQueryNode(clauses);}
    } else {
        {if (true) return first;}
    }
    throw new Error("Missing return statement in function");
  }

// QueryNode Query(CharSequence field) :
// {
// List clauses = new ArrayList();
//   List modifiers = new ArrayList();
//   QueryNode q, firstQuery=null;
//   ModifierQueryNode.Modifier mods;
//   int conj;
// }
// {
//   mods=Modifiers() q=Clause(field)
//   {
//     if (mods == ModifierQueryNode.Modifier.MOD_NONE) firstQuery=q;
//     
//     // do not create modifier nodes with MOD_NONE
//      if (mods != ModifierQueryNode.Modifier.MOD_NONE) {
//        q = new ModifierQueryNode(q, mods);
//      }
//      clauses.add(q);
//   }
//   (
//     conj=Conjunction() mods=Modifiers() q=Clause(field)
//     { 
//       // do not create modifier nodes with MOD_NONE
//        if (mods != ModifierQueryNode.Modifier.MOD_NONE) {
//          q = new ModifierQueryNode(q, mods);
//        }
//        clauses.add(q);
//        //TODO: figure out what to do with AND and ORs
//   }
//   )*
//     {
//      if (clauses.size() == 1 && firstQuery != null)
//         return firstQuery;
//       else {
//       return new BooleanQueryNode(clauses);
//       }
//     }
// }
  final public QueryNode ModClause(CharSequence field) throws ParseException {
  QueryNode q;
  ModifierQueryNode.Modifier mods;
    mods = Modifiers();
    q = Clause(field);
        if (mods != ModifierQueryNode.Modifier.MOD_NONE) {
          q = new ModifierQueryNode(q, mods);
        }
        {if (true) return q;}
    throw new Error("Missing return statement in function");
  }

  final public QueryNode Clause(CharSequence field) throws ParseException {
  QueryNode q;
  Token fieldToken=null, boost=null, operator=null, term=null;
  FieldQueryNode qLower, qUpper;
  boolean lowerInclusive, upperInclusive;

  boolean group = false;
    if (jj_2_2(3)) {
      fieldToken = jj_consume_token(TERM);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OP_COLON:
      case OP_EQUAL:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case OP_COLON:
          jj_consume_token(OP_COLON);
          break;
        case OP_EQUAL:
          jj_consume_token(OP_EQUAL);
          break;
        default:
          jj_la1[7] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                                 field=EscapeQuerySyntaxImpl.discardEscapeChar(fieldToken.image);
        q = Term(field);
        break;
      case OP_LESSTHAN:
      case OP_LESSTHANEQ:
      case OP_MORETHAN:
      case OP_MORETHANEQ:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case OP_LESSTHAN:
          operator = jj_consume_token(OP_LESSTHAN);
          break;
        case OP_LESSTHANEQ:
          operator = jj_consume_token(OP_LESSTHANEQ);
          break;
        case OP_MORETHAN:
          operator = jj_consume_token(OP_MORETHAN);
          break;
        case OP_MORETHANEQ:
          operator = jj_consume_token(OP_MORETHANEQ);
          break;
        default:
          jj_la1[8] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                                                                                                               field=EscapeQuerySyntaxImpl.discardEscapeChar(fieldToken.image);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case TERM:
          term = jj_consume_token(TERM);
          break;
        case QUOTED:
          term = jj_consume_token(QUOTED);
          break;
        case NUMBER:
          term = jj_consume_token(NUMBER);
          break;
        default:
          jj_la1[9] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
        if (term.kind == QUOTED) {
            term.image = term.image.substring(1, term.image.length()-1);
        }
        switch (operator.kind) {
            case OP_LESSTHAN:
              lowerInclusive = true;
              upperInclusive = false;

              qLower = new FieldQueryNode(field,
                                         "*", term.beginColumn, term.endColumn);
            qUpper = new FieldQueryNode(field,
                                 EscapeQuerySyntaxImpl.discardEscapeChar(term.image), term.beginColumn, term.endColumn);

                break;
            case OP_LESSTHANEQ:
              lowerInclusive = true;
              upperInclusive = true;

                qLower = new FieldQueryNode(field,
                                         "*", term.beginColumn, term.endColumn);
                qUpper = new FieldQueryNode(field,
                                         EscapeQuerySyntaxImpl.discardEscapeChar(term.image), term.beginColumn, term.endColumn);
                break;
            case OP_MORETHAN:
              lowerInclusive = false;
              upperInclusive = true;

                qLower = new FieldQueryNode(field,
                                         EscapeQuerySyntaxImpl.discardEscapeChar(term.image), term.beginColumn, term.endColumn);
                qUpper = new FieldQueryNode(field,
                                         "*", term.beginColumn, term.endColumn);
                break;
            case OP_MORETHANEQ:
              lowerInclusive = true;
              upperInclusive = true;

                qLower = new FieldQueryNode(field,
                                         EscapeQuerySyntaxImpl.discardEscapeChar(term.image), term.beginColumn, term.endColumn);
                qUpper = new FieldQueryNode(field,
                                         "*", term.beginColumn, term.endColumn);
                break;
            default:
                {if (true) throw new Error("Unhandled case: operator="+operator.toString());}
        }
        q = new TermRangeQueryNode(qLower, qUpper, lowerInclusive, upperInclusive);
        break;
      default:
        jj_la1[10] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LPAREN:
      case QUOTED:
      case TERM:
      case REGEXPTERM:
      case RANGEIN_START:
      case RANGEEX_START:
      case NUMBER:
        if (jj_2_1(2)) {
          fieldToken = jj_consume_token(TERM);
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case OP_COLON:
            jj_consume_token(OP_COLON);
            break;
          case OP_EQUAL:
            jj_consume_token(OP_EQUAL);
            break;
          default:
            jj_la1[11] = jj_gen;
            jj_consume_token(-1);
            throw new ParseException();
          }
                                 field=EscapeQuerySyntaxImpl.discardEscapeChar(fieldToken.image);
        } else {
          ;
        }
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case QUOTED:
        case TERM:
        case REGEXPTERM:
        case RANGEIN_START:
        case RANGEEX_START:
        case NUMBER:
          q = Term(field);
          break;
        case LPAREN:
          jj_consume_token(LPAREN);
          q = Query(field);
          jj_consume_token(RPAREN);
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case CARAT:
            jj_consume_token(CARAT);
            boost = jj_consume_token(NUMBER);
            break;
          default:
            jj_la1[12] = jj_gen;
            ;
          }
                                                                 group=true;
          break;
        default:
          jj_la1[13] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
        break;
      default:
        jj_la1[14] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
      if (boost != null) {
      float f = (float)1.0;
      try {
        f = Float.valueOf(boost.image).floatValue();
        // avoid boosting null queries, such as those caused by stop words
          if (q != null) {
            q = new BoostQueryNode(q, f);
          }
      } catch (Exception ignored) {
        /* Should this be handled somehow? (defaults to "no boost", if
             * boost number is invalid)
             */
      }
      }
      if (group) { q = new GroupQueryNode(q);}
      {if (true) return q;}
    throw new Error("Missing return statement in function");
  }

  final public QueryNode Term(CharSequence field) throws ParseException {
  Token term, boost=null, fuzzySlop=null, goop1, goop2;
  boolean fuzzy = false;
  boolean regexp = false;
  boolean startInc=false;
  boolean endInc=false;
  QueryNode q =null;
  FieldQueryNode qLower, qUpper;
  float defaultMinSimilarity = org.apache.lucene.search.FuzzyQuery.defaultMinSimilarity;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case TERM:
    case REGEXPTERM:
    case NUMBER:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case TERM:
        term = jj_consume_token(TERM);
                    q = new FieldQueryNode(field, EscapeQuerySyntaxImpl.discardEscapeChar(term.image), term.beginColumn, term.endColumn);
        break;
      case REGEXPTERM:
        term = jj_consume_token(REGEXPTERM);
                             regexp=true;
        break;
      case NUMBER:
        term = jj_consume_token(NUMBER);
        break;
      default:
        jj_la1[15] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case FUZZY_SLOP:
        fuzzySlop = jj_consume_token(FUZZY_SLOP);
                                fuzzy=true;
        break;
      default:
        jj_la1[16] = jj_gen;
        ;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CARAT:
        jj_consume_token(CARAT);
        boost = jj_consume_token(NUMBER);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case FUZZY_SLOP:
          fuzzySlop = jj_consume_token(FUZZY_SLOP);
                                                         fuzzy=true;
          break;
        default:
          jj_la1[17] = jj_gen;
          ;
        }
        break;
      default:
        jj_la1[18] = jj_gen;
        ;
      }
       if (fuzzy) {
           float fms = defaultMinSimilarity;
           try {
            fms = Float.valueOf(fuzzySlop.image.substring(1)).floatValue();
           } catch (Exception ignored) { }
          if(fms < 0.0f){
            {if (true) throw new ParseException(new MessageImpl(QueryParserMessages.INVALID_SYNTAX_FUZZY_LIMITS));}
          } else if (fms >= 1.0f && fms != (int) fms) {
            {if (true) throw new ParseException(new MessageImpl(QueryParserMessages.INVALID_SYNTAX_FUZZY_EDITS));}
          }
          q = new FuzzyQueryNode(field, EscapeQuerySyntaxImpl.discardEscapeChar(term.image), fms, term.beginColumn, term.endColumn);
       } else if (regexp) {
         String re = term.image.substring(1, term.image.length()-1);
         q = new RegexpQueryNode(field, re, 0, re.length());
       }
      break;
    case RANGEIN_START:
    case RANGEEX_START:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case RANGEIN_START:
        jj_consume_token(RANGEIN_START);
                            startInc=true;
        break;
      case RANGEEX_START:
        jj_consume_token(RANGEEX_START);
        break;
      default:
        jj_la1[19] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case RANGE_GOOP:
        goop1 = jj_consume_token(RANGE_GOOP);
        break;
      case RANGE_QUOTED:
        goop1 = jj_consume_token(RANGE_QUOTED);
        break;
      default:
        jj_la1[20] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case RANGE_TO:
        jj_consume_token(RANGE_TO);
        break;
      default:
        jj_la1[21] = jj_gen;
        ;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case RANGE_GOOP:
        goop2 = jj_consume_token(RANGE_GOOP);
        break;
      case RANGE_QUOTED:
        goop2 = jj_consume_token(RANGE_QUOTED);
        break;
      default:
        jj_la1[22] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case RANGEIN_END:
        jj_consume_token(RANGEIN_END);
                          endInc=true;
        break;
      case RANGEEX_END:
        jj_consume_token(RANGEEX_END);
        break;
      default:
        jj_la1[23] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CARAT:
        jj_consume_token(CARAT);
        boost = jj_consume_token(NUMBER);
        break;
      default:
        jj_la1[24] = jj_gen;
        ;
      }
          if (goop1.kind == RANGE_QUOTED) {
            goop1.image = goop1.image.substring(1, goop1.image.length()-1);
          }
          if (goop2.kind == RANGE_QUOTED) {
            goop2.image = goop2.image.substring(1, goop2.image.length()-1);
          }

          qLower = new FieldQueryNode(field,
                                   EscapeQuerySyntaxImpl.discardEscapeChar(goop1.image), goop1.beginColumn, goop1.endColumn);
      qUpper = new FieldQueryNode(field,
                                   EscapeQuerySyntaxImpl.discardEscapeChar(goop2.image), goop2.beginColumn, goop2.endColumn);
          q = new TermRangeQueryNode(qLower, qUpper, startInc ? true : false, endInc ? true : false);
      break;
    case QUOTED:
      term = jj_consume_token(QUOTED);
                      q = new QuotedFieldQueryNode(field, EscapeQuerySyntaxImpl.discardEscapeChar(term.image.substring(1, term.image.length()-1)), term.beginColumn + 1, term.endColumn - 1);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case FUZZY_SLOP:
        fuzzySlop = jj_consume_token(FUZZY_SLOP);
        break;
      default:
        jj_la1[25] = jj_gen;
        ;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CARAT:
        jj_consume_token(CARAT);
        boost = jj_consume_token(NUMBER);
        break;
      default:
        jj_la1[26] = jj_gen;
        ;
      }
         int phraseSlop = 0;

         if (fuzzySlop != null) {
           try {
             phraseSlop = Float.valueOf(fuzzySlop.image.substring(1)).intValue();
             q = new SlopQueryNode(q, phraseSlop);
           }
           catch (Exception ignored) {
            /* Should this be handled somehow? (defaults to "no PhraseSlop", if
           * slop number is invalid)
           */
           }
         }
      break;
    default:
      jj_la1[27] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    if (boost != null) {
      float f = (float)1.0;
      try {
        f = Float.valueOf(boost.image).floatValue();
        // avoid boosting null queries, such as those caused by stop words
          if (q != null) {
            q = new BoostQueryNode(q, f);
          }
      } catch (Exception ignored) {
        /* Should this be handled somehow? (defaults to "no boost", if
           * boost number is invalid)
           */
      }
    }
      {if (true) return q;}
    throw new Error("Missing return statement in function");
  }

  private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_1(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(0, xla); }
  }

  private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_2(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(1, xla); }
  }

  private boolean jj_3_2() {
    if (jj_scan_token(TERM)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_4()) {
    jj_scanpos = xsp;
    if (jj_3R_5()) return true;
    }
    return false;
  }

  private boolean jj_3R_12() {
    if (jj_scan_token(RANGEIN_START)) return true;
    return false;
  }

  private boolean jj_3R_11() {
    if (jj_scan_token(REGEXPTERM)) return true;
    return false;
  }

  private boolean jj_3_1() {
    if (jj_scan_token(TERM)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(15)) {
    jj_scanpos = xsp;
    if (jj_scan_token(16)) return true;
    }
    return false;
  }

  private boolean jj_3R_8() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_12()) {
    jj_scanpos = xsp;
    if (jj_scan_token(27)) return true;
    }
    return false;
  }

  private boolean jj_3R_10() {
    if (jj_scan_token(TERM)) return true;
    return false;
  }

  private boolean jj_3R_7() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_10()) {
    jj_scanpos = xsp;
    if (jj_3R_11()) {
    jj_scanpos = xsp;
    if (jj_scan_token(28)) return true;
    }
    }
    return false;
  }

  private boolean jj_3R_9() {
    if (jj_scan_token(QUOTED)) return true;
    return false;
  }

  private boolean jj_3R_5() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(17)) {
    jj_scanpos = xsp;
    if (jj_scan_token(18)) {
    jj_scanpos = xsp;
    if (jj_scan_token(19)) {
    jj_scanpos = xsp;
    if (jj_scan_token(20)) return true;
    }
    }
    }
    xsp = jj_scanpos;
    if (jj_scan_token(23)) {
    jj_scanpos = xsp;
    if (jj_scan_token(22)) {
    jj_scanpos = xsp;
    if (jj_scan_token(28)) return true;
    }
    }
    return false;
  }

  private boolean jj_3R_4() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(15)) {
    jj_scanpos = xsp;
    if (jj_scan_token(16)) return true;
    }
    if (jj_3R_6()) return true;
    return false;
  }

  private boolean jj_3R_6() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_7()) {
    jj_scanpos = xsp;
    if (jj_3R_8()) {
    jj_scanpos = xsp;
    if (jj_3R_9()) return true;
    }
    }
    return false;
  }

  /** Generated Token Manager. */
  public StandardSyntaxParserTokenManager token_source;
  /** Current token. */
  public Token token;
  /** Next token. */
  public Token jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  private int jj_gen;
  final private int[] jj_la1 = new int[28];
  static private int[] jj_la1_0;
  static private int[] jj_la1_1;
  static {
      jj_la1_init_0();
      jj_la1_init_1();
   }
   private static void jj_la1_init_0() {
      jj_la1_0 = new int[] {0x300,0x300,0x1c00,0x1c00,0x1ec03c00,0x200,0x100,0x18000,0x1e0000,0x10c00000,0x1f8000,0x18000,0x200000,0x1ec02000,0x1ec02000,0x12800000,0x1000000,0x1000000,0x200000,0xc000000,0x0,0x20000000,0x0,0xc0000000,0x200000,0x1000000,0x200000,0x1ec00000,};
   }
   private static void jj_la1_init_1() {
      jj_la1_1 = new int[] {0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x3,0x0,0x3,0x0,0x0,0x0,0x0,0x0,};
   }
  final private JJCalls[] jj_2_rtns = new JJCalls[2];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  /** Constructor with user supplied CharStream. */
  public StandardSyntaxParser(CharStream stream) {
    token_source = new StandardSyntaxParserTokenManager(stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(CharStream stream) {
    token_source.ReInit(stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Constructor with generated Token Manager. */
  public StandardSyntaxParser(StandardSyntaxParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(StandardSyntaxParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  static private final class LookaheadSuccess extends java.lang.Error { }
  final private LookaheadSuccess jj_ls = new LookaheadSuccess();
  private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    if (jj_scanpos.kind != kind) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) throw jj_ls;
    return false;
  }


/** Get the next Token. */
  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

/** Get the specific Token. */
  final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.List<int[]> jj_expentries = new java.util.ArrayList<>();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      jj_entries_loop: for (java.util.Iterator<?> it = jj_expentries.iterator(); it.hasNext();) {
        int[] oldentry = (int[])(it.next());
        if (oldentry.length == jj_expentry.length) {
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              continue jj_entries_loop;
            }
          }
          jj_expentries.add(jj_expentry);
          break jj_entries_loop;
        }
      }
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  /** Generate ParseException. */
  public ParseException generateParseException() {
    jj_expentries.clear();
    boolean[] la1tokens = new boolean[34];
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 28; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 34; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.add(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = jj_expentries.get(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  /** Enable tracing. */
  final public void enable_tracing() {
  }

  /** Disable tracing. */
  final public void disable_tracing() {
  }

  private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 2; i++) {
    try {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
          }
        }
        p = p.next;
      } while (p != null);
      } catch(LookaheadSuccess ls) { }
    }
    jj_rescan = false;
  }

  private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
