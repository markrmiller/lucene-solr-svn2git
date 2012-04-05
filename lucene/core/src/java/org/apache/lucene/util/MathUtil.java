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

/**
 * Math static utility methods.
 */

public final class MathUtil {

  // No instance:
  private MathUtil() {
  }

  /** returns x == 0 ? 0 : Math.floor(Math.log(x) / Math.log(base)) */
  public static int log(long x, int base) {
    assert base > 1;
    int ret = 0;
    while (x >= base) {
      x /= base;
      ret++;
    }
    return ret;
  }
}
