package org.apache.lucene.search.regex;

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

import org.apache.regexp.RE;
import org.apache.regexp.REProgram;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Implementation tying <a href="http://jakarta.apache.org/regexp">Jakarta
 * Regexp</a> to RegexQuery. Jakarta Regepx internally supports a
 * {@link #prefix} implementation which can offer performance gains under
 * certain circumstances. Yet, the implementation appears to be rather shaky as
 * it doesn't always provide a prefix even if one would exist.
 */
public class JakartaRegexpCapabilities implements RegexCapabilities {
  private RE regexp;

  private static Field prefixField;
  private static Method getPrefixMethod;
  static {
    try {
      getPrefixMethod = REProgram.class.getMethod("getPrefix");
    } catch (Exception e) {
      getPrefixMethod = null;
    }
    try {
      prefixField = REProgram.class.getDeclaredField("prefix");
      prefixField.setAccessible(true);
    } catch (Exception e) {
      prefixField = null;
    }
  }
  
  // Define the flags that are possible. Redefine them here
  // to avoid exposing the RE class to the caller.
  
  private int flags = RE.MATCH_NORMAL;

  /**
   * Flag to specify normal, case-sensitive matching behaviour. This is the default.
   */
  public static final int FLAG_MATCH_NORMAL = RE.MATCH_NORMAL;
  
  /**
   * Flag to specify that matching should be case-independent (folded)
   */
  public static final int FLAG_MATCH_CASEINDEPENDENT = RE.MATCH_CASEINDEPENDENT;
 
  /**
   * Constructs a RegexCapabilities with the default MATCH_NORMAL match style.
   */
  public JakartaRegexpCapabilities() {}
  
  /**
   * Constructs a RegexCapabilities with the provided match flags.
   * Multiple flags should be ORed together.
   * 
   * @param flags The matching style
   */
  public JakartaRegexpCapabilities(int flags)
  {
    this.flags = flags;
  }
  
  public void compile(String pattern) {
    regexp = new RE(pattern, this.flags);
  }

  public boolean match(String string) {
    return regexp.match(string);
  }

  public String prefix() {
    try {
      final char[] prefix;
      if (getPrefixMethod != null) {
        prefix = (char[]) getPrefixMethod.invoke(regexp.getProgram());
      } else if (prefixField != null) {
        prefix = (char[]) prefixField.get(regexp.getProgram());
      } else {
        return null;
      }
      return prefix == null ? null : new String(prefix);
    } catch (Exception e) {
      // if we cannot get the prefix, return none
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final JakartaRegexpCapabilities that = (JakartaRegexpCapabilities) o;

    if (regexp != null ? !regexp.equals(that.regexp) : that.regexp != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (regexp != null ? regexp.hashCode() : 0);
  }
}
