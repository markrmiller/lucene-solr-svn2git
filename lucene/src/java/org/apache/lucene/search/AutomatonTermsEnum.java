package org.apache.lucene.search;

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

import java.io.IOException;
import java.util.Comparator;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.SpecialOperations;
import org.apache.lucene.util.automaton.State;
import org.apache.lucene.util.automaton.Transition;

/**
 * A FilteredTermsEnum that enumerates terms based upon what is accepted by a
 * DFA.
 * <p>
 * The algorithm is such:
 * <ol>
 *   <li>As long as matches are successful, keep reading sequentially.
 *   <li>When a match fails, skip to the next string in lexicographic order that
 * does not enter a reject state.
 * </ol>
 * <p>
 * The algorithm does not attempt to actually skip to the next string that is
 * completely accepted. This is not possible when the language accepted by the
 * FSM is not finite (i.e. * operator).
 * </p>
 * @lucene.experimental
 */
public class AutomatonTermsEnum extends FilteredTermsEnum {
  // the object-oriented form of the DFA
  private final Automaton automaton;
  // a tableized array-based form of the DFA
  private final ByteRunAutomaton runAutomaton;
  // common suffix of the automaton
  private final BytesRef commonSuffixRef;
  // true if the automaton accepts a finite language
  private final boolean finite;
  // array of sorted transitions for each state, indexed by state number
  private final Transition[][] allTransitions;
  // for path tracking: each long records gen when we last
  // visited the state; we use gens to avoid having to clear
  private final long[] visited;
  private long curGen;
  // the reference used for seeking forwards through the term dictionary
  private final BytesRef seekBytesRef = new BytesRef(10); 
  // true if we are enumerating an infinite portion of the DFA.
  // in this case it is faster to drive the query based on the terms dictionary.
  // when this is true, linearUpperBound indicate the end of range
  // of terms where we should simply do sequential reads instead.
  private boolean linear = false;
  private final BytesRef linearUpperBound = new BytesRef(10);
  private final Comparator<BytesRef> termComp;

  /**
   * Expert ctor:
   * Construct an enumerator based upon an automaton, enumerating the specified
   * field, working on a supplied reader.
   * <p>
   * @lucene.experimental 
   * <p>
   * @param runAutomaton pre-compiled ByteRunAutomaton
   * @param finite true if the automaton accepts a finite language
   */
  public AutomatonTermsEnum(ByteRunAutomaton runAutomaton,
                     String field, IndexReader reader,
                     boolean finite, BytesRef commonSuffixRef)
      throws IOException {
    super(reader, field);
    this.automaton = runAutomaton.getAutomaton();
    this.finite = finite;

    this.runAutomaton = runAutomaton;
    if (finite) {
      // don't use suffix w/ finite DFAs
      this.commonSuffixRef = null;
    } else if (commonSuffixRef == null) {
      // compute now
      this.commonSuffixRef = SpecialOperations.getCommonSuffixBytesRef(automaton);
    } else {
      // precomputed
      this.commonSuffixRef = commonSuffixRef;
    }

    // build a cache of sorted transitions for every state
    allTransitions = new Transition[runAutomaton.getSize()][];
    for (State state : this.automaton.getNumberedStates()) {
      state.sortTransitions(Transition.CompareByMinMaxThenDest);
      state.trimTransitionsArray();
      allTransitions[state.getNumber()] = state.transitionsArray;
    }
    // used for path tracking, where each bit is a numbered state.
    visited = new long[runAutomaton.getSize()];

    setUseTermsCache(finite);
    termComp = getComparator();
  }
  
  /**
   * Construct an enumerator based upon an automaton, enumerating the specified
   * field, working on a supplied reader.
   * <p>
   * It will automatically calculate whether or not the automaton is finite
   */
  public AutomatonTermsEnum(Automaton automaton, String field, IndexReader reader)
    throws IOException {
    this(new ByteRunAutomaton(automaton), field, reader, SpecialOperations.isFinite(automaton), null);
  }
 
  /**
   * Returns true if the term matches the automaton. Also stashes away the term
   * to assist with smart enumeration.
   */
  @Override
  protected AcceptStatus accept(final BytesRef term) {
    if (commonSuffixRef == null || term.endsWith(commonSuffixRef)) {
      if (runAutomaton.run(term.bytes, term.offset, term.length))
        return linear ? AcceptStatus.YES : AcceptStatus.YES_AND_SEEK;
      else
        return (linear && termComp.compare(term, linearUpperBound) < 0) ? 
            AcceptStatus.NO : AcceptStatus.NO_AND_SEEK;
    } else {
      return (linear && termComp.compare(term, linearUpperBound) < 0) ? 
          AcceptStatus.NO : AcceptStatus.NO_AND_SEEK;
    }
  }
  
  @Override
  protected BytesRef nextSeekTerm(final BytesRef term) throws IOException {
    if (term == null) {
      seekBytesRef.copy("");
      // return the empty term, as its valid
      if (runAutomaton.run(seekBytesRef.bytes, seekBytesRef.offset, seekBytesRef.length)) {   
        return seekBytesRef;
      }
    } else {
      seekBytesRef.copy(term);
    }

    // seek to the next possible string;
    if (nextString()) {
      // reposition
           
      if (linear)
        setLinear(infinitePosition);
      return seekBytesRef;
    }
    // no more possible strings can match
    return null;
  }

  // this instance prevents unicode conversion during backtracking,
  // we can just call setLinear once at the end.
  int infinitePosition;

  /**
   * Sets the enum to operate in linear fashion, as we have found
   * a looping transition at position
   */
  private void setLinear(int position) {
    int state = runAutomaton.getInitialState();
    int maxInterval = 0xff;
    for (int i = 0; i < position; i++) {
      state = runAutomaton.step(state, seekBytesRef.bytes[i] & 0xff);
      assert state >= 0: "state=" + state;
    }
    for (int i = 0; i < allTransitions[state].length; i++) {
      Transition t = allTransitions[state][i];
      if (t.getMin() <= (seekBytesRef.bytes[position] & 0xff) && 
          (seekBytesRef.bytes[position] & 0xff) <= t.getMax()) {
        maxInterval = t.getMax();
        break;
      }
    }
    // 0xff terms don't get the optimization... not worth the trouble.
    if (maxInterval != 0xff)
      maxInterval++;
    int length = position + 1; /* position + maxTransition */
    if (linearUpperBound.bytes.length < length)
      linearUpperBound.bytes = new byte[length];
    System.arraycopy(seekBytesRef.bytes, 0, linearUpperBound.bytes, 0, position);
    linearUpperBound.bytes[position] = (byte) maxInterval;
    linearUpperBound.length = length;
  }

  /**
   * Increments the byte buffer to the next String in binary order after s that will not put
   * the machine into a reject state. If such a string does not exist, returns
   * false.
   * 
   * The correctness of this method depends upon the automaton being deterministic,
   * and having no transitions to dead states.
   * 
   * @return true if more possible solutions exist for the DFA
   */
  private boolean nextString() {
    int state;
    int pos = 0;

    while (true) {
      curGen++;
      linear = false;
      state = runAutomaton.getInitialState();
      // walk the automaton until a character is rejected.
      for (pos = 0; pos < seekBytesRef.length; pos++) {
        visited[state] = curGen;
        int nextState = runAutomaton.step(state, seekBytesRef.bytes[pos] & 0xff);
        if (nextState == -1)
          break;
        // we found a loop, record it for faster enumeration
        if (!finite && !linear && visited[nextState] == curGen) {
          linear = true;
          infinitePosition = pos;
        }
        state = nextState;
      }

      // take the useful portion, and the last non-reject state, and attempt to
      // append characters that will match.
      if (nextString(state, pos)) {
        return true;
      } else { /* no more solutions exist from this useful portion, backtrack */
        if (!backtrack(pos)) /* no more solutions at all */
          return false;
        else if (runAutomaton.run(seekBytesRef.bytes, 0, seekBytesRef.length)) 
          /* String is good to go as-is */
          return true;
        /* else advance further */
      }
    }
  }
  
  /**
   * Returns the next String in lexicographic order that will not put
   * the machine into a reject state. 
   * 
   * This method traverses the DFA from the given position in the String,
   * starting at the given state.
   * 
   * If this cannot satisfy the machine, returns false. This method will
   * walk the minimal path, in lexicographic order, as long as possible.
   * 
   * If this method returns false, then there might still be more solutions,
   * it is necessary to backtrack to find out.
   * 
   * @param state current non-reject state
   * @param position useful portion of the string
   * @return true if more possible solutions exist for the DFA from this
   *         position
   */
  private boolean nextString(int state, int position) {
    /* 
     * the next lexicographic character must be greater than the existing
     * character, if it exists.
     */
    int c = 0;
    if (position < seekBytesRef.length) {
      c = seekBytesRef.bytes[position] & 0xff;
      // if the next byte is 0xff and is not part of the useful portion,
      // then by definition it puts us in a reject state, and therefore this
      // path is dead. there cannot be any higher transitions. backtrack.
      if (c++ == 0xff)
        return false;
    }

    seekBytesRef.length = position;
    visited[state] = curGen;

    Transition transitions[] = allTransitions[state];

    // find the minimal path (lexicographic order) that is >= c
    
    for (int i = 0; i < transitions.length; i++) {
      Transition transition = transitions[i];
      if (transition.getMax() >= c) {
        int nextChar = Math.max(c, transition.getMin());
        // append either the next sequential char, or the minimum transition
        seekBytesRef.grow(seekBytesRef.length + 1);
        seekBytesRef.length++;
        seekBytesRef.bytes[seekBytesRef.length - 1] = (byte) nextChar;
        state = transition.getDest().getNumber();
        /* 
         * as long as is possible, continue down the minimal path in
         * lexicographic order. if a loop or accept state is encountered, stop.
         */
        while (visited[state] != curGen && !runAutomaton.isAccept(state)) {
          visited[state] = curGen;
          /* 
           * Note: we work with a DFA with no transitions to dead states.
           * so the below is ok, if it is not an accept state,
           * then there MUST be at least one transition.
           */
          transition = allTransitions[state][0];
          state = transition.getDest().getNumber();
          // we found a loop, record it for faster enumeration
          if (!finite && !linear && visited[state] == curGen) {
            linear = true;
            infinitePosition = seekBytesRef.length;
          }
          // append the minimum transition
          seekBytesRef.grow(seekBytesRef.length + 1);
          seekBytesRef.length++;
          seekBytesRef.bytes[seekBytesRef.length - 1] = (byte) transition.getMin();
        }
        return true;
      }
    }
    return false;
  }
  
  /**
   * Attempts to backtrack thru the string after encountering a dead end
   * at some given position. Returns false if no more possible strings 
   * can match.
   * 
   * @param position current position in the input String
   * @return true if more possible solutions exist for the DFA
   */
  private boolean backtrack(int position) {
    while (position > 0) {
      int nextChar = seekBytesRef.bytes[position - 1] & 0xff;
      // if a character is 0xff its a dead-end too,
      // because there is no higher character in binary sort order.
      if (nextChar++ != 0xff) {
        seekBytesRef.bytes[position - 1] = (byte) nextChar;
        seekBytesRef.length = position;
        return true;
      }
      position--;
    }
    return false; /* all solutions exhausted */
  }
}
