package org.apache.lucene.store;

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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.lucene.util.IOUtils;

/** Simple tests for NativeFSLockFactory */
public class TestNativeFSLockFactory extends BaseLockFactoryTestCase {

  @Override
  protected Directory getDirectory(Path path) throws IOException {
    return newFSDirectory(path, NativeFSLockFactory.INSTANCE);
  }
  
  /** Verify NativeFSLockFactory works correctly if the lock file exists */
  public void testLockFileExists() throws IOException {
    Path tempDir = createTempDir();
    Path lockFile = tempDir.resolve("test.lock");
    Files.createFile(lockFile);
    
    Directory dir = getDirectory(tempDir);
    Lock l = dir.obtainLock("test.lock");
    l.close();
    dir.close();
  }
  
  /** release the lock and test ensureValid fails */
  public void testInvalidateLock() throws IOException {
    Directory dir = getDirectory(createTempDir());
    NativeFSLockFactory.NativeFSLock lock =  (NativeFSLockFactory.NativeFSLock) dir.obtainLock("test.lock");
    lock.ensureValid();
    lock.lock.release();
    try {
      lock.ensureValid();
      fail("no exception");
    } catch (AlreadyClosedException expected) {
      // ok
    } finally {
      IOUtils.closeWhileHandlingException(lock);
    }
    dir.close();
  }
  
  /** close the channel and test ensureValid fails */
  public void testInvalidateChannel() throws IOException {
    Directory dir = getDirectory(createTempDir());
    NativeFSLockFactory.NativeFSLock lock =  (NativeFSLockFactory.NativeFSLock) dir.obtainLock("test.lock");
    lock.ensureValid();
    lock.channel.close();
    try {
      lock.ensureValid();
      fail("no exception");
    } catch (AlreadyClosedException expected) {
      // ok
    } finally {
      IOUtils.closeWhileHandlingException(lock);
    }
    dir.close();
  }
  
  /** delete the lockfile and test ensureValid fails */
  public void testDeleteLockFile() throws IOException {
    Directory dir = getDirectory(createTempDir());
    try {
      Lock lock = dir.obtainLock("test.lock");
      lock.ensureValid();
    
      try {
        dir.deleteFile("test.lock");
      } catch (Exception e) {
        // we can't delete a file for some reason, just clean up and assume the test.
        IOUtils.closeWhileHandlingException(lock);
        assumeNoException("test requires the ability to delete a locked file", e);
      }
    
      try {
        lock.ensureValid();
        fail("no exception");
      } catch (IOException expected) {
        // ok
      } finally {
        IOUtils.closeWhileHandlingException(lock);
      }
    } finally {
      // Do this in finally clause in case the assumeNoException is false:
      dir.close();
    }
  }
}
