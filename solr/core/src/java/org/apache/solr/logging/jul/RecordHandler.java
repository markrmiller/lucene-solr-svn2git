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
package org.apache.solr.logging.jul;


import java.util.logging.LogRecord;

import org.apache.solr.logging.LogWatcher;

public final class RecordHandler extends java.util.logging.Handler {
  final LogWatcher<LogRecord> framework;
  
  public RecordHandler(LogWatcher<LogRecord> framework) {
    this.framework = framework;
  }
  
  @Override
  public void close() throws SecurityException {
    //history.reset();
  }
  
  @Override
  public void flush() {
    // nothing
  }
  
  @Override
  public void publish(LogRecord r) {
    if(isLoggable(r)) {
      framework.add(r, r.getMillis());
    }
  }
}