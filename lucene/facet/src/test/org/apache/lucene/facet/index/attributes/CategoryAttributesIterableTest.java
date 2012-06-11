package org.apache.lucene.facet.index.attributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import org.apache.lucene.facet.index.CategoryContainerTestBase;
import org.apache.lucene.facet.index.attributes.CategoryAttribute;
import org.apache.lucene.facet.index.attributes.CategoryAttributesIterable;
import org.apache.lucene.facet.taxonomy.CategoryPath;

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

public class CategoryAttributesIterableTest extends CategoryContainerTestBase {

  @Test
  public void testIterator() throws IOException {
    List<CategoryPath> categoryList = new ArrayList<CategoryPath>();
    for (int i = 0; i < initialCatgeories.length; i++) {
      categoryList.add(initialCatgeories[i]);
    }

    CategoryAttributesIterable iterable = new CategoryAttributesIterable(
        categoryList);
    Iterator<CategoryAttribute> iterator = iterable.iterator();

    // count the number of tokens
    int nCategories;
    for (nCategories = 0; iterator.hasNext(); nCategories++) {
      iterator.next();
    }
    assertEquals("Wrong number of tokens", 3, nCategories);
  }

}
