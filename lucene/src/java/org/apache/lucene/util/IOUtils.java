package org.apache.lucene.util;

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

import java.io.Closeable;
import java.io.IOException;

import org.apache.lucene.store.DataOutput;

/** @lucene.internal */
public class IOUtils {
  /**
   * <p>Closes all given <tt>Closeable</tt>s, suppressing all thrown exceptions. Some of the <tt>Closeable</tt>s
   * may be null, they are ignored. After everything is closed, method either throws <tt>priorException</tt>,
   * if one is supplied, or the first of suppressed exceptions, or completes normally.</p>
   * <p>Sample usage:<br/>
   * <pre>
   * Closeable resource1 = null, resource2 = null, resource3 = null;
   * ExpectedException priorE = null;
   * try {
   *   resource1 = ...; resource2 = ...; resource3 = ...; // Aquisition may throw ExpectedException
   *   ..do..stuff.. // May throw ExpectedException
   * } catch (ExpectedException e) {
   *   priorE = e;
   * } finally {
   *   closeSafely(priorE, resource1, resource2, resource3);
   * }
   * </pre>
   * </p>
   * @param priorException  <tt>null</tt> or an exception that will be rethrown after method completion
   * @param objects         objects to call <tt>close()</tt> on
   */
  public static <E extends Exception> void closeSafely(E priorException, Closeable... objects) throws E, IOException {
    IOException firstIOE = null;

    for (Closeable object : objects) {
      try {
        if (object != null)
          object.close();
      } catch (IOException ioe) {
        if (firstIOE == null)
          firstIOE = ioe;
      }
    }

    if (priorException != null)
      throw priorException;
    else if (firstIOE != null)
      throw firstIOE;
  }
  
  /**
   * Writes the length of the {@link BytesRef} as either a one or two bytes to
   * the {@link DataOutput} and returns the number of bytes used.
   * 
   * @param datOut
   *          the output to write to
   * @param bytes
   *          the length to write
   * @return the length of the {@link BytesRef} as either a one or two bytes to
   *         the {@link DataOutput} and returns the number of bytes used.
   * @throws IOException
   *           if datOut throws an {@link IOException}
   */
  public static int writeLength(DataOutput datOut, BytesRef bytes)
      throws IOException {
    final int length = bytes.length;
    if (length < 128) {
      // 1 byte to store length
      datOut.writeByte((byte) length);
      return 1;
    } else {
      // 2 byte to store length
      datOut.writeByte((byte) (0x80 | (length & 0x7f)));
      datOut.writeByte((byte) ((length >> 7) & 0xff));
      return 2;
    }
  }
}
