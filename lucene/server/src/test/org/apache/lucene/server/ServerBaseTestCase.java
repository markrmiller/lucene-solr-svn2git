package org.apache.lucene.server;

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util._TestUtil;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.jboss.netty.channel.ChannelException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.ParseException;

public class ServerBaseTestCase extends LuceneTestCase {

  private static Thread serverThread;

  static int port;

  protected static void clearDir() throws IOException {
    _TestUtil.rmDir(new File("test"));
    _TestUtil.rmDir(new File("state"));
  }

  protected long addDocument(String json) throws Exception {
    JSONObject o = send("addDocument", json);
    return ((Number) o.get("indexGen")).longValue();
  }

  protected static void installPlugin(File sourceFile) throws IOException {
    ZipFile zipFile = new ZipFile(sourceFile);
    
    Enumeration<? extends ZipEntry> entries = zipFile.entries();

    File pluginsDir = new File("state/plugins");
    if (!pluginsDir.exists()) {
      pluginsDir.mkdirs();
    }

    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      
      InputStream in = zipFile.getInputStream(entry);
      File targetFile = new File(pluginsDir, entry.getName());
      if (entry.isDirectory()) {
        // allow unzipping with directory structure
        targetFile.mkdirs();
      } else {
        if (targetFile.getParentFile()!=null) {
          targetFile.getParentFile().mkdirs();   
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile));
        
        byte[] buffer = new byte[8192];
        int len;
        while((len = in.read(buffer)) >= 0) {
          out.write(buffer, 0, len);
        }
        
        in.close();
        out.close();
      }
    }
    
    zipFile.close();
  }

  protected static void put(JSONObject o, String key, String value) throws ParseException {
    o.put(key, JSONValue.parse(value));
  }
    
  protected static void startServer() throws Exception {
    final CountDownLatch ready = new CountDownLatch(1);
    final Exception[] exc = new Exception[1];
    final AtomicReference<Server> theServer = new AtomicReference<Server>();
    serverThread = new Thread() {
        @Override
        public void run() {
          try {
            Server s = new Server(new File("state"));
            theServer.set(s);
            s.run(0, 1, ready);
          } catch (Exception e) {
            exc[0] = e;
            ready.countDown();
          }
        }
      };
    serverThread.start();
    if (!ready.await(2, TimeUnit.SECONDS)) {
      throw new IllegalStateException("server took more than 2 seconds to start");
    }
    if (exc[0] != null) {
      throw exc[0];
    }
    ServerBaseTestCase.port = theServer.get().actualPort;
  }

  protected static void createAndStartIndex() throws Exception {
    send("createIndex", "{indexName: index, rootDir: test}");
    // Wait at most 1 msec for a searcher to reopen; this
    // value is too low for a production site but for
    // testing we want to minimize sleep time:
    send("liveSettings", "{indexName: index, minRefreshSec: 0.001}");
    send("startIndex", "{indexName: index}");
  }

  protected static void shutdownServer() throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + "/shutdown").openConnection();
    c.setUseCaches(false);
    c.setRequestMethod("GET");
    // c.connect()
    c.getContentLength();
    // nocommit how to check return status code...
    c.disconnect();

    if (serverThread != null) {
      serverThread.join();
      serverThread = null;
    }
  }

  protected static void deleteAllDocs() throws Exception {
    if (VERBOSE) {
      System.out.println("TEST: deleteAllDocs");
    }
    send("deleteAllDocuments", "{indexName: index}");
  }

  protected static void commit() throws Exception {
    send("commit", "{indexName: index}");
  }

  protected static JSONObject send(String command, String args) throws Exception {
    // nocommit detect if parser didn't consume all args!
    JSONObject o = (JSONObject) JSONValue.parse(args);
    if (o == null) {
      throw new IllegalArgumentException("invalid JSON: " + args);
    }
    return send(command, o);
  }

  protected static JSONObject send(String command, JSONObject args) throws Exception {
    return sendRaw(command, args.toJSONString(JSONStyle.NO_COMPRESS));
  }

  protected static JSONObject sendRaw(String command, String body) throws Exception {
    byte[] bytes = body.getBytes("UTF-8");
    HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + "/" + command).openConnection();
    c.setUseCaches(false);
    c.setDoOutput(true);
    c.setRequestMethod("POST");
    c.setRequestProperty("Content-Length", ""+bytes.length);
    c.setRequestProperty("Charset", "UTF-8");
    try {
      c.getOutputStream().write(bytes);
    } catch (ConnectException ce) {
      System.out.println("FAILED port=" + port + ":");
      ce.printStackTrace(System.out);
      throw ce;
    }
    // c.connect()
    int code = c.getResponseCode();
    int size = c.getContentLength();
    bytes = new byte[size];
    if (code == 200) {
      InputStream is = c.getInputStream();
      is.read(bytes);
      c.disconnect();
      return (JSONObject) JSONValue.parseStrict(new String(bytes, "UTF-8"));
    } else {
      InputStream is = c.getErrorStream();
      is.read(bytes);
      c.disconnect();
      throw new IOException("Server error:\n" + new String(bytes, "UTF-8"));
    }
  }

  protected static void copyFile(File source, File dest) throws IOException {
    InputStream is = null;
    OutputStream os = null;
    try {
      is = new FileInputStream(source);
      os = new FileOutputStream(dest);
      byte[] buffer = new byte[1024];
      int length;
      while ((length = is.read(buffer)) > 0) {
        os.write(buffer, 0, length);
      }
    } finally {
      is.close();
      os.close();
    }
  }

  protected String prettyPrint(JSONObject o) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode rootNode = mapper.readValue(o.toString(), JsonNode.class);
    //ObjectWriter writer = mapper.defaultPrettyPrintingWriter();
    // ***IMPORTANT!!!*** for Jackson 2.x use the line below instead of the one above: 
    ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
    return writer.writeValueAsString(rootNode);
  }

  protected static String httpLoad(String path) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + "/" + path).openConnection();
    c.setUseCaches(false);
    c.setDoOutput(true);
    c.setRequestMethod("GET");
    // c.connect()
    int code = c.getResponseCode();
    int size = c.getContentLength();
    byte[] bytes = new byte[size];
    if (code == 200) {
      InputStream is = c.getInputStream();
      is.read(bytes);
      c.disconnect();
      return new String(bytes, "UTF-8");
    } else {
      InputStream is = c.getErrorStream();
      is.read(bytes);
      c.disconnect();
      throw new IOException("Server error:\n" + new String(bytes, "UTF-8"));
    }
  }

  protected static JSONObject sendChunked(String body, String request) throws Exception {
    byte[] bytes = body.getBytes("UTF-8");
    HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + "/" + request).openConnection();
    c.setUseCaches(false);
    c.setDoOutput(true);
    c.setChunkedStreamingMode(256);
    c.setRequestMethod("POST");
    c.setRequestProperty("Charset", "UTF-8");
    c.getOutputStream().write(bytes);
    // c.connect()
    int size = c.getContentLength();
    InputStream is = c.getInputStream();
    bytes = new byte[size];
    is.read(bytes);
    // nocommit how to check return status code...
    c.disconnect();
    return (JSONObject) JSONValue.parseStrict(new String(bytes, "UTF-8"));
  }

  /** Simple xpath-like utility method to jump down and grab
   *  something out of the JSON response. */
  protected Object get(Object o, String path) {
    int upto = 0;
    int tokStart = 0;
    boolean inArrayIndex = false;
    while(upto < path.length()) {       
      char ch = path.charAt(upto++);
      if (inArrayIndex) {
        if (ch == ']') {
          int index = Integer.parseInt(path.substring(tokStart, upto-1));
          o = ((JSONArray) o).get(index);
          inArrayIndex = false;
          tokStart = upto;
        }
      } else if (ch == '.' || ch == '[') {
        String name = path.substring(tokStart, upto-1);
        if (name.length() != 0) {
          o = ((JSONObject) o).get(name);
        }
        tokStart = upto;
        if (ch == '[') {
          inArrayIndex = true;
        }
      }
    }

    String name = path.substring(tokStart, upto);
    if (name.length() > 0) {
      if (o instanceof JSONArray && name.equals("length")) {
        o = new Integer(((JSONArray) o).size());
      } else {
        o = ((JSONObject) o).get(name);
      }
    }
    return o;
  }

  protected String getString(Object o, String path) {
    return (String) get(o, path);
  }

  protected int getInt(Object o, String path) {
    return ((Number) get(o, path)).intValue();
  }

  protected boolean getBoolean(Object o, String path) {
    return ((Boolean) get(o, path)).booleanValue();
  }

  protected long getLong(Object o, String path) {
    return ((Number) get(o, path)).longValue();
  }

  protected float getFloat(Object o, String path) {
    return ((Number) get(o, path)).floatValue();
  }

  protected JSONObject getObject(Object o, String path) {
    return (JSONObject) get(o, path);
  }

  protected JSONArray getArray(Object o, String path) {
    return (JSONArray) get(o, path);
  }

  /** Renders one hilited field (multiple passages) value
   * with <b>...</b> tags, and ... separating the passages. */ 
  protected String renderHighlight(JSONArray hit) {
    StringBuilder sb = new StringBuilder();
    for(Object o : hit) {
      if (sb.length() != 0) {
        sb.append("...");
      }
      sb.append(renderSingleHighlight(getArray(o, "parts")));
    }

    return sb.toString();
  }

  /** Renders a single passage with <b>...</b> tags. */
  protected String renderSingleHighlight(JSONArray passage) {
    StringBuilder sb = new StringBuilder();
    for(Object o2 : passage) {
      if (o2 instanceof String) {
        sb.append((String) o2);
      } else {
        JSONObject obj = (JSONObject) o2;
        sb.append("<b>");
        sb.append(obj.get("text"));
        sb.append("</b>");
      }
    }

    return sb.toString();
  }
}

