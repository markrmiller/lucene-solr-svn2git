package org.apache.lucene.index;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.TrackingDirectoryWrapper;

// nocommit fix codec api to pass this around so they can
// store attrs

/**
 * Information about a segment such as it's name, directory, and files related
 * to the segment.
 *
 * @lucene.experimental
 */
public final class SegmentInfo {
  
  // TODO: remove these from this class, for now this is the representation
  public static final int NO = -1;          // e.g. no norms; no deletes;
  public static final int YES = 1;          // e.g. have norms; have deletes;

  public final String name;				  // unique name in dir
  // nocommit make me final:
  public int docCount;				  // number of docs in seg
  public final Directory dir;				  // where segment resides

  // nocommit what other members can we make final?

  /*
   * Current generation of each field's norm file. If this array is null,
   * means no separate norms. If this array is not null, its values mean:
   * - NO says this field has no separate norms
   * >= YES says this field has separate norms with the specified generation
   */
  private final Map<Integer,Long> normGen;

  private boolean isCompoundFile;

  private volatile long sizeInBytes = -1;         // total byte size of all files (computed on demand)

  //TODO: LUCENE-2555: remove once we don't need to support shared doc stores (pre 4.0)
  private final int docStoreOffset;                     // if this segment shares stored fields & vectors, this
                                                  // offset is where in that file this segment's docs begin
  //TODO: LUCENE-2555: remove once we don't need to support shared doc stores (pre 4.0)
  private final String docStoreSegment;                 // name used to derive fields/vectors file we share with
                                                  // other segments
  //TODO: LUCENE-2555: remove once we don't need to support shared doc stores (pre 4.0)
  private final boolean docStoreIsCompoundFile;         // whether doc store files are stored in compound file (*.cfx)

  private Codec codec;

  private Map<String,String> diagnostics;
  
  private Map<String,String> attributes;

  // Tracks the Lucene version this segment was created with, since 3.1. Null
  // indicates an older than 3.0 index, and it's used to detect a too old index.
  // The format expected is "x.y" - "2.x" for pre-3.0 indexes (or null), and
  // specific versions afterwards ("3.0", "3.1" etc.).
  // see Constants.LUCENE_MAIN_VERSION.
  // nocommit final?
  private String version;

  void setDiagnostics(Map<String, String> diagnostics) {
    this.diagnostics = diagnostics;
  }

  public Map<String, String> getDiagnostics() {
    return diagnostics;
  }

  /**
   * Construct a new complete SegmentInfo instance from input.
   * <p>Note: this is public only to allow access from
   * the codecs package.</p>
   */
  public SegmentInfo(Directory dir, String version, String name, int docCount, int docStoreOffset,
                     String docStoreSegment, boolean docStoreIsCompoundFile, Map<Integer,Long> normGen, boolean isCompoundFile,
                     Codec codec, Map<String,String> diagnostics, Map<String,String> attributes) {
    assert !(dir instanceof TrackingDirectoryWrapper);
    this.dir = dir;
    this.version = version;
    this.name = name;
    this.docCount = docCount;
    this.docStoreOffset = docStoreOffset;
    this.docStoreSegment = docStoreSegment;
    this.docStoreIsCompoundFile = docStoreIsCompoundFile;
    this.normGen = normGen;
    this.isCompoundFile = isCompoundFile;
    this.codec = codec;
    this.diagnostics = diagnostics;
    this.attributes = attributes;
  }

  /**
   * Returns total size in bytes of all of files used by this segment
   */
  // nocommit fails to take live docs into account... hmmm
  public long sizeInBytes() throws IOException {
    if (sizeInBytes == -1) {
      long sum = 0;
      for (final String fileName : files()) {
        sum += dir.fileLength(fileName);
      }
      sizeInBytes = sum;
    }
    return sizeInBytes;
  }

  void clearSizeInBytes() {
    sizeInBytes = -1;
  }

  /**
   * @deprecated separate norms are not supported in >= 4.0
   */
  @Deprecated
  boolean hasSeparateNorms() {
    if (normGen == null) {
      return false;
    } else {
      for (long fieldNormGen : normGen.values()) {
        if (fieldNormGen >= YES) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Mark whether this segment is stored as a compound file.
   *
   * @param isCompoundFile true if this is a compound file;
   * else, false
   */
  void setUseCompoundFile(boolean isCompoundFile) {
    this.isCompoundFile = isCompoundFile;
  }
  
  /**
   * Returns true if this segment is stored as a compound
   * file; else, false.
   */
  public boolean getUseCompoundFile() {
    return isCompoundFile;
  }

  /**
   * @deprecated shared doc stores are not supported in >= 4.0
   */
  @Deprecated
  public int getDocStoreOffset() {
    // TODO: LUCENE-2555: remove once we don't need to support shared doc stores (pre 4.0)
    return docStoreOffset;
  }

  /**
   * @deprecated shared doc stores are not supported in >= 4.0
   */
  @Deprecated
  public boolean getDocStoreIsCompoundFile() {
    // TODO: LUCENE-2555: remove once we don't need to support shared doc stores (pre 4.0)
    return docStoreIsCompoundFile;
  }

  /**
   * @deprecated shared doc stores are not supported in >= 4.0
   */
  @Deprecated
  public String getDocStoreSegment() {
    // TODO: LUCENE-2555: remove once we don't need to support shared doc stores (pre 4.0)
    return docStoreSegment;
  }

  /** Can only be called once. */
  public void setCodec(Codec codec) {
    assert this.codec == null;
    if (codec == null) {
      throw new IllegalArgumentException("segmentCodecs must be non-null");
    }
    this.codec = codec;
  }

  public Codec getCodec() {
    return codec;
  }

  /*
   * Return all files referenced by this SegmentInfo.  The
   * returns List is a locally cached List so you should not
   * modify it.
   */

  // nocommit remove this temporarily to see who is calling
  // it ...  very dangerous having this one AND SIPC.files()
  public Collection<String> files() throws IOException {
    // nocommit make sure when we are called we really have
    // files set ...
    if (setFiles == null) {
      throw new IllegalStateException("files were not computed yet");
    }
    return setFiles;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return toString(dir, 0);
  }

  /** Used for debugging.  Format may suddenly change.
   *
   *  <p>Current format looks like
   *  <code>_a(3.1):c45/4->_1</code>, which means the segment's
   *  name is <code>_a</code>; it was created with Lucene 3.1 (or
   *  '?' if it's unknown); it's using compound file
   *  format (would be <code>C</code> if not compound); it
   *  has 45 documents; it has 4 deletions (this part is
   *  left off when there are no deletions); it's using the
   *  shared doc stores named <code>_1</code> (this part is
   *  left off if doc stores are private).</p>
   */
  public String toString(Directory dir, int delCount) {

    StringBuilder s = new StringBuilder();
    s.append(name).append('(').append(version == null ? "?" : version).append(')').append(':');
    char cfs = getUseCompoundFile() ? 'c' : 'C';
    s.append(cfs);

    if (this.dir != dir) {
      s.append('x');
    }
    s.append(docCount);

    if (delCount != 0) {
      s.append('/').append(delCount);
    }

    if (docStoreOffset != -1) {
      s.append("->").append(docStoreSegment);
      if (docStoreIsCompoundFile) {
        s.append('c');
      } else {
        s.append('C');
      }
      s.append('+').append(docStoreOffset);
    }

    return s.toString();
  }

  /** We consider another SegmentInfo instance equal if it
   *  has the same dir and same name. */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof SegmentInfo) {
      final SegmentInfo other = (SegmentInfo) obj;
      return other.dir == dir && other.name.equals(name);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return dir.hashCode() + name.hashCode();
  }

  /**
   * Used by DefaultSegmentInfosReader to upgrade a 3.0 segment to record its
   * version is "3.0". This method can be removed when we're not required to
   * support 3x indexes anymore, e.g. in 5.0.
   * <p>
   * <b>NOTE:</b> this method is used for internal purposes only - you should
   * not modify the version of a SegmentInfo, or it may result in unexpected
   * exceptions thrown when you attempt to open the index.
   *
   * @lucene.internal
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /** Returns the version of the code which wrote the segment. */
  public String getVersion() {
    return version;
  }

  /** @lucene.internal */
  public Map<Integer,Long> getNormGen() {
    return normGen;
  }

  private Set<String> setFiles;

  // nocommit now on building a CFS we erase the files that
  // are in it... maybe we should somehow preserve it...
  public void setFiles(Set<String> files) {
    setFiles = files;
    sizeInBytes = -1;
  }

  // nocommit remove this!  it's only needed for
  // clearing/adding the files set...
  public Set<String> getFiles() {
    return setFiles;
  }
  
  /**
   * Get a codec attribute value, or null if it does not exist
   */
  public String getAttribute(String key) {
    if (attributes == null) {
      return null;
    } else {
      return attributes.get(key);
    }
  }
  
  /**
   * Puts a codec attribute value.
   * <p>
   * This is a key-value mapping for the field that the codec can use
   * to store additional metadata, and will be available to the codec
   * when reading the segment via {@link #getAttribute(String)}
   * <p>
   * If a value already exists for the field, it will be replaced with 
   * the new value.
   */
  public String putAttribute(String key, String value) {
    if (attributes == null) {
      attributes = new HashMap<String,String>();
    }
    return attributes.put(key, value);
  }
  
  /**
   * @return internal codec attributes map. May be null if no mappings exist.
   */
  public Map<String,String> attributes() {
    return attributes;
  }
}
