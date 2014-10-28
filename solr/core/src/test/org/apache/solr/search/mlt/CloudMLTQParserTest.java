package org.apache.solr.search.mlt;

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

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.cloud.AbstractFullDistribZkTestBase;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class CloudMLTQParserTest extends AbstractFullDistribZkTestBase {
  public CloudMLTQParserTest() {
    configString = "solrconfig.xml";
    schemaString = "schema.xml";
  }

  @Override
  protected String getCloudSolrConfig() {
    return configString;
  }
  
  public void doTest() throws Exception {
    
    waitForRecoveriesToFinish(false);

    String id = "id";
    delQ("*:*");
    indexDoc(sdoc(id, "1", "lowerfilt", "toyota"));
    indexDoc(sdoc(id, "2", "lowerfilt", "chevrolet"));
    indexDoc(sdoc(id, "3", "lowerfilt", "bmw usa"));
    indexDoc(sdoc(id, "4", "lowerfilt", "ford"));
    indexDoc(sdoc(id, "5", "lowerfilt", "ferrari"));
    indexDoc(sdoc(id, "6", "lowerfilt", "jaguar"));
    indexDoc(sdoc(id, "7", "lowerfilt", "mclaren moon or the moon and moon moon shine and the moon but moon was good foxes too"));
    indexDoc(sdoc(id, "8", "lowerfilt", "sonata"));
    indexDoc(sdoc(id, "9", "lowerfilt", "The quick red fox jumped over the lazy big and large brown dogs."));
    indexDoc(sdoc(id, "10", "lowerfilt", "blue"));
    indexDoc(sdoc(id, "12", "lowerfilt", "glue"));
    indexDoc(sdoc(id, "13", "lowerfilt", "The quote red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "14", "lowerfilt", "The quote red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "15", "lowerfilt", "The fat red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "16", "lowerfilt", "The slim red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "17", "lowerfilt", "The quote red fox jumped moon over the lazy brown dogs moon. Of course moon. Foxes and moon come back to the foxes and moon"));
    indexDoc(sdoc(id, "18", "lowerfilt", "The quote red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "19", "lowerfilt", "The hose red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "20", "lowerfilt", "The quote red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "21", "lowerfilt", "The court red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "22", "lowerfilt", "The quote red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "23", "lowerfilt", "The quote red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "24", "lowerfilt", "The file red fox jumped over the lazy brown dogs."));
    indexDoc(sdoc(id, "25", "lowerfilt", "rod fix"));
    indexDoc(sdoc(id, "26", "lowerfilt", "bmw usa 328i"));
    indexDoc(sdoc(id, "27", "lowerfilt", "bmw usa 535i"));
    indexDoc(sdoc(id, "28", "lowerfilt", "bmw 750Li"));

    commit();

    handle.clear();
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    handle.put("maxScore", SKIPVAL);

    ModifiableSolrParams params = new ModifiableSolrParams();

    params.set(CommonParams.Q, "{!mlt id=17 qf=lowerfilt}");
    QueryResponse queryResponse = cloudClient.query(params);
    SolrDocumentList solrDocuments = queryResponse.getResults();
    int[] expectedIds = new int[]{17, 13, 14, 20, 22, 15, 16, 24, 18, 23};
    int[] actualIds = new int[10];
    int i = 0;
    for (SolrDocument solrDocument : solrDocuments) {
      actualIds[i++] =  Integer.valueOf(String.valueOf(solrDocument.getFieldValue("id")));
    }
    assertArrayEquals(expectedIds, actualIds);
    
    params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "{!mlt id=3 qf=lowerfilt}");
    queryResponse = queryServer(params);
    solrDocuments = queryResponse.getResults();
    expectedIds = new int[]{3, 27, 26, 28};
    actualIds = new int[4];
    i = 0;
    for (SolrDocument solrDocument : solrDocuments) {
      actualIds[i++] =  Integer.valueOf(String.valueOf(solrDocument.getFieldValue("id")));
    }
    assertArrayEquals(expectedIds, actualIds);

    params = new ModifiableSolrParams();
    params.set(CommonParams.Q, "{!mlt id=20 qf=lowerfilt}");
    params.set("debug" , "query");
    queryResponse = queryServer(params);
    solrDocuments = queryResponse.getResults();
    expectedIds = new int[]{18, 23, 13, 14, 20, 22, 19, 21, 15, 16};
    actualIds = new int[10];
    i = 0;
    for (SolrDocument solrDocument : solrDocuments) {
      actualIds[i++] =  Integer.valueOf(String.valueOf(solrDocument.getFieldValue("id")));
    }
    assertArrayEquals(expectedIds, actualIds);

    String expectedQueryString = "lowerfilt:over lowerfilt:fox lowerfilt:lazy lowerfilt:brown "
        + "lowerfilt:jumped lowerfilt:red lowerfilt:dogs. lowerfilt:quote lowerfilt:the";
    
    ArrayList<String> actualParsedQueries = (ArrayList<String>) queryResponse
        .getDebugMap().get("parsedquery");
    
    for(int counter=0; counter < actualParsedQueries.size(); counter++) {
      assertTrue("Parsed queries aren't equal",
          compareParsedQueryStrings(expectedQueryString,
          actualParsedQueries.get(counter)));
    }
  }
  
  private boolean compareParsedQueryStrings(String expected, String actual) {
    HashSet<String> expectedQueryParts = new HashSet<>();
    expectedQueryParts.addAll(Arrays.asList(expected.split("\\s+")));
    HashSet<String> actualQueryParts = new HashSet();
    actualQueryParts.addAll(Arrays.asList(actual.split("\\s+")));
    return expectedQueryParts.containsAll(actualQueryParts);
  }
}
