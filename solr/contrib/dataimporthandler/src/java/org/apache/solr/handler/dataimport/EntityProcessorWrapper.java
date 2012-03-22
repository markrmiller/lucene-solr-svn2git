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
package org.apache.solr.handler.dataimport;

import org.apache.solr.common.SolrException;
import static org.apache.solr.handler.dataimport.DataImportHandlerException.*;
import static org.apache.solr.handler.dataimport.EntityProcessorBase.*;
import static org.apache.solr.handler.dataimport.EntityProcessorBase.SKIP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A Wrapper over {@link EntityProcessor} instance which performs transforms and handles multi-row outputs correctly.
 *
 * @version $Id$
 * @since solr 1.4
 */
public class EntityProcessorWrapper extends EntityProcessor {
  private static final Logger log = LoggerFactory.getLogger(EntityProcessorWrapper.class);

  EntityProcessor delegate;
  private DocBuilder docBuilder;

  String onError;
  Context context;
  protected VariableResolverImpl resolver;
  String entityName;

  protected List<Transformer> transformers;

  protected List<Map<String, Object>> rowcache;

  public EntityProcessorWrapper(EntityProcessor delegate, DocBuilder docBuilder) {
    this.delegate = delegate;
    this.docBuilder = docBuilder;
  }

  @Override
  public void init(Context context) {
    rowcache = null;
    this.context = context;
    resolver = (VariableResolverImpl) context.getVariableResolver();
    //context has to be set correctly . keep the copy of the old one so that it can be restored in destroy
    if (entityName == null) {
      onError = resolver.replaceTokens(context.getEntityAttribute(ON_ERROR));
      if (onError == null) {
          onError = ABORT;
      }
      entityName = context.getEntityAttribute(DataConfig.NAME);
    }
    delegate.init(context);

  }

  @SuppressWarnings("unchecked")
  void loadTransformers() {
    String transClasses = context.getEntityAttribute(TRANSFORMER);

    if (transClasses == null) {
      transformers = Collections.EMPTY_LIST;
      return;
    }

    String[] transArr = transClasses.split(",");
    transformers = new ArrayList<Transformer>() {
      @Override
      public boolean add(Transformer transformer) {
        if (docBuilder != null && docBuilder.verboseDebug) {
          transformer = docBuilder.getDebugLogger().wrapTransformer(transformer);
        }
        return super.add(transformer);
      }
    };
    for (String aTransArr : transArr) {
      String trans = aTransArr.trim();
      if (trans.startsWith("script:")) {
        String functionName = trans.substring("script:".length());
        ScriptTransformer scriptTransformer = new ScriptTransformer();
        scriptTransformer.setFunctionName(functionName);
        transformers.add(scriptTransformer);
        continue;
      }
      try {
        Class clazz = DocBuilder.loadClass(trans, context.getSolrCore());
        if (Transformer.class.isAssignableFrom(clazz)) {
          transformers.add((Transformer) clazz.newInstance());
        } else {
          Method meth = clazz.getMethod(TRANSFORM_ROW, Map.class);
          transformers.add(new ReflectionTransformer(meth, clazz, trans));
        }
      } catch (NoSuchMethodException nsme){
         String msg = "Transformer :"
                    + trans
                    + "does not implement Transformer interface or does not have a transformRow(Map<String.Object> m)method";
            log.error(msg);
            wrapAndThrow(SEVERE, nsme,msg);        
      } catch (Exception e) {
        log.error("Unable to load Transformer: " + aTransArr, e);
        wrapAndThrow(SEVERE, e,"Unable to load Transformer: " + trans);
      }
    }

  }

  @SuppressWarnings("unchecked")
  static class ReflectionTransformer extends Transformer {
    final Method meth;

    final Class clazz;

    final String trans;

    final Object o;

    public ReflectionTransformer(Method meth, Class clazz, String trans)
            throws Exception {
      this.meth = meth;
      this.clazz = clazz;
      this.trans = trans;
      o = clazz.newInstance();
    }

    @Override
    public Object transformRow(Map<String, Object> aRow, Context context) {
      try {
        return meth.invoke(o, aRow);
      } catch (Exception e) {
        log.warn("method invocation failed on transformer : " + trans, e);
        throw new DataImportHandlerException(WARN, e);
      }
    }
  }

  protected Map<String, Object> getFromRowCache() {
    if (rowcache.isEmpty()){
      return null;
    }
    return rowcache.remove(0);
  }

  /**
   * handles null-on-null invocation
   * 
   * call transformers via {@link #transformRow}, then assigns the result into row cache, and delegates to {@link #getFromRowCache}
   * */
  protected Map<String, Object> applyTransformer(Map<String, Object> row) {
    if(row == null) return null;
    
    List<Map<String, Object>> result = transformRow(row);
    if(!result.isEmpty()){
        rowcache = result;
        return getFromRowCache();
    } else {
        return null;
    }
  }
  /**
   * Initialises transformers, applies them on the given row. returned collection is mutable
   * @return several rows emitted by transformers. if there are no transformers, 
   * returns single element list contains the given row; if transformer returns null,
   * returns empty collection.
   * **/
  protected List<Map<String, Object>> transformRow(Map<String, Object> row) {
    if (transformers == null){
      loadTransformers();
    }
    if (transformers == Collections.EMPTY_LIST){
        List<Map<String, Object>> same = new ArrayList<Map<String, Object>>();
        same.add(row);
        return same;
    }else{
    Map<String, Object> transformedRow = row;
    List<Map<String, Object>> rows = null;
    boolean stopTransform = checkStopTransform(row);
    VariableResolverImpl resolver = (VariableResolverImpl) context.getVariableResolver();
    for (Transformer t : transformers) {
      if (stopTransform) break;
      try {
        if (rows != null) {
          List<Map<String, Object>> tmpRows = new ArrayList<Map<String, Object>>();
          for (Map<String, Object> map : rows) {
            resolver.addNamespace(entityName, map);
            Object o = t.transformRow(map, context);
            if (o == null)
              continue;
            if (o instanceof Map) {
              Map oMap = (Map) o;
              stopTransform = checkStopTransform(oMap);
              tmpRows.add((Map) o);
            } else if (o instanceof List) {
              tmpRows.addAll((List) o);
            } else {
              log.error("Transformer must return Map<String, Object> or a List<Map<String, Object>>");
            }
          }
          rows = tmpRows;
        } else {
          resolver.addNamespace(entityName, transformedRow);
          Object o = t.transformRow(transformedRow, context);
          if (o == null){
						transformedRow = null;
						break;
          }
          if (o instanceof Map) {
            Map oMap = (Map) o;
            stopTransform = checkStopTransform(oMap);
            transformedRow = (Map) o;
          } else if (o instanceof List) {
            rows = (List) o;
          } else {
            log.error("Transformer must return Map<String, Object> or a List<Map<String, Object>>");
          }
        }
      } catch (Exception e) {
        log.warn("transformer threw error", e);
        if (ABORT.equals(onError)) {
          wrapAndThrow(SEVERE, e);
        } else if (SKIP.equals(onError)) {
          wrapAndThrow(DataImportHandlerException.SKIP, e);
        }
        // onError = continue
      } // catch
    }// for each transformers
    if(rows == null){ // legacy behavior 
        List<Map<String, Object>> box = new ArrayList<Map<String, Object>>();
        if(transformedRow!=null){
            box.add(transformedRow);
        }// otherwise give an empty box
        return box;
    }else{
        return rows;
    }
    }// !transformers.isEmpty()
}

  private boolean checkStopTransform(Map oMap) {
    return oMap.get("$stopTransform") != null
            && Boolean.parseBoolean(oMap.get("$stopTransform").toString());
  }

  /**
   * for root entity it retrieves single row, transforms it, 
   *    and loop until transfomer passes the first row
   *    
   * for child entities whole page is pulled. where the page is non-null children entity rows.
   * then the whole page is transformed and emitted to the row cache.
   * 
   * the rationale is avoid stealing child rows by parent entity threads. For every parent row 
   * the linked children rows (page) is pulled under lock obtained on delegate EntityProcessor   
   **/
  @Override
  public Map<String, Object> nextRow() {
    if (rowcache != null) {
      Map<String,Object> rowFromCache = getFromRowCache();
      if(rowFromCache != null || !context.isRootEntity()){
        return rowFromCache;
      } 
    }
    
    List<Map<String, Object>> transformedRows = new ArrayList<Map<String,Object>>();
    boolean eof = false;
    while (transformedRows.isEmpty() && !eof) { // looping while transformer bans raw rows
        List<Map<String, Object>> rawRows = new ArrayList<Map<String, Object>>();
      synchronized (delegate) {
          Map<String, Object> arow = null;
          // for paginated case we need to loop through whole page, other wise single row is enough
          boolean retrieveWholePage = !context.isRootEntity();
          
          if(((EntityProcessorBase) delegate).context==null || retrieveWholePage){
            delegate.init(context);
          }
          
          for (int i = 0; 
              retrieveWholePage ? !eof : i==0; // otherwise only single row
                  i++) {
              arow = pullRow();
              if (arow != null) {
                  rawRows.add(arow);
              }else { // there is no row, eof
                  eof = true;
              }
          }
      }
      for(Map<String, Object> rawRow : rawRows){
          // transforming emits N rows
          List<Map<String, Object>> result = transformRow(rawRow);
       // but post-transforming is applied only to the first one (legacy as-is)
          if(!result.isEmpty() && result.get(0) != null){ 
              delegate.postTransform(result.get(0));
              transformedRows.addAll(result);
          }
      }
    }
    if(!transformedRows.isEmpty()){
        rowcache = transformedRows;
        return getFromRowCache();
    }else{ // caused by eof
        return null;
    }
  }
  
  /**
   * pulls single row from the delegate EntityProcessor.
   * it expect to be called in synchronized(delegate) section
   * @return row from delegate
   * */
  protected Map<String,Object> pullRow() {
    Map<String,Object> arow = null;
    try {
      arow = delegate.nextRow();
    } catch (Exception e) {
      if (ABORT.equals(onError)) {
        wrapAndThrow(SEVERE, e);
      } else {
        // SKIP is not really possible. If this calls the nextRow() again the
        // Entityprocessor would be in an inconistent state
        log.error("Exception in entity : " + entityName, e);
        return null;
      }
    }
    log.debug("arow : {}", arow);
    return arow;
  }

  @Override
  public Map<String, Object> nextModifiedRowKey() {
    Map<String, Object> row = delegate.nextModifiedRowKey();
    row = applyTransformer(row);
    rowcache = null;
    return row;
  }

  @Override
  public Map<String, Object> nextDeletedRowKey() {
    Map<String, Object> row = delegate.nextDeletedRowKey();
    row = applyTransformer(row);
    rowcache = null;
    return row;
  }

  @Override
  public Map<String, Object> nextModifiedParentRowKey() {
    return delegate.nextModifiedParentRowKey();
  }

  @Override
  public void destroy() {
    delegate.destroy();
  }

  public VariableResolverImpl getVariableResolver() {
    return (VariableResolverImpl) context.getVariableResolver();
  }

  public Context getContext() {
    return context;
  }

  @Override
  public void close() {
    delegate.close();
  }
}
