package org.apache.solr.schema;
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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.AttributeSource.State;
import org.apache.solr.analysis.SolrAnalyzer;
import org.apache.solr.response.TextResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-analyzed field type provides a way to index a serialized token stream,
 * optionally with an independent stored value of a field.
 */
public class PreAnalyzedField extends FieldType {
  private static final Logger LOG = LoggerFactory.getLogger(PreAnalyzedField.class);

  /** Init argument name. Value is a fully-qualified class name of the parser
   * that implements {@link PreAnalyzedParser}.
   */
  public static final String PARSER_IMPL = "parserImpl";
  
  private static final String DEFAULT_IMPL = JsonPreAnalyzedParser.class.getName();

  
  private PreAnalyzedParser parser;
  
  @Override
  public void init(IndexSchema schema, Map<String, String> args) {
    super.init(schema, args);
    String implName = args.get(PARSER_IMPL);
    if (implName == null) {
      parser = new JsonPreAnalyzedParser();
    } else {
      // short name
      if ("json".equalsIgnoreCase(implName)) {
        parser = new JsonPreAnalyzedParser();
      } else if ("simple".equalsIgnoreCase(implName)) {
        parser = new SimplePreAnalyzedParser();
      } else {
        try {
          Class<? extends PreAnalyzedParser> implClazz = schema.getResourceLoader().findClass(implName, PreAnalyzedParser.class);
          Constructor<?> c = implClazz.getConstructor(new Class<?>[0]);
          parser = (PreAnalyzedParser) c.newInstance(new Object[0]);
        } catch (Exception e) {
          LOG.warn("Can't use the configured PreAnalyzedParser class '" + implName +
              "', using default " + DEFAULT_IMPL, e);
          parser = new JsonPreAnalyzedParser();
        }
      }
      args.remove(PARSER_IMPL);
    }
  }

  @Override
  public Analyzer getAnalyzer() {
    return new SolrAnalyzer() {
      
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        return new TokenStreamComponents(new PreAnalyzedTokenizer(reader, parser));
      }
      
    };
  }
  
  @Override
  public Analyzer getQueryAnalyzer() {
    return getAnalyzer();
  }

  @Override
  public IndexableField createField(SchemaField field, Object value,
          float boost) {
    IndexableField f = null;
    try {
      f = fromString(field, String.valueOf(value), boost);
    } catch (Exception e) {
      LOG.warn("Error parsing pre-analyzed field '" + field.getName() + "'", e);
      return null;
    }
    return f;
  }

  @Override
  public SortField getSortField(SchemaField field, boolean top) {
    return getStringSort(field, top);
  }

  @Override
  public void write(TextResponseWriter writer, String name, IndexableField f)
          throws IOException {
    writer.writeStr(name, f.stringValue(), true);
  }
  
  /** Utility method to convert a field to a string that is parse-able by this
   * class.
   * @param f field to convert
   * @return string that is compatible with the serialization format
   * @throws IOException If there is a low-level I/O error.
   */
  public String toFormattedString(Field f) throws IOException {
    return parser.toFormattedString(f);
  }
  
  /**
   * Utility method to create a {@link org.apache.lucene.document.FieldType}
   * based on the {@link SchemaField}
   */
  public static org.apache.lucene.document.FieldType createFieldType(SchemaField field) {
    if (!field.indexed() && !field.stored()) {
      if (log.isTraceEnabled())
        log.trace("Ignoring unindexed/unstored field: " + field);
      return null;
    }
    org.apache.lucene.document.FieldType newType = new org.apache.lucene.document.FieldType();
    newType.setIndexed(field.indexed());
    newType.setTokenized(field.isTokenized());
    newType.setStored(field.stored());
    newType.setOmitNorms(field.omitNorms());
    IndexOptions options = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
    if (field.omitTermFreqAndPositions()) {
      options = IndexOptions.DOCS_ONLY;
    } else if (field.omitPositions()) {
      options = IndexOptions.DOCS_AND_FREQS;
    } else if (field.storeOffsetsWithPositions()) {
      options = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
    }
    newType.setIndexOptions(options);
    newType.setStoreTermVectors(field.storeTermVector());
    newType.setStoreTermVectorOffsets(field.storeTermOffsets());
    newType.setStoreTermVectorPositions(field.storeTermPositions());
    return newType;
  }
  
  /**
   * This is a simple holder of a stored part and the collected states (tokens with attributes).
   */
  public static class ParseResult {
    public String str;
    public byte[] bin;
    public List<State> states = new LinkedList<>();
  }
  
  /**
   * Parse the input and return the stored part and the tokens with attributes.
   */
  public static interface PreAnalyzedParser {
    /**
     * Parse input.
     * @param reader input to read from
     * @param parent parent who will own the resulting states (tokens with attributes)
     * @return parse result, with possibly null stored and/or states fields.
     * @throws IOException if a parsing error or IO error occurs
     */
    public ParseResult parse(Reader reader, AttributeSource parent) throws IOException;
    
    /**
     * Format a field so that the resulting String is valid for parsing with {@link #parse(Reader, AttributeSource)}.
     * @param f field instance
     * @return formatted string
     * @throws IOException If there is a low-level I/O error.
     */
    public String toFormattedString(Field f) throws IOException;
  }
  
  
  public IndexableField fromString(SchemaField field, String val, float boost) throws Exception {
    if (val == null || val.trim().length() == 0) {
      return null;
    }
    PreAnalyzedTokenizer parse = new PreAnalyzedTokenizer(new StringReader(val), parser);
    parse.reset(); // consume
    org.apache.lucene.document.FieldType type = createFieldType(field);
    if (type == null) {
      parse.close();
      return null;
    }
    Field f = null;
    if (parse.getStringValue() != null) {
      if (field.stored()) {
        f = new Field(field.getName(), parse.getStringValue(), type);
      } else {
        type.setStored(false);
      }
    } else if (parse.getBinaryValue() != null) {
      if (field.isBinary()) {
        f = new Field(field.getName(), parse.getBinaryValue(), type);
      }
    } else {
      type.setStored(false);
    }
    
    if (parse.hasTokenStream()) {
      if (field.indexed()) {
        type.setIndexed(true);
        type.setTokenized(true);
        if (f != null) {
          f.setTokenStream(parse);
        } else {
          f = new Field(field.getName(), parse, type);
        }
      } else {
        if (f != null) {
          f.fieldType().setIndexed(false);
          f.fieldType().setTokenized(false);
        }
      }
    }
    if (f != null) {
      f.setBoost(boost);
    }
    return f;
  }
    
  /**
   * Token stream that works from a list of saved states.
   */
  private static class PreAnalyzedTokenizer extends Tokenizer {
    private final List<AttributeSource.State> cachedStates = new LinkedList<>();
    private Iterator<AttributeSource.State> it = null;
    private String stringValue = null;
    private byte[] binaryValue = null;
    private PreAnalyzedParser parser;
    private Reader lastReader;
    private Reader input; // hides original input since we replay saved states (and dont reuse)
    
    public PreAnalyzedTokenizer(Reader reader, PreAnalyzedParser parser) {
      super(reader);
      this.input = reader;
      this.parser = parser;
    }
    
    public boolean hasTokenStream() {
      return !cachedStates.isEmpty();
    }
    
    public String getStringValue() {
      return stringValue;
    }
    
    public byte[] getBinaryValue() {
      return binaryValue;
    }
    
    @Override
    public final boolean incrementToken() {
      // lazy init the iterator
      if (it == null) {
        it = cachedStates.iterator();
      }
    
      if (!it.hasNext()) {
        return false;
      }
      
      AttributeSource.State state = (State) it.next();
      restoreState(state.clone());
      return true;
    }
  
    @Override
    public final void reset() throws IOException {
      // NOTE: this acts like rewind if you call it again
      if (input != lastReader) {
        lastReader = input;
        cachedStates.clear();
        stringValue = null;
        binaryValue = null;
        ParseResult res = parser.parse(input, this);
        if (res != null) {
          stringValue = res.str;
          binaryValue = res.bin;
          if (res.states != null) {
            cachedStates.addAll(res.states);
          }
        }
      }
      it = cachedStates.iterator();
    }

    @Override
    public void close() throws IOException {
      super.close();
      lastReader = null; // just a ref, null for gc
    }
  }
  
}
