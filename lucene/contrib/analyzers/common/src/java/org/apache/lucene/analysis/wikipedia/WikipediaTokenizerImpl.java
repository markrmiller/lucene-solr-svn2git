/* The following code was generated by JFlex 1.5.0-SNAPSHOT on 9/30/11 1:13 PM */

package org.apache.lucene.analysis.wikipedia;

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

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;


/**
 * This class is a scanner generated by 
 * <a href="http://www.jflex.de/">JFlex</a> 1.5.0-SNAPSHOT
 * on 9/30/11 1:13 PM from the specification file
 * <tt>/lucene/3x.clean2/lucene/contrib/analyzers/common/src/java/org/apache/lucene/analysis/wikipedia/WikipediaTokenizerImpl.jflex</tt>
 */
class WikipediaTokenizerImpl {

  /** This character denotes the end of file */
  public static final int YYEOF = -1;

  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int THREE_SINGLE_QUOTES_STATE = 10;
  public static final int EXTERNAL_LINK_STATE = 6;
  public static final int DOUBLE_EQUALS_STATE = 14;
  public static final int INTERNAL_LINK_STATE = 4;
  public static final int DOUBLE_BRACE_STATE = 16;
  public static final int CATEGORY_STATE = 2;
  public static final int YYINITIAL = 0;
  public static final int STRING = 18;
  public static final int FIVE_SINGLE_QUOTES_STATE = 12;
  public static final int TWO_SINGLE_QUOTES_STATE = 8;

  /**
   * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
   * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
   *                  at the beginning of a line
   * l is of the form l = 2*k, k a non negative integer
   */
  private static final int ZZ_LEXSTATE[] = { 
     0,  0,  1,  1,  2,  2,  3,  3,  4,  4,  5,  5,  6,  6,  7,  7, 
     8,  8,  9, 9
  };

  /** 
   * Translates characters to character classes
   */
  private static final String ZZ_CMAP_PACKED = 
    "\11\0\1\24\1\23\1\0\1\24\1\22\22\0\1\24\1\0\1\12"+
    "\1\53\2\0\1\3\1\1\4\0\1\14\1\5\1\2\1\10\12\16"+
    "\1\27\1\0\1\7\1\11\1\13\1\53\1\4\2\15\1\30\5\15"+
    "\1\41\21\15\1\25\1\0\1\26\1\0\1\6\1\0\1\31\1\43"+
    "\2\15\1\33\1\40\1\34\1\50\1\41\4\15\1\42\1\35\1\51"+
    "\1\15\1\36\1\52\1\32\3\15\1\44\1\37\1\15\1\45\1\47"+
    "\1\46\102\0\27\15\1\0\37\15\1\0\u0568\15\12\17\206\15\12\17"+
    "\u026c\15\12\17\166\15\12\17\166\15\12\17\166\15\12\17\166\15\12\17"+
    "\167\15\11\17\166\15\12\17\166\15\12\17\166\15\12\17\340\15\12\17"+
    "\166\15\12\17\u0166\15\12\17\266\15\u0100\15\u0e00\15\u1040\0\u0150\21\140\0"+
    "\20\21\u0100\0\200\21\200\0\u19c0\21\100\0\u5200\21\u0c00\0\u2bb0\20\u2150\0"+
    "\u0200\21\u0465\0\73\21\75\15\43\0";

  /** 
   * Translates characters to character classes
   */
  private static final char [] ZZ_CMAP = zzUnpackCMap(ZZ_CMAP_PACKED);

  /** 
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\12\0\4\1\4\2\1\3\1\1\1\4\1\1\2\5"+
    "\1\6\2\5\1\7\1\5\2\10\1\11\1\12\1\11"+
    "\1\13\1\14\1\10\1\15\1\16\1\15\1\17\1\20"+
    "\1\10\1\21\1\10\4\22\1\23\1\22\1\24\1\25"+
    "\1\26\3\0\1\27\14\0\1\30\1\31\1\32\1\33"+
    "\1\11\1\0\1\34\1\35\1\0\1\36\1\0\1\37"+
    "\3\0\1\40\1\41\2\42\1\41\2\43\2\0\1\42"+
    "\1\0\14\42\1\41\3\0\1\11\1\44\3\0\1\45"+
    "\1\46\5\0\1\47\4\0\1\47\2\0\2\47\2\0"+
    "\1\11\5\0\1\31\1\41\1\42\1\50\3\0\1\11"+
    "\2\0\1\51\30\0\1\52\2\0\1\53\1\54\1\55";

  private static int [] zzUnpackAction() {
    int [] result = new int[183];
    int offset = 0;
    offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAction(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /** 
   * Translates a state to a row index in the transition table
   */
  private static final int [] ZZ_ROWMAP = zzUnpackRowMap();

  private static final String ZZ_ROWMAP_PACKED_0 =
    "\0\0\0\54\0\130\0\204\0\260\0\334\0\u0108\0\u0134"+
    "\0\u0160\0\u018c\0\u01b8\0\u01e4\0\u0210\0\u023c\0\u0268\0\u0294"+
    "\0\u02c0\0\u02ec\0\u01b8\0\u0318\0\u0344\0\u0370\0\u01b8\0\u039c"+
    "\0\u03c8\0\u03f4\0\u0420\0\u044c\0\u0478\0\u01b8\0\u039c\0\u04a4"+
    "\0\u01b8\0\u04d0\0\u04fc\0\u0528\0\u0554\0\u0580\0\u05ac\0\u05d8"+
    "\0\u0604\0\u0630\0\u065c\0\u0688\0\u06b4\0\u01b8\0\u06e0\0\u039c"+
    "\0\u070c\0\u0738\0\u0764\0\u0790\0\u01b8\0\u01b8\0\u07bc\0\u07e8"+
    "\0\u0814\0\u01b8\0\u0840\0\u086c\0\u0898\0\u08c4\0\u08f0\0\u091c"+
    "\0\u0948\0\u0974\0\u09a0\0\u09cc\0\u09f8\0\u0a24\0\u0a50\0\u0a7c"+
    "\0\u01b8\0\u01b8\0\u0aa8\0\u0ad4\0\u0b00\0\u0b00\0\u0b2c\0\u0b58"+
    "\0\u0b84\0\u0bb0\0\u0bdc\0\u0c08\0\u0c34\0\u0c60\0\u0c8c\0\u0cb8"+
    "\0\u0ce4\0\u0d10\0\u0898\0\u0d3c\0\u0d68\0\u0d94\0\u0dc0\0\u0dec"+
    "\0\u0e18\0\u0e44\0\u0e70\0\u0e9c\0\u0ec8\0\u0ef4\0\u0f20\0\u0f4c"+
    "\0\u0f78\0\u0fa4\0\u0fd0\0\u0ffc\0\u1028\0\u1054\0\u1080\0\u10ac"+
    "\0\u10d8\0\u01b8\0\u1104\0\u1130\0\u115c\0\u1188\0\u01b8\0\u11b4"+
    "\0\u11e0\0\u120c\0\u1238\0\u1264\0\u1290\0\u12bc\0\u12e8\0\u1314"+
    "\0\u1340\0\u136c\0\u1398\0\u13c4\0\u086c\0\u09f8\0\u13f0\0\u141c"+
    "\0\u1448\0\u1474\0\u14a0\0\u14cc\0\u14f8\0\u1524\0\u01b8\0\u1550"+
    "\0\u157c\0\u15a8\0\u15d4\0\u1600\0\u162c\0\u1658\0\u1684\0\u16b0"+
    "\0\u01b8\0\u16dc\0\u1708\0\u1734\0\u1760\0\u178c\0\u17b8\0\u17e4"+
    "\0\u1810\0\u183c\0\u1868\0\u1894\0\u18c0\0\u18ec\0\u1918\0\u1944"+
    "\0\u1970\0\u199c\0\u19c8\0\u19f4\0\u1a20\0\u1a4c\0\u1a78\0\u1aa4"+
    "\0\u1ad0\0\u1afc\0\u1b28\0\u1b54\0\u01b8\0\u01b8\0\u01b8";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[183];
    int offset = 0;
    offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackRowMap(String packed, int offset, int [] result) {
    int i = 0;  /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int high = packed.charAt(i++) << 16;
      result[j++] = high | packed.charAt(i++);
    }
    return j;
  }

  /** 
   * The transition table of the DFA
   */
  private static final int [] ZZ_TRANS = zzUnpackTrans();

  private static final String ZZ_TRANS_PACKED_0 =
    "\1\13\1\14\5\13\1\15\1\13\1\16\3\13\1\17"+
    "\1\20\1\21\1\22\1\23\1\24\2\13\1\25\2\13"+
    "\15\17\1\26\2\13\3\17\1\13\7\27\1\30\5\27"+
    "\4\31\1\27\1\32\3\27\1\33\1\27\15\31\3\27"+
    "\3\31\10\27\1\30\5\27\4\34\1\27\1\32\3\27"+
    "\1\35\1\27\15\34\3\27\3\34\1\27\7\36\1\37"+
    "\5\36\4\40\1\36\1\32\2\27\1\36\1\41\1\36"+
    "\15\40\3\36\1\42\2\40\2\36\1\43\5\36\1\37"+
    "\5\36\4\44\1\36\1\45\2\36\1\46\2\36\15\44"+
    "\3\36\3\44\10\36\1\37\5\36\4\47\1\36\1\45"+
    "\2\36\1\46\2\36\15\47\3\36\3\47\10\36\1\37"+
    "\5\36\4\47\1\36\1\45\2\36\1\50\2\36\15\47"+
    "\3\36\3\47\10\36\1\37\1\36\1\51\3\36\4\52"+
    "\1\36\1\45\5\36\15\52\3\36\3\52\10\36\1\53"+
    "\5\36\4\54\1\36\1\45\5\36\15\54\1\36\1\55"+
    "\1\36\3\54\1\36\1\56\1\57\5\56\1\60\1\56"+
    "\1\61\3\56\4\62\1\56\1\63\2\56\1\64\2\56"+
    "\15\62\2\56\1\65\3\62\1\56\55\0\1\66\62\0"+
    "\1\67\4\0\4\70\7\0\6\70\1\71\6\70\3\0"+
    "\3\70\12\0\1\72\43\0\1\73\1\74\1\75\1\76"+
    "\2\77\1\0\1\100\3\0\1\100\1\17\1\20\1\21"+
    "\1\22\7\0\15\17\3\0\3\17\3\0\1\101\1\0"+
    "\1\102\2\103\1\0\1\104\3\0\1\104\3\20\1\22"+
    "\7\0\15\20\3\0\3\20\2\0\1\73\1\105\1\75"+
    "\1\76\2\103\1\0\1\104\3\0\1\104\1\21\1\20"+
    "\1\21\1\22\7\0\15\21\3\0\3\21\3\0\1\106"+
    "\1\0\1\102\2\77\1\0\1\100\3\0\1\100\4\22"+
    "\7\0\15\22\3\0\3\22\24\0\1\13\55\0\1\107"+
    "\73\0\1\110\16\0\1\67\4\0\4\70\7\0\15\70"+
    "\3\0\3\70\16\0\4\31\7\0\15\31\3\0\3\31"+
    "\24\0\1\27\56\0\1\111\42\0\4\34\7\0\15\34"+
    "\3\0\3\34\27\0\1\112\42\0\4\40\7\0\15\40"+
    "\3\0\3\40\16\0\4\40\7\0\2\40\1\113\12\40"+
    "\3\0\3\40\2\0\1\114\67\0\4\44\7\0\15\44"+
    "\3\0\3\44\24\0\1\36\55\0\1\115\43\0\4\47"+
    "\7\0\15\47\3\0\3\47\26\0\1\116\37\0\1\111"+
    "\57\0\4\52\7\0\15\52\3\0\3\52\11\0\1\117"+
    "\4\0\4\70\7\0\15\70\3\0\3\70\16\0\4\54"+
    "\7\0\15\54\3\0\3\54\47\0\1\111\6\0\1\120"+
    "\63\0\1\121\57\0\4\62\7\0\15\62\3\0\3\62"+
    "\24\0\1\56\55\0\1\122\43\0\4\70\7\0\15\70"+
    "\3\0\3\70\14\0\1\36\1\0\4\123\1\0\3\124"+
    "\3\0\15\123\3\0\3\123\14\0\1\36\1\0\4\123"+
    "\1\0\3\124\3\0\3\123\1\125\11\123\3\0\3\123"+
    "\16\0\1\126\1\0\1\126\10\0\15\126\3\0\3\126"+
    "\16\0\1\127\1\130\1\131\1\132\7\0\15\127\3\0"+
    "\3\127\16\0\1\133\1\0\1\133\10\0\15\133\3\0"+
    "\3\133\16\0\1\134\1\135\1\134\1\135\7\0\15\134"+
    "\3\0\3\134\16\0\1\136\2\137\1\140\7\0\15\136"+
    "\3\0\3\136\16\0\1\100\2\141\10\0\15\100\3\0"+
    "\3\100\16\0\1\142\2\143\1\144\7\0\15\142\3\0"+
    "\3\142\16\0\4\135\7\0\15\135\3\0\3\135\16\0"+
    "\1\145\2\146\1\147\7\0\15\145\3\0\3\145\16\0"+
    "\1\150\2\151\1\152\7\0\15\150\3\0\3\150\16\0"+
    "\1\153\1\143\1\154\1\144\7\0\15\153\3\0\3\153"+
    "\16\0\1\155\2\130\1\132\7\0\15\155\3\0\3\155"+
    "\30\0\1\156\1\157\64\0\1\160\27\0\4\40\7\0"+
    "\2\40\1\161\12\40\3\0\3\40\2\0\1\162\101\0"+
    "\1\163\1\164\40\0\4\70\7\0\6\70\1\165\6\70"+
    "\3\0\3\70\2\0\1\166\63\0\1\167\71\0\1\170"+
    "\1\171\34\0\1\172\1\0\1\36\1\0\4\123\1\0"+
    "\3\124\3\0\15\123\3\0\3\123\16\0\4\173\1\0"+
    "\3\124\3\0\15\173\3\0\3\173\12\0\1\172\1\0"+
    "\1\36\1\0\4\123\1\0\3\124\3\0\10\123\1\174"+
    "\4\123\3\0\3\123\2\0\1\73\13\0\1\126\1\0"+
    "\1\126\10\0\15\126\3\0\3\126\3\0\1\175\1\0"+
    "\1\102\2\176\6\0\1\127\1\130\1\131\1\132\7\0"+
    "\15\127\3\0\3\127\3\0\1\177\1\0\1\102\2\200"+
    "\1\0\1\201\3\0\1\201\3\130\1\132\7\0\15\130"+
    "\3\0\3\130\3\0\1\202\1\0\1\102\2\200\1\0"+
    "\1\201\3\0\1\201\1\131\1\130\1\131\1\132\7\0"+
    "\15\131\3\0\3\131\3\0\1\203\1\0\1\102\2\176"+
    "\6\0\4\132\7\0\15\132\3\0\3\132\3\0\1\204"+
    "\2\0\1\204\7\0\1\134\1\135\1\134\1\135\7\0"+
    "\15\134\3\0\3\134\3\0\1\204\2\0\1\204\7\0"+
    "\4\135\7\0\15\135\3\0\3\135\3\0\1\176\1\0"+
    "\1\102\2\176\6\0\1\136\2\137\1\140\7\0\15\136"+
    "\3\0\3\136\3\0\1\200\1\0\1\102\2\200\1\0"+
    "\1\201\3\0\1\201\3\137\1\140\7\0\15\137\3\0"+
    "\3\137\3\0\1\176\1\0\1\102\2\176\6\0\4\140"+
    "\7\0\15\140\3\0\3\140\3\0\1\201\2\0\2\201"+
    "\1\0\1\201\3\0\1\201\3\141\10\0\15\141\3\0"+
    "\3\141\3\0\1\106\1\0\1\102\2\77\1\0\1\100"+
    "\3\0\1\100\1\142\2\143\1\144\7\0\15\142\3\0"+
    "\3\142\3\0\1\101\1\0\1\102\2\103\1\0\1\104"+
    "\3\0\1\104\3\143\1\144\7\0\15\143\3\0\3\143"+
    "\3\0\1\106\1\0\1\102\2\77\1\0\1\100\3\0"+
    "\1\100\4\144\7\0\15\144\3\0\3\144\3\0\1\77"+
    "\1\0\1\102\2\77\1\0\1\100\3\0\1\100\1\145"+
    "\2\146\1\147\7\0\15\145\3\0\3\145\3\0\1\103"+
    "\1\0\1\102\2\103\1\0\1\104\3\0\1\104\3\146"+
    "\1\147\7\0\15\146\3\0\3\146\3\0\1\77\1\0"+
    "\1\102\2\77\1\0\1\100\3\0\1\100\4\147\7\0"+
    "\15\147\3\0\3\147\3\0\1\100\2\0\2\100\1\0"+
    "\1\100\3\0\1\100\1\150\2\151\1\152\7\0\15\150"+
    "\3\0\3\150\3\0\1\104\2\0\2\104\1\0\1\104"+
    "\3\0\1\104\3\151\1\152\7\0\15\151\3\0\3\151"+
    "\3\0\1\100\2\0\2\100\1\0\1\100\3\0\1\100"+
    "\4\152\7\0\15\152\3\0\3\152\3\0\1\205\1\0"+
    "\1\102\2\77\1\0\1\100\3\0\1\100\1\153\1\143"+
    "\1\154\1\144\7\0\15\153\3\0\3\153\3\0\1\206"+
    "\1\0\1\102\2\103\1\0\1\104\3\0\1\104\1\154"+
    "\1\143\1\154\1\144\7\0\15\154\3\0\3\154\3\0"+
    "\1\203\1\0\1\102\2\176\6\0\1\155\2\130\1\132"+
    "\7\0\15\155\3\0\3\155\31\0\1\157\54\0\1\207"+
    "\64\0\1\210\26\0\4\40\7\0\15\40\3\0\1\40"+
    "\1\211\1\40\31\0\1\164\54\0\1\212\35\0\1\36"+
    "\1\0\4\123\1\0\3\124\3\0\3\123\1\213\11\123"+
    "\3\0\3\123\2\0\1\214\102\0\1\171\54\0\1\215"+
    "\34\0\1\216\52\0\1\172\3\0\4\173\7\0\15\173"+
    "\3\0\3\173\12\0\1\172\1\0\1\217\1\0\4\123"+
    "\1\0\3\124\3\0\15\123\3\0\3\123\16\0\1\220"+
    "\1\132\1\220\1\132\7\0\15\220\3\0\3\220\16\0"+
    "\4\140\7\0\15\140\3\0\3\140\16\0\4\144\7\0"+
    "\15\144\3\0\3\144\16\0\4\147\7\0\15\147\3\0"+
    "\3\147\16\0\4\152\7\0\15\152\3\0\3\152\16\0"+
    "\1\221\1\144\1\221\1\144\7\0\15\221\3\0\3\221"+
    "\16\0\4\132\7\0\15\132\3\0\3\132\16\0\4\222"+
    "\7\0\15\222\3\0\3\222\33\0\1\223\61\0\1\224"+
    "\30\0\4\40\6\0\1\225\15\40\3\0\2\40\1\226"+
    "\33\0\1\227\32\0\1\172\1\0\1\36\1\0\4\123"+
    "\1\0\3\124\3\0\10\123\1\230\4\123\3\0\3\123"+
    "\2\0\1\231\104\0\1\232\36\0\4\233\7\0\15\233"+
    "\3\0\3\233\3\0\1\175\1\0\1\102\2\176\6\0"+
    "\1\220\1\132\1\220\1\132\7\0\15\220\3\0\3\220"+
    "\3\0\1\205\1\0\1\102\2\77\1\0\1\100\3\0"+
    "\1\100\1\221\1\144\1\221\1\144\7\0\15\221\3\0"+
    "\3\221\3\0\1\204\2\0\1\204\7\0\4\222\7\0"+
    "\15\222\3\0\3\222\34\0\1\234\55\0\1\235\26\0"+
    "\1\236\60\0\4\40\6\0\1\225\15\40\3\0\3\40"+
    "\34\0\1\237\31\0\1\172\1\0\1\111\1\0\4\123"+
    "\1\0\3\124\3\0\15\123\3\0\3\123\34\0\1\240"+
    "\32\0\1\241\2\0\4\233\7\0\15\233\3\0\3\233"+
    "\35\0\1\242\62\0\1\243\20\0\1\244\77\0\1\245"+
    "\53\0\1\246\32\0\1\36\1\0\4\173\1\0\3\124"+
    "\3\0\15\173\3\0\3\173\36\0\1\247\53\0\1\250"+
    "\33\0\4\251\7\0\15\251\3\0\3\251\36\0\1\252"+
    "\53\0\1\253\54\0\1\254\61\0\1\255\11\0\1\256"+
    "\12\0\4\251\7\0\15\251\3\0\3\251\37\0\1\257"+
    "\53\0\1\260\54\0\1\261\22\0\1\13\62\0\4\262"+
    "\7\0\15\262\3\0\3\262\40\0\1\263\53\0\1\264"+
    "\43\0\1\265\26\0\2\262\1\0\2\262\1\0\2\262"+
    "\2\0\5\262\7\0\15\262\3\0\4\262\27\0\1\266"+
    "\53\0\1\267\24\0";

  private static int [] zzUnpackTrans() {
    int [] result = new int[7040];
    int offset = 0;
    offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackTrans(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      value--;
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /* error codes */
  private static final int ZZ_UNKNOWN_ERROR = 0;
  private static final int ZZ_NO_MATCH = 1;
  private static final int ZZ_PUSHBACK_2BIG = 2;

  /* error messages for the codes above */
  private static final String ZZ_ERROR_MSG[] = {
    "Unkown internal scanner error",
    "Error: could not match input",
    "Error: pushback value was too large"
  };

  /**
   * ZZ_ATTRIBUTE[aState] contains the attributes of state <code>aState</code>
   */
  private static final int [] ZZ_ATTRIBUTE = zzUnpackAttribute();

  private static final String ZZ_ATTRIBUTE_PACKED_0 =
    "\12\0\1\11\7\1\1\11\3\1\1\11\6\1\1\11"+
    "\2\1\1\11\14\1\1\11\6\1\2\11\3\0\1\11"+
    "\14\0\2\1\2\11\1\1\1\0\2\1\1\0\1\1"+
    "\1\0\1\1\3\0\7\1\2\0\1\1\1\0\15\1"+
    "\3\0\1\1\1\11\3\0\1\1\1\11\5\0\1\1"+
    "\4\0\1\1\2\0\2\1\2\0\1\1\5\0\1\11"+
    "\3\1\3\0\1\1\2\0\1\11\30\0\1\1\2\0"+
    "\3\11";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[183];
    int offset = 0;
    offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAttribute(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /** the input device */
  private java.io.Reader zzReader;

  /** the current state of the DFA */
  private int zzState;

  /** the current lexical state */
  private int zzLexicalState = YYINITIAL;

  /** this buffer contains the current text to be matched and is
      the source of the yytext() string */
  private char zzBuffer[] = new char[ZZ_BUFFERSIZE];

  /** the textposition at the last accepting state */
  private int zzMarkedPos;

  /** the current text position in the buffer */
  private int zzCurrentPos;

  /** startRead marks the beginning of the yytext() string in the buffer */
  private int zzStartRead;

  /** endRead marks the last character in the buffer, that has been read
      from input */
  private int zzEndRead;

  /** number of newlines encountered up to the start of the matched text */
  private int yyline;

  /** the number of characters up to the start of the matched text */
  private int yychar;

  /**
   * the number of characters from the last newline up to the start of the 
   * matched text
   */
  private int yycolumn;

  /** 
   * zzAtBOL == true <=> the scanner is currently at the beginning of a line
   */
  private boolean zzAtBOL = true;

  /** zzAtEOF == true <=> the scanner is at the EOF */
  private boolean zzAtEOF;

  /** denotes if the user-EOF-code has already been executed */
  private boolean zzEOFDone;

  /* user code: */

public static final int ALPHANUM          = WikipediaTokenizer.ALPHANUM_ID;
public static final int APOSTROPHE        = WikipediaTokenizer.APOSTROPHE_ID;
public static final int ACRONYM           = WikipediaTokenizer.ACRONYM_ID;
public static final int COMPANY           = WikipediaTokenizer.COMPANY_ID;
public static final int EMAIL             = WikipediaTokenizer.EMAIL_ID;
public static final int HOST              = WikipediaTokenizer.HOST_ID;
public static final int NUM               = WikipediaTokenizer.NUM_ID;
public static final int CJ                = WikipediaTokenizer.CJ_ID;
public static final int INTERNAL_LINK     = WikipediaTokenizer.INTERNAL_LINK_ID;
public static final int EXTERNAL_LINK     = WikipediaTokenizer.EXTERNAL_LINK_ID;
public static final int CITATION          = WikipediaTokenizer.CITATION_ID;
public static final int CATEGORY          = WikipediaTokenizer.CATEGORY_ID;
public static final int BOLD              = WikipediaTokenizer.BOLD_ID;
public static final int ITALICS           = WikipediaTokenizer.ITALICS_ID;
public static final int BOLD_ITALICS      = WikipediaTokenizer.BOLD_ITALICS_ID;
public static final int HEADING           = WikipediaTokenizer.HEADING_ID;
public static final int SUB_HEADING       = WikipediaTokenizer.SUB_HEADING_ID;
public static final int EXTERNAL_LINK_URL = WikipediaTokenizer.EXTERNAL_LINK_URL_ID;


private int currentTokType;
private int numBalanced = 0;
private int positionInc = 1;
private int numLinkToks = 0;
//Anytime we start a new on a Wiki reserved token (category, link, etc.) this value will be 0, otherwise it will be the number of tokens seen
//this can be useful for detecting when a new reserved token is encountered
//see https://issues.apache.org/jira/browse/LUCENE-1133
private int numWikiTokensSeen = 0;

public static final String [] TOKEN_TYPES = WikipediaTokenizer.TOKEN_TYPES;

/**
Returns the number of tokens seen inside a category or link, etc.
@return the number of tokens seen inside the context of wiki syntax.
**/
public final int getNumWikiTokensSeen(){
  return numWikiTokensSeen;
}

public final int yychar()
{
    return yychar;
}

public final int getPositionIncrement(){
  return positionInc;
}

/**
 * Fills Lucene token with the current token text.
 */
final void getText(CharTermAttribute t) {
  t.copyBuffer(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
}

final int setText(StringBuilder buffer){
  int length = zzMarkedPos - zzStartRead;
  buffer.append(zzBuffer, zzStartRead, length);
  return length;
}




  /**
   * Creates a new scanner
   * There is also a java.io.InputStream version of this constructor.
   *
   * @param   in  the java.io.Reader to read input from.
   */
  WikipediaTokenizerImpl(java.io.Reader in) {
    this.zzReader = in;
  }

  /**
   * Creates a new scanner.
   * There is also java.io.Reader version of this constructor.
   *
   * @param   in  the java.io.Inputstream to read input from.
   */
  WikipediaTokenizerImpl(java.io.InputStream in) {
    this(new java.io.InputStreamReader(in));
  }

  /** 
   * Unpacks the compressed character translation table.
   *
   * @param packed   the packed character translation table
   * @return         the unpacked character translation table
   */
  private static char [] zzUnpackCMap(String packed) {
    char [] map = new char[0x10000];
    int i = 0;  /* index in packed string  */
    int j = 0;  /* index in unpacked array */
    while (i < 230) {
      int  count = packed.charAt(i++);
      char value = packed.charAt(i++);
      do map[j++] = value; while (--count > 0);
    }
    return map;
  }


  /**
   * Refills the input buffer.
   *
   * @return      <code>false</code>, iff there was new input.
   * 
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  private boolean zzRefill() throws java.io.IOException {

    /* first: make room (if you can) */
    if (zzStartRead > 0) {
      System.arraycopy(zzBuffer, zzStartRead,
                       zzBuffer, 0,
                       zzEndRead-zzStartRead);

      /* translate stored positions */
      zzEndRead-= zzStartRead;
      zzCurrentPos-= zzStartRead;
      zzMarkedPos-= zzStartRead;
      zzStartRead = 0;
    }

    /* is the buffer big enough? */
    if (zzCurrentPos >= zzBuffer.length) {
      /* if not: blow it up */
      char newBuffer[] = new char[zzCurrentPos*2];
      System.arraycopy(zzBuffer, 0, newBuffer, 0, zzBuffer.length);
      zzBuffer = newBuffer;
    }

    /* finally: fill the buffer with new input */
    int numRead = zzReader.read(zzBuffer, zzEndRead,
                                            zzBuffer.length-zzEndRead);

    if (numRead > 0) {
      zzEndRead+= numRead;
      return false;
    }
    // unlikely but not impossible: read 0 characters, but not at end of stream    
    if (numRead == 0) {
      int c = zzReader.read();
      if (c == -1) {
        return true;
      } else {
        zzBuffer[zzEndRead++] = (char) c;
        return false;
      }     
    }

	// numRead < 0
    return true;
  }

    
  /**
   * Closes the input stream.
   */
  public final void yyclose() throws java.io.IOException {
    zzAtEOF = true;            /* indicate end of file */
    zzEndRead = zzStartRead;  /* invalidate buffer    */

    if (zzReader != null)
      zzReader.close();
  }


  /**
   * Resets the scanner to read from a new input stream.
   * Does not close the old reader.
   *
   * All internal variables are reset, the old input stream 
   * <b>cannot</b> be reused (internal buffer is discarded and lost).
   * Lexical state is set to <tt>ZZ_INITIAL</tt>.
   *
   * Internal scan buffer is resized down to its initial length, if it has grown.
   *
   * @param reader   the new input stream 
   */
  public final void yyreset(java.io.Reader reader) {
    zzReader = reader;
    zzAtBOL  = true;
    zzAtEOF  = false;
    zzEOFDone = false;
    zzEndRead = zzStartRead = 0;
    zzCurrentPos = zzMarkedPos = 0;
    yyline = yychar = yycolumn = 0;
    zzLexicalState = YYINITIAL;
    if (zzBuffer.length > ZZ_BUFFERSIZE)
      zzBuffer = new char[ZZ_BUFFERSIZE];
  }


  /**
   * Returns the current lexical state.
   */
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }


  /**
   * Returns the text matched by the current regular expression.
   */
  public final String yytext() {
    return new String( zzBuffer, zzStartRead, zzMarkedPos-zzStartRead );
  }


  /**
   * Returns the character at position <tt>pos</tt> from the 
   * matched text. 
   * 
   * It is equivalent to yytext().charAt(pos), but faster
   *
   * @param pos the position of the character to fetch. 
   *            A value from 0 to yylength()-1.
   *
   * @return the character at position pos
   */
  public final char yycharat(int pos) {
    return zzBuffer[zzStartRead+pos];
  }


  /**
   * Returns the length of the matched text region.
   */
  public final int yylength() {
    return zzMarkedPos-zzStartRead;
  }


  /**
   * Reports an error that occured while scanning.
   *
   * In a wellformed scanner (no or only correct usage of 
   * yypushback(int) and a match-all fallback rule) this method 
   * will only be called with things that "Can't Possibly Happen".
   * If this method is called, something is seriously wrong
   * (e.g. a JFlex bug producing a faulty scanner etc.).
   *
   * Usual syntax/scanner level error handling should be done
   * in error fallback rules.
   *
   * @param   errorCode  the code of the errormessage to display
   */
  private void zzScanError(int errorCode) {
    String message;
    try {
      message = ZZ_ERROR_MSG[errorCode];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
    }

    throw new Error(message);
  } 


  /**
   * Pushes the specified amount of characters back into the input stream.
   *
   * They will be read again by then next call of the scanning method
   *
   * @param number  the number of characters to be read again.
   *                This number must not be greater than yylength()!
   */
  public void yypushback(int number)  {
    if ( number > yylength() )
      zzScanError(ZZ_PUSHBACK_2BIG);

    zzMarkedPos -= number;
  }


  /**
   * Resumes scanning until the next regular expression is matched,
   * the end of input is encountered or an I/O-Error occurs.
   *
   * @return      the next token
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  public int getNextToken() throws java.io.IOException {
    int zzInput;
    int zzAction;

    // cached fields:
    int zzCurrentPosL;
    int zzMarkedPosL;
    int zzEndReadL = zzEndRead;
    char [] zzBufferL = zzBuffer;
    char [] zzCMapL = ZZ_CMAP;

    int [] zzTransL = ZZ_TRANS;
    int [] zzRowMapL = ZZ_ROWMAP;
    int [] zzAttrL = ZZ_ATTRIBUTE;

    while (true) {
      zzMarkedPosL = zzMarkedPos;

      yychar+= zzMarkedPosL-zzStartRead;

      zzAction = -1;

      zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;
  
      zzState = ZZ_LEXSTATE[zzLexicalState];

      // set up zzAction for empty match case:
      int zzAttributes = zzAttrL[zzState];
      if ( (zzAttributes & 1) == 1 ) {
        zzAction = zzState;
      }


      zzForAction: {
        while (true) {
    
          if (zzCurrentPosL < zzEndReadL)
            zzInput = zzBufferL[zzCurrentPosL++];
          else if (zzAtEOF) {
            zzInput = YYEOF;
            break zzForAction;
          }
          else {
            // store back cached positions
            zzCurrentPos  = zzCurrentPosL;
            zzMarkedPos   = zzMarkedPosL;
            boolean eof = zzRefill();
            // get translated positions and possibly new buffer
            zzCurrentPosL  = zzCurrentPos;
            zzMarkedPosL   = zzMarkedPos;
            zzBufferL      = zzBuffer;
            zzEndReadL     = zzEndRead;
            if (eof) {
              zzInput = YYEOF;
              break zzForAction;
            }
            else {
              zzInput = zzBufferL[zzCurrentPosL++];
            }
          }
          int zzNext = zzTransL[ zzRowMapL[zzState] + zzCMapL[zzInput] ];
          if (zzNext == -1) break zzForAction;
          zzState = zzNext;

          zzAttributes = zzAttrL[zzState];
          if ( (zzAttributes & 1) == 1 ) {
            zzAction = zzState;
            zzMarkedPosL = zzCurrentPosL;
            if ( (zzAttributes & 8) == 8 ) break zzForAction;
          }

        }
      }

      // store back cached position
      zzMarkedPos = zzMarkedPosL;

      switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
        case 16: 
          { currentTokType = HEADING; yybegin(DOUBLE_EQUALS_STATE); numWikiTokensSeen++; return currentTokType;
          }
        case 46: break;
        case 39: 
          { positionInc = 1; return ACRONYM;
          }
        case 47: break;
        case 8: 
          { /* ignore */
          }
        case 48: break;
        case 20: 
          { numBalanced = 0; numWikiTokensSeen = 0; currentTokType = EXTERNAL_LINK;yybegin(EXTERNAL_LINK_STATE);
          }
        case 49: break;
        case 35: 
          { positionInc = 1; return COMPANY;
          }
        case 50: break;
        case 4: 
          { numWikiTokensSeen = 0; positionInc = 1; currentTokType = EXTERNAL_LINK_URL; yybegin(EXTERNAL_LINK_STATE);
          }
        case 51: break;
        case 25: 
          { numWikiTokensSeen = 0; positionInc = 1; currentTokType = CITATION; yybegin(DOUBLE_BRACE_STATE);
          }
        case 52: break;
        case 43: 
          { numWikiTokensSeen = 0; positionInc = 1; currentTokType = CATEGORY; yybegin(CATEGORY_STATE);
          }
        case 53: break;
        case 22: 
          { numWikiTokensSeen = 0; positionInc = 1; if (numBalanced == 0){numBalanced++;yybegin(TWO_SINGLE_QUOTES_STATE);} else{numBalanced = 0;}
          }
        case 54: break;
        case 34: 
          { positionInc = 1; return NUM;
          }
        case 55: break;
        case 32: 
          { positionInc = 1; return APOSTROPHE;
          }
        case 56: break;
        case 23: 
          { numWikiTokensSeen = 0; positionInc = 1; yybegin(DOUBLE_EQUALS_STATE);
          }
        case 57: break;
        case 21: 
          { yybegin(STRING); return currentTokType;/*pipe*/
          }
        case 58: break;
        case 2: 
          { positionInc = 1; return ALPHANUM;
          }
        case 59: break;
        case 29: 
          { currentTokType = INTERNAL_LINK; numWikiTokensSeen = 0;  yybegin(INTERNAL_LINK_STATE);
          }
        case 60: break;
        case 17: 
          { yybegin(DOUBLE_BRACE_STATE); numWikiTokensSeen = 0; return currentTokType;
          }
        case 61: break;
        case 44: 
          { currentTokType = CATEGORY; numWikiTokensSeen = 0; yybegin(CATEGORY_STATE);
          }
        case 62: break;
        case 26: 
          { yybegin(YYINITIAL);
          }
        case 63: break;
        case 3: 
          { positionInc = 1; return CJ;
          }
        case 64: break;
        case 38: 
          { numBalanced = 0;currentTokType = ALPHANUM; yybegin(YYINITIAL);/*end sub header*/
          }
        case 65: break;
        case 15: 
          { currentTokType = SUB_HEADING; numWikiTokensSeen = 0; yybegin(STRING);
          }
        case 66: break;
        case 30: 
          { numBalanced = 0;currentTokType = ALPHANUM; yybegin(YYINITIAL);/*end italics*/
          }
        case 67: break;
        case 6: 
          { yybegin(CATEGORY_STATE); numWikiTokensSeen++; return currentTokType;
          }
        case 68: break;
        case 5: 
          { positionInc = 1;
          }
        case 69: break;
        case 19: 
          { yybegin(STRING); numWikiTokensSeen++; return currentTokType;/* STRING ALPHANUM*/
          }
        case 70: break;
        case 42: 
          { positionInc = 1; numWikiTokensSeen++; yybegin(EXTERNAL_LINK_STATE); return currentTokType;
          }
        case 71: break;
        case 27: 
          { numLinkToks = 0; yybegin(YYINITIAL);
          }
        case 72: break;
        case 11: 
          { currentTokType = BOLD;  yybegin(THREE_SINGLE_QUOTES_STATE);
          }
        case 73: break;
        case 13: 
          { currentTokType = EXTERNAL_LINK; numWikiTokensSeen = 0; yybegin(EXTERNAL_LINK_STATE);
          }
        case 74: break;
        case 14: 
          { yybegin(STRING); numWikiTokensSeen++; return currentTokType;
          }
        case 75: break;
        case 45: 
          { numBalanced = 0; numWikiTokensSeen = 0; currentTokType = CATEGORY;yybegin(CATEGORY_STATE);
          }
        case 76: break;
        case 28: 
          { currentTokType = INTERNAL_LINK; numWikiTokensSeen = 0; yybegin(INTERNAL_LINK_STATE);
          }
        case 77: break;
        case 37: 
          { numBalanced = 0;currentTokType = ALPHANUM;yybegin(YYINITIAL);/*end bold*/
          }
        case 78: break;
        case 9: 
          { if (numLinkToks == 0){positionInc = 0;} else{positionInc = 1;} numWikiTokensSeen++; currentTokType = EXTERNAL_LINK; yybegin(EXTERNAL_LINK_STATE); numLinkToks++; return currentTokType;
          }
        case 79: break;
        case 7: 
          { yybegin(INTERNAL_LINK_STATE); numWikiTokensSeen++; return currentTokType;
          }
        case 80: break;
        case 24: 
          { numWikiTokensSeen = 0; positionInc = 1; currentTokType = INTERNAL_LINK; yybegin(INTERNAL_LINK_STATE);
          }
        case 81: break;
        case 40: 
          { positionInc = 1; return EMAIL;
          }
        case 82: break;
        case 1: 
          { numWikiTokensSeen = 0;  positionInc = 1;
          }
        case 83: break;
        case 18: 
          { /* ignore STRING */
          }
        case 84: break;
        case 36: 
          { currentTokType = BOLD_ITALICS;  yybegin(FIVE_SINGLE_QUOTES_STATE);
          }
        case 85: break;
        case 33: 
          { positionInc = 1; return HOST;
          }
        case 86: break;
        case 31: 
          { numBalanced = 0; numWikiTokensSeen = 0; currentTokType = INTERNAL_LINK;yybegin(INTERNAL_LINK_STATE);
          }
        case 87: break;
        case 41: 
          { numBalanced = 0;currentTokType = ALPHANUM; yybegin(YYINITIAL);/*end bold italics*/
          }
        case 88: break;
        case 12: 
          { currentTokType = ITALICS; numWikiTokensSeen++;  yybegin(STRING); return currentTokType;/*italics*/
          }
        case 89: break;
        case 10: 
          { numLinkToks = 0; positionInc = 0; yybegin(YYINITIAL);
          }
        case 90: break;
        default: 
          if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
            zzAtEOF = true;
            return YYEOF;
          } 
          else {
            zzScanError(ZZ_NO_MATCH);
          }
      }
    }
  }


}
