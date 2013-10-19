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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.analysis.ko.dic.DictionaryUtil;
import org.apache.lucene.analysis.ko.utils.ConstraintUtil;
import org.apache.lucene.analysis.ko.utils.EomiUtil;
import org.apache.lucene.analysis.ko.utils.IrregularUtil;
import org.apache.lucene.analysis.ko.utils.MorphUtil;
import org.apache.lucene.analysis.ko.utils.NounUtil;
import org.apache.lucene.analysis.ko.utils.SyllableUtil;
import org.apache.lucene.analysis.ko.utils.VerbUtil;

public class MorphAnalyzer {

  /**
   * starting word of sentence.
   */
  public static final int POS_START = 1;
  
  /**
   * middle word of sentence
   */
  public static final int POS_MID = 2;
  
  /**
   * ending word of sentence.
   */
  public static final int POS_END = 3;    
  
  private CompoundNounAnalyzer cnAnalyzer = new CompoundNounAnalyzer();  
  
  public MorphAnalyzer() {
    cnAnalyzer.setExactMach(false);
  }
  
  public void setExactCompound(boolean is) {
    cnAnalyzer.setExactMach(is);
  }
  
  public List<AnalysisOutput> analyze(String input) {  

    if(input.endsWith("."))  
      return analyze(input.substring(0,input.length()-1), POS_END);
    
    return analyze(input, POS_MID);
  }
  
  /**
   * 
   * @param input input
   * @param pos pos
   * @return candidates
   */
  public List<AnalysisOutput> analyze(String input, int pos) {    

    List<AnalysisOutput> candidates = new ArrayList<AnalysisOutput>();        
    boolean isVerbOnly = MorphUtil.hasVerbOnly(input);

    analysisByRule(input, candidates);    
    
    if(!isVerbOnly||candidates.size()==0) addSingleWord(input,candidates);
  
    Collections.sort(candidates,new AnalysisOutputComparator<AnalysisOutput>());
    
    // 복합명사 분해여부 결정하여 분해
    boolean changed = false;
    boolean correct = false;
    for(AnalysisOutput o:candidates) {
    
      if(o.getScore()==AnalysisOutput.SCORE_CORRECT || isVerbOnly) {
        break;
//        if(o.getPatn()!=PatternConstants.PTN_NJ) correct=true;
//        // "활성화해"가 [활성화(N),하(t),어야(e)] 분석성공하였는데 [활성/화해]분해되는 것을 방지
//        if(o.getPatn()==PatternConstants.PTN_NSM) break; 
//        continue;
      }
      
      if(o.getPatn()<PatternConstants.PTN_VM&&o.getStem().length()>2) {
        if(!(correct&&o.getPatn()==PatternConstants.PTN_N)) confirmCNoun(o);
        if(o.getScore()>=AnalysisOutput.SCORE_COMPOUNDS) changed=true;
      }
    
    }
    
    if(correct)
      filterInCorrect(candidates);
    
    if(changed) {
      Collections.sort(candidates,new AnalysisOutputComparator<AnalysisOutput>());  
    }

    List<AnalysisOutput> results = new ArrayList<AnalysisOutput>();  
    
    boolean hasCorrect = false;
    boolean hasCorrectNoun = false;
    boolean correctCnoun = false;
    
    HashMap<String, AnalysisOutput> stems = new HashMap<String, AnalysisOutput>();
    AnalysisOutput noun = null;
    
    double ratio = 0;
    AnalysisOutput compound = null;
    
    for(AnalysisOutput o:candidates) { 
      
      o.setSource(input);
      if(o.getScore()==AnalysisOutput.SCORE_FAIL) continue; // 분석에는 성공했으나, 제약조건에 실패
      
      if(o.getScore()==AnalysisOutput.SCORE_CORRECT && o.getPos()!=PatternConstants.POS_NOUN ) 
      {
        addResults(o,results,stems);
        hasCorrect = true;
      }
      else if(o.getPos()==PatternConstants.POS_NOUN
          &&o.getScore()==AnalysisOutput.SCORE_CORRECT) 
      {
        
        if((hasCorrect||correctCnoun)&&o.getCNounList().size()>0) continue;
        
        if(o.getPos()==PatternConstants.POS_NOUN) 
        {
          addResults(o,results,stems);
        }
        else if(noun==null) 
        {
          addResults(o,results,stems);
          noun = o;
        }
        else if(o.getPatn()==PatternConstants.PTN_N
            ||(o.getPatn()>noun.getPatn())||
            (o.getPatn()==noun.getPatn()&&
                o.getJosa()!=null&&noun.getJosa()!=null
                &&o.getJosa().length()>noun.getJosa().length())) 
        {
          results.remove(noun);
          addResults(o,results,stems);
          noun = o;
        }
        hasCorrectNoun=true;
//        if(o.getCNounList().size()>0) correctCnoun = true;
      }
      else if(o.getPos()==PatternConstants.POS_NOUN
          &&o.getCNounList().size()>0&&!hasCorrect
          &&!hasCorrectNoun) 
      {
        double curatio = NounUtil.countFoundNouns(o);
        if(ratio<curatio&&(compound==null||(compound!=null&&compound.getJosa()==null))) {
          ratio  = curatio;
          compound = o;
        }
      }
      else if(o.getPos()==PatternConstants.POS_NOUN
          &&!hasCorrect
          &&!hasCorrectNoun&&compound==null) 
      {
        addResults(o,results,stems);
      }
      else if(o.getPatn()==PatternConstants.PTN_NSM) 
      {
        addResults(o,results,stems);
      } else {
//        System.out.println(o);
//    	  System.out.println("do nothing");
      }
    }      

    if(compound!=null) addResults(compound,results,stems);
    
    if(results.size()==0) {
      AnalysisOutput output = new AnalysisOutput(input, null, null, PatternConstants.PTN_N, AnalysisOutput.SCORE_ANALYSIS);
      output.setSource(input);
      output.setPos(PatternConstants.POS_NOUN);
      results.add(output);
    }
    
    return results;
  }
  
  /**
   * removed the candidate items when one more candidates in correct is found
   * @param candidates  analysis candidates
   */
  private void filterInCorrect(List<AnalysisOutput> candidates) {
    List<AnalysisOutput> removeds = new ArrayList<AnalysisOutput>();
    
    for(AnalysisOutput o : candidates) {
      if(o.getScore()!=AnalysisOutput.SCORE_CORRECT)
        removeds.add(o);
    }
    
    for(AnalysisOutput o : removeds) {
      candidates.remove(o);
    }
    
  }
  
  private void analysisByRule(String input, List<AnalysisOutput> candidates) {
  
    boolean josaFlag = true;
    boolean eomiFlag = true;
        
    int strlen = input.length();
    
//    boolean isVerbOnly = MorphUtil.hasVerbOnly(input);
    boolean isVerbOnly = false;
    analysisWithEomi(input,"",candidates);
    
    for (int i = strlen-1; i > 0; i--) {
      
      String stem = input.substring(0, i);
      String eomi = input.substring(i);

      char ch = eomi.charAt(0);    
      if (!isVerbOnly && josaFlag && SyllableUtil.hasFeature(ch, SyllableUtil.JOSA1)) {        
        analysisWithJosa(stem, eomi, candidates);
      }
      
      if (eomiFlag) {      
        analysisWithEomi(stem, eomi, candidates);
        eomiFlag &= SyllableUtil.hasFeature(ch, SyllableUtil.EOMI2);
      }      
      
      if (josaFlag) {
        josaFlag &= SyllableUtil.hasFeature(ch, SyllableUtil.JOSA2);
      }
      
      if (!josaFlag && !eomiFlag) {
        break;
      }
    }
  }
  
  private void addResults(AnalysisOutput o, List<AnalysisOutput> results, HashMap<String, AnalysisOutput> stems) {
    AnalysisOutput old = stems.get(o.getStem());
    if(old==null||old.getPos()!=o.getPos()) {
      results.add(o);
      stems.put(o.getStem(), o);
    }else if(old.getPatn()<o.getPatn()) {
      results.remove(old);
      results.add(o);
      stems.put(o.getStem(), o);
    }
  }
  
  private void addSingleWord(String word, List<AnalysisOutput> candidates) {
    
//    if(candidates.size()!=0&&candidates.get(0).getScore()==AnalysisOutput.SCORE_CORRECT) return;
    
    AnalysisOutput output = new AnalysisOutput(word, null, null, PatternConstants.PTN_N);
    output.setPos(PatternConstants.POS_NOUN);

    WordEntry entry;
    if((entry=DictionaryUtil.getWord(word))!=null) {

      if(entry.getFeature(WordEntry.IDX_NOUN)!='1'&&
          entry.getFeature(WordEntry.IDX_BUSA)=='1') {
        AnalysisOutput busa = new AnalysisOutput(word, null, null, PatternConstants.PTN_AID);
        busa.setPos(PatternConstants.POS_ETC);
        
        busa.setScore(AnalysisOutput.SCORE_CORRECT);
        candidates.add(0,busa);    
      }else if(entry.getFeature(WordEntry.IDX_NOUN)=='1') {
        output.setScore(AnalysisOutput.SCORE_CORRECT);
        candidates.add(0,output);
      }else if(entry.getFeature(WordEntry.IDX_NOUN)=='2') {
        candidates.add(0,output);
      }
      
      if(entry.getFeature(WordEntry.IDX_VERB)!='1') return;
    } else if(candidates.size()==0||!NounUtil.endsWith2Josa(word)) {
      output.setScore(AnalysisOutput.SCORE_ANALYSIS);
      candidates.add(0,output);
    }
  }
  
  /**
   * 체언 + 조사 (PTN_NJ)
   * 체언 + 용언화접미사 + '음/기' + 조사 (PTN_NSMJ
   * 용언 + '음/기' + 조사 (PTN_VMJ)
   * 용언 + '아/어' + 보조용언 + '음/기' + 조사(PTN_VMXMJ)
   * 
   * @param stem  stem
   * @param end end
   * @param candidates  candidates
   */
  public void analysisWithJosa(String stem, String end, List<AnalysisOutput> candidates) {
  
    if(stem==null||stem.length()==0) return;  
    
    char[] chrs = MorphUtil.decompose(stem.charAt(stem.length()-1));
    if(!DictionaryUtil.existJosa(end)||
        (chrs.length==3&&ConstraintUtil.isTwoJosa(end))||
        (chrs.length==2&&(ConstraintUtil.isThreeJosa(end))||"".equals(end))) return; // 연결이 가능한 조사가 아니면...

    AnalysisOutput output = new AnalysisOutput(stem, end, null, PatternConstants.PTN_NJ);
    output.setPos(PatternConstants.POS_NOUN);
    
    boolean success = NounUtil.analysisMJ(output.clone(), candidates);

    WordEntry entry = DictionaryUtil.getWordExceptVerb(stem);
    if(entry!=null) {
      output.setScore(AnalysisOutput.SCORE_CORRECT);
      if(entry.getFeature(WordEntry.IDX_NOUN)=='0'&&entry.getFeature(WordEntry.IDX_BUSA)=='1') {
        output.setPos(PatternConstants.POS_ETC);
        output.setPatn(PatternConstants.PTN_ADVJ);
      }
      if(entry.getCompounds().size()>1) output.addCNoun(entry.getCompounds());
    }else {
      if(MorphUtil.hasVerbOnly(stem)) return;
    }
    
    candidates.add(output);

  }
  
  /**
   * 
   *  1. 사랑받다 : 체언 + 용언화접미사 + 어미 (PTN_NSM) <br>
   *  2. 사랑받아보다 : 체언 + 용언화접미사 + '아/어' + 보조용언 + 어미 (PTN_NSMXM) <br>
   *  3. 학교에서이다 : 체언 + '에서/부터/에서부터' + '이' + 어미 (PTN_NJCM) <br>
   *  4. 돕다 : 용언 + 어미 (PTN_VM) <br>
   *  5. 도움이다 : 용언 + '음/기' + '이' + 어미 (PTN_VMCM) <br>
   *  6. 도와주다 : 용언 + '아/어' + 보조용언 + 어미 (PTN_VMXM) <br>
   *  
   * @param stem  stem
   * @param end end
   * @param candidates  candidates
   */
  public void analysisWithEomi(String stem, String end, List<AnalysisOutput> candidates) {
    
    String[] morphs = EomiUtil.splitEomi(stem, end);
    if(morphs[0]==null) return; // 어미가 사전에 등록되어 있지 않다면....

    String[] pomis = EomiUtil.splitPomi(morphs[0]);

    AnalysisOutput o = new AnalysisOutput(pomis[0],null,morphs[1],PatternConstants.PTN_VM);
    o.setPomi(pomis[1]);
  
    WordEntry entry = DictionaryUtil.getVerb(o.getStem());  
    if(entry!=null&&!("을".equals(end)&&entry.getFeature(WordEntry.IDX_REGURA)==IrregularUtil.IRR_TYPE_LIUL)) {              
      AnalysisOutput output = o.clone();
      output.setScore(AnalysisOutput.SCORE_CORRECT);
      MorphUtil.buildPtnVM(output, candidates);
      
      char ch = stem.charAt(stem.length()-1); // ㄹ불규칙일 경우
      if ((SyllableUtil.hasFeature(ch, SyllableUtil.YNPLN) == false || morphs[1].charAt(0) != 'ㄴ')
          &&!"는".equals(end))   // "갈(V),는" 분석될 수 있도록
        return;
    }

    String[] irrs = IrregularUtil.restoreIrregularVerb(o.getStem(), o.getPomi()==null?o.getEomi():o.getPomi());

    if(irrs!=null) { // 불규칙동사인 경우
      AnalysisOutput output = o.clone();
      output.setStem(irrs[0]);
      if(output.getPomi()==null)
        output.setEomi(irrs[1]);
      else
        output.setPomi(irrs[1]);
      
//        entry = DictionaryUtil.getVerb(output.getStem());
//        if(entry!=null && VerbUtil.constraintVerb(o.getStem(), o.getPomi()==null?o.getEomi():o.getPomi())) { // 4. 돕다 (PTN_VM)
      output.setScore(AnalysisOutput.SCORE_CORRECT);
      MorphUtil.buildPtnVM(output, candidates);
//        }        
    }

    if(VerbUtil.ananlysisNSM(o.clone(), candidates)) return;
    
    if(VerbUtil.ananlysisNSMXM(o.clone(), candidates)) return;
    
    // [체언 + '에서/에서부터' + '이' +  어미]
    if(VerbUtil.ananlysisNJCM(o.clone(),candidates)) return;      
    
    if(VerbUtil.analysisVMCM(o.clone(),candidates)) return;  

    VerbUtil.analysisVMXM(o.clone(), candidates);
  }    
  
  /**
   * 복합명사인지 조사하고, 복합명사이면 단위명사들을 찾는다.
   * 복합명사인지 여부는 단위명사가 모두 사전에 있는지 여부로 판단한다.
   * 단위명사는 2글자 이상 단어에서만 찾는다.
   */
  public boolean confirmCNoun(AnalysisOutput o)  {

    if(o.getStem().length()<3) return false;
     
    
    List<CompoundEntry> results = cnAnalyzer.analyze(o.getStem());

    boolean success = false;
       
    if(results.size()>1) {       
      o.setCNoun(results);
      success = true;
      int maxWordLen = 0;
      int dicWordLen = 0;
      for(CompoundEntry entry : results) {           
        if(!entry.isExist()) 
        {
          success = false;
        } 
        else 
        {
          if(entry.getWord().length()>maxWordLen) 
            maxWordLen = entry.getWord().length();
          dicWordLen += entry.getWord().length();
        }
      }
      o.setScore(AnalysisOutput.SCORE_COMPOUNDS);   
      o.setMaxWordLen(maxWordLen);
      o.setDicWordLen(dicWordLen);
    }
  
    if(success) {
      if(constraint(o)) {
        o.setScore(AnalysisOutput.SCORE_CORRECT);
      } else {
        o.setScore(AnalysisOutput.SCORE_FAIL);
        success = false;
      }
    } else {
      if(NounUtil.confirmDNoun(o)&&o.getScore()!=AnalysisOutput.SCORE_CORRECT) {
        confirmCNoun(o);
      }
      if(o.getScore()==AnalysisOutput.SCORE_CORRECT) success = true;
      if(o.getCNounList().size()>0&&!constraint(o)) o.setScore(AnalysisOutput.SCORE_FAIL);
    }

    return success;
       
  }  
     
  private boolean constraint(AnalysisOutput o)  {
       
    List<CompoundEntry> cnouns = o.getCNounList();
       
    if("화해".equals(cnouns.get(cnouns.size()-1).getWord())) {
      if(!ConstraintUtil.canHaheCompound(cnouns.get(cnouns.size()-2).getWord())) return false;
    }else if(o.getPatn()==PatternConstants.PTN_NSM) {         
      if("내".equals(o.getVsfx())&&cnouns.get(cnouns.size()-1).getWord().length()!=1) {
        WordEntry entry = DictionaryUtil.getWord(cnouns.get(cnouns.size()-1).getWord());
        if(entry!=null&&entry.getFeature(WordEntry.IDX_NE)=='0') return false;
//      }else if("하".equals(o.getVsfx())&&cnouns.get(cnouns.size()-1).getWord().length()==1) { 
//        // 짝사랑하다 와 같은 경우에 뒷글자가 1글자이면 제외
//        return false;
      }         
    }       
    return true;
  }
}
