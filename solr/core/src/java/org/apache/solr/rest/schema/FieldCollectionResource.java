package org.apache.solr.rest.schema;
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


import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.rest.GETable;
import org.apache.solr.rest.POSTable;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.noggit.ObjectBuilder;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class responds to requests at /solr/(corename)/schema/fields
 * <p/>
 * Two query parameters are supported:
 * <ul>
 * <li>
 * "fl": a comma- and/or space-separated list of fields to send properties
 * for in the response, rather than the default: all of them.
 * </li>
 * <li>
 * "includeDynamic": if the "fl" parameter is specified, matching dynamic
 * fields are included in the response and identified with the "dynamicBase"
 * property.  If the "fl" parameter is not specified, the "includeDynamic"
 * query parameter is ignored.
 * </li>
 * </ul>
 */
public class FieldCollectionResource extends BaseFieldResource implements GETable, POSTable {
  private static final Logger log = LoggerFactory.getLogger(FieldCollectionResource.class);
  private boolean includeDynamic;

  public FieldCollectionResource() {
    super();
  }

  @Override
  public void doInit() throws ResourceException {
    super.doInit();
    if (isExisting()) {
      includeDynamic = getSolrRequest().getParams().getBool(INCLUDE_DYNAMIC_PARAM, false);
    }
  }

  @Override
  public Representation get() {
    try {
      final List<SimpleOrderedMap<Object>> props = new ArrayList<SimpleOrderedMap<Object>>();
      if (null == getRequestedFields()) {
        SortedSet<String> fieldNames = new TreeSet<String>(getSchema().getFields().keySet());
        for (String fieldName : fieldNames) {
          props.add(getFieldProperties(getSchema().getFields().get(fieldName)));
        }
      } else {
        if (0 == getRequestedFields().size()) {
          String message = "Empty " + CommonParams.FL + " parameter value";
          throw new SolrException(ErrorCode.BAD_REQUEST, message);
        }
        // Use the same order as the fl parameter
        for (String fieldName : getRequestedFields()) {
          final SchemaField field;
          if (includeDynamic) {
            field = getSchema().getFieldOrNull(fieldName);
          } else {
            field = getSchema().getFields().get(fieldName);
          }
          if (null == field) {
            log.info("Requested field '" + fieldName + "' not found.");
          } else {
            props.add(getFieldProperties(field));
          }
        }
      }
      getSolrResponse().add(IndexSchema.FIELDS, props);
    } catch (Exception e) {
      getSolrResponse().setException(e);
    }
    handlePostExecution(log);

    return new SolrOutputRepresentation();
  }

  @Override
  public Representation post(Representation entity) {
    try {
      if (!getSchema().isMutable()) {
        final String message = "This IndexSchema is not mutable.";
        throw new SolrException(ErrorCode.BAD_REQUEST, message);
      } else {
        if (null == entity.getMediaType()) {
          entity.setMediaType(MediaType.APPLICATION_JSON);
        }
        if (!entity.getMediaType().equals(MediaType.APPLICATION_JSON, true)) {
          String message = "Only media type " + MediaType.APPLICATION_JSON.toString() + " is accepted."
              + "  Request has media type " + entity.getMediaType().toString() + ".";
          log.error(message);
          throw new SolrException(ErrorCode.BAD_REQUEST, message);
        } else {
          Object object = ObjectBuilder.fromJSON(entity.getText());
          if ( ! (object instanceof List)) {
            String message = "Invalid JSON type " + object.getClass().getName() + ", expected List of the form"
                + " (ignore the backslashes): [{\"name\":\"foo\",\"type\":\"text_general\", ...}, {...}, ...]";
            log.error(message);
            throw new SolrException(ErrorCode.BAD_REQUEST, message);
          } else {
            List<Map<String, Object>> list = (List<Map<String, Object>>) object;
            List<SchemaField> newFields = new ArrayList<SchemaField>();
            IndexSchema oldSchema = getSchema();
            Map<String, Collection<String>> copyFields = new HashMap<String, Collection<String>>();
            Set<String> malformed = new HashSet<String>();
            for (Map<String, Object> map : list) {
              String fieldName = (String) map.remove(IndexSchema.NAME);
              if (null == fieldName) {
                String message = "Missing '" + IndexSchema.NAME + "' mapping.";
                log.error(message);
                throw new SolrException(ErrorCode.BAD_REQUEST, message);
              }
              String fieldType = (String) map.remove(IndexSchema.TYPE);
              if (null == fieldType) {
                String message = "Missing '" + IndexSchema.TYPE + "' mapping.";
                log.error(message);
                throw new SolrException(ErrorCode.BAD_REQUEST, message);
              }
              // copyFields:"comma separated list of destination fields"
              String copyTo = (String) map.get(IndexSchema.COPY_FIELDS);
              if (copyTo != null) {
                map.remove(IndexSchema.COPY_FIELDS);
                String[] splits = copyTo.split(",");
                Set<String> destinations = new HashSet<String>();
                if (splits != null && splits.length > 0) {
                  for (int i = 0; i < splits.length; i++) {
                    destinations.add(splits[i].trim());
                  }
                  copyFields.put(fieldName, destinations);
                } else{
                  malformed.add(fieldName);
                }
              }
              newFields.add(oldSchema.newField(fieldName, fieldType, map));
            }
            if (malformed.size() > 0){
              StringBuilder message = new StringBuilder("Malformed destination(s) for: ");
              for (String s : malformed) {
                message.append(s).append(", ");
              }
              if (message.length() > 2) {
                message.setLength(message.length() - 2);//drop the last ,
              }
              log.error(message.toString().trim());
              throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, message.toString().trim());
            }
            IndexSchema newSchema = oldSchema.addFields(newFields, copyFields);

            getSolrCore().setLatestSchema(newSchema);
          }
        }
      }
    } catch (Exception e) {
      getSolrResponse().setException(e);
    }
    handlePostExecution(log);

    return new SolrOutputRepresentation();
  }
}
