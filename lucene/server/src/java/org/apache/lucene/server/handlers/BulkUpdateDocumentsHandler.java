package org.apache.lucene.server.handlers;

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

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.apache.lucene.server.FinishRequest;
import org.apache.lucene.server.GlobalState;
import org.apache.lucene.server.IndexState;
import org.apache.lucene.server.params.*;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import static org.apache.lucene.server.IndexState.AddDocumentContext;
import static org.apache.lucene.server.IndexState.DocumentAndFacets;

/** Reads more than one { ... } request in a single
 *  connection, but each request must be separated by at
 *  least one whitespace char. */

public class BulkUpdateDocumentsHandler extends Handler {

  private StructType TYPE = new StructType(
                                     new Param("indexName", "Index name", new StringType()),
                                     new Param("documents", "List of documents",
                                         new ListType(
                                             new StructType(
                                                 new Param("term", "Identifies which document to replace", 
                                                           new StructType(
                                                                          new Param("field", "Field", new StringType()),
                                                                          new Param("term", "Text", new StringType()))),
                                                 new Param("parent", "The (one) parent document for this block.  The value of this key is a single document that @addDocument expects.  Be sure to add an indexed field to only the parent document so that you can subsequently provide the filter that identifies only parent documents.",
                                                           AddDocumentHandler.DOCUMENT_TYPE),
                                                 new Param("children", "List of child documents.",
                                                           new ListType(AddDocumentHandler.DOCUMENT_TYPE))))));

  /** Sole constructor. */
  public BulkUpdateDocumentsHandler(GlobalState state) {
    super(state);
  }

  @Override
  public StructType getType() {
    return TYPE;
  }

  @Override
  public boolean doStream() {
    return true;
  }

  @Override
  public String getTopDoc() {
    return "Add more than one document block in a single connection.  Each document should be its own JSON struct, matching @addDocuments, and then there must one whitespace character separating each document.  Returns the index generation (indexGen) that contains all added document blocks.";
  }

  @Override
  public FinishRequest handle(IndexState state, Request r, Map<String,List<String>> params) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String handleStreamed(Reader reader, Map<String,List<String>> params) throws Exception {

    JsonFactory jfactory = new JsonFactory();

    JsonParser parser = jfactory.createJsonParser(reader);

    if (parser.nextToken() != JsonToken.START_OBJECT) {
      throw new IllegalArgumentException("expected JSON object");
    }
    if (parser.nextToken() != JsonToken.FIELD_NAME) {
      throw new IllegalArgumentException("expected indexName first");
    }
    if (!parser.getText().equals("indexName")) {
      throw new IllegalArgumentException("expected indexName first");
    }
    if (parser.nextToken() != JsonToken.VALUE_STRING) {
      throw new IllegalArgumentException("indexName should be string");
    }

    IndexState state = globalState.get(parser.getText());
    if (!state.started()) {
      throw new IllegalArgumentException("index \"" + state.name + "\" isn't started: cannot add documents");
    }
    if (parser.nextToken() != JsonToken.FIELD_NAME) {
      throw new IllegalArgumentException("expected documents next");
    }
    if (!parser.getText().equals("documents")) {
      throw new IllegalArgumentException("expected documents after indexName");
    }

    if (parser.nextToken() != JsonToken.START_ARRAY) {
      throw new IllegalArgumentException("documents should be a list");
    }

    int count = 0;
    AddDocumentContext ctx = new AddDocumentContext();

    AddDocumentHandler addDocHandler = (AddDocumentHandler) globalState.getHandler("addDocument");

    // Parse as many doc blocks as there are:
    while (true) {

      List<DocumentAndFacets> children = null;
      DocumentAndFacets parent = null;
      Term updateTerm = null;

      JsonToken token = parser.nextToken();
      if (token == JsonToken.END_ARRAY) {
        break;
      }
      if (token != JsonToken.START_OBJECT) {
        throw new IllegalArgumentException("expected object");
      }

      // Parse term + parent + children for this one doc block:
      while(true) {
        token = parser.nextToken();
        if (token == JsonToken.END_OBJECT) {
          // Done with parent + child in this block
          break;
        }
        if (token != JsonToken.FIELD_NAME) {
          throw new IllegalArgumentException("missing field name: " + token);
        }
        String f = parser.getText();
        if (f.equals("term")) {
          if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IllegalArgumentException("missing object");
          }

          // TODO: allow field to be specified only once, then
          // only text per document

          String field=null, term=null;

          while (parser.nextToken() != JsonToken.END_OBJECT) {
            String f2 = parser.getText();
            if (f2.equals("field")) {
              if (parser.nextToken() != JsonToken.VALUE_STRING) {
                throw new IllegalArgumentException("missing string value");
              }
              field = parser.getText();
              // Ensure field is valid:
              state.getField(field);
            } else if (f2.equals("term")) {
              if (parser.nextToken() != JsonToken.VALUE_STRING) {
                throw new IllegalArgumentException("missing string value");
              }
              term = parser.getText();
            } else {
              throw new IllegalArgumentException("unexpected field " + f);
            }
          }
          updateTerm = new Term(field, term);
        } else if (f.equals("children")) {
          token = parser.nextToken();
          if (token != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("expected array for children");
          }

          children = new ArrayList<DocumentAndFacets>();

          // Parse each child:
          while (true) {
            DocumentAndFacets doc = addDocHandler.parseDocument(state, parser);
            if (doc == null) {
              break;
            }
            children.add(doc);
          }
        } else if (f.equals("parent")) {
          parent = addDocHandler.parseDocument(state, parser);
        } else {
          throw new IllegalArgumentException("unrecognized field name \"" + f + "\"");
        }
      }

      if (parent == null) {
        throw new IllegalArgumentException("missing parent");
      }
      if (children == null) {
        throw new IllegalArgumentException("missing children");
      }

      // Parent is last:
      children.add(parent);

      globalState.indexService.submit(state.getAddDocumentsJob(count, updateTerm, children, ctx));
      count++;
    }

    // nocommit this is ... lameish:
    while (true) {
      if (ctx.addCount.get() == count) {
        break;
      }
      Thread.sleep(1);
    }

    JSONObject o = new JSONObject();
    o.put("indexGen", state.writer.getGeneration());
    o.put("indexedDocumentBlockCount", count);
    if (!ctx.errors.isEmpty()) {
      JSONArray errors = new JSONArray();
      o.put("errors", errors);
      for(int i=0;i<ctx.errors.size();i++) {
        JSONObject err = new JSONObject();
        errors.add(err);
        err.put("index", ctx.errorIndex.get(i));
        err.put("exception", ctx.errors.get(i));
      }
    }

    return o.toString();
  }
}
