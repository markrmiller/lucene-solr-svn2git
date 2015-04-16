package org.apache.lucene.spatial.spatial4j.geo3d;

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

/** Bounding box wider than PI but limited on left and right sides (
* left lon, right lon).
*/
public class GeoWideLongitudeSlice implements GeoBBox
{
    public final double leftLon;
    public final double rightLon;
      
    public final SidedPlane leftPlane;
    public final SidedPlane rightPlane;
      
    public final GeoPoint centerPoint;

    /** Accepts only values in the following ranges: lon: {@code -PI -> PI}.
    * Horizantal angle must be greater than or equal to PI.
    */
    public GeoWideLongitudeSlice(double leftLon, double rightLon)
    {
        // Argument checking
        if (leftLon < -Math.PI || leftLon > Math.PI)
            throw new IllegalArgumentException("Left longitude out of range");
        if (rightLon < -Math.PI || rightLon > Math.PI)
            throw new IllegalArgumentException("Right longitude out of range");
        double extent = rightLon - leftLon;
        if (extent < 0.0) {
            extent += 2.0 * Math.PI;
        }
        if (extent < Math.PI)
            throw new IllegalArgumentException("Width of rectangle too small");

        this.leftLon = leftLon;
        this.rightLon = rightLon;
          
        double sinLeftLon = Math.sin(leftLon);
        double cosLeftLon = Math.cos(leftLon);
        double sinRightLon = Math.sin(rightLon);
        double cosRightLon = Math.cos(rightLon);

        // Normalize
        while (leftLon > rightLon) {
            rightLon += Math.PI * 2.0;
        }
        double middleLon = (leftLon + rightLon) * 0.5;
        centerPoint = new GeoPoint(0.0,middleLon);              
        
        this.leftPlane = new SidedPlane(centerPoint,cosLeftLon,sinLeftLon);
        this.rightPlane = new SidedPlane(centerPoint,cosRightLon,sinRightLon);
    }

    @Override
    public GeoBBox expand(double angle)
    {
        // Figuring out when we escalate to a special case requires some prefiguring
        double currentLonSpan = rightLon - leftLon;
        if (currentLonSpan < 0.0)
            currentLonSpan += Math.PI * 2.0;
        double newLeftLon = leftLon - angle;
        double newRightLon = rightLon + angle;
        if (currentLonSpan + 2.0 * angle >= Math.PI * 2.0) {
            newLeftLon = -Math.PI;
            newRightLon = Math.PI;
        }
        return GeoBBoxFactory.makeGeoBBox(Math.PI * 0.5,-Math.PI * 0.5,newLeftLon,newRightLon);
    }

    @Override
    public boolean isWithin(Vector point)
    {
        return leftPlane.isWithin(point) ||
          rightPlane.isWithin(point);
    }

    @Override
    public boolean isWithin(double x, double y, double z)
    {
        return leftPlane.isWithin(x,y,z) ||
          rightPlane.isWithin(x,y,z);
    }

    @Override
    public double getRadius()
    {
        // Compute the extent and divide by two
        double extent = rightLon - leftLon;
        if (extent < 0.0)
            extent += Math.PI * 2.0;
        return Math.max(Math.PI * 0.5, extent * 0.5);
    }
      
    @Override
    public GeoPoint getInteriorPoint()
    {
        return centerPoint;
    }
      
    @Override
    public boolean intersects(Plane p, Membership... bounds)
    {
        // Right and left bounds are essentially independent hemispheres; crossing into the wrong part of one
        // requires crossing into the right part of the other.  So intersection can ignore the left/right bounds.
        return  p.intersects(leftPlane,bounds) ||
          p.intersects(rightPlane,bounds);
    }

    /** Compute longitude/latitude bounds for the shape.
    *@param bounds is the optional input bounds object.  If this is null,
    * a bounds object will be created.  Otherwise, the input object will be modified.
    *@return a Bounds object describing the shape's bounds.  If the bounds cannot
    * be computed, then return a Bounds object with noLongitudeBound,
    * noTopLatitudeBound, and noBottomLatitudeBound.
    */
    @Override
    public Bounds getBounds(Bounds bounds)
    {
        if (bounds == null)
            bounds = new Bounds();
        bounds.noTopLatitudeBound().noBottomLatitudeBound();
        bounds.addLongitudeSlice(leftLon,rightLon);
        return bounds;
    }

    @Override
    public int getRelationship(GeoShape path) {
        if (path.intersects(leftPlane) ||
            path.intersects(rightPlane))
            return OVERLAPS;

        if (isWithin(path.getInteriorPoint()))
            return WITHIN;

        if (path.isWithin(centerPoint))
            return CONTAINS;
        
        return DISJOINT;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof GeoWideLongitudeSlice))
            return false;
        GeoWideLongitudeSlice other = (GeoWideLongitudeSlice)o;
        return other.leftLon == leftLon && other.rightLon == rightLon;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(leftLon);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(rightLon);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
  
