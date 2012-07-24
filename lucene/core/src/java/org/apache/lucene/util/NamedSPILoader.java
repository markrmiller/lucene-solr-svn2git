package org.apache.lucene.util;

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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.ServiceConfigurationError;

/**
 * Helper class for loading named SPIs from classpath (e.g. Codec, PostingsFormat).
 * @lucene.internal
 */
// TODO: would be nice to have case insensitive lookups.
public final class NamedSPILoader<S extends NamedSPILoader.NamedSPI> implements Iterable<S> {

  private final Map<String,S> services;
  private final Class<S> clazz;

  public NamedSPILoader(Class<S> clazz) {
    this.clazz = clazz;
    final SPIClassIterator<S> loader = SPIClassIterator.get(clazz);
    final LinkedHashMap<String,S> services = new LinkedHashMap<String,S>();
    while (loader.hasNext()) {
      final Class<? extends S> c = loader.next();
      final S service;
      try {
        service = c.newInstance();
      } catch (InstantiationException ie) {
        throw new ServiceConfigurationError("Cannot instantiate SPI class: " + c.getName(), ie); 
      } catch (IllegalAccessException iae) {
        throw new ServiceConfigurationError("Cannot instantiate SPI class: " + c.getName(), iae); 
      }
      final String name = service.getName();
      // only add the first one for each name, later services will be ignored
      // this allows to place services before others in classpath to make 
      // them used instead of others
      if (!services.containsKey(name)) {
        assert checkServiceName(name);
        services.put(name, service);
      }
    }
    this.services = Collections.unmodifiableMap(services);
  }
  
  /**
   * Validates that a service name meets the requirements of {@link NamedSPI}
   */
  public static boolean checkServiceName(String name) {
    // based on harmony charset.java
    if (name.length() >= 128) {
      throw new IllegalArgumentException("Illegal service name: '" + name + "' is too long (must be < 128 chars).");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!isLetter(c) && !isDigit(c)) {
        throw new IllegalArgumentException("Illegal service name: '" + name + "' must be simple ascii alphanumeric.");
      }
    }
    return true;
  }
  
  /*
   * Checks whether a character is a letter (ascii) which are defined in the spec.
   */
  private static boolean isLetter(char c) {
      return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
  }

  /*
   * Checks whether a character is a digit (ascii) which are defined in the spec.
   */
  private static boolean isDigit(char c) {
      return ('0' <= c && c <= '9');
  }
  
  public S lookup(String name) {
    final S service = services.get(name);
    if (service != null) return service;
    throw new IllegalArgumentException("A SPI class of type "+clazz.getName()+" with name '"+name+"' does not exist. "+
     "You need to add the corresponding JAR file supporting this SPI to your classpath."+
     "The current classpath supports the following names: "+availableServices());
  }

  public Set<String> availableServices() {
    return services.keySet();
  }
  
  public Iterator<S> iterator() {
    return services.values().iterator();
  }
  
  /**
   * Interface to support {@link NamedSPILoader#lookup(String)} by name.
   * <p>
   * Names must be all ascii alphanumeric, and less than 128 characters in length.
   */
  public static interface NamedSPI {
    String getName();
  }
  
}
