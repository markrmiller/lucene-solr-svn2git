package org.apache.lucene.queryparser.spans.tokens;

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

public class SQPOpenClause extends SQPClause {
  private final TYPE type;
  private final int startCharOffset;
  
  public SQPOpenClause(int startTokenOffset, int startCharOffset, TYPE type) {
    super(startTokenOffset);
    this.type = type;
    this.startCharOffset = startCharOffset;
  }

  public int getStartCharOffset() {
    return startCharOffset;
  }
  
  public TYPE getType() {
    return type;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof SQPOpenClause)) {
      return false;
    }
    SQPOpenClause other = (SQPOpenClause) obj;
    if (type != other.type) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("SQPOpenClause [type=");
    builder.append(type);
    builder.append("]");
    return builder.toString();
  }
}
