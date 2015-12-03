package org.apache.solr.common.util;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Set;

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

import java.util.concurrent.TimeUnit;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryUtil {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  public static interface RetryCmd {
    public void execute() throws Throwable;
  }
  
  public static interface BooleanRetryCmd {
    public boolean execute();
  }
  
  public static void retryOnThrowable(Class clazz, long timeoutms, long intervalms, RetryCmd cmd) throws Throwable {
    retryOnThrowable(Collections.singleton(clazz), timeoutms, intervalms, cmd);
  }
  
  public static void retryOnThrowable(Set<Class> classes, long timeoutms, long intervalms, RetryCmd cmd) throws Throwable {
    long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeoutms, TimeUnit.MILLISECONDS);
    while (true) {
      try {
        cmd.execute();
      } catch (Throwable t) {
        if (isInstanceOf(classes, t) && System.nanoTime() < timeout) {
          log.info("Retry due to Throwable, " + t.getClass().getName() + " " + t.getMessage());
          Thread.sleep(intervalms);
          continue;
        }
        throw t;
      }
      // success
      break;
    }
  }
  
  private static boolean isInstanceOf(Set<Class> classes, Throwable t) {
    for (Class c : classes) {
      if (c.isInstance(t)) {
        return true;
      }
    }
    return false;
  }
  
  public static void retryOnBoolean(long timeoutms, long intervalms, BooleanRetryCmd cmd) {
    long timeout = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeoutms, TimeUnit.MILLISECONDS);
    while (true) {
      boolean resp = cmd.execute();
      if (!resp && System.nanoTime() < timeout) {
        continue;
      } else if (System.nanoTime() >= timeout) {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Timed out while retrying operation");
      }
      
      // success
      break;
    }
  }
  
}
