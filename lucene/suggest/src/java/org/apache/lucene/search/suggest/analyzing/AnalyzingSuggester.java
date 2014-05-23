package org.apache.lucene.search.suggest.analyzing;

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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.TokenStreamToAutomaton;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.OfflineSorter;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.BasicOperations;
import org.apache.lucene.util.automaton.SpecialOperations;
import org.apache.lucene.util.automaton.State;
import org.apache.lucene.util.automaton.Transition;
import org.apache.lucene.util.fst.Builder;
import org.apache.lucene.util.fst.ByteSequenceOutputs;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FST.BytesReader;
import org.apache.lucene.util.fst.PairOutputs;
import org.apache.lucene.util.fst.PairOutputs.Pair;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;
import org.apache.lucene.util.fst.Util.Result;
import org.apache.lucene.util.fst.Util.TopResults;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Suggester that first analyzes the surface form, adds the
 * analyzed form to a weighted FST, and then does the same
 * thing at lookup time.  This means lookup is based on the
 * analyzed form while suggestions are still the surface
 * form(s).
 *
 * <p>
 * This can result in powerful suggester functionality.  For
 * example, if you use an analyzer removing stop words, 
 * then the partial text "ghost chr..." could see the
 * suggestion "The Ghost of Christmas Past". Note that
 * position increments MUST NOT be preserved for this example
 * to work, so you should call the constructor with 
 * <code>preservePositionIncrements</code> parameter set to 
 * false
 *
 * <p>
 * If SynonymFilter is used to map wifi and wireless network to
 * hotspot then the partial text "wirele..." could suggest
 * "wifi router".  Token normalization like stemmers, accent
 * removal, etc., would allow suggestions to ignore such
 * variations.
 *
 * <p>
 * When two matching suggestions have the same weight, they
 * are tie-broken by the analyzed form.  If their analyzed
 * form is the same then the order is undefined.
 *
 * <p>
 * There are some limitations:
 * <ul>
 *
 *   <li> A lookup from a query like "net" in English won't
 *        be any different than "net " (ie, user added a
 *        trailing space) because analyzers don't reflect
 *        when they've seen a token separator and when they
 *        haven't.
 *
 *   <li> If you're using {@code StopFilter}, and the user will
 *        type "fast apple", but so far all they've typed is
 *        "fast a", again because the analyzer doesn't convey whether
 *        it's seen a token separator after the "a",
 *        {@code StopFilter} will remove that "a" causing
 *        far more matches than you'd expect.
 *
 *   <li> Lookups with the empty string return no results
 *        instead of all results.
 * </ul>
 * 
 * @lucene.experimental
 */
public class AnalyzingSuggester extends Lookup {
 
  /**
   * FST<Weight,Surface>: 
   *  input is the analyzed form, with a null byte between terms
   *  weights are encoded as costs: (Integer.MAX_VALUE-weight)
   *  surface is the original, unanalyzed form.
   */
  private FST<Pair<Long,BytesRef>> fst = null;
  
  /** 
   * Analyzer that will be used for analyzing suggestions at
   * index time.
   */
  private final Analyzer indexAnalyzer;

  /** 
   * Analyzer that will be used for analyzing suggestions at
   * query time.
   */
  private final Analyzer queryAnalyzer;
  
  /** 
   * True if exact match suggestions should always be returned first.
   */
  private final boolean exactFirst;
  
  /** 
   * True if separator between tokens should be preserved.
   */
  private final boolean preserveSep;

  /** Include this flag in the options parameter to {@link
   *  #AnalyzingSuggester(Analyzer,Analyzer,int,int,int,boolean)} to always
   *  return the exact match first, regardless of score.  This
   *  has no performance impact but could result in
   *  low-quality suggestions. */
  public static final int EXACT_FIRST = 1;

  /** Include this flag in the options parameter to {@link
   *  #AnalyzingSuggester(Analyzer,Analyzer,int,int,int,boolean)} to preserve
   *  token separators when matching. */
  public static final int PRESERVE_SEP = 2;

  /** Represents the separation between tokens, if
   *  PRESERVE_SEP was specified */
  private static final int SEP_LABEL = '\u001F';

  /** Marks end of the analyzed input and start of dedup
   *  byte. */
  private static final int END_BYTE = 0x0;

  /** Maximum number of dup surface forms (different surface
   *  forms for the same analyzed form). */
  private final int maxSurfaceFormsPerAnalyzedForm;

  /** Maximum graph paths to index for a single analyzed
   *  surface form.  This only matters if your analyzer
   *  makes lots of alternate paths (e.g. contains
   *  SynonymFilter). */
  private final int maxGraphExpansions;

  /** Highest number of analyzed paths we saw for any single
   *  input surface form.  For analyzers that never create
   *  graphs this will always be 1. */
  private int maxAnalyzedPathsForOneInput;

  private boolean hasPayloads;

  private static final int PAYLOAD_SEP = '\u001f';

  /** Whether position holes should appear in the automaton. */
  private boolean preservePositionIncrements;

  /** Number of entries the lookup was built with */
  private long count = 0;

  /**
   * Calls {@link #AnalyzingSuggester(Analyzer,Analyzer,int,int,int,boolean)
   * AnalyzingSuggester(analyzer, analyzer, EXACT_FIRST |
   * PRESERVE_SEP, 256, -1, true)}
   */
  public AnalyzingSuggester(Analyzer analyzer) {
    this(analyzer, analyzer, EXACT_FIRST | PRESERVE_SEP, 256, -1, true);
  }

  /**
   * Calls {@link #AnalyzingSuggester(Analyzer,Analyzer,int,int,int,boolean)
   * AnalyzingSuggester(indexAnalyzer, queryAnalyzer, EXACT_FIRST |
   * PRESERVE_SEP, 256, -1, true)}
   */
  public AnalyzingSuggester(Analyzer indexAnalyzer, Analyzer queryAnalyzer) {
    this(indexAnalyzer, queryAnalyzer, EXACT_FIRST | PRESERVE_SEP, 256, -1, true);
  }

  /**
   * Creates a new suggester.
   * 
   * @param indexAnalyzer Analyzer that will be used for
   *   analyzing suggestions while building the index.
   * @param queryAnalyzer Analyzer that will be used for
   *   analyzing query text during lookup
   * @param options see {@link #EXACT_FIRST}, {@link #PRESERVE_SEP}
   * @param maxSurfaceFormsPerAnalyzedForm Maximum number of
   *   surface forms to keep for a single analyzed form.
   *   When there are too many surface forms we discard the
   *   lowest weighted ones.
   * @param maxGraphExpansions Maximum number of graph paths
   *   to expand from the analyzed form.  Set this to -1 for
   *   no limit.
   * @param preservePositionIncrements Whether position holes
   *   should appear in the automata
   */
  public AnalyzingSuggester(Analyzer indexAnalyzer, Analyzer queryAnalyzer, int options, int maxSurfaceFormsPerAnalyzedForm, int maxGraphExpansions,
      boolean preservePositionIncrements) {
    this.indexAnalyzer = indexAnalyzer;
    this.queryAnalyzer = queryAnalyzer;
    if ((options & ~(EXACT_FIRST | PRESERVE_SEP)) != 0) {
      throw new IllegalArgumentException("options should only contain EXACT_FIRST and PRESERVE_SEP; got " + options);
    }
    this.exactFirst = (options & EXACT_FIRST) != 0;
    this.preserveSep = (options & PRESERVE_SEP) != 0;

    // NOTE: this is just an implementation limitation; if
    // somehow this is a problem we could fix it by using
    // more than one byte to disambiguate ... but 256 seems
    // like it should be way more then enough.
    if (maxSurfaceFormsPerAnalyzedForm <= 0 || maxSurfaceFormsPerAnalyzedForm > 256) {
      throw new IllegalArgumentException("maxSurfaceFormsPerAnalyzedForm must be > 0 and < 256 (got: " + maxSurfaceFormsPerAnalyzedForm + ")");
    }
    this.maxSurfaceFormsPerAnalyzedForm = maxSurfaceFormsPerAnalyzedForm;

    if (maxGraphExpansions < 1 && maxGraphExpansions != -1) {
      throw new IllegalArgumentException("maxGraphExpansions must -1 (no limit) or > 0 (got: " + maxGraphExpansions + ")");
    }
    this.maxGraphExpansions = maxGraphExpansions;
    this.preservePositionIncrements = preservePositionIncrements;
  }

  /** Returns byte size of the underlying FST. */
  @Override
  public long sizeInBytes() {
    return fst == null ? 0 : fst.sizeInBytes();
  }

  private void copyDestTransitions(State from, State to, List<Transition> transitions) {
    if (to.isAccept()) {
      from.setAccept(true);
    }
    for(Transition t : to.getTransitions()) {
      transitions.add(t);
    }
  }

  // Replaces SEP with epsilon or remaps them if
  // we were asked to preserve them:
  private void replaceSep(Automaton a) {

    State[] states = a.getNumberedStates();

    // Go in reverse topo sort so we know we only have to
    // make one pass:
    for(int stateNumber=states.length-1;stateNumber >=0;stateNumber--) {
      final State state = states[stateNumber];
      List<Transition> newTransitions = new ArrayList<>();
      for(Transition t : state.getTransitions()) {
        assert t.getMin() == t.getMax();
        if (t.getMin() == TokenStreamToAutomaton.POS_SEP) {
          if (preserveSep) {
            // Remap to SEP_LABEL:
            newTransitions.add(new Transition(SEP_LABEL, t.getDest()));
          } else {
            copyDestTransitions(state, t.getDest(), newTransitions);
            a.setDeterministic(false);
          }
        } else if (t.getMin() == TokenStreamToAutomaton.HOLE) {

          // Just remove the hole: there will then be two
          // SEP tokens next to each other, which will only
          // match another hole at search time.  Note that
          // it will also match an empty-string token ... if
          // that's somehow a problem we can always map HOLE
          // to a dedicated byte (and escape it in the
          // input).
          copyDestTransitions(state, t.getDest(), newTransitions);
          a.setDeterministic(false);
        } else {
          newTransitions.add(t);
        }
      }
      state.setTransitions(newTransitions.toArray(new Transition[newTransitions.size()]));
    }
  }

  /** Used by subclass to change the lookup automaton, if
   *  necessary. */
  protected Automaton convertAutomaton(Automaton a) {
    return a;
  }
  
  TokenStreamToAutomaton getTokenStreamToAutomaton() {
    final TokenStreamToAutomaton tsta = new TokenStreamToAutomaton();
    tsta.setPreservePositionIncrements(preservePositionIncrements);
    return tsta;
  }
  
  private static class AnalyzingComparator implements Comparator<BytesRef> {

    private final boolean hasPayloads;

    public AnalyzingComparator(boolean hasPayloads) {
      this.hasPayloads = hasPayloads;
    }

    private final ByteArrayDataInput readerA = new ByteArrayDataInput();
    private final ByteArrayDataInput readerB = new ByteArrayDataInput();
    private final BytesRef scratchA = new BytesRef();
    private final BytesRef scratchB = new BytesRef();

    @Override
    public int compare(BytesRef a, BytesRef b) {

      // First by analyzed form:
      readerA.reset(a.bytes, a.offset, a.length);
      scratchA.length = readerA.readShort();
      scratchA.bytes = a.bytes;
      scratchA.offset = readerA.getPosition();

      readerB.reset(b.bytes, b.offset, b.length);
      scratchB.bytes = b.bytes;
      scratchB.length = readerB.readShort();
      scratchB.offset = readerB.getPosition();

      int cmp = scratchA.compareTo(scratchB);
      if (cmp != 0) {
        return cmp;
      }
      readerA.skipBytes(scratchA.length);
      readerB.skipBytes(scratchB.length);

      // Next by cost:
      long aCost = readerA.readInt();
      long bCost = readerB.readInt();
      assert decodeWeight(aCost) >= 0;
      assert decodeWeight(bCost) >= 0;
      if (aCost < bCost) {
        return -1;
      } else if (aCost > bCost) {
        return 1;
      }

      // Finally by surface form:
      if (hasPayloads) {
        scratchA.length = readerA.readShort();
        scratchB.length = readerB.readShort();
        scratchA.offset = readerA.getPosition();
        scratchB.offset = readerB.getPosition();
      } else {
        scratchA.offset = readerA.getPosition();
        scratchB.offset = readerB.getPosition();
        scratchA.length = a.length - scratchA.offset;
        scratchB.length = b.length - scratchB.offset;
      }
   
      return scratchA.compareTo(scratchB);
    }
  }

  @Override
  public void build(InputIterator iterator) throws IOException {
    if (iterator.hasContexts()) {
      throw new IllegalArgumentException("this suggester doesn't support contexts");
    }
    String prefix = getClass().getSimpleName();
    File directory = OfflineSorter.defaultTempDir();
    File tempInput = File.createTempFile(prefix, ".input", directory);
    File tempSorted = File.createTempFile(prefix, ".sorted", directory);

    hasPayloads = iterator.hasPayloads();

    OfflineSorter.ByteSequencesWriter writer = new OfflineSorter.ByteSequencesWriter(tempInput);
    OfflineSorter.ByteSequencesReader reader = null;
    BytesRef scratch = new BytesRef();

    TokenStreamToAutomaton ts2a = getTokenStreamToAutomaton();

    boolean success = false;
    count = 0;
    byte buffer[] = new byte[8];
    try {
      ByteArrayDataOutput output = new ByteArrayDataOutput(buffer);
      BytesRef surfaceForm;

      while ((surfaceForm = iterator.next()) != null) {
        Set<IntsRef> paths = toFiniteStrings(surfaceForm, ts2a);
        
        maxAnalyzedPathsForOneInput = Math.max(maxAnalyzedPathsForOneInput, paths.size());

        for (IntsRef path : paths) {

          Util.toBytesRef(path, scratch);
          
          // length of the analyzed text (FST input)
          if (scratch.length > Short.MAX_VALUE-2) {
            throw new IllegalArgumentException("cannot handle analyzed forms > " + (Short.MAX_VALUE-2) + " in length (got " + scratch.length + ")");
          }
          short analyzedLength = (short) scratch.length;

          // compute the required length:
          // analyzed sequence + weight (4) + surface + analyzedLength (short)
          int requiredLength = analyzedLength + 4 + surfaceForm.length + 2;

          BytesRef payload;

          if (hasPayloads) {
            if (surfaceForm.length > (Short.MAX_VALUE-2)) {
              throw new IllegalArgumentException("cannot handle surface form > " + (Short.MAX_VALUE-2) + " in length (got " + surfaceForm.length + ")");
            }
            payload = iterator.payload();
            // payload + surfaceLength (short)
            requiredLength += payload.length + 2;
          } else {
            payload = null;
          }
          
          buffer = ArrayUtil.grow(buffer, requiredLength);
          
          output.reset(buffer);

          output.writeShort(analyzedLength);

          output.writeBytes(scratch.bytes, scratch.offset, scratch.length);

          output.writeInt(encodeWeight(iterator.weight()));

          if (hasPayloads) {
            for(int i=0;i<surfaceForm.length;i++) {
              if (surfaceForm.bytes[i] == PAYLOAD_SEP) {
                throw new IllegalArgumentException("surface form cannot contain unit separator character U+001F; this character is reserved");
              }
            }
            output.writeShort((short) surfaceForm.length);
            output.writeBytes(surfaceForm.bytes, surfaceForm.offset, surfaceForm.length);
            output.writeBytes(payload.bytes, payload.offset, payload.length);
          } else {
            output.writeBytes(surfaceForm.bytes, surfaceForm.offset, surfaceForm.length);
          }

          assert output.getPosition() == requiredLength: output.getPosition() + " vs " + requiredLength;
          writer.write(buffer, 0, output.getPosition());
        }
        count++;
      }
      writer.close();

      // Sort all input/output pairs (required by FST.Builder):
      new OfflineSorter(new AnalyzingComparator(hasPayloads)).sort(tempInput, tempSorted);

      // Free disk space:
      tempInput.delete();

      reader = new OfflineSorter.ByteSequencesReader(tempSorted);
     
      PairOutputs<Long,BytesRef> outputs = new PairOutputs<>(PositiveIntOutputs.getSingleton(), ByteSequenceOutputs.getSingleton());
      Builder<Pair<Long,BytesRef>> builder = new Builder<>(FST.INPUT_TYPE.BYTE1, outputs);

      // Build FST:
      BytesRef previousAnalyzed = null;
      BytesRef analyzed = new BytesRef();
      BytesRef surface = new BytesRef();
      IntsRef scratchInts = new IntsRef();
      ByteArrayDataInput input = new ByteArrayDataInput();

      // Used to remove duplicate surface forms (but we
      // still index the hightest-weight one).  We clear
      // this when we see a new analyzed form, so it cannot
      // grow unbounded (at most 256 entries):
      Set<BytesRef> seenSurfaceForms = new HashSet<>();

      int dedup = 0;
      while (reader.read(scratch)) {
        input.reset(scratch.bytes, scratch.offset, scratch.length);
        short analyzedLength = input.readShort();
        analyzed.grow(analyzedLength+2);
        input.readBytes(analyzed.bytes, 0, analyzedLength);
        analyzed.length = analyzedLength;

        long cost = input.readInt();

        surface.bytes = scratch.bytes;
        if (hasPayloads) {
          surface.length = input.readShort();
          surface.offset = input.getPosition();
        } else {
          surface.offset = input.getPosition();
          surface.length = scratch.length - surface.offset;
        }
        
        if (previousAnalyzed == null) {
          previousAnalyzed = new BytesRef();
          previousAnalyzed.copyBytes(analyzed);
          seenSurfaceForms.add(BytesRef.deepCopyOf(surface));
        } else if (analyzed.equals(previousAnalyzed)) {
          dedup++;
          if (dedup >= maxSurfaceFormsPerAnalyzedForm) {
            // More than maxSurfaceFormsPerAnalyzedForm
            // dups: skip the rest:
            continue;
          }
          if (seenSurfaceForms.contains(surface)) {
            continue;
          }
          seenSurfaceForms.add(BytesRef.deepCopyOf(surface));
        } else {
          dedup = 0;
          previousAnalyzed.copyBytes(analyzed);
          seenSurfaceForms.clear();
          seenSurfaceForms.add(BytesRef.deepCopyOf(surface));
        }

        // TODO: I think we can avoid the extra 2 bytes when
        // there is no dup (dedup==0), but we'd have to fix
        // the exactFirst logic ... which would be sort of
        // hairy because we'd need to special case the two
        // (dup/not dup)...

        // NOTE: must be byte 0 so we sort before whatever
        // is next
        analyzed.bytes[analyzed.offset+analyzed.length] = 0;
        analyzed.bytes[analyzed.offset+analyzed.length+1] = (byte) dedup;
        analyzed.length += 2;

        Util.toIntsRef(analyzed, scratchInts);
        //System.out.println("ADD: " + scratchInts + " -> " + cost + ": " + surface.utf8ToString());
        if (!hasPayloads) {
          builder.add(scratchInts, outputs.newPair(cost, BytesRef.deepCopyOf(surface)));
        } else {
          int payloadOffset = input.getPosition() + surface.length;
          int payloadLength = scratch.length - payloadOffset;
          BytesRef br = new BytesRef(surface.length + 1 + payloadLength);
          System.arraycopy(surface.bytes, surface.offset, br.bytes, 0, surface.length);
          br.bytes[surface.length] = PAYLOAD_SEP;
          System.arraycopy(scratch.bytes, payloadOffset, br.bytes, surface.length+1, payloadLength);
          br.length = br.bytes.length;
          builder.add(scratchInts, outputs.newPair(cost, br));
        }
      }
      fst = builder.finish();

      //Util.dotToFile(fst, "/tmp/suggest.dot");
      
      success = true;
    } finally {
      if (success) {
        IOUtils.close(reader, writer);
      } else {
        IOUtils.closeWhileHandlingException(reader, writer);
      }
      
      tempInput.delete();
      tempSorted.delete();
    }
  }

  @Override
  public boolean store(DataOutput output) throws IOException {
    output.writeVLong(count);
    if (fst == null) {
      return false;
    }

    fst.save(output);
    output.writeVInt(maxAnalyzedPathsForOneInput);
    output.writeByte((byte) (hasPayloads ? 1 : 0));
    return true;
  }

  @Override
  public boolean load(DataInput input) throws IOException {
    count = input.readVLong();
    this.fst = new FST<>(input, new PairOutputs<>(PositiveIntOutputs.getSingleton(), ByteSequenceOutputs.getSingleton()));
    maxAnalyzedPathsForOneInput = input.readVInt();
    hasPayloads = input.readByte() == 1;
    return true;
  }

  private LookupResult getLookupResult(Long output1, BytesRef output2, CharsRef spare) {
    LookupResult result;
    if (hasPayloads) {
      int sepIndex = -1;
      for(int i=0;i<output2.length;i++) {
        if (output2.bytes[output2.offset+i] == PAYLOAD_SEP) {
          sepIndex = i;
          break;
        }
      }
      assert sepIndex != -1;
      spare.grow(sepIndex);
      final int payloadLen = output2.length - sepIndex - 1;
      UnicodeUtil.UTF8toUTF16(output2.bytes, output2.offset, sepIndex, spare);
      BytesRef payload = new BytesRef(payloadLen);
      System.arraycopy(output2.bytes, sepIndex+1, payload.bytes, 0, payloadLen);
      payload.length = payloadLen;
      result = new LookupResult(spare.toString(), decodeWeight(output1), payload);
    } else {
      spare.grow(output2.length);
      UnicodeUtil.UTF8toUTF16(output2, spare);
      result = new LookupResult(spare.toString(), decodeWeight(output1));
    }

    return result;
  }

  private boolean sameSurfaceForm(BytesRef key, BytesRef output2) {
    if (hasPayloads) {
      // output2 has at least PAYLOAD_SEP byte:
      if (key.length >= output2.length) {
        return false;
      }
      for(int i=0;i<key.length;i++) {
        if (key.bytes[key.offset+i] != output2.bytes[output2.offset+i]) {
          return false;
        }
      }
      return output2.bytes[output2.offset + key.length] == PAYLOAD_SEP;
    } else {
      return key.bytesEquals(output2);
    }
  }

  @Override
  public List<LookupResult> lookup(final CharSequence key, Set<BytesRef> contexts, boolean onlyMorePopular, int num) {
    assert num > 0;

    if (onlyMorePopular) {
      throw new IllegalArgumentException("this suggester only works with onlyMorePopular=false");
    }
    if (contexts != null) {
      throw new IllegalArgumentException("this suggester doesn't support contexts");
    }
    if (fst == null) {
      return Collections.emptyList();
    }

    //System.out.println("lookup key=" + key + " num=" + num);
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) == 0x1E) {
        throw new IllegalArgumentException("lookup key cannot contain HOLE character U+001E; this character is reserved");
      }
      if (key.charAt(i) == 0x1F) {
        throw new IllegalArgumentException("lookup key cannot contain unit separator character U+001F; this character is reserved");
      }
    }
    final BytesRef utf8Key = new BytesRef(key);
    try {

      Automaton lookupAutomaton = toLookupAutomaton(key);

      final CharsRef spare = new CharsRef();

      //System.out.println("  now intersect exactFirst=" + exactFirst);
    
      // Intersect automaton w/ suggest wFST and get all
      // prefix starting nodes & their outputs:
      //final PathIntersector intersector = getPathIntersector(lookupAutomaton, fst);

      //System.out.println("  prefixPaths: " + prefixPaths.size());

      BytesReader bytesReader = fst.getBytesReader();

      FST.Arc<Pair<Long,BytesRef>> scratchArc = new FST.Arc<>();

      final List<LookupResult> results = new ArrayList<>();

      List<FSTUtil.Path<Pair<Long,BytesRef>>> prefixPaths = FSTUtil.intersectPrefixPaths(convertAutomaton(lookupAutomaton), fst);

      if (exactFirst) {

        int count = 0;
        for (FSTUtil.Path<Pair<Long,BytesRef>> path : prefixPaths) {
          if (fst.findTargetArc(END_BYTE, path.fstNode, scratchArc, bytesReader) != null) {
            // This node has END_BYTE arc leaving, meaning it's an
            // "exact" match:
            count++;
          }
        }

        // Searcher just to find the single exact only
        // match, if present:
        Util.TopNSearcher<Pair<Long,BytesRef>> searcher;
        searcher = new Util.TopNSearcher<>(fst, count * maxSurfaceFormsPerAnalyzedForm, count * maxSurfaceFormsPerAnalyzedForm, weightComparator);

        // NOTE: we could almost get away with only using
        // the first start node.  The only catch is if
        // maxSurfaceFormsPerAnalyzedForm had kicked in and
        // pruned our exact match from one of these nodes
        // ...:
        for (FSTUtil.Path<Pair<Long,BytesRef>> path : prefixPaths) {
          if (fst.findTargetArc(END_BYTE, path.fstNode, scratchArc, bytesReader) != null) {
            // This node has END_BYTE arc leaving, meaning it's an
            // "exact" match:
            searcher.addStartPaths(scratchArc, fst.outputs.add(path.output, scratchArc.output), false, path.input);
          }
        }

        TopResults<Pair<Long,BytesRef>> completions = searcher.search();
        assert completions.isComplete;

        // NOTE: this is rather inefficient: we enumerate
        // every matching "exactly the same analyzed form"
        // path, and then do linear scan to see if one of
        // these exactly matches the input.  It should be
        // possible (though hairy) to do something similar
        // to getByOutput, since the surface form is encoded
        // into the FST output, so we more efficiently hone
        // in on the exact surface-form match.  Still, I
        // suspect very little time is spent in this linear
        // seach: it's bounded by how many prefix start
        // nodes we have and the
        // maxSurfaceFormsPerAnalyzedForm:
        for(Result<Pair<Long,BytesRef>> completion : completions) {
          BytesRef output2 = completion.output.output2;
          if (sameSurfaceForm(utf8Key, output2)) {
            results.add(getLookupResult(completion.output.output1, output2, spare));
            break;
          }
        }

        if (results.size() == num) {
          // That was quick:
          return results;
        }
      }

      Util.TopNSearcher<Pair<Long,BytesRef>> searcher;
      searcher = new Util.TopNSearcher<Pair<Long,BytesRef>>(fst,
                                                            num - results.size(),
                                                            num * maxAnalyzedPathsForOneInput,
                                                            weightComparator) {
        private final Set<BytesRef> seen = new HashSet<>();

        @Override
        protected boolean acceptResult(IntsRef input, Pair<Long,BytesRef> output) {

          // Dedup: when the input analyzes to a graph we
          // can get duplicate surface forms:
          if (seen.contains(output.output2)) {
            return false;
          }
          seen.add(output.output2);
          
          if (!exactFirst) {
            return true;
          } else {
            // In exactFirst mode, don't accept any paths
            // matching the surface form since that will
            // create duplicate results:
            if (sameSurfaceForm(utf8Key, output.output2)) {
              // We found exact match, which means we should
              // have already found it in the first search:
              assert results.size() == 1;
              return false;
            } else {
              return true;
            }
          }
        }
      };

      prefixPaths = getFullPrefixPaths(prefixPaths, lookupAutomaton, fst);
      
      for (FSTUtil.Path<Pair<Long,BytesRef>> path : prefixPaths) {
        searcher.addStartPaths(path.fstNode, path.output, true, path.input);
      }

      TopResults<Pair<Long,BytesRef>> completions = searcher.search();
      assert completions.isComplete;

      for(Result<Pair<Long,BytesRef>> completion : completions) {

        LookupResult result = getLookupResult(completion.output.output1, completion.output.output2, spare);

        // TODO: for fuzzy case would be nice to return
        // how many edits were required

        //System.out.println("    result=" + result);
        results.add(result);

        if (results.size() == num) {
          // In the exactFirst=true case the search may
          // produce one extra path
          break;
        }
      }

      return results;
    } catch (IOException bogus) {
      throw new RuntimeException(bogus);
    }
  }
  
  @Override
  public long getCount() {
    return count;
  }

  /** Returns all prefix paths to initialize the search. */
  protected List<FSTUtil.Path<Pair<Long,BytesRef>>> getFullPrefixPaths(List<FSTUtil.Path<Pair<Long,BytesRef>>> prefixPaths,
                                                                       Automaton lookupAutomaton,
                                                                       FST<Pair<Long,BytesRef>> fst)
    throws IOException {
    return prefixPaths;
  }
  
  final Set<IntsRef> toFiniteStrings(final BytesRef surfaceForm, final TokenStreamToAutomaton ts2a) throws IOException {
    // Analyze surface form:
    Automaton automaton = null;
    try (TokenStream ts = indexAnalyzer.tokenStream("", surfaceForm.utf8ToString())) {

      // Create corresponding automaton: labels are bytes
      // from each analyzed token, with byte 0 used as
      // separator between tokens:
      automaton = ts2a.toAutomaton(ts);
    }

    replaceSep(automaton);
    automaton = convertAutomaton(automaton);

    // TODO: LUCENE-5660 re-enable this once we disallow massive suggestion strings
    // assert SpecialOperations.isFinite(automaton);

    // Get all paths from the automaton (there can be
    // more than one path, eg if the analyzer created a
    // graph using SynFilter or WDF):

    // TODO: we could walk & add simultaneously, so we
    // don't have to alloc [possibly biggish]
    // intermediate HashSet in RAM:
    return SpecialOperations.getFiniteStrings(automaton, maxGraphExpansions);
  }

  final Automaton toLookupAutomaton(final CharSequence key) throws IOException {
    // TODO: is there a Reader from a CharSequence?
    // Turn tokenstream into automaton:
    Automaton automaton = null;
    try (TokenStream ts = queryAnalyzer.tokenStream("", key.toString())) {
      automaton = (getTokenStreamToAutomaton()).toAutomaton(ts);
    }

    // TODO: we could use the end offset to "guess"
    // whether the final token was a partial token; this
    // would only be a heuristic ... but maybe an OK one.
    // This way we could eg differentiate "net" from "net ",
    // which we can't today...

    replaceSep(automaton);

    // TODO: we can optimize this somewhat by determinizing
    // while we convert
    BasicOperations.determinize(automaton);
    return automaton;
  }
  
  

  /**
   * Returns the weight associated with an input string,
   * or null if it does not exist.
   */
  public Object get(CharSequence key) {
    throw new UnsupportedOperationException();
  }
  
  /** cost -> weight */
  private static int decodeWeight(long encoded) {
    return (int)(Integer.MAX_VALUE - encoded);
  }
  
  /** weight -> cost */
  private static int encodeWeight(long value) {
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw new UnsupportedOperationException("cannot encode value: " + value);
    }
    return Integer.MAX_VALUE - (int)value;
  }
   
  static final Comparator<Pair<Long,BytesRef>> weightComparator = new Comparator<Pair<Long,BytesRef>> () {
    @Override
    public int compare(Pair<Long,BytesRef> left, Pair<Long,BytesRef> right) {
      return left.output1.compareTo(right.output1);
    }
  };
}
