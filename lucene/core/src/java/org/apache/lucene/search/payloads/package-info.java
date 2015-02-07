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

/** 
 * The payloads package provides Query mechanisms for finding and using payloads.
 * <p>
 *   The following Query implementations are provided:
 *   <ol>
 *    <li>{@link org.apache.lucene.search.payloads.PayloadTermQuery PayloadTermQuery} -- Boost a term's score based on the value of the payload located at that term.</li>
 *    <li>{@link org.apache.lucene.search.payloads.PayloadNearQuery PayloadNearQuery} -- A {@link org.apache.lucene.search.spans.SpanNearQuery SpanNearQuery} that factors in the value of the payloads located 
 *        at each of the positions where the spans occur.</li>
 *   </ol>
 */
package org.apache.lucene.search.payloads;
