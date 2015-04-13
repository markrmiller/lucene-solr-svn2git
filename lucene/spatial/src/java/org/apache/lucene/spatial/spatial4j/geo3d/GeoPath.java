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

import java.util.*;

/** GeoSearchableShape representing a path across the surface of the globe,
* with a specified half-width.  Path is described by a series of points.
* Distances are measured from the starting point along the path, and then at right
* angles to the path.
*/
public class GeoPath extends GeoBaseExtendedShape implements GeoDistanceShape
{
    public final double cutoffAngle;
    public final double cutoffOffset;
    public final double originDistance;
    public final double chordDistance;
      
    public final List<SegmentEndpoint> points = new ArrayList<SegmentEndpoint>();
    public final List<PathSegment> segments = new ArrayList<PathSegment>();
    
    public GeoPath(double cutoffAngle)
    {
        super();
        if (cutoffAngle < 0.0 || cutoffAngle > Math.PI * 0.5)
            throw new IllegalArgumentException("Cutoff angle out of bounds");
        this.cutoffAngle = cutoffAngle;
        double cosAngle = Math.cos(cutoffAngle);
        double sinAngle = Math.sin(cutoffAngle);
        // Cutoff offset is the linear distance given the angle
        this.cutoffOffset = sinAngle;
        this.originDistance = cosAngle;
        // Compute chord distance
        double xDiff = 1.0 - cosAngle;
        this.chordDistance = Math.sqrt(xDiff * xDiff + sinAngle * sinAngle);
    }
    
    public void addPoint(double lat, double lon)
    {
        if (lat < -Math.PI * 0.5 || lat > Math.PI * 0.5)
            throw new IllegalArgumentException("Latitude out of range");
        if (lon < -Math.PI || lon > Math.PI)
            throw new IllegalArgumentException("Longitude out of range");
        GeoPoint end = new GeoPoint(lat,lon);
        if (points.size() > 0) {
          GeoPoint start = points.get(points.size()-1).point;
          PathSegment ps = new PathSegment(start,end,cutoffOffset,cutoffAngle,chordDistance);
          // Check for degeneracy; if the segment is degenerate, don't include the point
          if (ps.isDegenerate())
              return;
          segments.add(ps);
        }
        SegmentEndpoint se = new SegmentEndpoint(end, originDistance, cutoffOffset, cutoffAngle, chordDistance);
        points.add(se);
    }
    
    /** Compute an estimate of "distance" to the GeoPoint.
    * A return value of Double.MAX_VALUE should be returned for
    * points outside of the shape.
    */
    @Override
    public double computeNormalDistance(GeoPoint point)
    {
        // Algorithm:
        // (1) If the point is within any of the segments along the path, return that value.
        // (2) If the point is within any of the segment end circles along the path, return that value.
        double currentDistance = 0.0;
        for (PathSegment segment : segments) {
            double distance = segment.pathNormalDistance(point);
            if (distance != Double.MAX_VALUE)
                return currentDistance + distance;
            currentDistance += segment.fullNormalDistance;
        }

        int segmentIndex = 0;
        currentDistance = 0.0;
        for (SegmentEndpoint endpoint : points) {
            double distance = endpoint.pathNormalDistance(point);
            if (distance != Double.MAX_VALUE)
                return currentDistance + distance;
            if (segmentIndex < segments.size())
                currentDistance += segments.get(segmentIndex++).fullNormalDistance;
        }

        return Double.MAX_VALUE;
    }

    /** Compute an estimate of "distance" to the GeoPoint.
    * A return value of Double.MAX_VALUE should be returned for
    * points outside of the shape.
    */
    @Override
    public double computeNormalDistance(double x, double y, double z)
    {
        return computeNormalDistance(new GeoPoint(x,y,z));
    }
      
    /** Compute a squared estimate of the "distance" to the
    * GeoPoint.  Double.MAX_VALUE indicates a point outside of the
    * shape.
    */
    @Override
    public double computeSquaredNormalDistance(GeoPoint point)
    {
        double pd = computeNormalDistance(point);
        if (pd == Double.MAX_VALUE)
            return pd;
        return pd * pd;
    }
    
    /** Compute a squared estimate of the "distance" to the
    * GeoPoint.  Double.MAX_VALUE indicates a point outside of the
    * shape.
    */
    @Override
    public double computeSquaredNormalDistance(double x, double y, double z)
    {
        return computeSquaredNormalDistance(new GeoPoint(x,y,z));
    }
    
    /** Compute a linear distance to the point.
    */
    @Override
    public double computeLinearDistance(GeoPoint point)
    {
        // Algorithm:
        // (1) If the point is within any of the segments along the path, return that value.
        // (2) If the point is within any of the segment end circles along the path, return that value.
        double currentDistance = 0.0;
        for (PathSegment segment : segments) {
            double distance = segment.pathLinearDistance(point);
            if (distance != Double.MAX_VALUE)
                return currentDistance + distance;
            currentDistance += segment.fullLinearDistance;
        }
        
        int segmentIndex = 0;
        currentDistance = 0.0;
        for (SegmentEndpoint endpoint : points) {
            double distance = endpoint.pathLinearDistance(point);
            if (distance != Double.MAX_VALUE)
                return currentDistance + distance;
            if (segmentIndex < segments.size())
                currentDistance += segments.get(segmentIndex++).fullLinearDistance;
        }

        return Double.MAX_VALUE;
    }

    /** Compute a linear distance to the point.
    */
    @Override
    public double computeLinearDistance(double x, double y, double z)
    {
        return computeLinearDistance(new GeoPoint(x,y,z));
    }

    /** Compute a squared linear distance to the vector.
    */
    @Override
    public double computeSquaredLinearDistance(GeoPoint point)
    {
        double pd = computeLinearDistance(point);
        if (pd == Double.MAX_VALUE)
            return pd;
        return pd * pd;
    }

    /** Compute a squared linear distance to the vector.
    */
    @Override
    public double computeSquaredLinearDistance(double x, double y, double z)
    {
        return computeSquaredLinearDistance(new GeoPoint(x,y,z));
    }

    /** Compute a true, accurate, great-circle distance.
    * Double.MAX_VALUE indicates a point is outside of the shape.
    */
    @Override
    public double computeArcDistance(GeoPoint point)
    {
        // Algorithm:
        // (1) If the point is within any of the segments along the path, return that value.
        // (2) If the point is within any of the segment end circles along the path, return that value.
        double currentDistance = 0.0;
        for (PathSegment segment : segments) {
            double distance = segment.pathDistance(point);
            if (distance != Double.MAX_VALUE)
                return currentDistance + distance;
            currentDistance += segment.fullDistance;
        }

        int segmentIndex = 0;
        currentDistance = 0.0;
        for (SegmentEndpoint endpoint : points) {
            double distance = endpoint.pathDistance(point);
            if (distance != Double.MAX_VALUE)
                return currentDistance + distance;
            if (segmentIndex < segments.size())
                currentDistance += segments.get(segmentIndex++).fullDistance;
        }

        return Double.MAX_VALUE;
    }

    @Override
    public boolean isWithin(Vector point)
    {
        for (SegmentEndpoint pathPoint : points) {
            if (pathPoint.isWithin(point))
                return true;
        }
        for (PathSegment pathSegment : segments) {
            if (pathSegment.isWithin(point))
                return true;
        }
        return false;
    }

    @Override
    public boolean isWithin(double x, double y, double z)
    {
        for (SegmentEndpoint pathPoint : points) {
            if (pathPoint.isWithin(x,y,z))
                return true;
        }
        for (PathSegment pathSegment : segments) {
            if (pathSegment.isWithin(x,y,z))
                return true;
        }
        return false;
    }

    @Override
    public GeoPoint getInteriorPoint()
    {
        return points.get(0).point;
    }
      
    @Override
    public boolean intersects(Plane plane, Membership... bounds)
    {
        // We look for an intersection with any of the exterior edges of the path.
        // We also have to look for intersections with the cones described by the endpoints.
        // Return "true" if any such intersections are found.

        // For plane intersections, the basic idea is to come up with an equation of the line that is
        // the intersection (if any).  Then, find the intersections with the unit sphere (if any).  If
        // any of the intersection points are within the bounds, then we've detected an intersection.
        
        for (SegmentEndpoint pathPoint : points) {
            if (pathPoint.intersects(plane, bounds))
                return true;
        }
        for (PathSegment pathSegment : segments) {
            if (pathSegment.intersects(plane, bounds))
                return true;
        }

        return false;
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
        bounds = super.getBounds(bounds);
        for (SegmentEndpoint pathPoint : points) {
            pathPoint.getBounds(bounds);
        }
        for (PathSegment pathSegment : segments) {
            pathSegment.getBounds(bounds);
        }
        return bounds;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GeoPath))
            return false;
        GeoPath p = (GeoPath)o;
        if (points.size() != p.points.size())
            return false;
        if (cutoffAngle != p.cutoffAngle)
            return false;
        for (int i = 0; i < points.size(); i++) {
            SegmentEndpoint point = points.get(i);
            SegmentEndpoint point2 = p.points.get(i);
            if (!point.equals(point2))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(cutoffAngle);
        result = (int) (temp ^ (temp >>> 32));
        result = 31 * result + points.hashCode();
        return result;
    }

    /** This is precalculated data for segment endpoint.
     */
    public static class SegmentEndpoint
    {
        public final GeoPoint point;
        public final SidedPlane circlePlane;
        public final double cutoffNormalDistance;
        public final double cutoffAngle;
        public final double chordDistance;

        public SegmentEndpoint(GeoPoint point, double originDistance, double cutoffOffset, double cutoffAngle, double chordDistance)
        {
            this.point = point;
            this.cutoffNormalDistance = cutoffOffset;
            this.cutoffAngle = cutoffAngle;
            this.chordDistance = chordDistance;
            this.circlePlane = new SidedPlane(point, point, -originDistance);
        }
      
        public boolean isWithin(Vector point)
        {
            return circlePlane.isWithin(point);
        }

        public boolean isWithin(double x, double y, double z)
        {
            return circlePlane.isWithin(x,y,z);
        }
        
        public double pathDistance(GeoPoint point)
        {
            double dist = this.point.arcDistance(point);
            if (dist > cutoffAngle)
                return Double.MAX_VALUE;
            return dist;
        }
      
        public double pathNormalDistance(GeoPoint point)
        {
            double dist = this.point.normalDistance(point);
            if (dist > cutoffNormalDistance)
                return Double.MAX_VALUE;
            return dist;
        }

        public double pathLinearDistance(GeoPoint point)
        {
            double dist = this.point.linearDistance(point);
            if (dist > chordDistance)
                return Double.MAX_VALUE;
            return dist;
        }
        
        public boolean intersects(Plane p, Membership[] bounds)
        {
            return circlePlane.intersects(p, bounds);
        }

        public void getBounds(Bounds bounds)
        {
            circlePlane.recordBounds(bounds);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SegmentEndpoint))
                return false;
            SegmentEndpoint other = (SegmentEndpoint)o;
            return point.equals(other.point);
        }

        @Override
        public int hashCode() {
            return point.hashCode();
        }
    }
    
    /** This is the precalculated data for a path segment.
     */
    public static class PathSegment
    {
        public final GeoPoint start;
        public final GeoPoint end;
        public final double fullDistance;
        public final double fullNormalDistance;
        public final double fullLinearDistance;
        public final Plane normalizedConnectingPlane;
        public final SidedPlane upperConnectingPlane;
        public final SidedPlane lowerConnectingPlane;
        public final SidedPlane startCutoffPlane;
        public final SidedPlane endCutoffPlane;
        public final double planeBoundingOffset;
        public final double arcWidth;
        public final double chordDistance;
        
        public PathSegment(GeoPoint start, GeoPoint end, double planeBoundingOffset, double arcWidth, double chordDistance)
        {
            this.start = start;
            this.end = end;
            this.planeBoundingOffset = planeBoundingOffset;
            this.arcWidth = arcWidth;
            this.chordDistance = chordDistance;
            
            fullDistance = start.arcDistance(end);
            fullNormalDistance = start.normalDistance(end);
            fullLinearDistance = start.linearDistance(end);
            normalizedConnectingPlane = new Plane(start,end).normalize();
            if (normalizedConnectingPlane == null) {
                upperConnectingPlane = null;
                lowerConnectingPlane = null;
                startCutoffPlane = null;
                endCutoffPlane = null;
            } else {
                // Either start or end should be on the correct side
                upperConnectingPlane = new SidedPlane(start,normalizedConnectingPlane,-planeBoundingOffset);
                lowerConnectingPlane = new SidedPlane(start,normalizedConnectingPlane,planeBoundingOffset);
                // Cutoff planes use opposite endpoints as correct side examples
                startCutoffPlane = new SidedPlane(end,normalizedConnectingPlane,start);
                endCutoffPlane = new SidedPlane(start,normalizedConnectingPlane,end);
            }
        }

        public boolean isDegenerate()
        {
            return normalizedConnectingPlane == null;
        }

        public boolean isWithin(Vector point)
        {
            return startCutoffPlane.isWithin(point) &&
              endCutoffPlane.isWithin(point) &&
              upperConnectingPlane.isWithin(point) &&
              lowerConnectingPlane.isWithin(point);
        }

        public boolean isWithin(double x, double y, double z)
        {
            return startCutoffPlane.isWithin(x,y,z) &&
              endCutoffPlane.isWithin(x,y,z) &&
              upperConnectingPlane.isWithin(x,y,z) &&
              lowerConnectingPlane.isWithin(x,y,z);
        }
        
        public double pathDistance(GeoPoint point)
        {
            if (!isWithin(point))
                return Double.MAX_VALUE;

            // Compute the distance, filling in both components.
            double perpDistance = Math.PI * 0.5 - Tools.safeAcos(Math.abs(normalizedConnectingPlane.evaluate(point)));
            Plane normalizedPerpPlane = new Plane(normalizedConnectingPlane,point).normalize();
            double pathDistance = Math.PI * 0.5 - Tools.safeAcos(Math.abs(normalizedPerpPlane.evaluate(start)));
            return perpDistance + pathDistance;
        }
        
        public double pathNormalDistance(GeoPoint point)
        {
            if (!isWithin(point))
                return Double.MAX_VALUE;

            double pointEval = Math.abs(normalizedConnectingPlane.evaluate(point));

            // Want no allocations or expensive operations!  so we do this the hard way
            double perpX = normalizedConnectingPlane.y * point.z - normalizedConnectingPlane.z * point.y;
            double perpY = normalizedConnectingPlane.z * point.x - normalizedConnectingPlane.x * point.z;
            double perpZ = normalizedConnectingPlane.x * point.y - normalizedConnectingPlane.y * point.x;

            // If we have a degenerate line, then just compute the normal distance from point x to the start
            if (perpX < 1e-10 && perpY < 1e-10 && perpZ < 1e-10)
              return point.normalDistance(start);

            double normFactor = 1.0 / Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
            double perpEval = Math.abs(perpX * start.x + perpY * start.y + perpZ * start.z);
            return perpEval * normFactor + pointEval;
        }

        public double pathLinearDistance(GeoPoint point)
        {
            if (!isWithin(point))
                return Double.MAX_VALUE;

            // We have a normalized connecting plane.
            // First, compute the perpendicular plane.
            // Want no allocations or expensive operations!  so we do this the hard way
            double perpX = normalizedConnectingPlane.y * point.z - normalizedConnectingPlane.z * point.y;
            double perpY = normalizedConnectingPlane.z * point.x - normalizedConnectingPlane.x * point.z;
            double perpZ = normalizedConnectingPlane.x * point.y - normalizedConnectingPlane.y * point.x;

            // If we have a degenerate line, then just compute the normal distance from point x to the start
            if (Math.abs(perpX) < 1e-10 && Math.abs(perpY) < 1e-10 && Math.abs(perpZ) < 1e-10)
                return point.linearDistance(start);

            // Next, we need the vector of the line, which is the cross product of the normalized connecting plane
            // and the perpendicular plane that we just calculated.
            double lineX = normalizedConnectingPlane.y * perpZ - normalizedConnectingPlane.z * perpY;
            double lineY = normalizedConnectingPlane.z * perpX - normalizedConnectingPlane.x * perpZ;
            double lineZ = normalizedConnectingPlane.x * perpY - normalizedConnectingPlane.y * perpX;
            
            // Now, compute a normalization factor
            double normalizer = 1.0/Math.sqrt(lineX * lineX + lineY * lineY + lineZ * lineZ);
            
            // Pick which point by using bounding planes
            double normLineX = lineX * normalizer;
            double normLineY = lineY * normalizer;
            double normLineZ = lineZ * normalizer;
            if (!startCutoffPlane.isWithin(normLineX,normLineY,normLineZ) ||
              !endCutoffPlane.isWithin(normLineX,normLineY,normLineZ))
            {
                normLineX = -normLineX;
                normLineY = -normLineY;
                normLineZ = -normLineZ;
            }
            
            // Compute linear distance for the two points
            return point.linearDistance(normLineX,normLineY,normLineZ) + start.linearDistance(normLineX,normLineY,normLineZ);
        }
        
        public boolean intersects(Plane p, Membership[] bounds)
        {
            return upperConnectingPlane.intersects(p, bounds, lowerConnectingPlane, startCutoffPlane, endCutoffPlane) ||
                lowerConnectingPlane.intersects(p, bounds, upperConnectingPlane, startCutoffPlane, endCutoffPlane);
        }

        public void getBounds(Bounds bounds)
        {
            // We need to do all bounding planes as well as corner points
            upperConnectingPlane.recordBounds(startCutoffPlane, bounds, lowerConnectingPlane, endCutoffPlane);
            startCutoffPlane.recordBounds(lowerConnectingPlane, bounds, endCutoffPlane, upperConnectingPlane);
            lowerConnectingPlane.recordBounds(endCutoffPlane, bounds, upperConnectingPlane, startCutoffPlane);
            endCutoffPlane.recordBounds(upperConnectingPlane, bounds, startCutoffPlane, lowerConnectingPlane);
            upperConnectingPlane.recordBounds(bounds, lowerConnectingPlane, startCutoffPlane, endCutoffPlane);
            lowerConnectingPlane.recordBounds(bounds, upperConnectingPlane, startCutoffPlane, endCutoffPlane);
            startCutoffPlane.recordBounds(bounds, endCutoffPlane, upperConnectingPlane, lowerConnectingPlane);
            endCutoffPlane.recordBounds(bounds, startCutoffPlane, upperConnectingPlane, lowerConnectingPlane);
        }
        
    }

}
