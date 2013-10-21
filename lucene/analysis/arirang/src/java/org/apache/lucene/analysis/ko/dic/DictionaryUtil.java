package org.apache.lucene.analysis.ko.dic;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.ko.utils.Trie;

public class DictionaryUtil {
  private DictionaryUtil() {}
  
  private static final Trie<String,WordEntry> dictionary = new Trie<String, WordEntry>(false);
  
  private static final Set<String> josas = new HashSet<String>();
  
  private static final Set<String> eomis = new HashSet<String>();;
  
  private static final Set<String> prefixs = new HashSet<String>();;
  
  private static final Set<String> suffixs = new HashSet<String>();;
  
  private static final Set<String> uncompounds = new HashSet<String>();
  
  static {  
    try {
      final LineProcessor proc = new LineProcessor() {
        @Override
        public void processLine(String line) throws IOException {
          String[] infos = line.split("[,]+");
          if (infos.length != 2) {
            throw new IOException("Invalid file format: " + line);
          }
          if (infos[1].length() != 10) {
            throw new IOException("Invalid file format: " + line);
          }
          
          WordEntry entry = new WordEntry(infos[0].trim(),infos[1].toCharArray(), null);
          dictionary.add(entry.getWord(), entry);          
        }
      };
      DictionaryResources.readLines(DictionaryResources.FILE_DICTIONARY, proc);
      DictionaryResources.readLines(DictionaryResources.FILE_EXTENSION, proc);
      
      DictionaryResources.readLines(DictionaryResources.FILE_COMPOUNDS, new LineProcessor() {
        @Override
        public void processLine(String compound) throws IOException {
          String[] infos = compound.split("[:]+");
          if (infos.length != 3) {
            throw new IOException("Invalid file format: " + compound);
          }
          if (infos[2].length() != 4) {
            throw new IOException("Illegal file format: " + compound);
          }
          
          final List<CompoundEntry> c = compoundArrayToList(infos[1], infos[1].split("[,]+"));
          final WordEntry entry = new WordEntry(infos[0].trim(),("200"+infos[2]+"00X").toCharArray(), c);
          dictionary.add(entry.getWord(), entry);          
        }       
      }); 
      
      DictionaryResources.readLines(DictionaryResources.FILE_UNCOMPOUNDS, new LineProcessor() {
        @Override
        public void processLine(String compound) throws IOException {
          String[] infos = compound.split("[:]+");
          if(infos.length!=2) {
            throw new IOException("Invalid file format: "+compound);
          }
          uncompounds.add(infos[1]);
        }
      });

      readFileToSet(josas,DictionaryResources.FILE_JOSA);
      
      readFileToSet(eomis,DictionaryResources.FILE_EOMI);
      
      readFileToSet(prefixs,DictionaryResources.FILE_PREFIX);
  
      readFileToSet(suffixs,DictionaryResources.FILE_SUFFIX);
      
    } catch (IOException e) {
      throw new Error("Cannot load resource",e);
    }
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  public static Iterator<String[]> findWithPrefix(String prefix) {
    return dictionary.getPrefixedBy(prefix);
  }

  public static WordEntry getWord(String key) {    
    if(key.length()==0) return null;
    
    return (WordEntry)dictionary.get(key);
  }
  
  public static WordEntry getWordExceptVerb(String key) {
    WordEntry entry = getWord(key);
    if (entry != null && (entry.isNoun() || entry.isAdverb())) {
      return entry;
    } else {
      return null;
    }
  }
  
  public static WordEntry getNoun(String key) {  
    WordEntry entry = getWord(key);
    if (entry != null && entry.isNoun() && !entry.isCompoundNoun()) {
      return entry;
    } else {
      return null;
    }
  }
  
  /**
   * 
   * return all noun including compound noun
   * @param key the lookup key text
   * @return  WordEntry
   */
  public static WordEntry getAllNoun(String key) {  
    WordEntry entry = getWord(key);
    if (entry != null && entry.isNoun()) {
      return entry;
    } else {
      return null;
    }
  }
  
  public static WordEntry getVerb(String key) {
    WordEntry entry = getWord(key);  
    if (entry != null && entry.isVerb()) {
      return entry;
    } else {
      return null;
    }
  }
  
  public static WordEntry getBusa(String key) {
    WordEntry entry = getWord(key);
    if (entry != null && entry.isAdverb() && !entry.isNoun()) {
      return entry;
    } else {
      return null;
    }
  }
  
  // TODO: make this more efficient later
  public static boolean isUncompound(String before, String after) {
    return uncompounds.contains(before + "," + after);
  }
  
  public static boolean existJosa(String str) {
    return josas.contains(str);
  }
  
  public static boolean existEomi(String str) {
    return eomis.contains(str);
  }
  
  public static boolean existPrefix(String str) {
    return prefixs.contains(str);
  }
  
  public static boolean existSuffix(String str) {
    return suffixs.contains(str);
  }
  
  /**
   * ㄴ,ㄹ,ㅁ,ㅂ과 eomi 가 결합하여 어미가 될 수 있는지 점검한다.
   */
  public static String combineAndEomiCheck(char s, String eomi) {
  
    if(eomi==null) eomi="";

    if(s=='ㄴ') eomi = "은"+eomi;
    else if(s=='ㄹ') eomi = "을"+eomi;
    else if(s=='ㅁ') eomi = "음"+eomi;
    else if(s=='ㅂ') eomi = "습"+eomi;
    else eomi = s+eomi;

    if(existEomi(eomi)) return eomi;    

    return null;
    
  }
  
  private static void readFileToSet(final Set<String> set, String dic) throws IOException {    
    DictionaryResources.readLines(dic, new LineProcessor() {
      @Override
      public void processLine(String line) {
        set.add(line.trim());
      }
    });
  }
  
  private static List<CompoundEntry> compoundArrayToList(String source, String[] arr) {
    List<CompoundEntry> list = new ArrayList<CompoundEntry>();
    for(String str: arr) {
      list.add(new CompoundEntry(str, true));
    }
    return list;
  }
}
