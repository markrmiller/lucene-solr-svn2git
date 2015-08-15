package org.apache.lucene.geo3d;

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

/**
 * 3D rectangle, bounded on six sides by X,Y,Z limits, degenerate in X.
 *
 * @lucene.internal
 */
public class dXYZSolid extends BaseXYZSolid {

  /** X plane */
  protected final Plane xPlane;
  /** Min-Y plane */
  protected final SidedPlane minYPlane;
  /** Max-Y plane */
  protected final SidedPlane maxYPlane;
  /** Min-Z plane */
  protected final SidedPlane minZPlane;
  /** Max-Z plane */
  protected final SidedPlane maxZPlane;
  
  /** These are the edge points of the shape, which are defined to be at least one point on
   * each surface area boundary.  In the case of a solid, this includes points which represent
   * the intersection of XYZ bounding planes and the planet, as well as points representing
   * the intersection of single bounding planes with the planet itself.
   */
  protected final GeoPoint[] edgePoints;

  /** Notable points for XPlane */
  protected final GeoPoint[] notableXPoints;

  /**
   *@param planetModel is the planet model.
   *@param X is the X value.
   *@param minY is the minimum Y value.
   *@param maxY is the maximum Y value.
   *@param minZ is the minimum Z value.
   *@param maxZ is the maximum Z value.
   */
  public dXYZSolid(final PlanetModel planetModel,
    final double X,
    final double minY,
    final double maxY,
    final double minZ,
    final double maxZ) {
    super(planetModel);
    // Argument checking
    if (maxY - minY < Vector.MINIMUM_RESOLUTION)
      throw new IllegalArgumentException("Y values in wrong order or identical");
    if (maxZ - minZ < Vector.MINIMUM_RESOLUTION)
      throw new IllegalArgumentException("Z values in wrong order or identical");

    final double worldMinX = planetModel.getMinimumXValue();
    final double worldMaxX = planetModel.getMaximumXValue();
    final double worldMinY = planetModel.getMinimumYValue();
    final double worldMaxY = planetModel.getMaximumYValue();
    final double worldMinZ = planetModel.getMinimumZValue();
    final double worldMaxZ = planetModel.getMaximumZValue();
    
    // Construct the planes
    xPlane = new Plane(xUnitVector,-X);
    minYPlane = new SidedPlane(0.0,maxY,0.0,yUnitVector,-minY);
    maxYPlane = new SidedPlane(0.0,minY,0.0,yUnitVector,-maxY);
    minZPlane = new SidedPlane(0.0,0.0,maxZ,zUnitVector,-minZ);
    maxZPlane = new SidedPlane(0.0,0.0,minZ,zUnitVector,-maxZ);
      
    // We need at least one point on the planet surface for each manifestation of the shape.
    // There can be up to 2 (on opposite sides of the world).  But we have to go through
    // 4 combinations of adjacent planes in order to find out if any have 2 intersection solution.
    // Typically, this requires 4 square root operations. 
    final GeoPoint[] XminY = xPlane.findIntersections(planetModel,minYPlane,maxYPlane,minZPlane,maxZPlane);
    final GeoPoint[] XmaxY = xPlane.findIntersections(planetModel,maxYPlane,minYPlane,minZPlane,maxZPlane);
    final GeoPoint[] XminZ = xPlane.findIntersections(planetModel,minZPlane,maxZPlane,minYPlane,maxYPlane);
    final GeoPoint[] XmaxZ = xPlane.findIntersections(planetModel,maxZPlane,minZPlane,minYPlane,maxYPlane);

    notableXPoints = glueTogether(XminY, XmaxY, XminZ, XmaxZ);

    // Now, compute the edge points.
    // This is the trickiest part of setting up an XYZSolid.  We've computed intersections already, so
    // we'll start there.  We know that at most there will be two disconnected shapes on the planet surface.
    // But there's also a case where exactly one plane slices through the world, and none of the bounding plane
    // intersections do.  Thus, if we don't find any of the edge intersection cases, we have to look for that last case.
    GeoPoint[] edgePoints = findLargestSolution(XminY,XmaxY,XminZ,XmaxZ);
      
    if (edgePoints.length == 0) {
      // If we still haven't encountered anything, we need to look at single-plane/world intersections.
      // We detect these by looking at the world model and noting its x, y, and z bounds.
      // For the single-dimension degenerate case, there's really only one plane that can possibly intersect the world.
        
      if (X - worldMinX >= -Vector.MINIMUM_RESOLUTION && X - worldMaxX <= Vector.MINIMUM_RESOLUTION &&
        (minY - worldMinY < -Vector.MINIMUM_RESOLUTION) &&
        (maxY - worldMaxY > Vector.MINIMUM_RESOLUTION) &&
        (minZ - worldMinZ < -Vector.MINIMUM_RESOLUTION) &&
        (maxZ - worldMaxZ > Vector.MINIMUM_RESOLUTION)) {
        // Find any point on the X plane that intersects the world
        // First construct a perpendicular plane that will allow us to find a sample point.
        // This plane is vertical and goes through the points (0,0,0) and (1,0,0)
        // Then use it to compute a sample point.
        edgePoints = new GeoPoint[]{xPlane.getSampleIntersectionPoint(planetModel, xVerticalPlane)};
      }
    }

    this.edgePoints = edgePoints;
  }

  @Override
  protected GeoPoint[] getEdgePoints() {
    return edgePoints;
  }
  
  @Override
  public boolean isWithin(final double x, final double y, final double z) {
    return xPlane.evaluateIsZero(x, y, z) &&
      minYPlane.isWithin(x, y, z) &&
      maxYPlane.isWithin(x, y, z) &&
      minZPlane.isWithin(x, y, z) &&
      maxZPlane.isWithin(x, y, z);
  }

  @Override
  public int getRelationship(final GeoShape path) {
    //System.err.println(this+" getrelationship with "+path);
    final int insideRectangle = isShapeInsideArea(path);
    if (insideRectangle == SOME_INSIDE) {
      //System.err.println(" some shape points inside area");
      return OVERLAPS;
    }

    // Figure out if the entire XYZArea is contained by the shape.
    final int insideShape = isAreaInsideShape(path);
    if (insideShape == SOME_INSIDE) {
      //System.err.println(" some area points inside shape");
      return OVERLAPS;
    }

    if (insideRectangle == ALL_INSIDE && insideShape == ALL_INSIDE) {
      //System.err.println(" inside of each other");
      return OVERLAPS;
    }

    // The entire locus of points in this shape is on a single plane, so we only need ot look for an intersection with that plane.
    System.err.println("xPlane = "+xPlane);
    if (path.intersects(xPlane, notableXPoints, minYPlane, maxYPlane, minZPlane, maxZPlane)) {
      //System.err.println(" edges intersect");
      return OVERLAPS;
    }

    if (insideRectangle == ALL_INSIDE) {
      //System.err.println(" shape points inside area");
      return WITHIN;
    }

    if (insideShape == ALL_INSIDE) {
      //System.err.println(" shape contains all area");
      return CONTAINS;
    }
    //System.err.println(" disjoint");
    return DISJOINT;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof dXYZSolid))
      return false;
    dXYZSolid other = (dXYZSolid) o;
    if (!super.equals(other)) {
      return false;
    }
    return other.xPlane.equals(xPlane) &&
      other.minYPlane.equals(minYPlane) &&
      other.maxYPlane.equals(maxYPlane) &&
      other.minZPlane.equals(minZPlane) &&
      other.maxZPlane.equals(maxZPlane);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result  + xPlane.hashCode();
    result = 31 * result  + minYPlane.hashCode();
    result = 31 * result  + maxYPlane.hashCode();
    result = 31 * result  + minZPlane.hashCode();
    result = 31 * result  + maxZPlane.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "dXYZSolid: {planetmodel="+planetModel+", xplane="+xPlane+", minYplane="+minYPlane+", maxYplane="+maxYPlane+", minZplane="+minZPlane+", maxZplane="+maxZPlane+"}";
  }
  
}
  
