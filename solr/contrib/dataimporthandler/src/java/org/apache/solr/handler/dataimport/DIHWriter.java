package org.apache.solr.handler.dataimport;
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
import org.apache.solr.common.SolrInputDocument;

/**
 * @solr.experimental
 *
 */
public interface DIHWriter {
	
	/**
	 * <p>
	 *  If this writer supports transactions or commit points, then commit any changes, 
	 *  optionally optimizing the data for read/write performance
	 * </p>
	 * @param optimize
	 */
	public void commit(boolean optimize);
	
	/**
	 * <p>
	 *  Release resources used by this writer.  After calling close, reads & updates will throw exceptions.
	 * </p>
	 */
	public void close();

	/**
	 * <p>
	 *  If this writer supports transactions or commit points, then roll back any uncommitted changes.
	 * </p>
	 */
	public void rollback();

	/**
	 * <p>
	 *  Delete from the writer's underlying data store based the passed-in writer-specific query. (Optional Operation)
	 * </p>
	 * @param q
	 */
	public void deleteByQuery(String q);

	/**
	 * <p>
	 *  Delete everything from the writer's underlying data store
	 * </p>
	 */
	public void doDeleteAll();

	/**
	 * <p>
	 *  Delete from the writer's underlying data store based on the passed-in Primary Key
	 * </p>
	 * @param key
	 */
	public void deleteDoc(Object key);
	


	/**
	 * <p>
	 *  Add a document to this writer's underlying data store.
	 * </p>
	 * @param doc
	 * @return
	 */
	public boolean upload(SolrInputDocument doc);


	
	/**
	 * <p>
	 *  Provide context information for this writer.  init() should be called before using the writer.
	 * </p>
	 * @param context
	 */
	public void init(Context context) ;

}
