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

package org.apache.lucene.spatial.base.distance;

import org.apache.lucene.spatial.base.context.SpatialContext;
import org.apache.lucene.spatial.base.shape.Point;
import org.apache.lucene.spatial.base.shape.Rectangle;

public interface DistanceCalculator {

  public double distance(Point from, Point to);
  public double distance(Point from, double toX, double toY);

  public Point pointOnBearing(Point from, double dist, double bearingDEG, SpatialContext ctx);
  
  /**
   * Converts a distance to radians (multiples of the radius). A spherical
   * earth model is assumed for geospatial, and non-geospatial is the identity function.
   */
  public double distanceToDegrees(double distance);

  public double degreesToDistance(double degrees);

  //public Point pointOnBearing(Point from, double angle);

  public Rectangle calcBoxByDistFromPt(Point from, double distance, SpatialContext ctx);

  public double calcBoxByDistFromPtHorizAxis(Point from, double distance, SpatialContext ctx);

}
