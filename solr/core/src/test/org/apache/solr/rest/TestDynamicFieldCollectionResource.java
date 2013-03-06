package org.apache.solr.rest;
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

import org.junit.Test;

import java.io.IOException;

public class TestDynamicFieldCollectionResource extends SchemaRestletTestBase {
  @Test
  public void testGetAllDynamicFields() throws Exception {
    assertQ("/schema/dynamicfields?indent=on&wt=xml",
        "(/response/arr[@name='dynamicfields']/lst/str[@name='name'])[1] = '*_coordinate'",
        "(/response/arr[@name='dynamicfields']/lst/str[@name='name'])[2] = 'ignored_*'",
        "(/response/arr[@name='dynamicfields']/lst/str[@name='name'])[3] = '*_mfacet'",
        "count(//copySources/str)=count(//copyDests/str)");
  }

  @Test
  public void testGetTwoDynamicFields() throws IOException {
    assertQ("/schema/dynamicfields?indent=on&wt=xml&fl=*_i,*_s",
            "count(/response/arr[@name='dynamicfields']/lst/str[@name='name']) = 2",
            "(/response/arr[@name='dynamicfields']/lst/str[@name='name'])[1] = '*_i'",
            "(/response/arr[@name='dynamicfields']/lst/str[@name='name'])[2] = '*_s'");
  }

  @Test
  public void testNotFoundDynamicFields() throws IOException {
    assertQ("/schema/dynamicfields?indent=on&wt=xml&fl=*_not_in_there,this_one_isnt_either_*",
        "count(/response/arr[@name='dynamicfields']) = 1",
        "count(/response/arr[@name='dynamicfields']/lst/str[@name='name']) = 0");
  }

  @Test
  public void testJsonGetAllDynamicFields() throws Exception {
    assertJQ("/schema/dynamicfields?indent=on",
             "/dynamicfields/[0]/name=='*_coordinate'",
             "/dynamicfields/[1]/name=='ignored_*'",
             "/dynamicfields/[2]/name=='*_mfacet'");
  }
  
  @Test
  public void testJsonGetTwoDynamicFields() throws Exception {
    assertJQ("/schema/dynamicfields?indent=on&fl=*_i,*_s&wt=xml", // assertJQ will fix the wt param to be json
             "/dynamicfields/[0]/name=='*_i'",
             "/dynamicfields/[1]/name=='*_s'");
  }
}
