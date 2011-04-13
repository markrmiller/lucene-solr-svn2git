package org.apache.lucene.index;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.index.DocumentsWriterPerThreadPool.ThreadState;
import org.apache.lucene.util.ThreadInterruptedException;

/**
 * This class controls {@link DocumentsWriterPerThread} flushing during
 * indexing. It tracks the memory consumption per
 * {@link DocumentsWriterPerThread} and uses a configured {@link FlushPolicy} to
 * decide if a {@link DocumentsWriterPerThread} must flush.
 * <p>
 * In addition to the {@link FlushPolicy} the flush control might set certain
 * {@link DocumentsWriterPerThread} as flush pending iff a
 * {@link DocumentsWriterPerThread} exceeds the
 * {@link IndexWriterConfig#getRAMPerThreadHardLimitMB()} to prevent address
 * space exhaustion.
 */
public final class DocumentsWriterFlushControl {

  private final long maxBytesPerDWPT;
  private long activeBytes = 0;
  private long flushBytes = 0;
  private volatile int numPending = 0;
  private volatile int numFlushing = 0;
  final AtomicBoolean flushDeletes = new AtomicBoolean(false);
  private boolean fullFlush = false;
  private Queue<DocumentsWriterPerThread> flushQueue = new LinkedList<DocumentsWriterPerThread>();
  // only for safety reasons if a DWPT is close to the RAM limit
  private Queue<DocumentsWriterPerThread> blockedFlushes = new LinkedList<DocumentsWriterPerThread>();


  long peakActiveBytes = 0;// only with assert
  long peakFlushBytes = 0;// only with assert
  long peakNetBytes = 0;// only with assert
  private final Healthiness healthiness;
  private final DocumentsWriterPerThreadPool perThreadPool;
  private final FlushPolicy flushPolicy;
  private boolean closed = false;
  private final HashMap<DocumentsWriterPerThread, Long> flushingWriters = new HashMap<DocumentsWriterPerThread, Long>();
  private final DocumentsWriter documentsWriter;

  DocumentsWriterFlushControl(DocumentsWriter documentsWriter,
      Healthiness healthiness, long maxBytesPerDWPT) {
    this.healthiness = healthiness;
    this.perThreadPool = documentsWriter.perThreadPool;
    this.flushPolicy = documentsWriter.flushPolicy;
    this.maxBytesPerDWPT = maxBytesPerDWPT;
    this.documentsWriter = documentsWriter;
  }

  public synchronized long activeBytes() {
    return activeBytes;
  }

  public synchronized long flushBytes() {
    return flushBytes;
  }

  public synchronized long netBytes() {
    return flushBytes + activeBytes;
  }

  private void commitPerThreadBytes(ThreadState perThread) {
    final long delta = perThread.perThread.bytesUsed()
        - perThread.perThreadBytes;
    perThread.perThreadBytes += delta;
    /*
     * We need to differentiate here if we are pending since setFlushPending
     * moves the perThread memory to the flushBytes and we could be set to
     * pending during a delete
     */
    if (perThread.flushPending) {
      flushBytes += delta;
    } else {
      activeBytes += delta;
    }
    assert updatePeaks(delta);
  }

  private boolean updatePeaks(long delta) {
    peakActiveBytes = Math.max(peakActiveBytes, activeBytes);
    peakFlushBytes = Math.max(peakFlushBytes, flushBytes);
    peakNetBytes = Math.max(peakNetBytes, netBytes());
    return true;
  }

  synchronized DocumentsWriterPerThread doAfterDocument(ThreadState perThread,
      boolean isUpdate) {
    commitPerThreadBytes(perThread);
    if (!perThread.flushPending) {
      if (isUpdate) {
        flushPolicy.onUpdate(this, perThread);
      } else {
        flushPolicy.onInsert(this, perThread);
      }
      if (!perThread.flushPending && perThread.perThreadBytes > maxBytesPerDWPT) {
        // safety check to prevent a single DWPT exceeding its RAM limit. This
        // is super
        // important since we can not address more than 2048 MB per DWPT
        setFlushPending(perThread);
        if (fullFlush) {
          DocumentsWriterPerThread toBlock = internalTryCheckOutForFlush(perThread, false);
          assert toBlock != null;
          blockedFlushes.add(toBlock);
        }
      }
    }
    final DocumentsWriterPerThread flushingDWPT = getFlushIfPending(perThread);
    healthiness.updateStalled(this);
    return flushingDWPT;
  }

  synchronized void doAfterFlush(DocumentsWriterPerThread dwpt) {
    assert flushingWriters.containsKey(dwpt);
    try {
      numFlushing--;
      Long bytes = flushingWriters.remove(dwpt);
      flushBytes -= bytes.longValue();
      perThreadPool.recycle(dwpt);
      healthiness.updateStalled(this);
    } finally {
      notifyAll();
    }
  }
  
  public synchronized boolean allFlushesDue() {
    return numFlushing == 0;
  }
  
  public synchronized void waitForFlush() {
    if (numFlushing != 0) {
      try {
        this.wait();
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      }
    }
  }

  /**
   * Sets flush pending state on the given {@link ThreadState}. The
   * {@link ThreadState} must have indexed at least on Document and must not be
   * already pending.
   */
  public synchronized void setFlushPending(ThreadState perThread) {
    assert !perThread.flushPending;
    assert perThread.perThread.getNumDocsInRAM() > 0;
    perThread.flushPending = true; // write access synced
    final long bytes = perThread.perThreadBytes;
    flushBytes += bytes;
    activeBytes -= bytes;
    numPending++; // write access synced
  }

  synchronized void doOnAbort(ThreadState state) {
    if (state.flushPending) {
      flushBytes -= state.perThreadBytes;
    } else {
      activeBytes -= state.perThreadBytes;
    }
    // take it out of the loop this DWPT is stale
    perThreadPool.replaceForFlush(state, closed);
    healthiness.updateStalled(this);
  }

  synchronized DocumentsWriterPerThread tryCheckoutForFlush(
      ThreadState perThread, boolean setPending) {
    if (fullFlush)
      return null;
    return internalTryCheckOutForFlush(perThread, setPending);
  }

  private DocumentsWriterPerThread internalTryCheckOutForFlush(
      ThreadState perThread, boolean setPending) {
    if (setPending && !perThread.flushPending) {
      setFlushPending(perThread);
    }
    if (perThread.flushPending) {
      // we are pending so all memory is already moved to flushBytes
      if (perThread.tryLock()) {
        try {
          if (perThread.isActive()) {
            assert perThread.isHeldByCurrentThread();
            final DocumentsWriterPerThread dwpt;
            final long bytes = perThread.perThreadBytes; // do that before
                                                         // replace!
            dwpt = perThreadPool.replaceForFlush(perThread, closed);
            assert !flushingWriters.containsKey(dwpt) : "DWPT is already flushing";
            // record the flushing DWPT to reduce flushBytes in doAfterFlush
            flushingWriters.put(dwpt, Long.valueOf(bytes));
            numPending--; // write access synced
            numFlushing++;
            return dwpt;
          }
        } finally {
          perThread.unlock();
        }
      }
    }
    return null;
  }

  private DocumentsWriterPerThread getFlushIfPending(ThreadState perThread) {
    if (numPending > 0) {
      final DocumentsWriterPerThread dwpt = perThread == null ? null
          : tryCheckoutForFlush(perThread, false);
      if (dwpt == null) {
        return nextPendingFlush();
      }
      return dwpt;
    }
    return null;
  }

  @Override
  public String toString() {
    return "DocumentsWriterFlushControl [activeBytes=" + activeBytes
        + ", flushBytes=" + flushBytes + "]";
  }

  DocumentsWriterPerThread nextPendingFlush() {
    synchronized (this) {
      DocumentsWriterPerThread poll = flushQueue.poll();
      if (poll != null) {
        return poll;
      }  
    }
    if (numPending > 0) {
      final Iterator<ThreadState> allActiveThreads = perThreadPool
          .getActivePerThreadsIterator();
      while (allActiveThreads.hasNext() && numPending > 0) {
        ThreadState next = allActiveThreads.next();
        if (next.flushPending) {
          DocumentsWriterPerThread dwpt = tryCheckoutForFlush(next, false);
          if (dwpt != null) {
            return dwpt;
          }
        }
      }
    }
    return null;
  }

  synchronized void setClosed() {
    // set by DW to signal that we should not release new DWPT after close
    this.closed = true;
  }

  /**
   * Returns an iterator that provides access to all currently active {@link ThreadState}s 
   */
  public Iterator<ThreadState> allActiveThreads() {
    return perThreadPool.getActivePerThreadsIterator();
  }

  long maxNetBytes() {
    return flushPolicy.getMaxNetBytes();
  }

  synchronized void doOnDelete() {
    // pass null this is a global delete no update
    flushPolicy.onDelete(this, null);
  }

  /**
   * Returns the number of delete terms in the global pool
   */
  public int getNumGlobalTermDeletes() {
    return documentsWriter.deleteQueue.numGlobalTermDeletes();
  }

  int numFlushingDWPT() {
    return numFlushing;
  }
  
  public void setFlushDeletes() {	
	  flushDeletes.set(true);
  }
  
  int numActiveDWPT() {
    return this.perThreadPool.getMaxThreadStates();
  }
  
  void markForFullFlush() {
    synchronized (this) {
      assert !fullFlush;
      fullFlush = true;
    }
    final Iterator<ThreadState> allActiveThreads = perThreadPool
    .getActivePerThreadsIterator();
    final ArrayList<DocumentsWriterPerThread> toFlush = new ArrayList<DocumentsWriterPerThread>();
    while (allActiveThreads.hasNext()) {
      final ThreadState next = allActiveThreads.next();
      next.lock();
      try {
        if (!next.isActive()) {
          continue; 
        }
        if (next.perThread.getNumDocsInRAM() > 0) {
          final DocumentsWriterPerThread dwpt = next.perThread; // just for assert
          final DocumentsWriterPerThread flushingDWPT = internalTryCheckOutForFlush(next, true);
          assert flushingDWPT != null : "DWPT must never be null here since we hold the lock and it holds documents";
          assert dwpt == flushingDWPT : "flushControl returned different DWPT";
          toFlush.add(flushingDWPT);
        } else {
          next.perThread.initialize();
        }
      } finally {
        next.unlock();
      }
    }
    synchronized (this) {
      flushQueue.addAll(blockedFlushes);
      blockedFlushes.clear();
      flushQueue.addAll(toFlush);
    }
    
  }
  
  synchronized void finishFullFlush() {
    assert fullFlush;
    assert flushQueue.isEmpty();
    try {
      if (!blockedFlushes.isEmpty()) {
        flushQueue.addAll(blockedFlushes);
        blockedFlushes.clear();
      }
    } finally {
      fullFlush = false;
    }
  }

  synchronized void abortFullFlushes() {
    try {
      for (DocumentsWriterPerThread dwpt : flushQueue) {
        doAfterFlush(dwpt);
      }
      for (DocumentsWriterPerThread dwpt : blockedFlushes) {
        doAfterFlush(dwpt);
      }
    } finally {
      fullFlush = false;
    }
  }
}