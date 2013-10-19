package org.apache.lucene.analysis.ko.morph;

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

import org.apache.lucene.analysis.ko.dic.DictionaryUtil;
import org.apache.lucene.analysis.ko.dic.SyllableFeatures;

class EomiUtil {
  private EomiUtil() {}

  static final String RESULT_FAIL = "0";
  
  static final String RESULT_SUCCESS = "1";
  
  static final String[] verbSuffix = {
      "이","하","되","스럽","스러우","시키","있","없","같","당하","만하","드리","받","나","내"
  };
  
  /**
   * 선어말어미를 분석한다.
   */
  public static String[] splitPomi(String stem)  {

    //   results[0]:성공(1)/실패(0), results[1]: 어근, results[2]: 선어말어미
    String[] results = new String[2];  
    results[0] = stem;

    if(stem==null||stem.length()==0||"있".equals(stem)) return results;
  
    char[] chrs = stem.toCharArray();
    int len = chrs.length;
    String pomi = "";
    int index = len-1;
  
    char[] jaso = MorphUtil.decompose(chrs[index]);
    if(chrs[index]!='시'&&chrs[index]!='ㅆ'&&jaso[jaso.length-1]!='ㅆ') return results;  // 선어말어미가 발견되지 않았다
    
    if(chrs[index]=='겠') {
      pomi = "겠";
      setPomiResult(results,stem.substring(0,index),pomi);    
      if(--index<=0||
          (chrs[index]!='시'&&chrs[index]!='ㅆ'&&jaso[jaso.length-1]!='ㅆ')) 
        return results; // 다음이거나 선어말어미가 없다면...
      jaso = MorphUtil.decompose(chrs[index]);
    }

    if(chrs[index]=='었') { // 시었, ㅆ었, 었
      pomi = chrs[index]+pomi;  
      setPomiResult(results,stem.substring(0,index),pomi);    
      if(--index<=0||
          (chrs[index]!='시'&&chrs[index]!='ㅆ'&&jaso[jaso.length-1]!='ㅆ')) 
        return results; // 다음이거나 선어말어미가 없다면...        
      jaso = MorphUtil.decompose(chrs[index]);
    }

    if(chrs[index]=='였'){
      pomi = MorphUtil.replaceJongsung('어',chrs[index])+pomi;  
      if(index>0&&chrs[index-1]=='하') 
        stem = stem.substring(0,index);  
      else
        stem = stem.substring(0,index)+"이";
      setPomiResult(results,stem,pomi);  
    }else if(chrs[index]=='셨'){
      pomi = MorphUtil.replaceJongsung('어',chrs[index])+pomi;  
      stem = stem.substring(0,index);    
      setPomiResult(results,stem,"시"+pomi);        
    }else if(chrs[index]=='았'||chrs[index]=='었') {
      pomi = chrs[index]+pomi;  
      setPomiResult(results,stem.substring(0,index),pomi);    
      if(--index<=0||
          (chrs[index]!='시'&&chrs[index]!='으')) return results; // 다음이거나 선어말어미가 없다면...        
      jaso = MorphUtil.decompose(chrs[index]);    
    }else if(jaso.length==3&&jaso[2]=='ㅆ') {
    
      if(jaso[0]=='ㅎ'&&jaso[1]=='ㅐ') {       
        pomi = MorphUtil.replaceJongsung('어',chrs[index])+pomi;  
        stem = stem.substring(0,index)+"하";  
      }else if(jaso[0]!='ㅇ'&&(jaso[1]=='ㅏ'||jaso[1]=='ㅓ'||jaso[1]=='ㅔ'||jaso[1]=='ㅐ')) {    
        pomi = "었"+pomi;
        stem = stem.substring(0,index)+MorphUtil.makeChar(chrs[index], 0);        
      }else if(jaso[0]!='ㅇ'&&(jaso[1]=='ㅙ')) {
        pomi = "었"+pomi;
        stem = stem.substring(0,index)+MorphUtil.makeChar(chrs[index],11, 0);        
      } else if(jaso[1]=='ㅘ') {      
        pomi = MorphUtil.replaceJongsung('아',chrs[index])+pomi;  
        stem = stem.substring(0,index)+MorphUtil.makeChar(chrs[index],8, 0);
      } else if(jaso[1]=='ㅝ') {
        pomi = MorphUtil.replaceJongsung('어',chrs[index])+pomi;  
        stem = stem.substring(0,index)+MorphUtil.makeChar(chrs[index],13, 0);
      } else if(jaso[1]=='ㅕ') {          
        pomi = MorphUtil.replaceJongsung('어',chrs[index])+pomi;        
        stem = stem.substring(0,index)+MorphUtil.makeChar(chrs[index],20, 0);          
      } else if(jaso[1]=='ㅐ') {
        pomi = MorphUtil.replaceJongsung('어',chrs[index])+pomi;
        stem = stem.substring(0,index);
      } else if(jaso[1]=='ㅒ') {
        pomi = MorphUtil.replaceJongsung('애',chrs[index])+pomi;  
        stem = stem.substring(0,index);
      } else {
        pomi = "었"+pomi;
      }
      setPomiResult(results,stem,pomi);        
      if(chrs[index]!='시'&&chrs[index]!='으') return results; // 다음이거나 선어말어미가 없다면...        
      jaso = MorphUtil.decompose(chrs[index]);        
    }

    char[] nChrs = null;
    if(index>0) nChrs = MorphUtil.decompose(chrs[index-1]);
    else nChrs = new char[2];

    if(nChrs.length==2&&chrs[index]=='시'&&(chrs.length<=index+1||
        (chrs.length>index+1&&chrs[index+1]!='셨'))) {
      if(DictionaryUtil.getWord(results[0])!=null) return results;  //'시'가 포함된 단어가 있다. 성가시다/도시다/들쑤시다 
      pomi = chrs[index]+pomi;  
      setPomiResult(results,stem.substring(0,index),pomi);      
      if(--index==0||chrs[index]!='으') return results; // 다음이거나 선어말어미가 없다면...        
      jaso = MorphUtil.decompose(chrs[index]);
    }
    
    if(index>0) nChrs = MorphUtil.decompose(chrs[index-1]);
    else nChrs = new char[2];
    if(chrs.length>index+1&&nChrs.length==3&&(chrs[index+1]=='셨'||chrs[index+1]=='시')&&chrs[index]=='으') {
      pomi = chrs[index]+pomi;  
      setPomiResult(results,stem.substring(0,index),pomi);    
    }
  
    return results;
  }
   
  private static void setPomiResult(String[] results,String stem, String pomi ) {
    results[0] = stem;
    results[1] = pomi;
  }  
  
  static boolean IsNLMBSyl(char ech, char lch) {
    switch(lch) {
      case 'ㄴ' : 
        return SyllableFeatures.hasFeature(ech, SyllableFeatures.YNPNA) || SyllableFeatures.hasFeature(ech, SyllableFeatures.YNPLN);
      case 'ㄹ' : 
        return SyllableFeatures.hasFeature(ech, SyllableFeatures.YNPLA);
      case 'ㅁ' : 
        return SyllableFeatures.hasFeature(ech, SyllableFeatures.YNPMA);
      case 'ㅂ' : 
        return SyllableFeatures.hasFeature(ech, SyllableFeatures.YNPBA);
      default: 
        return false;
    }
  }
  
  /**
   * 어미를 분리한다.
   * 
   * 1. 규칙용언과 어간만 바뀌는 불규칙 용언
   * 2. 어미가 종성 'ㄴ/ㄹ/ㅁ/ㅂ'으로 시작되는 어절
   * 3. '여/거라/너라'의 불규칙 어절
   * 4. 어미 '아/어'가 탈락되는 어절
   * 5. '아/어'의 변이체 분리
   */
  public static String[] splitEomi(String stem, String end) {

    String[] strs = new String[2];
    int strlen = stem.length();
    if(strlen==0) return strs;
   
    char estem = stem.charAt(strlen-1);
    char[] chrs = MorphUtil.decompose(estem);
    if(chrs.length==1) return strs; // 한글이 아니라면...

    if((chrs.length==3)
        &&(chrs[2]=='ㄴ'||chrs[2]=='ㄹ'||chrs[2]=='ㅁ'||chrs[2]=='ㅂ')
        &&EomiUtil.IsNLMBSyl(estem,chrs[2])
        && DictionaryUtil.combineAndEomiCheck(chrs[2], end)!=null) 
    {    
      strs[1] = Character.toString(chrs[2]);
      if(end.length()>0) strs[1] += end;
      
   	  strs[0] = stem.substring(0,strlen-1) + MorphUtil.makeChar(estem, 0);  
    } 
    else if(chrs.length==3 && chrs[2]=='ㄹ' && DictionaryUtil.getVerb(stem)!=null 
    		&& DictionaryUtil.combineAndEomiCheck(chrs[2], end)!=null) 
    {
        strs[1] = Character.toString(chrs[2]);
        if(end.length()>0) strs[1] += end;
        strs[0] = stem; // "만들 때와는"에서 "만들"과 같은 경우
    }
    else if(estem=='해'&&DictionaryUtil.existEomi("어"+end))
    {      
      strs[0] = stem.substring(0,strlen-1)+"하";
      strs[1] = "어"+end;  
    }
    else if(estem=='히'&&DictionaryUtil.existEomi("이"+end)) 
    {      
      strs[0] = stem.substring(0,strlen-1)+"하";
      strs[1] = "이"+end;        
    } 
    else if(chrs[0]!='ㅇ'&&
        (chrs[1]=='ㅏ'||chrs[1]=='ㅓ'||chrs[1]=='ㅔ'||chrs[1]=='ㅐ')&&
        (chrs.length==2 || SyllableFeatures.hasFeature(estem, SyllableFeatures.YNPAH)) &&
        (DictionaryUtil.combineAndEomiCheck('어', end)!=null)) 
    {        
      strs[0] = stem;
      if(chrs.length==2) strs[1] = "어"+end;  
      else strs[1] = end;    
    } 
    else if(estem=='하'&&end!=null&&end.startsWith("여")&&
        DictionaryUtil.combineAndEomiCheck('어', end.substring(1))!=null) 
    {      
      strs[0] = stem;
      strs[1] = "어"+end.substring(1);  
    }
    else if(estem=='려'&&end!=null&& // 꺼려=>꺼리어, 꺼려서=>꺼리어서
          DictionaryUtil.combineAndEomiCheck('어', end)!=null) 
    {      
        strs[0] = stem.substring(0,stem.length()-1)+"리";
        strs[1] = "어"+end;        
    }
    else if((chrs.length==2)&&
        (chrs[1]=='ㅘ'||chrs[1]=='ㅙ'||chrs[1]=='ㅝ'||chrs[1]=='ㅕ'||chrs[1]=='ㅐ'||chrs[1]=='ㅒ')&&
        (DictionaryUtil.combineAndEomiCheck('어', end)!=null)) 
    {    
  
      StringBuffer sb = new StringBuffer();
      
      if(strlen>1) sb.append(stem.substring(0,strlen-1));
      
      if(chrs[1]=='ㅘ')
        sb.append(MorphUtil.makeChar(estem, 8, 0)).append(MorphUtil.replaceJongsung('아',estem));  
      else if(chrs[1]=='ㅝ')
        sb.append(MorphUtil.makeChar(estem, 13, 0)).append(MorphUtil.replaceJongsung('어',estem));  
      else if(chrs[1]=='ㅙ')
        sb.append(MorphUtil.makeChar(estem, 11, 0)).append(MorphUtil.replaceJongsung('어',estem));        
      else if(chrs[1]=='ㅕ')
        sb.append(Character.toString(MorphUtil.makeChar(estem, 20, 0))).append(MorphUtil.replaceJongsung('어',estem));
      else if(chrs[1]=='ㅐ')
        sb.append(MorphUtil.makeChar(estem, 0, 0)).append(MorphUtil.replaceJongsung('어',estem));
      else if(chrs[1]=='ㅒ')
        sb.append(MorphUtil.makeChar(estem, 20, 0)).append(MorphUtil.replaceJongsung('애',estem));  
    
      strs[0] = sb.toString();
    
      end = strs[0].substring(strs[0].length()-1)+end;        
      strs[0] = strs[0].substring(0,strs[0].length()-1);
      
      strs[1] = end;    

    } else if(!"".equals(end)&&DictionaryUtil.existEomi(end)) 
    {    
      strs = new String[]{stem, end};
    }
    
    return strs;
  }
}
