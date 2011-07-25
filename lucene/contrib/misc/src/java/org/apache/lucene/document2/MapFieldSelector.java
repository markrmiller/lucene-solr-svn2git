package org.apache.lucene.document2;

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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link FieldSelector} based on a Map of field names to {@link FieldSelectorResult}s
 *
 */
public class MapFieldSelector implements FieldSelector {
    
    Map<String,FieldSelectorResult> fieldSelections;
    
    /** Create a a MapFieldSelector
     * @param fieldSelections maps from field names (String) to {@link FieldSelectorResult}s
     */
    public MapFieldSelector(Map<String,FieldSelectorResult> fieldSelections) {
        this.fieldSelections = fieldSelections;
    }
    
    /** Create a a MapFieldSelector
     * @param fields fields to LOAD.  List of Strings.  All other fields are NO_LOAD.
     */
    public MapFieldSelector(List<String> fields) {
        fieldSelections = new HashMap<String,FieldSelectorResult>(fields.size()*5/3);
        for (final String field : fields)
            fieldSelections.put(field, FieldSelectorResult.LOAD);
    }
    
    /** Create a a MapFieldSelector
     * @param fields fields to LOAD.  All other fields are NO_LOAD.
     */
    public MapFieldSelector(String... fields) {
      this(Arrays.asList(fields));
    }


    
    /** Load field according to its associated value in fieldSelections
     * @param field a field name
     * @return the fieldSelections value that field maps to or NO_LOAD if none.
     */
    public FieldSelectorResult accept(String field) {
        FieldSelectorResult selection = fieldSelections.get(field);
        return selection!=null ? selection : FieldSelectorResult.NO_LOAD;
    }
    
}
