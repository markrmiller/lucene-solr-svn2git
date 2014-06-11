package org.apache.lucene.util.automaton;

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
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.InPlaceMergeSorter;
import org.apache.lucene.util.Sorter;

// nocommit make tests that do the same ops w/ old and new and assertSameLang

// TODO
//   - could use packed int arrays instead
//   - could encode dest w/ delta from to?

// nocommit should we keep determinized bit?

/** Uses only int[]s to represent the automaton, but requires that all
 *  transitions for each state are added at once.  If this is too restrictive,
 *  use {@link #Builder} instead.  State 0 is always the
 *  initial state.
 *
 * @lucene.experimental */

// nocommit rename to Automaton once everything is cutover

public class LightAutomaton {
  private int nextState;

  /** Where we next write to in int[] transitions; this
   *  increments by 3 for each added transition because we
   *  pack min, max, dest in sequence. */
  private int nextTransition;

  /** Current state we are adding transitions to; the caller
   *  must add all transitions for this state before moving
   *  onto another state. */
  private int curState = -1;

  /** Index in the transitions array, where this states
   *  leaving transitions are stored, or -1 if this state
   *  has not added any transitions yet, followed by number
   *  of transitions. */
  private int[] states = new int[4];

  /** Holds toState, min, max for each transition: */
  // nocommit inefficient when labels are really bytes (max 256)
  private int[] transitions = new int[6];

  private final Set<Integer> finalStates = new HashSet<Integer>();

  public int createState() {
    growStates();
    int state = nextState/2;
    states[nextState] = -1;
    nextState += 2;
    return state;
  }

  /** Mark this state as an accept state. */
  public void setAccept(int state, boolean isAccept) {
    if (isAccept) {
      finalStates.add(state);
    } else {
      finalStates.remove(state);
    }
  }

  public boolean isEmpty() {
    return finalStates.isEmpty();
  }

  /** Sugar, but object-heavy; it's better to iterate instead. */
  public Transition[][] getSortedTransitions() {
    int numStates = getNumStates();
    Transition[][] transitions = new Transition[numStates][];
    for(int s=0;s<numStates;s++) {
      int numTransitions = getNumTransitions(s);
      transitions[s] = new Transition[numTransitions];
      for(int t=0;t<numTransitions;t++) {
        Transition transition = new Transition();
        getTransition(s, t, transition);
        transitions[s][t] = transition;
      }
    }

    return transitions;
  }

  public Set<Integer> getAcceptStates() {
    return finalStates;
  }

  /** Returns true if this state is an accept state. */
  public boolean isAccept(int state) {
    return finalStates.contains(state);
  }

  public void addTransition(int source, int dest, int label) {
    addTransition(source, dest, label, label);
  }

  public void addTransition(int source, int dest, int min, int max) {
    assert nextTransition%3 == 0;

    if (source >= nextState/2) {
      throw new IllegalArgumentException("source is out of bounds");
    }
    if (dest >= nextState/2) {
      throw new IllegalArgumentException("dest is out of bounds");
    }

    //System.out.println("  addTransition nextTransition=" + nextTransition + " source=" + source + " dest=" + dest + " min=" + min + " max=" + max);
    growTransitions();
    if (curState != source) {
      //System.out.println("    newstate");
      if (curState != -1) {
        finishCurrentState();
      }

      // Move to next source:
      curState = source;
      if (states[2*curState] != -1) {
        throw new IllegalStateException("from state (" + source + ") already had transitions added");
      }
      assert states[2*curState+1] == 0;
      states[2*curState] = nextTransition;
    }

    transitions[nextTransition++] = dest;
    transitions[nextTransition++] = min;
    transitions[nextTransition++] = max;

    // Increment transition count for this state
    states[2*curState+1]++;
  }

  public void addEpsilon(int source, int dest) {
    Transition t = new Transition();
    int count = initTransition(dest, t);
    for(int i=0;i<count;i++) {
      getNextTransition(t);
      addTransition(source, t.dest, t.min, t.max);
    }
    if (isAccept(dest)) {
      setAccept(source, true);
    }
  }

  /** Copies over all states/transitions from other. */
  public void copy(LightAutomaton other) {
    int offset = getNumStates();
    int otherNumStates = other.getNumStates();
    for(int s=0;s<otherNumStates;s++) {
      createState();
      setAccept(offset+s, other.isAccept(s));
    }
    Transition t = new Transition();
    for(int s=0;s<otherNumStates;s++) {
      int count = other.initTransition(s, t);
      for(int i=0;i<count;i++) {
        other.getNextTransition(t);
        addTransition(offset + s, offset + t.dest, t.min, t.max);
      }
    }
  }

  /** Freezes the last state, reducing and sorting its transitions. */
  private void finishCurrentState() {
    int numTransitions = states[2*curState+1];
    assert numTransitions > 0;

    // System.out.println("finish curState=" + curState + " numTransitions=" + numTransitions);
    int offset = states[2*curState];
    int start = offset/3;
    destMinMaxSorter.sort(start, start+numTransitions);

    /*
    for(int i=0;i<numTransitions;i++) {
      System.out.println("  " + i + ": dest=" + transitions[offset+3*i] + " (accept?=" + isAccept(transitions[offset+3*i]) + ") min=" + transitions[offset+3*i+1] + " max=" + transitions[offset+3*i+2]);
    }
    */

    // Reduce any "adjacent" transitions:
    int upto = 0;
    int min = -1;
    int max = -1;
    int dest = -1;

    for(int i=0;i<numTransitions;i++) {
      int tDest = transitions[offset+3*i];
      int tMin = transitions[offset+3*i+1];
      int tMax = transitions[offset+3*i+2];

      if (dest == tDest) {
        if (tMin <= max+1) {
          if (tMax > max) {
            max = tMax;
          }
        } else {
          if (dest != -1) {
            transitions[offset+3*upto] = dest;
            transitions[offset+3*upto+1] = min;
            transitions[offset+3*upto+2] = max;
            upto++;
          }
          min = tMin;
          max = tMax;
        }
      } else {
        if (dest != -1) {
          transitions[offset+3*upto] = dest;
          transitions[offset+3*upto+1] = min;
          transitions[offset+3*upto+2] = max;
          upto++;
        }
        dest = tDest;
        min = tMin;
        max = tMax;
      }
    }

    if (dest != -1) {
      // Last transition
      transitions[offset+3*upto] = dest;
      transitions[offset+3*upto+1] = min;
      transitions[offset+3*upto+2] = max;
      upto++;
    }

    nextTransition -= (numTransitions-upto)*3;
    states[2*curState+1] = upto;

    // Sort transitions by min/max/dest:
    minMaxDestSorter.sort(start, start+upto);

    /*
    System.out.println("after finish: reduce collapsed " + (numTransitions-upto) + " transitions");
    for(int i=0;i<upto;i++) {
      System.out.println("  " + i + ": dest=" + transitions[offset+3*i] + " (accept?=" + isAccept(transitions[offset+3*i]) + ") min=" + transitions[offset+3*i+1] + " max=" + transitions[offset+3*i+2]);
    }
    */
  }

  public void finish() {
    if (curState != -1) {
      //System.out.println("finish: finish current state " + curState);
      finishCurrentState();
      curState = -1;
    }
    // nocommit downsize the arrays?
    //assert getNumStates() > 0;
  }

  public int getNumStates() {
    return nextState/2;
  }

  public int getNumTransitions(int state) {
    //assert curState == -1: "not finished";
    int count = states[2*state+1];
    if (count == -1) {
      return 0;
    } else {
      return count;
    }
  }

  public int getDest(int state, int transitionIndex) {
    return transitions[states[2*state]];
  }

  public int getMin(int state, int transitionIndex) {
    return transitions[states[2*state]+1];
  }

  public int getMax(int state, int transitionIndex) {
    return transitions[states[2*state]+2];
  }

  private void growStates() {
    if (nextState+2 >= states.length) {
      states = ArrayUtil.grow(states, nextState+2);
    }
  }

  private void growTransitions() {
    if (nextTransition+3 >= transitions.length) {
      transitions = ArrayUtil.grow(transitions, nextTransition+3);
    }
  }

  /** Sorts transitions by dest, ascending, then min label ascending, then max label ascending */
  private final Sorter destMinMaxSorter = new InPlaceMergeSorter() {

      private void swapOne(int i, int j) {
        int x = transitions[i];
        transitions[i] = transitions[j];
        transitions[j] = x;
      }

      @Override
      protected void swap(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;
        swapOne(iStart, jStart);
        swapOne(iStart+1, jStart+1);
        swapOne(iStart+2, jStart+2);
      };

      @Override
      protected int compare(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;

        // First dest:
        int iDest = transitions[iStart];
        int jDest = transitions[jStart];
        if (iDest < jDest) {
          return -1;
        } else if (iDest > jDest) {
          return 1;
        }

        // Then min:
        int iMin = transitions[iStart+1];
        int jMin = transitions[jStart+1];
        if (iMin < jMin) {
          return -1;
        } else if (iMin > jMin) {
          return 1;
        }

        // Then max:
        int iMax = transitions[iStart+2];
        int jMax = transitions[jStart+2];
        if (iMax < jMax) {
          return -1;
        } else if (iMax > jMax) {
          return 1;
        }

        return 0;
      }
    };

  /** Sorts transitions by min label, ascending, then max label ascending, then dest ascending */
  private final Sorter minMaxDestSorter = new InPlaceMergeSorter() {

      private void swapOne(int i, int j) {
        int x = transitions[i];
        transitions[i] = transitions[j];
        transitions[j] = x;
      }

      @Override
      protected void swap(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;
        swapOne(iStart, jStart);
        swapOne(iStart+1, jStart+1);
        swapOne(iStart+2, jStart+2);
      };

      @Override
      protected int compare(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;

        // First min:
        int iMin = transitions[iStart+1];
        int jMin = transitions[jStart+1];
        if (iMin < jMin) {
          return -1;
        } else if (iMin > jMin) {
          return 1;
        }

        // Then max:
        int iMax = transitions[iStart+2];
        int jMax = transitions[jStart+2];
        if (iMax < jMax) {
          return -1;
        } else if (iMax > jMax) {
          return 1;
        }

        // Then dest:
        int iDest = transitions[iStart];
        int jDest = transitions[jStart];
        if (iDest < jDest) {
          return -1;
        } else if (iDest > jDest) {
          return 1;
        }

        return 0;
      }
    };

  /** Just used temporarily to return the transition from
   *  {@link getTransition} and {@link #getNextTransition}. */
  public static class Transition {
    // used only for assert:
    public int source;
    public int dest;
    public int min;
    public int max;

    /** Remembers where we are in the iteration; init to -1 to provoke
     *  exception if nextTransition is called without first initTransition. */
    private int transitionUpto = -1;

    @Override
    public String toString() {
      return source + " --> " + dest + " " + (char) min + "-" + (char) max;
    }

    // nocommit equals?  hashCode?  don't want to encourage putting these into a Map...?
  }

  // nocommit createStates(int count)?

  // nocommit kinda awkward iterator api...
  /** Initialize the provided Transition for iteration; you
   *  must call {@link #getNextTransition} to get the first
   *  transition for the state.  Returns the number of transitions
   *  leaving this state. */
  public int initTransition(int state, Transition t) {
    // assert curState == -1: "not finished";
    t.source = state;
    //System.out.println("initTrans source=" + state  + " numTrans=" + getNumTransitions(state));
    t.transitionUpto = states[2*state];
    return getNumTransitions(state);
  }

  /** Iterate to the next transition after the provided one */
  public void getNextTransition(Transition t) {
    //assert curState == -1: "not finished";
    // Make sure there is still a transition left:
    //System.out.println("getNextTrans transUpto=" + t.transitionUpto);
    //System.out.println("  states[2*t.source]=" + states[2*t.source] + " numTrans=" + states[2*t.source+1] + " transitionUpto+3=" + (t.transitionUpto+3) + " t=" + t);
    assert (t.transitionUpto+3 - states[2*t.source]) <= 3*states[2*t.source+1];
    t.dest = transitions[t.transitionUpto++];
    t.min = transitions[t.transitionUpto++];
    t.max = transitions[t.transitionUpto++];
  }

  /** Fill the provided {@link Transition} with the index'th
   *  transition leaving the specified state. */
  public void getTransition(int state, int index, Transition t) {
    assert curState == -1: "not finished";
    int i = states[2*state] + 3*index;
    t.source = state;
    t.dest = transitions[i++];
    t.min = transitions[i++];
    t.max = transitions[i++];
  }

  private static void appendCharString(int c, StringBuilder b) {
    if (c >= 0x21 && c <= 0x7e && c != '\\' && c != '"') b.appendCodePoint(c);
    else {
      b.append("\\\\U");
      String s = Integer.toHexString(c);
      if (c < 0x10) b.append("0000000").append(s);
      else if (c < 0x100) b.append("000000").append(s);
      else if (c < 0x1000) b.append("00000").append(s);
      else if (c < 0x10000) b.append("0000").append(s);
      else if (c < 0x100000) b.append("000").append(s);
      else if (c < 0x1000000) b.append("00").append(s);
      else if (c < 0x10000000) b.append("0").append(s);
      else b.append(s);
    }
  }

  public LightAutomaton totalize() {
    LightAutomaton result = new LightAutomaton();
    int numStates = getNumStates();
    for(int i=0;i<numStates;i++) {
      result.createState();
      result.setAccept(i, isAccept(i));
    }

    int deadState = result.createState();
    result.addTransition(deadState, deadState, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);

    Transition t = new Transition();
    for(int i=0;i<numStates;i++) {
      int maxi = Character.MIN_CODE_POINT;
      int count = initTransition(i, t);
      for(int j=0;j<count;j++) {
        getNextTransition(t);
        result.addTransition(i, t.dest, t.min, t.max);
        if (t.min > maxi) {
          result.addTransition(i, deadState, maxi, t.min-1);
        }
        if (t.max + 1 > maxi) {
          maxi = t.max + 1;
        }
      }

      if (maxi <= Character.MAX_CODE_POINT) {
        result.addTransition(i, deadState, maxi, Character.MAX_CODE_POINT);
      }
    }
    result.finish();
    return result;
  }

  public void writeDot(String fileName) {
    if (fileName.indexOf('/') == -1) {
      fileName = "/l/la/lucene/core/" + fileName + ".dot";
    }
    try {
      PrintWriter pw = new PrintWriter(fileName);
      pw.println(toDot());
      pw.close();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public String toDot() {
    // TODO: breadth first search so we can see get layered output...

    StringBuilder b = new StringBuilder();
    b.append("digraph Automaton {\n");
    b.append("  rankdir = LR\n");
    final int numStates = getNumStates();
    if (numStates > 0) {
      b.append("  initial [shape=plaintext,label=\"0\"]\n");
      b.append("  initial -> 0\n");
    }

    Transition t = new Transition();

    for(int state=0;state<numStates;state++) {
      b.append("  ");
      b.append(state);
      if (isAccept(state)) {
        b.append(" [shape=doublecircle,label=\"" + state + "\"]\n");
      } else {
        b.append(" [shape=circle,label=\"" + state + "\"]\n");
      }
      int numTransitions = getNumTransitions(state);
      initTransition(state, t);
      //System.out.println("toDot: state " + state + " has " + numTransitions + " transitions; t.nextTrans=" + t.transitionUpto);
      for(int i=0;i<numTransitions;i++) {
        getNextTransition(t);
        //System.out.println("  t.nextTrans=" + t.transitionUpto);
        assert t.max >= t.min;
        b.append("  ");
        b.append(state);
        b.append(" -> ");
        b.append(t.dest);
        b.append(" [label=\"");
        appendCharString(t.min, b);
        if (t.max != t.min) {
          b.append('-');
          appendCharString(t.max, b);
        }
        b.append("\"]\n");
        //System.out.println("  t=" + t);
      }
    }
    b.append('}');
    return b.toString();
  }

  /**
   * Returns sorted array of all interval start points.
   */
  int[] getStartPoints() {
    Set<Integer> pointset = new HashSet<>();
    pointset.add(Character.MIN_CODE_POINT);
    //System.out.println("getStartPoints");
    for (int s=0;s<nextState;s+=2) {
      int trans = states[s];
      int limit = trans+3*states[s+1];
      //System.out.println("  state=" + (s/2) + " trans=" + trans + " limit=" + limit);
      while (trans < limit) {
        int min = transitions[trans+1];
        int max = transitions[trans+2];
        //System.out.println("    min=" + min);
        pointset.add(min);
        if (max < Character.MAX_CODE_POINT) {
          pointset.add(max + 1);
        }
        trans += 3;
      }
    }
    int[] points = new int[pointset.size()];
    int n = 0;
    for (Integer m : pointset) {
      points[n++] = m;
    }
    Arrays.sort(points);
    return points;
  }

  /**
   * Performs lookup in transitions, assuming determinism.
   * 
   * @param c codepoint to look up
   * @return destination state, -1 if no matching outgoing transition
   * @see #step(int, Collection)
   */
  public int step(int state, int label) {
    assert state >= 0;
    assert label >= 0;
    int trans = states[2*state];
    int limit = trans + 3*states[2*state+1];
    // nocommit we could do bin search; transitions are sorted
    // System.out.println("la.step state=" + state + " label=" + label + "  trans=" + trans + " limit=" + limit);
    while (trans < limit) {
      int dest = transitions[trans];
      int min = transitions[trans+1];
      int max = transitions[trans+2];
      if (min <= label && label <= max) {
        //System.out.println("  ret dest=" + dest);
        return dest;
      }
      trans += 3;
    }

    return -1;
  }

  /** Records new states and transitions and then {@link
   *  #finish} creates the {@link LightAutomaton}.  Use this
   *  when it's too restrictive to have to add all transitions
   *  leaving each state at once. */
  public static class Builder {
    private int[] transitions = new int[4];
    private int nextTransition;
    private final LightAutomaton a = new LightAutomaton();

    public void addTransition(int from, int to, int label) {
      addTransition(from, to, label, label);
    }

    public void addTransition(int from, int to, int min, int max) {
      if (transitions.length < nextTransition+4) {
        transitions = ArrayUtil.grow(transitions, nextTransition+4);
      }
      transitions[nextTransition++] = from;
      transitions[nextTransition++] = to;
      transitions[nextTransition++] = min;
      transitions[nextTransition++] = max;
    }

    /** Sorts transitions first then min label ascending, then
     *  max label ascending, then dest ascending */
    private final Sorter sorter = new InPlaceMergeSorter() {

        private void swapOne(int i, int j) {
          int x = transitions[i];
          transitions[i] = transitions[j];
          transitions[j] = x;
        }

        @Override
        protected void swap(int i, int j) {
          int iStart = 4*i;
          int jStart = 4*j;
          swapOne(iStart, jStart);
          swapOne(iStart+1, jStart+1);
          swapOne(iStart+2, jStart+2);
          swapOne(iStart+3, jStart+3);
        };

        @Override
        protected int compare(int i, int j) {
          int iStart = 4*i;
          int jStart = 4*j;

          // First src:
          int iSrc = transitions[iStart];
          int jSrc = transitions[jStart];
          if (iSrc < jSrc) {
            return -1;
          } else if (iSrc > jSrc) {
            return 1;
          }

          // Then min:
          int iMin = transitions[iStart+2];
          int jMin = transitions[jStart+2];
          if (iMin < jMin) {
            return -1;
          } else if (iMin > jMin) {
            return 1;
          }

          // Then max:
          int iMax = transitions[iStart+3];
          int jMax = transitions[jStart+3];
          if (iMax < jMax) {
            return -1;
          } else if (iMax > jMax) {
            return 1;
          }

          // First dest:
          int iDest = transitions[iStart+1];
          int jDest = transitions[jStart+1];
          if (iDest < jDest) {
            return -1;
          } else if (iDest > jDest) {
            return 1;
          }

          return 0;
        }
      };

    public LightAutomaton finish() {
      //System.out.println("LA.Builder.finish: count=" + (nextTransition/4));
      // nocommit: we could make this more efficient,
      // e.g. somehow xfer the int[] to the automaton, or
      // alloc exactly the right size from the automaton
      //System.out.println("finish pending");
      sorter.sort(0, nextTransition/4);
      int upto = 0;
      while (upto < nextTransition) {
        a.addTransition(transitions[upto],
                        transitions[upto+1],
                        transitions[upto+2],
                        transitions[upto+3]);
        upto += 4;
      }

      a.finish();
      return a;
    }

    public int createState() {
      return a.createState();
    }

    public void setAccept(int state, boolean accept) {
      a.setAccept(state, accept);
    }

    public boolean isAccept(int state) {
      return a.isAccept(state);
    }

    public int getNumStates() {
      return a.getNumStates();
    }

    /** Copies over all states/transitions from other. */
    public void copy(LightAutomaton other) {
      int offset = getNumStates();
      int otherNumStates = other.getNumStates();
      for(int s=0;s<otherNumStates;s++) {
        int newState = createState();
        setAccept(newState, other.isAccept(s));
      }
      Transition t = new Transition();
      for(int s=0;s<otherNumStates;s++) {
        int count = other.initTransition(s, t);
        for(int i=0;i<count;i++) {
          other.getNextTransition(t);
          addTransition(offset + s, offset + t.dest, t.min, t.max);
        }
      }
    }
  }
}
