package org.apache.lucene.mockfile;

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
import java.io.OutputStream;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.InfoStream;

/** 
 * FileSystem that records all major destructive filesystem activities.
 */
public class VerboseFS extends FilterFileSystemProvider {
  final InfoStream infoStream;
  final Path root;
  
  /**
   * Create a new instance, recording major filesystem write activities
   * (create, delete, etc) to the specified {@code InfoStream}.
   * @param delegate delegate filesystem to wrap.
   * @param infoStream infoStream to send messages to. The component for 
   * messages is named "FS".
   */
  public VerboseFS(FileSystem delegate, InfoStream infoStream) {
    super("verbose://", delegate);
    this.infoStream = infoStream;
    this.root = this.getFileSystem(null).getPath(".").toAbsolutePath().normalize();
  }
  
  /** Records message, and rethrows exception if not null */
  private void sop(String text, Throwable exception) throws IOException {
    if (exception == null) {
      if (infoStream.isEnabled("FS")) {
        infoStream.message("FS", text);
      }
    } else {
      if (infoStream.isEnabled("FS")) {
        infoStream.message("FS", text + " (FAILED: " + exception + ")");
      }
      IOUtils.reThrow(exception);
    }
  }
  
  private String path(Path path) {
    path = root.relativize(path.toAbsolutePath().normalize());
    return path.toString();
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    Throwable exception = null;
    try {
      super.createDirectory(dir, attrs);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("createDirectory: " + path(dir), exception);
    }
  }

  @Override
  public void delete(Path path) throws IOException {
    Throwable exception = null;
    try {
      super.delete(path);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("delete: " + path(path), exception);
    }
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    Throwable exception = null;
    try {
      super.copy(source, target, options);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("copy" + Arrays.toString(options) + ": " + path(source) + " -> " + path(target), exception);
    }
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    Throwable exception = null;
    try {
      super.move(source, target, options);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("move" + Arrays.toString(options) + ": " + path(source) + " -> " + path(target), exception);
    }
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    Throwable exception = null;
    try {
      super.setAttribute(path, attribute, value, options);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("setAttribute[" + attribute + "=" + value + "]: " + path(path), exception);
    }
  }

  @Override
  public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
    Throwable exception = null;
    try {
      return super.newOutputStream(path, options);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("newOutputStream" + Arrays.toString(options) + ": " + path(path), exception);
    }
    throw new AssertionError();
  }
  
  private boolean containsDestructive(Set<? extends OpenOption> options) {
    return (options.contains(StandardOpenOption.APPEND) ||
            options.contains(StandardOpenOption.WRITE)  || 
            options.contains(StandardOpenOption.DELETE_ON_CLOSE));
  }

  @Override
  public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    Throwable exception = null;
    try {
      return super.newFileChannel(path, options, attrs);
    } catch (Throwable t) {
      exception = t;
    } finally {
      if (containsDestructive(options)) {
        sop("newFileChannel" + options + ": " + path(path), exception);
      } else {
        IOUtils.reThrow(exception);
      }
    }
    throw new AssertionError();
  }

  @Override
  public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options, ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
    Throwable exception = null;
    try {
      return super.newAsynchronousFileChannel(path, options, executor, attrs);
    } catch (Throwable t) {
      exception = t;
    } finally {
      if (containsDestructive(options)) {
        sop("newAsynchronousFileChannel" + options + ": " + path(path), exception);
      } else {
        IOUtils.reThrow(exception);
      }
    }
    throw new AssertionError();
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    Throwable exception = null;
    try {
      return super.newByteChannel(path, options, attrs);
    } catch (Throwable t) {
      exception = t;
    } finally {
      if (containsDestructive(options)) {
        sop("newByteChannel" + options + ": " + path(path), exception);
      } else {
        IOUtils.reThrow(exception);
      }
    }
    throw new AssertionError();
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    Throwable exception = null;
    try {
      super.createSymbolicLink(link, target, attrs);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("createSymbolicLink: " + path(link) + " -> " + path(target), exception);
    }
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    Throwable exception = null;
    try {
      super.createLink(link, existing);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("createLink: " + path(link) + " -> " + path(existing), exception);
    }
  }

  @Override
  public boolean deleteIfExists(Path path) throws IOException {
    Throwable exception = null;
    try {
      return super.deleteIfExists(path);
    } catch (Throwable t) {
      exception = t;
    } finally {
      sop("deleteIfExists: " + path(path), exception);
    }
    throw new AssertionError();
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
    DirectoryStream<Path> stream = super.newDirectoryStream(dir, filter);
    if (stream instanceof SecureDirectoryStream) {
      stream = new VerboseSecureDirectoryStream((SecureDirectoryStream<Path>)stream, dir);
    }
    return stream;
  }
  
  class VerboseSecureDirectoryStream extends FilterSecureDirectoryStream<Path> {
    final Path dir;
    
    VerboseSecureDirectoryStream(SecureDirectoryStream<Path> delegate, Path dir) {
      super(delegate);
      this.dir = dir;
    }

    @Override
    public SecureDirectoryStream<Path> newDirectoryStream(Path path, LinkOption... options) throws IOException {
      return new VerboseSecureDirectoryStream(super.newDirectoryStream(path, options), path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
      Throwable exception = null;
      try {
        return super.newByteChannel(path, options, attrs);
      } catch (Throwable t) {
        exception = t;
      } finally {
        if (containsDestructive(options)) {
          sop("newByteChannel[SECURE]" + options + ": " + path(path), exception);
        } else {
          IOUtils.reThrow(exception);
        }
      }
      throw new AssertionError();
    }

    @Override
    public void deleteFile(Path path) throws IOException {
      Throwable exception = null;
      try {
        super.deleteFile(path);
      } catch (Throwable t) {
        exception = t;
      } finally {
        sop("deleteFile[SECURE]: " + path(path), exception);
      }
    }

    @Override
    public void deleteDirectory(Path path) throws IOException {
      Throwable exception = null;
      try {
        super.deleteDirectory(path);
      } catch (Throwable t) {
        exception = t;
      } finally {
        sop("deleteDirectory[SECURE]: " + path(path), exception);
      }
    }

    @Override
    public void move(Path srcpath, SecureDirectoryStream<Path> targetdir, Path targetpath) throws IOException {
      Throwable exception = null;
      try {
        super.move(srcpath, targetdir, targetpath);
      } catch (Throwable t) {
        exception = t;
      } finally {
        sop("move[SECURE]: " + path(srcpath) + " -> " + path(targetpath), exception);
      }
    }
  }
}
