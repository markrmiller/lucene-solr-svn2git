package org.apache.lucene.analysis.kuromoji.dict;

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

import java.io.File;
import java.io.IOException;

public class UnknownDictionary extends TokenInfoDictionary {
  
  public static final String FILENAME = "unk.dat";
  
  public static final String TARGETMAP_FILENAME = "unk_map.dat";
  
  private CharacterDefinition characterDefinition;
  
  /**
   * Constructor
   */
  public UnknownDictionary() {
  }
  
  public UnknownDictionary(int size) {
    super(size);
    characterDefinition = new CharacterDefinition();    	
  }
  
  @Override
  public int put(String[] entry) {
    // Get wordId of current entry
    int wordId = buffer.position();
    
    // Put entry
    int result = super.put(entry);
    
    // Put entry in targetMap
    int characterId = CharacterDefinition.lookupCharacterClass(entry[0]);
    addMapping(characterId, wordId);
    return result;
  }
  
  public int lookup(String text) {
    if(!characterDefinition.isGroup(text.charAt(0))) {
      return 1;
    }
    
    // Extract unknown word. Characters with the same character class are considered to be part of unknown word
    byte characterIdOfFirstCharacter = characterDefinition.getCharacterClass(text.charAt(0));
    int length = 1;
    for (int i = 1; i < text.length(); i++) {
      if (characterIdOfFirstCharacter == characterDefinition.getCharacterClass(text.charAt(i))){
        length++;    			
      } else {
        break;
      }
    }
    
    return length;
  }
  
  /**
   * Put mapping from unicode code point to character class.
   * 
   * @param codePoint code point
   * @param characterClassName character class name
   */
  public void putCharacterCategory(int codePoint, String characterClassName) {
    characterDefinition.putCharacterCategory(codePoint, characterClassName);
  }
  
  public void putInvokeDefinition(String characterClassName, int invoke, int group, int length) {
    characterDefinition.putInvokeDefinition(characterClassName, invoke, group, length);
  }
  
  
  public CharacterDefinition getCharacterDefinition() {
    return characterDefinition;
  }
  
  /**
   * Write dictionary in file
   * Dictionary format is:
   * [Size of dictionary(int)], [entry:{left id(short)}{right id(short)}{word cost(short)}{length of pos info(short)}{pos info(char)}], [entry...], [entry...].....
   * @throws IOException
   */
  public void write(String directoryname) throws IOException {
    writeDictionary(directoryname + File.separator + FILENAME);
    writeTargetMap(directoryname + File.separator + TARGETMAP_FILENAME);
    characterDefinition.write(directoryname);
  }
  
  public static UnknownDictionary getInstance() throws IOException, ClassNotFoundException {
    UnknownDictionary dictionary = new UnknownDictionary();
    dictionary.characterDefinition = CharacterDefinition.getInstance();
    dictionary.loadDictionary(UnknownDictionary.class.getResourceAsStream(FILENAME));
    dictionary.loadTargetMap(UnknownDictionary.class.getResourceAsStream(TARGETMAP_FILENAME));
    return dictionary;
  }
  
  @Override
  public String getReading(int wordId) {
    return null;
  }
}
