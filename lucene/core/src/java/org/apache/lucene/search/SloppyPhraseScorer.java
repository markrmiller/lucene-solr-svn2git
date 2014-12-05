package org.apache.lucene.search;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.FixedBitSet;


final class SloppyPhraseScorer extends PhraseScorer {
  private PhrasePositions min, max;

  private float sloppyFreq;

  private final Similarity.SimScorer docScorer;
  private final PhraseQuery.PostingsAndFreq[] postings;
  
  private final int slop;
  private final int numPostings;
  private final PhraseQueue pq; // for advancing min position
  
  private int currentEnd; // current largest phrase position
  private int currentRealEnd; // current largest phrase position, including phrase offset
  private int currentEndOffset; // current largest phrase currentEnd offset

  private int spanEnd;
  private int spanOffsetEnd;

  private boolean hasRpts; // flag indicating that there are repetitions (as checked in first candidate doc)
  private boolean checkedRpts; // flag to only check for repetitions in first candidate doc
  private boolean hasMultiTermRpts; //  
  private PhrasePositions[][] rptGroups; // in each group are PPs that repeats each other (i.e. same term), sorted by (query) phraseOffset
  private PhrasePositions[] rptStack; // temporary stack for switching colliding repeating pps 
  
  private final long cost;
  
  SloppyPhraseScorer(Weight weight, PhraseQuery.PostingsAndFreq[] postings,
      int slop, Similarity.SimScorer docScorer) {
    super(weight);
    this.docScorer = docScorer;
    this.postings = postings;
    this.slop = slop;
    this.numPostings = postings==null ? 0 : postings.length;
    pq = new PhraseQueue(this.numPostings);
    // convert tps to a list of phrase positions.
    // note: phrase-position differs from term-position in that its position
    // reflects the phrase phraseOffset: pp.pos = tp.pos - phraseOffset.
    // this allows to easily identify a matching (exact) phrase 
    // when all PhrasePositions have exactly the same position.
    if (postings != null && postings.length > 0) {
      // min(cost)
      cost = postings[0].postings.cost();
      min = new PhrasePositions(postings[0].postings, postings[0].position, 0, postings[0].terms);
      max = min;
      max.doc = -1;
      for (int i = 1; i < postings.length; i++) {
        PhrasePositions pp = new PhrasePositions(postings[i].postings, postings[i].position, i, postings[i].terms);
        max.next = pp;
        max = pp;
        max.doc = -1;
      }
      max.next = min; // make it cyclic for easier manipulation
    }
    else {
      cost = 0;
    }
  }

  private int matchLength;
  private int startpos = -1;
  private int startoffset = -1;
  private int startoffset = -1;
  private int endoffset = -1;

  @Override
  protected int doNextPosition() throws IOException {
    if (cached) {
      cached = false;
      return this.startPosition();
    }

    if (pq.size() < postings.length)
      return NO_MORE_POSITIONS;

    PhrasePositions top = pq.pop();
    matchLength = currentEnd - top.position;
    int next = pq.top().position;
    int pos = top.position + top.phraseOffset;
    int startoffset = top.startOffset();
    spanEnd = currentRealEnd;
    spanOffsetEnd = currentEndOffset;
    while (advancePP(top)) {
      if (hasRpts && !advanceRpts(top))
        break; // pps exhausted
      if (top.position > next) { // done minimizing current match-length
        if (matchLength <= slop) {
          setSpanStart(pos, startoffset);
          pq.add(top);
          return startpos;
        }
        pq.add(top);
        top = pq.pop();
        next = pq.top().position;
        matchLength = currentEnd - top.position;
        pos = top.position + top.phraseOffset;
        startoffset = top.startOffset();
        spanEnd = currentRealEnd;
        spanOffsetEnd = currentEndOffset;
      }
      else {
        int matchLength2 = currentEnd - top.position;
        pos = top.position + top.phraseOffset;
        startoffset = top.startOffset();
        spanEnd = currentRealEnd;
        spanOffsetEnd = currentEndOffset;
        if (matchLength2 < matchLength)
          matchLength = matchLength2;
      }
    }

    if (matchLength <= slop) {
      setSpanStart(pos, startoffset);
      return startpos;
    }

    return NO_MORE_POSITIONS;

  }

  private void setSpanStart(int topPos, int topStartOffset) throws IOException {
    startpos = topPos;
    startoffset = topStartOffset;
    startoffset = topStartOffset;
    endoffset = topEndOffset;
    for (Object o : pq.getPPs()) {
      if (o == null)
        continue;
      PhrasePositions pp = (PhrasePositions) o;
      if (pp.position != NO_MORE_POSITIONS) {
        startpos = Math.min(startpos, pp.position + pp.phraseOffset);
        startoffset = Math.min(startoffset, pp.startOffset());
      }
    }
  }

  boolean cached = false;

  private int firstPosition() throws IOException {
    if (!initPhrasePositions())
      return NO_MORE_POSITIONS;

    freq = -1;
    sloppyFreq = -1;
    cached = false;
    int pos = doNextPosition();
    cached = true;
    return pos;
  }

  /** advance a PhrasePosition and update 'currentEnd', return false if exhausted */
  private boolean advancePP(PhrasePositions pp) throws IOException {
    if (!pp.nextPosition()) {
      return false;
    }
    if (pp.position > currentEnd) {
      currentEnd = pp.position;
    }
    if (pp.position + pp.phraseOffset > currentRealEnd) {
      currentRealEnd = pp.position + pp.phraseOffset;
      currentEndOffset = pp.endOffset();
    }
    return true;
  }
  
  /** pp was just advanced. If that caused a repeater collision, resolve by advancing the lesser
   * of the two colliding pps. Note that there can only be one collision, as by the initialization
   * there were no collisions before pp was advanced.  */
  private boolean advanceRpts(PhrasePositions pp) throws IOException {
    if (pp.rptGroup < 0) {
      return true; // not a repeater
    }
    PhrasePositions[] rg = rptGroups[pp.rptGroup];
    FixedBitSet bits = new FixedBitSet(rg.length); // for re-queuing after collisions are resolved
    int k0 = pp.rptInd;
    int k;
    while((k=collide(pp)) >= 0) {
      pp = lesser(pp, rg[k]); // always advance the lesser of the (only) two colliding pps
      if (!advancePP(pp)) {
        return false; // exhausted
      }
      if (k != k0) { // careful: mark only those currently in the queue
        bits = FixedBitSet.ensureCapacity(bits, k);
        bits.set(k); // mark that pp2 need to be re-queued
      }
    }
    // collisions resolved, now re-queue
    // empty (partially) the queue until seeing all pps advanced for resolving collisions
    int n = 0;
    // TODO would be good if we can avoid calling cardinality() in each iteration!
    int numBits = bits.length(); // larges bit we set
    while (bits.cardinality() > 0) {
      PhrasePositions pp2 = pq.pop();
      rptStack[n++] = pp2;
      if (pp2.rptGroup >= 0 
          && pp2.rptInd < numBits  // this bit may not have been set
          && bits.get(pp2.rptInd)) {
        bits.clear(pp2.rptInd);
      }
    }
    // add back to queue
    for (int i=n-1; i>=0; i--) {
      pq.add(rptStack[i]);
    }
    return true;
  }

  /** compare two pps, but only by position and phraseOffset */
  private PhrasePositions lesser(PhrasePositions pp, PhrasePositions pp2) {
    if (pp.position < pp2.position ||
        (pp.position == pp2.position && pp.phraseOffset < pp2.phraseOffset)) {
      return pp;
    }
    return pp2;
  }

  /** index of a pp2 colliding with pp, or -1 if none */
  private int collide(PhrasePositions pp) {
    int tpPos = tpPos(pp);
    PhrasePositions[] rg = rptGroups[pp.rptGroup];
    for (int i=0; i<rg.length; i++) {
      PhrasePositions pp2 = rg[i];
      if (pp2 != pp && tpPos(pp2) == tpPos) {
        return pp2.rptInd;
      }
    }
    return -1;
  }

  /**
   * Initialize PhrasePositions in place.
   * A one time initialization for this scorer (on first doc matching all terms):
   * <ul>
   *  <li>Check if there are repetitions
   *  <li>If there are, find groups of repetitions.
   * </ul>
   * Examples:
   * <ol>
   *  <li>no repetitions: <b>"ho my"~2</b>
   *  <li>repetitions: <b>"ho my my"~2</b>
   *  <li>repetitions: <b>"my ho my"~2</b>
   * </ol>
   * @return false if PPs are exhausted (and so current doc will not be a match) 
   */
  private boolean initPhrasePositions() throws IOException {
    currentEnd = currentRealEnd = Integer.MIN_VALUE;
    currentEndOffset = -1;
    if (!checkedRpts) {
      return initFirstTime();
    }
    if (!hasRpts) {
      initSimple();
      return true; // PPs available
    }
    return initComplex();
  }
  
  /** no repeats: simplest case, and most common. It is important to keep this piece of the code simple and efficient */
  private void initSimple() throws IOException {
    //System.err.println("initSimple: doc: "+min.doc);
    pq.clear();
    // position pps and build queue from list
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) {  // iterate cyclic list: done once handled max
      pp.firstPosition();
      if (pp.position > currentEnd) {
        currentEnd = pp.position;
      }
      if (pp.position + pp.phraseOffset > currentRealEnd) {
        currentRealEnd = pp.position + pp.phraseOffset;
        currentEndOffset = pp.endOffset();
      }
      pq.add(pp);
    }
  }
  
  /** with repeats: not so simple. */
  private boolean initComplex() throws IOException {
    //System.err.println("initComplex: doc: "+min.doc);
    placeFirstPositions();
    if (!advanceRepeatGroups()) {
      return false; // PPs exhausted
    }
    fillQueue();
    return true; // PPs available
  }

  /** move all PPs to their first position */
  private void placeFirstPositions() throws IOException {
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) { // iterate cyclic list: done once handled max
      pp.firstPosition();
    }
  }

  /** Fill the queue (all pps are already placed */
  private void fillQueue() throws IOException {
    pq.clear();
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) {  // iterate cyclic list: done once handled max
      if (pp.position > currentEnd) {
        currentEnd = pp.position;
      }
      if (pp.position + pp.phraseOffset > currentRealEnd) {
        currentRealEnd = pp.position + pp.phraseOffset;
        currentEndOffset = pp.endOffset();
      }
      pq.add(pp);
    }
  }

  /** At initialization (each doc), each repetition group is sorted by (query) phraseOffset.
   * This provides the start condition: no collisions.
   * <p>Case 1: no multi-term repeats<br>
   * It is sufficient to advance each pp in the group by one less than its group index.
   * So lesser pp is not advanced, 2nd one advance once, 3rd one advanced twice, etc.
   * <p>Case 2: multi-term repeats<br>
   * 
   * @return false if PPs are exhausted. 
   */
  private boolean advanceRepeatGroups() throws IOException {
    for (PhrasePositions[] rg: rptGroups) { 
      if (hasMultiTermRpts) {
        // more involved, some may not collide
        int incr;
        for (int i=0; i<rg.length; i+=incr) {
          incr = 1;
          PhrasePositions pp = rg[i];
          int k;
          while((k=collide(pp)) >= 0) {
            PhrasePositions pp2 = lesser(pp, rg[k]);
            if (!advancePP(pp2)) {  // at initialization always advance pp with higher phraseOffset
              return false; // exhausted
            }
            if (pp2.rptInd < i) { // should not happen?
              incr = 0;
              break;
            }
          }
        }
      } else {
        // simpler, we know exactly how much to advance
        for (int j=1; j<rg.length; j++) {
          for (int k=0; k<j; k++) {
            if (!rg[j].nextPosition()) {
              return false; // PPs exhausted
            }
          }
        }
      }
    }
    return true; // PPs available
  }
  
  /** initialize with checking for repeats. Heavy work, but done only for the first candidate doc.<p>
   * If there are repetitions, check if multi-term postings (MTP) are involved.<p>
   * Without MTP, once PPs are placed in the first candidate doc, repeats (and groups) are visible.<br>
   * With MTP, a more complex check is needed, up-front, as there may be "hidden collisions".<br>
   * For example P1 has {A,B}, P1 has {B,C}, and the first doc is: "A C B". At start, P1 would point
   * to "A", p2 to "C", and it will not be identified that P1 and P2 are repetitions of each other.<p>
   * The more complex initialization has two parts:<br>
   * (1) identification of repetition groups.<br>
   * (2) advancing repeat groups at the start of the doc.<br>
   * For (1), a possible solution is to just create a single repetition group, 
   * made of all repeating pps. But this would slow down the check for collisions, 
   * as all pps would need to be checked. Instead, we compute "connected regions" 
   * on the bipartite graph of postings and terms.  
   */
  private boolean initFirstTime() throws IOException {
    //System.err.println("initFirstTime: doc: "+min.doc);
    checkedRpts = true;
    placeFirstPositions();

    LinkedHashMap<Term,Integer> rptTerms = repeatingTerms(); 
    hasRpts = !rptTerms.isEmpty();

    if (hasRpts) {
      rptStack = new PhrasePositions[numPostings]; // needed with repetitions
      ArrayList<ArrayList<PhrasePositions>> rgs = gatherRptGroups(rptTerms);
      sortRptGroups(rgs);
      if (!advanceRepeatGroups()) {
        return false; // PPs exhausted
      }
    }
    
    fillQueue();
    return true; // PPs available
  }

  /** sort each repetition group by (query) phraseOffset.
   * Done only once (at first doc) and allows to initialize faster for each doc. */
  private void sortRptGroups(ArrayList<ArrayList<PhrasePositions>> rgs) {
    rptGroups = new PhrasePositions[rgs.size()][];
    Comparator<PhrasePositions> cmprtr = new Comparator<PhrasePositions>() {
      @Override
      public int compare(PhrasePositions pp1, PhrasePositions pp2) {
        return pp1.phraseOffset - pp2.phraseOffset;
      }
    };
    for (int i=0; i<rptGroups.length; i++) {
      PhrasePositions[] rg = rgs.get(i).toArray(new PhrasePositions[0]);
      Arrays.sort(rg, cmprtr);
      rptGroups[i] = rg;
      for (int j=0; j<rg.length; j++) {
        rg[j].rptInd = j; // we use this index for efficient re-queuing
      }
    }
  }

  /** Detect repetition groups. Done once - for first doc */
  private ArrayList<ArrayList<PhrasePositions>> gatherRptGroups(LinkedHashMap<Term,Integer> rptTerms) throws IOException {
    PhrasePositions[] rpp = repeatingPPs(rptTerms); 
    ArrayList<ArrayList<PhrasePositions>> res = new ArrayList<>();
    if (!hasMultiTermRpts) {
      // simpler - no multi-terms - can base on positions in first doc
      for (int i=0; i<rpp.length; i++) {
        PhrasePositions pp = rpp[i];
        if (pp.rptGroup >=0) continue; // already marked as a repetition
        int tpPos = tpPos(pp);
        for (int j=i+1; j<rpp.length; j++) {
          PhrasePositions pp2 = rpp[j];
          if (
              pp2.rptGroup >=0        // already marked as a repetition
              || pp2.phraseOffset == pp.phraseOffset // not a repetition: two PPs are originally in same phraseOffset in the query!
              || tpPos(pp2) != tpPos) {  // not a repetition
            continue; 
          }
          // a repetition
          int g = pp.rptGroup;
          if (g < 0) {
            g = res.size();
            pp.rptGroup = g;  
            ArrayList<PhrasePositions> rl = new ArrayList<>(2);
            rl.add(pp);
            res.add(rl); 
          }
          pp2.rptGroup = g;
          res.get(g).add(pp2);
        }
      }
    } else {
      // more involved - has multi-terms
      ArrayList<HashSet<PhrasePositions>> tmp = new ArrayList<>();
      ArrayList<FixedBitSet> bb = ppTermsBitSets(rpp, rptTerms);
      unionTermGroups(bb);
      HashMap<Term,Integer> tg = termGroups(rptTerms, bb);
      HashSet<Integer> distinctGroupIDs = new HashSet<>(tg.values());
      for (int i=0; i<distinctGroupIDs.size(); i++) {
        tmp.add(new HashSet<PhrasePositions>());
      }
      for (PhrasePositions pp : rpp) {
        for (Term t: pp.terms) {
          if (rptTerms.containsKey(t)) {
            int g = tg.get(t);
            tmp.get(g).add(pp);
            assert pp.rptGroup==-1 || pp.rptGroup==g;  
            pp.rptGroup = g;
          }
        }
      }
      for (HashSet<PhrasePositions> hs : tmp) {
        res.add(new ArrayList<>(hs));
      }
    }
    return res;
  }

  /** Actual position in doc of a PhrasePosition, relies on that position = tpPos - phraseOffset) */
  private int tpPos(PhrasePositions pp) {
    return pp.position + pp.phraseOffset;
  }

  /** find repeating terms and assign them ordinal values */
  private LinkedHashMap<Term,Integer> repeatingTerms() {
    LinkedHashMap<Term,Integer> tord = new LinkedHashMap<>();
    HashMap<Term,Integer> tcnt = new HashMap<>();
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) { // iterate cyclic list: done once handled max
      for (Term t : pp.terms) {
        Integer cnt0 = tcnt.get(t);
        Integer cnt = cnt0==null ? new Integer(1) : new Integer(1 + cnt0);
        tcnt.put(t, cnt);
        if (cnt==2) {
          tord.put(t,tord.size());
        }
      }
    }
    return tord;
  }

  /** find repeating pps, and for each, if has multi-terms, update this.hasMultiTermRpts */
  private PhrasePositions[] repeatingPPs(HashMap<Term,Integer> rptTerms) {
    ArrayList<PhrasePositions> rp = new ArrayList<>();
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) { // iterate cyclic list: done once handled max
      for (Term t : pp.terms) {
        if (rptTerms.containsKey(t)) {
          rp.add(pp);
          hasMultiTermRpts |= (pp.terms.length > 1);
          break;
        }
      }
    }
    return rp.toArray(new PhrasePositions[rp.size()]);
  }
  
  /** bit-sets - for each repeating pp, for each of its repeating terms, the term ordinal values is set */
  private ArrayList<FixedBitSet> ppTermsBitSets(PhrasePositions[] rpp, HashMap<Term,Integer> tord) {
    ArrayList<FixedBitSet> bb = new ArrayList<>(rpp.length);
    for (PhrasePositions pp : rpp) {
      FixedBitSet b = new FixedBitSet(tord.size());
      Integer ord;
      for (Term t: pp.terms) {
        if ((ord=tord.get(t))!=null) {
          b.set(ord);
        }
      }
      bb.add(b);
    }
    return bb;
  }
  
  /** union (term group) bit-sets until they are disjoint (O(n^^2)), and each group have different terms */
  private void unionTermGroups(ArrayList<FixedBitSet> bb) {
    int incr;
    for (int i=0; i<bb.size()-1; i+=incr) {
      incr = 1;
      int j = i+1;
      while (j<bb.size()) {
        if (bb.get(i).intersects(bb.get(j))) {
          bb.get(i).or(bb.get(j));
          bb.remove(j);
          incr = 0;
        } else {
          ++j;
        }
      }
    }
  }
  
  /** map each term to the single group that contains it */ 
  private HashMap<Term,Integer> termGroups(LinkedHashMap<Term,Integer> tord, ArrayList<FixedBitSet> bb) throws IOException {
    HashMap<Term,Integer> tg = new HashMap<>();
    Term[] t = tord.keySet().toArray(new Term[tord.size()]);
    for (int i=0; i<bb.size(); i++) { // i is the group no.
      FixedBitSet bits = bb.get(i);
      for (int ord = bits.nextSetBit(0); ord != DocIdSetIterator.NO_MORE_DOCS; ord = ord + 1 >= bits.length() ? DocIdSetIterator.NO_MORE_DOCS : bits.nextSetBit(ord + 1)) {
        tg.put(t[ord],i);
      }
    }
    return tg;
  }

  /**
   * Score a candidate doc for all slop-valid position-combinations (matches)
   * encountered while traversing/hopping the PhrasePositions.
   * <br> The score contribution of a match depends on the distance:
   * <br> - highest score for distance=0 (exact match).
   * <br> - score gets lower as distance gets higher.
   * <br>Example: for query "a b"~2, a document "x a b a y" can be scored twice:
   * once for "a b" (distance=0), and once for "b a" (distance=2).
   * <br>Possibly not all valid combinations are encountered, because for efficiency
   * we always propagate the least PhrasePosition. This allows to base on
   * PriorityQueue and move forward faster.
   * As result, for example, document "a b c b a"
   * would score differently for queries "a b c"~4 and "c b a"~4, although
   * they really are equivalent.
   * Similarly, for doc "a b c b a f g", query "c b"~2
   * would get same score as "g f"~2, although "c b"~2 could be matched twice.
   * We may want to fix this in the future (currently not, for performance reasons).
   */
  float sloppyFreq() throws IOException {
    if (sloppyFreq == -1) {
      sloppyFreq = 0.0f;
      while (nextPosition() != NO_MORE_POSITIONS) {
        sloppyFreq += docScorer.computeSlopFactor(matchLength);
      }
    }
    return sloppyFreq;
  }

  @Override
  protected int doStartOffset() throws IOException {
    return startoffset;
  }

  @Override
  protected int doEndOffset() throws IOException {
    return spanOffsetEnd;
  }

  // TODO : getPayload on spans?
  @Override
  protected int doStartPosition() throws IOException {
    return startpos;
  }

  @Override
  protected int doEndPosition() throws IOException {
    return spanEnd;
  }

  //  private void printQueue(PrintStream ps, PhrasePositions ext, String title) {
//    //if (min.doc != ?) return;
//    ps.println();
//    ps.println("---- "+title);
//    ps.println("EXT: "+ext);
//    PhrasePositions[] t = new PhrasePositions[pq.size()];
//    if (pq.size()>0) {
//      t[0] = pq.pop();
//      ps.println("  " + 0 + "  " + t[0]);
//      for (int i=1; i<t.length; i++) {
//        t[i] = pq.pop();
//        assert t[i-1].position <= t[i].position;
//        ps.println("  " + i + "  " + t[i]);
//      }
//      // add them back
//      for (int i=t.length-1; i>=0; i--) {
//        pq.add(t[i]);
//      }
//    }
//  }
  
  private boolean advanceMin(int target) throws IOException {
    if (!min.skipTo(target)) { 
      max.doc = NO_MORE_DOCS; // for further calls to docID() 
      return false;
    }
    min = min.next; // cyclic
    max = max.next; // cyclic
    return true;
  }
  
  @Override
  public int docID() {
    return max.doc; 
  }

  @Override
  public int nextDoc() throws IOException {
    return advance(max.doc + 1); // advance to the next doc after #docID()
  }
  
  @Override
  public float score() throws IOException {
    return docScorer.score(max.doc, sloppyFreq());
  }

  @Override
  public int advance(int target) throws IOException {
    assert target > docID();
    do {
      if (!advanceMin(target)) {
        return NO_MORE_DOCS;
      }
      while (min.doc < max.doc) {
        if (!advanceMin(max.doc)) {
          return NO_MORE_DOCS;
        }
      }
      // found a doc with all of the terms
      //sloppyFreq = phraseFreq(); // check for phrase
      target = min.doc + 1; // next target in case sloppyFreq is still 0
    } while (firstPosition() == NO_MORE_POSITIONS);

    // found a match
    return max.doc;
  }

  @Override
  public long cost() {
    return cost;
  }

  @Override
  public String toString() { return "scorer(" + weight + ")"; }

}
