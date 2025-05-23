/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PolyBase.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.database.geometry;

import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.PortOriginal;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.EditWindow0;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.technology.Layer;
import com.sun.electric.tool.Job;
import com.sun.electric.util.math.AbstractFixpPoint;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.ECoord;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

/**
 * The Poly class describes an extended set of points
 * that can be outlines, filled shapes, curves, text, and more.
 * The Poly also contains a Layer and some connectivity information.
 */
public class PolyBase implements Shape, PolyNodeMerge {
	private static final boolean DEBUGMERGE = false;
	private static final boolean DEBUGCOINCIDENT = false;
	private static final boolean DEBUGCOLLINEAR = false;
	private static final boolean DEBUGMANHATTAN = false;
    private static final boolean ALLOWTINYPOLYGONS = true;
    /** the style (outline, text, lines, etc.) */
    private Poly.Type style;
    /** the points */
    protected Point points[];
	protected Line2D lines[];
    /** the layer (used for graphics) */
    private Layer layer;
    /** the bounds of the points */
    protected FixpRectangle bounds;
    /** the PortProto (if from a node or TEXT) */
    private PortProto pp;
    /** the bit saying if the polygon is perfect rectangle */
    private char bitRectangle = 2;
    /** 2 not calculated, 0 not a rectangle, 1 a rectangle */
    /** represents X axis */
    public static final int X = 0;
    /** represents Y axis */
    public static final int Y = 1;
    /** represents Z axis */
    public static final int Z = 2;
    /** represents on the plane XY */
    public static final int XY = 4;

    public static class Point extends AbstractFixpPoint {
        private long fixpX;
        private long fixpY;

        /**
         * Constructs and initializes a <code>Point</code> with the
         * specified coordinates.
         *
         * @param fixpX the X coordinate of the newly constructed <code>Point</code>
         * @param fixpY the Y coordinate of the newly constructed <code>Point</code>
         */
        private Point(long fixpX, long fixpY) {
            this.fixpX = fixpX;
            this.fixpY = fixpY;
        }

        
        @Override
        public long getFixpX() {
            return fixpX;
        }
    
        @Override
        public long getFixpY() {
            return fixpY;
        }
    
        @Override
        public void setFixpLocation(long fixpX, long fixpY) {
            this.fixpX = fixpX;
            this.fixpY = fixpY;
        }
    
        @Override
        protected AbstractFixpPoint create(long fixpX, long fixpY) {
            return new Point(fixpX, fixpY);
        }
    }

    /**
     * Constructs and initializes a <code>PolyBase.Point</code> with the
     * specified <code>Point2D</code>.
     *
     * @param p coordinates of the newly constructed <code>Point</code>
     */
    public static Point from(Point2D p) {
        if (p instanceof AbstractFixpPoint) {
            AbstractFixpPoint fp = (AbstractFixpPoint)p;
            return fromFixp(fp.getFixpX(), fp.getFixpY());
        }
        return fromLambda(p.getX(), p.getY());
    }

    /**
     * Constructs and initializes a <code>PolyBase.Point</code> with the
     * specified coordinates.
     *
     * @param x the X coordinate of the newly constructed <code>Point</code>
     * @param y the Y coordinate of the newly constructed <code>Point</code>
     */
    public static Point from(ECoord x, ECoord y) {
        return new Point(x.getFixp(), y.getFixp());
    }

    /**
     * Constructs and initializes a <code>PolyBase.Point</code> with the
     * specified lambda coordinates.
     *
     * @param lambdaX the X coordinate of the newly constructed <code>Point</code>
     * @param lambdaY the Y coordinate of the newly constructed <code>Point</code>
     */
    public static Point fromLambda(double lambdaX, double lambdaY) {
        return new Point(FixpCoord.lambdaToFixp(lambdaX), FixpCoord.lambdaToFixp(lambdaY));
    }

    /**
     * Constructs and initializes a <code>PolyBase.Point</code> with the
     * specified fixed-point long coordinates.
     *
     * @param fixpX the X coordinate of the newly constructed <code>Point</code>
     * @param fixpY the Y coordinate of the newly constructed <code>Point</code>
     */
    public static Point fromFixp(long fixpX, long fixpY) {
        return new Point(fixpX, fixpY);
    }

    /**
     * Constructs and initializes a <code>PolyBase.Point</code> with the
     * specified grid coordinates.
     *
     * @param gridX the X coordinate of the newly constructed <code>Point</code>
     * @param gridY the Y coordinate of the newly constructed <code>Point</code>
     */
    public static Point fromGrid(long gridX, long gridY) {
        return new Point(gridX << FixpCoord.FRACTION_BITS, gridY << FixpCoord.FRACTION_BITS);
    }

    /**
     * The constructor creates a new Poly given an array of points.
     * @param points the array of coordinates.
     */
    public PolyBase(Point... points) {
        initialize(points);
    }

    /**
     * The constructor creates a new Poly that describes a rectangle.
     * @param cX the center X coordinate of the rectangle.
     * @param cY the center Y coordinate of the rectangle.
     * @param width the width of the rectangle.
     * @param height the height of the rectangle.
     */
    public PolyBase(double cX, double cY, double width, double height) {
        double halfWidth = width / 2;
        double halfHeight = height / 2;
        initialize(makePoints(cX - halfWidth, cX + halfWidth, cY - halfHeight, cY + halfHeight));
    }

    /**
     * The constructor creates a new Poly that describes a rectangle.
     * @param rect the Rectangle2D of the rectangle.
     */
    public PolyBase(Rectangle2D rect) {
        initialize(makePoints(rect));
    }

    /**
     * The constructor creates a new Poly that clones an existing Poly
     * @param rect the Rectangle2D of the rectangle.
     */
    public PolyBase(PolyBase poly) {
        List<Point> list = new ArrayList<Point>();
		for (Point point : poly.points) list.add(from(point));
		this.style  = poly.style;
		this.points = list.toArray(new Point[list.size()]);
		this.layer  = poly.layer;
		this.pp     = poly.pp;
    }

    /**
     * Method to create an array of Points that describes a Rectangle.
     * @param lX the low X coordinate of the rectangle.
     * @param hX the high X coordinate of the rectangle.
     * @param lY the low Y coordinate of the rectangle.
     * @param hY the high Y coordinate of the rectangle.
     * @return an array of 4 Points that describes the Rectangle.
     */
    public static Point[] makePoints(double lX, double hX, double lY, double hY) {
        return new Point[]{
                    fromLambda(lX, lY),
                    fromLambda(hX, lY),
                    fromLambda(hX, hY),
                    fromLambda(lX, hY)};
    }

    /**
     * Method to create an array of Points that describes a Rectangle.
     * @param rect the Rectangle.
     * @return an array of 4 Points that describes the Rectangle.
     */
    public static Point[] makePoints(Rectangle2D rect) {
        double lX = rect.getMinX();
        double hX = rect.getMaxX();
        double lY = rect.getMinY();
        double hY = rect.getMaxY();
        return new Point[]{
                    fromLambda(lX, lY),
                    fromLambda(hX, lY),
                    fromLambda(hX, hY),
                    fromLambda(lX, hY)};
    }

    /**
     * Method to help initialize this Poly.
     */
    private void initialize(Point[] points) {
		for (Point p : points) assert(p != null);
        this.style = Poly.Type.CLOSED;
        this.points = points;
		this.lines = null;
        this.layer = null;
        this.bounds = null;
        this.pp = null;
    }

    /**
     * Convert to useful string
     * @return a string describing this object
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (layer != null) {
            buf.append(layer.getName() + ": ");
        }
        for (Point2D p : points) {
            buf.append("(" + p.getX() + ", " + p.getY() + "), ");
        }
        if (style != null) {
            buf.append(style.toString());
        }
        return buf.toString();
    }

    /**
     * Method to return the style associated with this Poly.
     * The style controls how the points are interpreted (FILLED, CIRCLE, etc.)
     * @return the style associated with this Poly.
     */
    public Poly.Type getStyle() {
        return style;
    }

    /**
     * Method to set the style associated with this Poly.
     * The style controls how the points are interpreted (FILLED, CIRCLE, etc.)
     * @param style the style associated with this Poly.
     */
    public void setStyle(Poly.Type style) {
        this.style = style;
    }

    /**
     * Method to return the points associated with this Poly.
     * @return the points associated with this Poly.
     */
    public Point[] getPoints() {
        return points;
    }

	public Line2D[] getLines() {
		if (lines != null) return lines;
		List<Line2D> pLs = new ArrayList<Line2D>();
		PolyPathIterator pIt = getEdgeIterator();
		while (!pIt.isDone()) getLinesFromEdge(pIt, pLs);
		lines = pLs.toArray(new Line2D.Double[pLs.size()]);
		return lines;
	}


    /**
     * Method to return the layer associated with this Poly.
     * @return the layer associated with this Poly.
     */
    public Layer getLayer() {
        return layer;
    }

    /**
     * Method to return the layer or pseudo-layer associated with this Poly.
     * @return the layer or pseudo-layer associated with this Poly.
     */
    public Layer getLayerOrPseudoLayer() {
        return layer;
    }

    /**
     * Method to set the layer associated with this Poly.
     * @param layer the layer associated with this Poly.
     */
    public void setLayer(Layer layer) {
        this.layer = layer;
    }

    /**
     * Method to return the PortProto associated with this Poly.
     * This applies to ports on Nodes and Exports on Cells.
     * @return the PortProto associated with this Poly.
     */
    public PortProto getPort() {
        return pp;
    }

    /**
     * Method to set the PortProto associated with this Poly.
     * This applies to ports on Nodes and Exports on Cells.
     * @param pp the PortProto associated with this Poly.
     */
    public void setPort(PortProto pp) {
        this.pp = pp;
    }

    /**
     * Method to transformed the points in this Poly.
     * @param af transformation to apply.
     */
    public void transform(FixpTransform af) {
        // Nothing to do
        if (af.getType() == FixpTransform.TYPE_IDENTITY) {
            return;
        }

        // special case for Poly type CIRCLEARC and THICKCIRCLEARC: if transposing, reverse points
        if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC) {
            double det = af.getDeterminant();
            if (det < 0) {
                for (int i = 0; i < points.length; i += 3) {
                    double x = points[i + 1].getX();
                    double y = points[i + 1].getY();
                    points[i + 1].setLocation(points[i + 2].getX(), points[i + 2].getY());
                    points[i + 2].setLocation(x, y);
                }
            }
        }
        af.transform(points, 0, points, 0, points.length);
        bounds = null;
        bitRectangle = 2;  // Not calculated
    }

    /**
     * Method to return a Rectangle that describes the orthogonal box in this Poly.
     * @return the Rectangle that describes this Poly.
     * If the Poly is not an orthogonal box, returns null.
     * IT IS NOT PERMITTED TO MODIFY THE RETURNED RECTANGLE
     * (because it is taken from the internal bounds of the Poly).
     */
    public FixpRectangle getBox() {
        if (bitRectangle == 1) {
            return getBounds2D();
        } else if (bitRectangle == 0) {
            return null;
        }

        bitRectangle = 0;
        if (points.length == 4) {
            // only closed polygons and text can be boxes
            if (style != Poly.Type.FILLED && style != Poly.Type.CLOSED && style != Poly.Type.TEXTBOX && style != Poly.Type.CROSSED) {
                return null;
            }
			// make sure the polygon is rectangular and orthogonal
			if (isHorizontalBox(points[0], points[1], points[2], points[3])) {
				bitRectangle = 1;
				return getBounds2D();
			}
			if (isVerticalBox(points[0], points[1], points[2], points[3])) {
				bitRectangle = 1;
				return getBounds2D();
			}
        } else if (points.length >= 5) {
			// only polygons can be boxes
            if (style != Poly.Type.FILLED && style != Poly.Type.CLOSED
                    && style != Poly.Type.OPENED && style != Poly.Type.OPENEDT1
                    && style != Poly.Type.OPENEDT2 && style != Poly.Type.OPENEDT3) {
                return null;
            }
			// check if computed area is equal to bounding box
			double testArea = GenMath.getArea(getBounds2D());
			double realArea = GenMath.getAreaOfPoints(points);
			if (DBMath.areEquals(testArea, realArea)) {
				bitRectangle = 1;
				return getBounds2D();
			}
		} else {
            return null;
        }

        return null;
    }

    /**
     * Method to compute the minimum size of this Polygon.
     * Only works with manhattan geometry.
     * @return the minimum dimension.
     */
    public double getMinSize() {
        Rectangle2D box = getBox();
        if (box == null) {
            return 0;
        }
        return Math.min(box.getWidth(), box.getHeight());
    }

    /**
     * Method to compute the maximum size of this Polygon.
     * Only works with manhattan geometry.
     */
    public double getMaxSize() {
        Rectangle2D box = getBox();
        if (box == null) {
            return 0;
        }
        return Math.max(box.getWidth(), box.getHeight());
    }

    /**
     * Method to compare this Poly to another.
     * @param polyOther the other Poly to compare.
     * @return true if the Polys are the same.
     */
    public boolean polySame(PolyBase polyOther) {
        // polygons must have the same number of points
        Point2D[] points = getPoints();
        Point2D[] pointsO = polyOther.getPoints();
        if (points.length != pointsO.length) {
            return false;
        }

        // if both are boxes, compare their extents
        Rectangle2D box = getBox();
        Rectangle2D boxO = polyOther.getBox();
        if (box != null && boxO != null) {
            // compare box extents
            return box.equals(boxO);
        }
        if (box != null || boxO != null) {
            return false;
        }

        // compare these boxes the hard way
        for (int i = 0; i < points.length; i++) {
            if (!points[i].equals(pointsO[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method to tell whether a coordinate is inside of this Poly.
     * The algorithm relies on the Java class Area. Very slow.
     * @param pt the point in question.
     * @return true if the point is inside of this Poly.
     */
//    public boolean isPointInsideArea(Point2D pt)
//    {
//        Area area = new Area(this);
//        return area.contains(pt.getX(), pt.getY());
//    }
    /**
     * Method to determine if a point is inside a polygon. The method is based on counting
     * how many time an imaginary line cuts the polygon in one direction.
     * @param pt
     * @return true if point is inside polygon.
     */
    private boolean isPointInsideCutAlgorithm(Point2D pt) {
        // general polygon containment by counting reference line intersections
        Point2D lastPoint = points[points.length - 1];
        //if (pt.equals(lastPoint)) return true;
        if (DBMath.areEquals(pt, lastPoint)) {
            return true;
        }
        Rectangle2D box = getBounds2D();

        // The point is outside the bounding box of the polygon
        if (!DBMath.pointInRect(pt, box)) //pointInsideRect. It could be at the edges
        {
            return false;
        }

        int count = 0;

        for (Point2D thisPoint : points) {
            if (DBMath.areEquals(pt, thisPoint)) {
                return true;
            }

            // Checking if point is along polygon edge
            if (DBMath.isOnLine(thisPoint, lastPoint, pt)) {
                return true;
            }

            double ptY = pt.getY();
            double lastY = lastPoint.getY();
            double thisY = thisPoint.getY();

            // not counting if the horizontal line passes through the point
            boolean skip = DBMath.areEquals(ptY, thisY) && DBMath.areEquals(ptY, lastY);

            if (!skip) {
                boolean thisPointGreaterY = DBMath.isGreaterThan(thisY, ptY);
                boolean lastPointGreaterY = DBMath.isGreaterThan(lastY, ptY);

                // doesn't intersect the horizontal line
                // pt[y] < s[Y] && pt[y] < e[Y] || pt[Y] > s[Y] && pt[Y] > e[Y]
                skip = (thisPointGreaterY && lastPointGreaterY)
                        || (DBMath.isGreaterThan(ptY, thisY)
                        && DBMath.isGreaterThan(ptY, lastY));

                if (!skip) {
                    // only counting half of the domain
                    // at least one must be greater than pt[Y] and pt[X]
                    // both X must be greater because it already checked if point is on the line.
                    double ptX = pt.getX();
                    // not a vertical line. Horizontal lines won't make it to this point
                    if (!DBMath.areEquals(thisY, lastY)) {
                        ptX = thisPoint.getX() + (ptY - thisY) * (lastPoint.getX() - thisPoint.getX()) / (lastY - thisY);
                    }
                    // point must be at the right side of the point. Not checking if they are identical because
                    // that was tested above.
                    boolean pointGreaterX = DBMath.isGreaterThan(ptX, pt.getX());
                    if ((thisPointGreaterY || lastPointGreaterY) && pointGreaterX) {
                        count++;
                    }
                }
            }

            lastPoint = thisPoint;
        }
        boolean inside = (count != 0 && count % 2 != 0);
        return (inside);
    }

    /**
     * Method to tell whether a coordinate is inside of this Poly.
     * This algorithm is based on angles. If the angle is 360 then the point is inside
     * @param pt the point in question.
     * @return true if the point is inside of this Poly.
     */
    public boolean isInside(Point2D pt) {
        if (style == Poly.Type.FILLED || style == Poly.Type.CLOSED || style == Poly.Type.CROSSED || style.isText()) {
            // If point is not in 2D bounding box -> is outside anyway
            Rectangle2D bounds2D = this.getBounds2D();
            if (!DBMath.pointInRect(pt, bounds2D)) {
                return false;
            }

            // check rectangular case for containment
            Rectangle2D bounds = getBox();
            if (bounds != null) {
                if (DBMath.pointInRect(pt, bounds)) {
                    return true;
                }
                // special case: single point, take care of double precision error
                if (bounds.getWidth() == 0 && bounds.getHeight() == 0) {
                    if (DBMath.areEquals(pt.getX(), bounds.getX())
                            && DBMath.areEquals(pt.getY(), bounds.getY())) {
                        return true;
                    }
                }
                return false;
            }

            boolean method = isPointInsideCutAlgorithm(pt);
            return method;
        }

        if (style == Poly.Type.CROSS || style == Poly.Type.BIGCROSS) {
            if (DBMath.areEquals(getCenterX(), pt.getX()) && DBMath.areEquals(getCenterY(), pt.getY())) {
                return true;
            }
            return false;
        }

        if (style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1 || style == Poly.Type.OPENEDT2
                || style == Poly.Type.OPENEDT3 || style == Poly.Type.VECTORS) {
            for (Point2D point : points) {
                if (DBMath.areEquals(pt, point)) {
                    return true;
                }
            }

            // see if the point is on one of the edges
            if (style == Poly.Type.VECTORS) {
                for (int i = 0; i < points.length; i += 2) {
                    if (DBMath.isOnLine(points[i], points[i + 1], pt)) {
                        return true;
                    }
                }
            } else {
                for (int i = 1; i < points.length; i++) {
                    if (DBMath.isOnLine(points[i - 1], points[i], pt)) {
                        return true;
                    }
                }
            }
            return false;
        }

        if (style == Poly.Type.CIRCLE || style == Poly.Type.THICKCIRCLE || style == Poly.Type.DISC) {
            double dist = points[0].distance(points[1]);
            double odist = points[0].distance(pt);
            if (odist < dist) {
                return true;
            }
            return false;
        }

        if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC) {
            // first see if the point is at the proper angle from the center of the arc
            int ang = DBMath.figureAngle(points[0], pt);
            int endangle = DBMath.figureAngle(points[0], points[1]);
            int startangle = DBMath.figureAngle(points[0], points[2]);
            double angrange;
            if (endangle > startangle) {
                if (ang < startangle || ang > endangle) {
                    return false;
                }
                angrange = endangle - startangle;
            } else {
                if (ang < startangle && ang > endangle) {
                    return false;
                }
                angrange = 3600 - startangle + endangle;
            }

            // now see if the point is the proper distance from the center of the arc
            double dist = points[0].distance(pt);
            double wantdist;
            if (ang == startangle || angrange == 0) {
                wantdist = points[0].distance(points[1]);
            } else if (ang == endangle) {
                wantdist = points[0].distance(points[2]);
            } else {
                double startdist = points[0].distance(points[1]);
                double enddist = points[0].distance(points[2]);
                if (enddist == startdist) {
                    wantdist = startdist;
                } else {
                    wantdist = startdist + (ang - startangle) / angrange
                            * (enddist - startdist);
                }
            }
            //if (dist == wantdist) return true;
            if (DBMath.areEquals(dist, wantdist)) {
                return true;
            }
            return false;
        }

        // I give up
        return false;
    }

    /**
     * Method to tell whether a coordinates of this Poly are inside of a Rectangle2D.
     * @param bounds the Rectangle2D in question.
     * @return true if this Poly is completely inside of the bounds.
     */
    public boolean isInside(Rectangle2D bounds) {
        if (style == Poly.Type.CIRCLE || style == Poly.Type.THICKCIRCLE || style == Poly.Type.DISC) {
            Point2D ctr = points[0];
            double dx = Math.abs(ctr.getX() - points[1].getX());
            double dy = Math.abs(ctr.getY() - points[1].getY());
            double rad = Math.max(dx, dy);
            if (!DBMath.pointInRect(new Point2D.Double(ctr.getX() + rad, ctr.getY() + rad), bounds)) {
                return false;
            }
            if (!DBMath.pointInRect(new Point2D.Double(ctr.getX() - rad, ctr.getY() - rad), bounds)) {
                return false;
            }
            return true;
        }
        for (Point2D p : points) {
            if (!DBMath.pointInRect(p, bounds)) {
                return false;
            }
        }
        return true;
    }

    /** Method to check if point is part of the point set that defines
     * the polygon
     * @param point
     * @return true if found in points set
     */
    public boolean isPointOnCorner(Point2D point) {
        for (Point2D p : points) {
            if (DBMath.areEquals(point, p)) {
                return (true);
            }
        }
        return (false);
    }

    /**
     * Method to reduce this Poly by the proper amount presuming that it describes a port connected to an arc.
     * This Poly is modified in place to reduce its size.
     * @param pi the PortInst that describes this Poly.
     * @param wid the width of the arc connected to this port-poly.
     * This should be the base width, not the actual width stored in memory.
     * @param angle the angle of the arc connected to this port-poly.
     * If negative, do not consider arc angle.
     */
    public void reducePortPoly(PortInst pi, double wid, int angle) {
        // look down to the bottom level node/port
        PortOriginal fp = new PortOriginal(pi);
        NodeInst ni = fp.getBottomNodeInst();

        // do not reduce port if not filled
        if (getStyle() != Poly.Type.FILLED && getStyle() != Poly.Type.CROSSED
                && getStyle() != Poly.Type.DISC) {
            return;
        }

        // do not reduce port areas on polygonally defined nodes
        if (ni.getTrace() != null) {
            return;
        }

        // determine amount to reduce port
        double realWid = wid / 2;

        // get bounding box of port polygon
        Rectangle2D portBounds = getBox();
        if (portBounds == null) {
            // special case: nonrectangular port
            if (getStyle() == Poly.Type.DISC) {
                // shrink discs
                double dist = points[0].distance(points[1]);
                dist = Math.max(0, dist - realWid);
                points[1].setLocation(points[0].getX() + dist, points[0].getY());
                return;
            }

            // cannot handle other forms of polygon yet
            return;
        }

        // determine the edge and center of the port polygon
        double bx = portBounds.getMinX();
        double ux = portBounds.getMaxX();
        double by = portBounds.getMinY();
        double uy = portBounds.getMaxY();

        // compute the area of the nodeinst
        Rectangle2D r = ni.getBaseShape().getBounds2D();
        double lx = r.getMinX();
        double hx = r.getMaxX();
        double ly = r.getMinY();
        double hy = r.getMaxY();

        // do not reduce in X if arc is horizontal
        if (angle != -1 && angle != 0 && angle != 1800) {
            // determine reduced port area
            lx = Math.max(bx, lx + realWid);
            hx = Math.min(ux, hx - realWid);
            if (hx < lx) {
                hx = lx = (hx + lx) / 2;
            }

            // only clip in X if the port area is within of the reduced node X area
            if (ux >= lx && bx <= hx) {
                for (Point2D point : points) {
                    double x = point.getX();
                    if (x < lx) {
                        x = lx;
                    }
                    if (x > hx) {
                        x = hx;
                    }
                    point.setLocation(x, point.getY());
                }
            }
        }

        // do not reduce in Y if arc is vertical
        if (angle != -1 && angle != 900 && angle != 2700) {
            // determine reduced port area
            ly = Math.max(by, ly + realWid);
            hy = Math.min(uy, hy - realWid);
            if (hy < ly) {
                hy = ly = (hy + ly) / 2;
            }

            // only clip in Y if the port area is inside of the reduced node Y area
            if (uy >= ly && by <= hy) {
                for (Point2D point : points) {
                    double y = point.getY();
                    if (y < ly) {
                        y = ly;
                    }
                    if (y > hy) {
                        y = hy;
                    }
                    point.setLocation(point.getX(), y);
                }
            }
        }
    }

    /**
     * Method to rotate a text Type according to the rotation of the object on which it resides.
     * @param origType the original text Type.
     * @param eObj the ElectricObject on which the text resides.
     * @return the new text Type that accounts for the rotation.
     */
    public static Poly.Type rotateType(Poly.Type origType, ElectricObject eObj) {
        if (Poly.NEWTEXTTREATMENT) {
            return origType;
        }
        // centered text does not rotate its anchor
        if (origType == Poly.Type.TEXTCENT || origType == Poly.Type.TEXTBOX) {
            return origType;
        }

        // get node this sits on
        NodeInst ni;
        if (eObj instanceof NodeInst) {
            ni = (NodeInst) eObj;
        } else if (eObj instanceof Export) {
            Export pp = (Export) eObj;
            ni = pp.getOriginalPort().getNodeInst();
        } else {
            return origType;
        }

        // no need to rotate anchor if the node is not transformed
        int nodeAngle = ni.getAngle();
        if (nodeAngle == 0 && !ni.isMirroredAboutXAxis() && !ni.isMirroredAboutYAxis()) {
            return origType;
        }

        // can only rotate anchor when node is in a manhattan orientation
        if ((nodeAngle % 900) != 0) {
            return origType;
        }

        // special case handling for left/right/top/bottom orientations
        if (origType == Poly.Type.TEXTLEFT || origType == Poly.Type.TEXTRIGHT
                || origType == Poly.Type.TEXTTOP || origType == Poly.Type.TEXTBOT) {
            boolean flipAnchor = false;
            if (ni.isMirroredAboutXAxis()) {
                if (ni.isMirroredAboutYAxis()) {
                    // mirrored in both directions: flip only the 0-degree anchor
                    if (nodeAngle == 0) {
                        flipAnchor = true;
                    }
                } else {
                    // mirrored only U-D: flip conditionally
                    if (origType == Poly.Type.TEXTLEFT || origType == Poly.Type.TEXTRIGHT) {
                        if (nodeAngle == 900 || nodeAngle == 1800) {
                            flipAnchor = true;
                        }
                    } else if (origType == Poly.Type.TEXTTOP || origType == Poly.Type.TEXTBOT) {
                        if (nodeAngle == 0 || nodeAngle == 2700) {
                            flipAnchor = true;
                        }
                    }
                }
            } else {
                if (ni.isMirroredAboutYAxis()) {
                    // mirrored only L-R: flip  conditionally
                    if (origType == Poly.Type.TEXTLEFT || origType == Poly.Type.TEXTRIGHT) {
                        if (nodeAngle == 0 || nodeAngle == 2700) {
                            flipAnchor = true;
                        }
                    } else if (origType == Poly.Type.TEXTTOP || origType == Poly.Type.TEXTBOT) {
                        if (nodeAngle == 900 || nodeAngle == 1800) {
                            flipAnchor = true;
                        }
                    }
                } else {
                    // not mirrored: flip only 180-degree anchor
                    if (nodeAngle == 1800) {
                        flipAnchor = true;
                    }
                }
            }
            if (flipAnchor) {
                if (origType == Poly.Type.TEXTLEFT) {
                    return Poly.Type.TEXTRIGHT;
                }
                if (origType == Poly.Type.TEXTRIGHT) {
                    return Poly.Type.TEXTLEFT;
                }
                if (origType == Poly.Type.TEXTTOP) {
                    return Poly.Type.TEXTBOT;
                }
                if (origType == Poly.Type.TEXTBOT) {
                    return Poly.Type.TEXTTOP;
                }
            }
            return origType;
        }

        // determine angle of original style
        int origAngle = origType.getTextAngle();
        if (ni.isMirroredAboutXAxis() != ni.isMirroredAboutYAxis() && ((origAngle % 1800) == 0 || (origAngle % 1800) == 1350)) {
            origAngle += 1800;
        }

        // determine change in angle because of node rotation
        Orientation orient = Orientation.fromJava(nodeAngle, ni.isMirroredAboutXAxis(), ni.isMirroredAboutYAxis());
        FixpTransform trans = orient.pureRotate();
        Point2D pt = new Point2D.Double(100, 0);
        trans.transform(pt, pt);
        int xAngle = GenMath.figureAngle(new Point2D.Double(0, 0), pt);
        if (ni.isMirroredAboutXAxis() != ni.isMirroredAboutYAxis() && ((origAngle % 1800) == 450)) {
            xAngle += 900;
        }

        // determine new angle and style
        int angle = (origAngle + xAngle) % 3600;
        Poly.Type style = Poly.Type.getTextTypeFromAngle(angle);
        return style;
    }

    /**
     * Method to unrotate a text Type according to the rotation of the object on which it resides.
     * Unrotation implies converting apparent anchor information to actual stored anchor information
     * on a transformed node.  For example, if the node is rotated, and the anchor appears to be at the
     * bottom, then the actual anchor that is stored with the node will be different (and when transformed
     * will appear to be at the bottom).
     * @param origType the original text Type.
     * @param eObj the ElectricObject on which the text resides.
     * @return the new text Type that accounts for the rotation.
     */
    public static Poly.Type unRotateType(Poly.Type origType, ElectricObject eObj) {
        if (Poly.NEWTEXTTREATMENT) {
            return origType;
        }
        // centered text does not rotate its anchor
        if (origType == Poly.Type.TEXTCENT || origType == Poly.Type.TEXTBOX) {
            return origType;
        }

        // get node this sits on
        NodeInst ni;
        if (eObj instanceof NodeInst) {
            ni = (NodeInst) eObj;
        } else if (eObj instanceof Export) {
            Export pp = (Export) eObj;
            ni = pp.getOriginalPort().getNodeInst();
        } else {
            return origType;
        }

        // no need to rotate anchor if the node is not transformed
        int nodeAngle = ni.getAngle();
        if (nodeAngle == 0 && !ni.isMirroredAboutXAxis() && !ni.isMirroredAboutYAxis()) {
            return origType;
        }

        // can only rotate anchor when node is in a manhattan orientation
        if ((nodeAngle % 900) != 0) {
            return origType;
        }

        // rotate the anchor
        int angle = origType.getTextAngle();

        int rotAngle = ni.getAngle();
        if (ni.isMirroredAboutXAxis() != ni.isMirroredAboutYAxis()) {
            rotAngle = -rotAngle;
        }
        Orientation orient = Orientation.fromJava(rotAngle, ni.isMirroredAboutXAxis(), ni.isMirroredAboutYAxis());
        FixpTransform trans = orient.pureRotate();

        Point2D pt = new Point2D.Double(100, 0);
        trans.transform(pt, pt);
        int xAngle = GenMath.figureAngle(new Point2D.Double(0, 0), pt);
        if (ni.isMirroredAboutXAxis() != ni.isMirroredAboutYAxis()
                && ((angle % 1800) == 0 || (angle % 1800) == 1350)) {
            angle += 1800;
        }
        angle = (angle - xAngle + 3600) % 3600;
        return Poly.Type.getTextTypeFromAngle(angle);
    }

    /**
     * Method to return the scaling factor between database and screen for the given text.
     * @param wnd the window with the text.
     * @param glyphBounds the bounds of text.
     * @param style the anchor information for the text.
     * @param lX the low X bound of the polygon containing the text.
     * @param hX the high X bound of the polygon containing the text.
     * @param lY the low Y bound of the polygon containing the text.
     * @param hY the high Y bound of the polygon containing the text.
     * @return the scale of the text (from database to screen).
     */
    protected double getTextScale(EditWindow0 wnd, Rectangle2D glyphBounds, Poly.Type style, double lX, double hX, double lY, double hY) {
        double textScale = 1.0 / wnd.getScale();
        if (style == Poly.Type.TEXTBOX) {
            double textWidth = glyphBounds.getWidth() * textScale;
            if (textWidth > hX - lX) {
                // text too big for box: scale it down
                textScale *= (hX - lX) / textWidth;
            }
        }
        return textScale;
    }

    /**
     * Method to report the distance of a point to this Poly.
     * @param x coordinate of a point.
     * @param y coordinate of a point.
     * @return the distance of the point to the Poly.
     * The method returns a negative amount if the point is a direct hit on or inside
     * the polygon (the more negative, the closer to the center).
     */
    public double polyDistance(double x, double y) {
        return polyDistance(new Rectangle2D.Double(x, y, 0, 0));
    }

    /**
     * Method to report the distance of a point to this Poly.
     * @param Point2D coordinate of a point
     * @return the distance of the point to the Poly.
     * The method returns a negative amount if the point is a direct hit on or inside
     * the polygon (the more negative, the closer to the center).
     */
    public double polyDistance(Point2D pt) {
		Point2D cl = closestPoint(pt);
		if (isInside(pt)) return -pt.distance(cl);
		else return pt.distance(cl);
    }

    /**
     * Method to report the distance of a rectangle or point to this Poly.
     * @param otherBounds the area to test for distance to the Poly.
     * @return the distance of the area to the Poly.
     * The method returns a negative amount if the point/area is a direct hit on or inside
     * the polygon (the more negative, the closer to the center).
     */
    public double polyDistance(Rectangle2D otherBounds) {
        // get information about this Poly
        Rectangle2D polyBounds = getBounds2D();
        double polyCX = polyBounds.getCenterX();
        double polyCY = polyBounds.getCenterY();
        Point2D polyCenter = new Point2D.Double(polyCX, polyCY);
        Poly.Type localStyle = style;
        boolean thisIsPoint = (polyBounds.getWidth() == 0 && polyBounds.getHeight() == 0);

        // get information about the other area being tested
        boolean otherIsPoint = (otherBounds.getWidth() == 0 && otherBounds.getHeight() == 0);
        double otherCX = otherBounds.getCenterX();
        double otherCY = otherBounds.getCenterY();
        Point2D otherPt = new Point2D.Double(otherCX, otherCY);

        // handle single point polygons
        if (thisIsPoint) {
            if (otherIsPoint) {
                if (polyCX == otherCX && polyCY == otherCY) {
                    return Double.MIN_VALUE;
                }
            } else {
                if (otherBounds.contains(polyCenter)) {
                    return Double.MIN_VALUE;
                }
            }
            return otherPt.distance(polyCenter);
        }

        // handle polygons that are filled in
        if (localStyle == Poly.Type.FILLED || localStyle == Poly.Type.CROSSED || localStyle.isText()) {
            if (otherIsPoint) {
                // give special returned value if point is a direct hit
                if (isInside(otherPt)) {
                    return otherPt.distance(polyCenter) - Double.MAX_VALUE;
                }

                // if polygon is a box, use M.B.R. information
                Rectangle2D box = getBox();
                if (box != null) {
                    if (otherCX > box.getMaxX()) {
                        polyCX = otherCX - box.getMaxX();
                    } else if (otherCX < box.getMinX()) {
                        polyCX = box.getMinX() - otherCX;
                    } else {
                        polyCX = 0;
                    }
                    if (otherCY > box.getMaxY()) {
                        polyCY = otherCY - box.getMaxY();
                    } else if (otherCY < box.getMinY()) {
                        polyCY = box.getMinY() - otherCY;
                    } else {
                        polyCY = 0;
                    }
                    if (polyCX == 0 || polyCY == 0) {
                        return polyCX + polyCY;
                    }
                    polyCenter.setLocation(polyCX, polyCY);
                    return polyCenter.distance(new Point2D.Double(0, 0));
                }

                // point is outside of irregular polygon: fall into to next case
                localStyle = Poly.Type.CLOSED;
            } else {
                if (DBMath.rectsIntersect(otherBounds, polyBounds)) {
                    return Double.MIN_VALUE;
                }
                return otherPt.distance(polyCenter);
            }
        }

        // handle closed outline figures
        if (localStyle == Poly.Type.CLOSED) {
            if (otherIsPoint) {
                double bestDist = Double.MAX_VALUE;
                Point2D lastPt = points[points.length - 1];
                for (int i = 0; i < points.length; i++) {
                    if (i != 0) {
                        lastPt = points[i - 1];
                    }
                    Point2D thisPt = points[i];

                    // compute distance of close point to "otherPt"
                    double dist = DBMath.distToLine(lastPt, thisPt, otherPt);
                    if (dist < bestDist) {
                        bestDist = dist;
                    }
                }
                return bestDist;
            } else {
                if (DBMath.rectsIntersect(otherBounds, polyBounds)) {
                    return Double.MIN_VALUE;
                }
                return otherPt.distance(polyCenter);
            }
        }

        // handle opened outline figures
        if (localStyle == Poly.Type.OPENED || localStyle == Poly.Type.OPENEDT1
                || localStyle == Poly.Type.OPENEDT2 || localStyle == Poly.Type.OPENEDT3) {
            if (otherIsPoint) {
                double bestDist = Double.MAX_VALUE;
                for (int i = 1; i < points.length; i++) {
                    Point2D lastPt = points[i - 1];
                    Point2D thisPt = points[i];

                    // compute distance of close point to "otherPt"
                    double dist = DBMath.distToLine(lastPt, thisPt, otherPt);
                    if (dist < bestDist) {
                        bestDist = dist;
                    }
                }
                return bestDist;
            } else {
                if (DBMath.rectsIntersect(otherBounds, polyBounds)) {
                    return Double.MIN_VALUE;
                }
                return otherPt.distance(polyCenter);
            }
        }

        // handle outline vector lists
        if (localStyle == Poly.Type.VECTORS) {
            if (otherIsPoint) {
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < points.length; i += 2) {
                    Point2D lastPt = points[i];
                    Point2D thisPt = points[i + 1];

                    // compute distance of close point to "otherPt"
                    double dist = DBMath.distToLine(lastPt, thisPt, otherPt);
                    if (dist < bestDist) {
                        bestDist = dist;
                    }
                }
                return bestDist;
            } else {
                if (DBMath.rectsIntersect(otherBounds, polyBounds)) {
                    return Double.MIN_VALUE;
                }
                return otherPt.distance(polyCenter);
            }
        }

        // handle circular objects
        if (localStyle == Poly.Type.CIRCLE || localStyle == Poly.Type.THICKCIRCLE || localStyle == Poly.Type.DISC) {
            double odist = points[0].distance(points[1]);
            double dist = points[0].distance(otherPt);
            if (otherIsPoint) {
                if (localStyle == Poly.Type.DISC && dist < odist) {
                    return dist - Double.MAX_VALUE;
                }
                return Math.abs(dist - odist);
            } else {
                if (points[0].getX() + dist < otherBounds.getMinX()) {
                    return dist;
                }
                if (points[0].getX() - dist > otherBounds.getMaxX()) {
                    return dist;
                }
                if (points[0].getY() + dist < otherBounds.getMinY()) {
                    return dist;
                }
                if (points[0].getY() - dist > otherBounds.getMaxY()) {
                    return dist;
                }
                return Double.MIN_VALUE;
            }
        }
        if (localStyle == Poly.Type.CIRCLEARC || localStyle == Poly.Type.THICKCIRCLEARC) {
            if (otherIsPoint) {
                // determine closest point to ends of arc
                double sdist = otherPt.distance(points[1]);
                double edist = otherPt.distance(points[2]);
                double dist = Math.min(sdist, edist);

                // see if the point is in the segment of the arc
                int pang = DBMath.figureAngle(points[0], otherPt);
                int sang = DBMath.figureAngle(points[0], points[1]);
                int eang = DBMath.figureAngle(points[0], points[2]);
                if (eang > sang) {
                    if (pang < eang && pang > sang) {
                        return dist;
                    }
                } else {
                    if (pang < eang || pang > sang) {
                        return dist;
                    }
                }

                // point in arc: determine distance
                double odist = points[0].distance(points[1]);
                dist = points[0].distance(otherPt);
                return Math.abs(dist - odist);
            } else {
                Point[] savePoints = points;
                clipArc(otherBounds.getMinX(), otherBounds.getMaxX(), otherBounds.getMinY(), otherBounds.getMaxY());
                Point[] newPoints = points;
                points = savePoints;
                if (newPoints.length > 0) {
                    return Double.MIN_VALUE;
                }
                double dist = points[0].distance(points[1]);
                return points[0].distance(otherPt) - dist;
            }
        }

        // can't figure out others: use distance to polygon center
        return otherPt.distance(polyCenter);
    }

    /**
     * Method to calculate fast distance between two manhattan
     * polygons that do not intersect
     * @param polyOther the other polygon being examined with this.
     * @return non-negative distance if both polygons are manhattan types,
     * -1 if at least one of them is not manhattan.
     */
    public double separationBox(PolyBase polyOther) {
        Rectangle2D thisBounds = getBox();
        Rectangle2D otherBounds = polyOther.getBox();

        // Both polygons must be manhattan-shaped
        if (thisBounds == null || otherBounds == null) {
            return -1;
        }

        double lX1 = thisBounds.getMinX();
        double hX1 = thisBounds.getMaxX();
        double lY1 = thisBounds.getMinY();
        double hY1 = thisBounds.getMaxY();
        double lX2 = otherBounds.getMinX();
        double hX2 = otherBounds.getMaxX();
        double lY2 = otherBounds.getMinY();
        double hY2 = otherBounds.getMaxY();
        double pdx = Math.max(lX2 - hX1, lX1 - hX2);
        double pdy = Math.max(lY2 - hY1, lY1 - hY2);

        double pd = (pdx > 0 && pdy > 0) ? // Diagonal
                Math.hypot(pdx, pdy)
                : Math.max(pdx, pdy);
        return pd;
    }

    /**
     * Method to return the distance between this Poly and another.
     * @param polyOther the other Poly to consider.
     * @return the distance between them (returns 0 if they touch or overlap).
     */
    public double separation(PolyBase polyOther) {
        // stop now if they touch
        if (intersects(polyOther)) {
            return 0;
        }

        // look at all points on polygon 1
        double minPD = 0;
        for (int i = 0; i < points.length; i++) {
            Point2D c = polyOther.closestPoint(points[i]);
            double pd = c.distance(points[i]);
            if (pd <= 0) {
                return 0;
            }
            if (i == 0) {
                minPD = pd;
            } else {
                if (pd < minPD) {
                    minPD = pd;
                }
            }
        }

        // look at all points on polygon 2
        for (Point2D point : polyOther.points) {
            Point2D c = closestPoint(point);
            double pd = c.distance(point);
            if (pd <= 0) {
                return 0;
            }
            if (pd < minPD) {
                minPD = pd;
            }
        }

        // also compute manhattan separation and use it if better
        double minPDman = separationBox(polyOther);
        if (minPDman != -1 && minPDman < minPD) {
            minPD = minPDman;
        }

        return minPD;
    }

    /**
     * Method to find the point on this polygon closest to a given point.
     * @param pt the given point
     * @return a point on this Poly that is closest.
     */
    public Point2D closestPoint(Point2D pt) {
        Poly.Type localStyle = style;
        if (localStyle == Poly.Type.FILLED || localStyle == Poly.Type.CROSSED || localStyle == Poly.Type.TEXTCENT
                || localStyle.isText()) {
            // filled polygon: check for regularity first
            Rectangle2D bounds = getBox();
            if (bounds != null) {
                double x = pt.getX();
                double y = pt.getY();
                if (x < bounds.getMinX()) {
                    x = bounds.getMinX();
                }
                if (x > bounds.getMaxX()) {
                    x = bounds.getMaxX();
                }
                if (y < bounds.getMinY()) {
                    y = bounds.getMinY();
                }
                if (y > bounds.getMaxY()) {
                    y = bounds.getMaxY();
                }
                return new Point2D.Double(x, y);
            }
            if (localStyle == Poly.Type.FILLED) {
                if (isInside(pt)) {
                    return pt;
                }
            }
            localStyle = Poly.Type.CLOSED;
            // FALLTHROUGH 
        }
        if (localStyle == Poly.Type.CLOSED) {
            // check outline of description
            double bestDist = Double.MAX_VALUE;
            Point2D bestPoint = new Point2D.Double();
            for (int i = 0; i < points.length; i++) {
                int lastI;
                if (i == 0) {
                    lastI = points.length - 1;
                } else {
                    lastI = i - 1;
                }
                Point2D pc = DBMath.closestPointToSegment(points[lastI], points[i], pt);
                double dist = pc.distance(pt);
                if (dist > bestDist) {
                    continue;
                }
                bestDist = dist;
                bestPoint.setLocation(pc);
            }
            return bestPoint;
        }
        if (localStyle == Poly.Type.OPENED || localStyle == Poly.Type.OPENEDT1
                || localStyle == Poly.Type.OPENEDT2 || localStyle == Poly.Type.OPENEDT3) {
            // check outline of description
            double bestDist = Double.MAX_VALUE;
            Point2D bestPoint = new Point2D.Double();
            for (int i = 1; i < points.length; i++) {
                Point2D pc = DBMath.closestPointToSegment(points[i - 1], points[i], pt);
                double dist = pc.distance(pt);
                if (dist > bestDist) {
                    continue;
                }
                bestDist = dist;
                bestPoint.setLocation(pc);
            }
            return bestPoint;
        }
        if (localStyle == Poly.Type.VECTORS) {
            // check outline of description
            double bestDist = Double.MAX_VALUE;
            Point2D bestPoint = new Point2D.Double();
            for (int i = 0; i < points.length; i += 2) {
                Point2D pc = DBMath.closestPointToSegment(points[i], points[i + 1], pt);
                double dist = pc.distance(pt);
                if (dist > bestDist) {
                    continue;
                }
                bestDist = dist;
                bestPoint.setLocation(pc);
            }
            return bestPoint;
        }

        // presume single-point polygon and use the center
        Rectangle2D bounds = getBounds2D();
        return new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
    }

    /**
     * Method to tell whether a point is inside of this Poly.
     * This method is a requirement of the Shape implementation.
     * @param x the X coordinate of the point.
     * @param y the Y coordinate of the point.
     * @return true if the point is inside the Poly.
     */
    @Override
    public boolean contains(double x, double y) {
        return isInside(new Point2D.Double(x, y));
    }

    /**
     * Method to tell whether a point is inside of this Poly.
     * This method is a requirement of the Shape implementation.
     * @param p the point.
     * @return true if the point is inside the Poly.
     */
    @Override
    public boolean contains(Point2D p) {
        return isInside(p);
    }

    /**
     * Method to tell whether a rectangle is inside of this Poly.
     * This method is a requirement of the Shape implementation.
     * @param lX the X corner of the rectangle.
     * @param lY the Y corner of the rectangle.
     * @param w the width of the rectangle.
     * @param h the height of the rectangle.
     * @return true if the rectangle is inside the Poly.
     */
    @Override
    public boolean contains(double lX, double lY, double w, double h) {
        // first ensure all rectangle corners are inside of the polygon
        double hX = lX + w;
        double hY = lY + h;
        if (!isInside(new Point2D.Double(lX, lY))
                || !isInside(new Point2D.Double(hX, lY))
                || !isInside(new Point2D.Double(lX, hY))
                || !isInside(new Point2D.Double(hX, hY))) {
            return false;
        }

        // because nonconvex polygons may pierce rectangle, check all edges
        for (int i = 0; i < points.length; i++) {
            // get a polygon edge
            int last = i - 1;
            if (last < 0) {
                last = points.length - 1;
            }
            Point2D thisPt = new Point2D.Double(points[i].getX(), points[i].getY());
            Point2D lastPt = new Point2D.Double(points[last].getX(), points[last].getY());

            // if the edge is completely outside of the rectangle, it is OK
            boolean invisible = GenMath.clipLine(lastPt, thisPt, lX, hX, lY, hY);
            if (invisible) {
                continue;
            }

            // if the polygon edge line was not completely clipped, it must sit on the rectangle edge
            if (lastPt.getX() == thisPt.getX()) {
                if (lastPt.getY() == thisPt.getY()) {
                    if (thisPt.getX() <= lX || thisPt.getX() >= hX
                            || thisPt.getY() <= lY || thisPt.getY() >= hY) {
                        continue;
                    }
                } else {
                    if (thisPt.getX() <= lX || thisPt.getX() >= hX) {
                        continue;
                    }
                }
            }
            if (lastPt.getY() == thisPt.getY()) {
                if (thisPt.getY() <= lY || thisPt.getY() >= hY) {
                    continue;
                }
            }

            // polygon edge is inside of rectangle: no containment
            return false;
        }

        // all edges pass: rectangle is contained
        return true;
    }

    /**
     * Method to tell whether a rectangle is inside of this Poly.
     * This method is a requirement of the Shape implementation.
     * @param r the rectangle.
     * @return true if the rectangle is inside the Poly.
     */
    @Override
    public boolean contains(Rectangle2D r) {
        return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /**
     * Method to tell whether a rectangle intersects this Poly.
     * This method is a requirement of the Shape implementation.
     * THIS METHOD HAS NOT BEEN WRITTEN YET!!!
     * @param x the X corner of the rectangle.
     * @param y the Y corner of the rectangle.
     * @param w the width of the rectangle.
     * @param h the height of the rectangle.
     * @return true if the rectangle intersects the Poly.
     */
    @Override
    public boolean intersects(double x, double y, double w, double h) {
        throw new Error("intersects method not implemented in Poly.intersects()");
        //return false;
    }

    /**
     * Method to tell whether a rectangle intersects this Poly.
     * This method is a requirement of the Shape implementation.
     * @param r the rectangle.
     * @return true if the rectangle intersects the Poly.
     */
    @Override
    public boolean intersects(Rectangle2D r) {
        return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /**
     * Method to tell whether this Poly intersects another one.
     * @param polyOther the other Poly to test.
     * @return true if polygons intersect (that is, if any of their lines intersect).
     */
    public boolean intersects(PolyBase polyOther) {
        // quit now if bounding boxes don't overlap
        Rectangle2D thisBounds = getBounds2D();
        Rectangle2D otherBounds = polyOther.getBounds2D();
        if (thisBounds.getMaxX() < otherBounds.getMinX()
                || otherBounds.getMaxX() < thisBounds.getMinX()
                || thisBounds.getMaxY() < otherBounds.getMinY()
                || otherBounds.getMaxY() < thisBounds.getMinY()) {
            return false;
        }

        // check each line in this Poly
        int count = points.length;
        for (int i = 0; i < count; i++) {
            Point2D p;
            if (i == 0) {
                if (style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1
                        || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3
                        || style == Poly.Type.VECTORS) {
                    continue;
                }
                p = points[count - 1];
            } else {
                p = points[i - 1];
            }
            Point2D t = points[i];
            if (style == Poly.Type.VECTORS && (i & 1) != 0) {
                i++;
            }
            if (p.getX() == t.getX() && p.getY() == t.getY()) {
                continue;
            }

            // compare this line with the other Poly
            if (Math.min(p.getX(), t.getX()) > otherBounds.getMaxX()
                    || Math.max(p.getX(), t.getX()) < otherBounds.getMinX()
                    || Math.min(p.getY(), t.getY()) > otherBounds.getMaxY()
                    || Math.max(p.getY(), t.getY()) < otherBounds.getMinY()) {
                continue;
            }
            if (polyOther.lineIntersect(p, t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method to find the intersection area of this poly with another poly.
     * Returns null if no intersection.
     * @param polyOther the other poly
     * @param overlappingEdges if non-null, this list will be filled with edges that overlap
     * between the two polygons. This is useful if the polygons touch, as the intersection area will be
     * empty.
     * @return the intersection area of the two, or null if none
     */
    public List<PolyBase> getIntersection(PolyBase polyOther, List<Line2D> overlappingEdges) {
        // get overlapping edges if requested
        if (overlappingEdges != null) {
            List<Line2D> overlaps = getOverlappingEdges(polyOther);
            overlappingEdges.addAll(overlaps);
        }
        // get intersected area
        Area myArea = new Area(this);
        Area otherArea = new Area(polyOther);
        myArea.intersect(otherArea);

        List<PolyBase> polys = getPointsInArea(myArea, layer, false, true);
        if (polys == null) {
            return null;
        }
        return polys;
    }

    /**
     * Get the overlapping edges with other polygon. Returns an empty list if none.
     * @param polyOther the other polygon
     * @return a list of overlapping edges
     */
    public List<Line2D> getOverlappingEdges(PolyBase polyOther) {
        List<Line2D> overlappingSegs = new ArrayList<Line2D>();

        // quit now if bounding boxes don't overlap
        Rectangle2D thisBounds = getBounds2D();
        Rectangle2D otherBounds = polyOther.getBounds2D();
        if (thisBounds.getMaxX() < otherBounds.getMinX()
                || otherBounds.getMaxX() < thisBounds.getMinX()
                || thisBounds.getMaxY() < otherBounds.getMinY()
                || otherBounds.getMaxY() < thisBounds.getMinY()) {
            return overlappingSegs;
        }

        // check if any line segments intersect, and add the intersection
        // point to the list if points
        List<Line2D> myLineSegs = getLineSegments();
        List<Line2D> otherLineSegs = polyOther.getLineSegments();

        for (Line2D line1 : myLineSegs) {
            for (Line2D line2 : otherLineSegs) {
                Line2D seg = getLineOverlap(line1, line2);
                if (seg != null) {
                    overlappingSegs.add(seg);
                }
            }
        }
        // remove single points if they are redundant with line segments
        List<Line2D> realLines = new ArrayList<Line2D>();
        List<Line2D> points = new ArrayList<Line2D>();
        for (Line2D seg : overlappingSegs) {
            if (seg.getP1().equals(seg.getP2())) {
                points.add(seg);
            } else {
                realLines.add(seg);
            }
        }
        for (Line2D p : points) {
            boolean redundant = false;
            for (Line2D seg : realLines) {
                if (p.getP1().equals(seg.getP1()) || p.getP1().equals(seg.getP2())) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                realLines.add(p);
            }
        }

        return realLines;
    }

    // get the line segments of this polygon
    private List<Line2D> getLineSegments() {
        List<Line2D> lineSegs = new ArrayList<Line2D>();
        for (int i = 0; i < points.length; i++) {
            Point2D p;
            if (i == 0) {
                if (style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1
                        || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3
                        || style == Poly.Type.VECTORS) {
                    continue;
                }
                p = points[points.length - 1];
            } else {
                p = points[i - 1];
            }
            Point2D t = points[i];
            if (style == Poly.Type.VECTORS && (i & 1) != 0) {
                i++;
            }
            if (p.getX() == t.getX() && p.getY() == t.getY()) {
                continue;
            }

            // see if this line intersects a line on the other poly
            lineSegs.add(new Line2D.Double(p, t));
        }
        return lineSegs;
    }

    /**
     * Get the intersection point of two line segments, if any.
     * This also returns null if the two lines are the same line,
     * as all points on either lines are intersection points.
     * @param line1 the first line segment
     * @param line2 the second line segment
     * @return the intersection point, or null if none
     */
    public static Point2D getLineSegmentIntersection(Line2D line1, Line2D line2) {
        if (!line1.intersectsLine(line2)) {
            return null;
        }
        double[] co1 = getLineCoeffs(line1);
        double[] co2 = getLineCoeffs(line2);
        // det = A1*B2 - A2*B1
        double det = co1[0] * co2[1] - co2[0] * co1[1];
        // if det == 0, lines are parallel, but already checked by intersection check above
        // if det == 0 and we got here, lines are the same line.
        if (det == 0) {
            return null;
        }
        // x = (B2*C1 - B1*C2)/det
        double x = (co2[1] * co1[2] - co1[1] * co2[2]) / det;
        // y = (A1*C2 - A2*C1)/det
        double y = (co1[0] * co2[2] - co2[0] * co1[2]) / det;
        if (x == -0.0) {
            x = 0;
        }
        if (y == -0.0) {
            y = 0;
        }
        return new Point2D.Double(x, y);
    }

    /**
     * Get the line segment that is common to both lines. Returns null if none.
     * @param line1 the first line segment
     * @param line2 the second line segment
     * @return the common line segment, or null if none
     */
    public static Line2D getLineOverlap(Line2D line1, Line2D line2) {
        if (!line1.intersectsLine(line2)) {
            return null;
        }
        double[] co1 = getLineCoeffs(line1);
        double[] co2 = getLineCoeffs(line2);
        // det = A1*B2 - A2*B1
        double det = co1[0] * co2[1] - co2[0] * co1[1];
        // if det == 0, lines are parallel, but already checked by intersection check above
        // if det == 0 and we got here, lines are the same line.
        if (det != 0) {
            return null;
        }
		double X11 = line1.getX1();
		double X12 = line1.getX2();
		double X21 = line2.getX1();
		double X22 = line2.getX2();
		double Y11 = line1.getY1();
		double Y12 = line1.getY2();
		double Y21 = line2.getY1();
		double Y22 = line2.getY2();

        double minX1 = Math.min(X11, X12);
        double minX2 = Math.min(X21, X22);
        double minX = Math.max(minX1, minX2);

        double minY1 = Math.min(Y11, Y12);
        double minY2 = Math.min(Y21, Y22);
        double minY = Math.max(minY1, minY2);

        double maxX1 = Math.max(X11, X12);
        double maxX2 = Math.max(X21, X22);
        double maxX = Math.min(maxX1, maxX2);

        double maxY1 = Math.max(Y11, Y12);
        double maxY2 = Math.max(Y21, Y22);
        double maxY = Math.min(maxY1, maxY2);

		/**
		 * The above (original) code works independently on the two axes, essentially:
		 * 1. convert the lines to rectangles
		 * 2. clip them to each other
		 * 3. return (arbitrarily) the I-quadrant diagonal
		 * This is clearly not the intention, this diagonal might not even be parallel to the lines
		 * Below we select the diagonal that is in the same quadrant as line1
		 */
		double begX = (X11 < X12) ? minX : maxX;
		double begY = (Y11 < Y12) ? minY : maxY;
		double endX = (X11 < X12) ? maxX : minX;
		double endY = (Y11 < Y12) ? maxY : minY;

		Point2D p1 = new Point2D.Double(begX, begY);
		Point2D p2 = new Point2D.Double(endX, endY);
		return new Line2D.Double(p1, p2);
    }

    /**
     * Get the coeffecients of the line of the form Ax + By = C.
     * Can't use y = Ax + B because it does not allow x = A type equations.
     * @param line the line
     * @return an array of the values A,B,C
     */
    private static double[] getLineCoeffs(Line2D line) {
        double A = line.getP2().getY() - line.getP1().getY();
        double B = line.getP1().getX() - line.getP2().getX();
        double C = A * line.getP1().getX() + B * line.getP1().getY();
        return new double[]{A, B, C};
    }

    /**
     * Method to return true if the line segment from (px1,py1) to (tx1,ty1)
     * intersects any line in polygon "poly"
     */
    private boolean lineIntersect(Point2D p1, Point2D t1) {
        int count = points.length;
        for (int i = 0; i < count; i++) {
            Point2D p2;
            if (i == 0) {
                if (style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1
                        || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3
                        || style == Poly.Type.VECTORS) {
                    continue;
                }
                p2 = points[count - 1];
            } else {
                p2 = points[i - 1];
            }
            Point2D t2 = points[i];
            if (style == Poly.Type.VECTORS && (i & 1) != 0) {
                i++;
            }

            // simple test: if it hit one of the points, it is an intersection
            if (t2.getX() == p1.getX() && t2.getY() == p1.getY()) {
                return true;
            }
            if (t2.getX() == t1.getX() && t2.getY() == t1.getY()) {
                return true;
            }

            // ignore zero-size segments
            if (p2.getX() == t2.getX() && p2.getY() == t2.getY()) {
                continue;
            }

            // special case: this line is vertical
            if (p2.getX() == t2.getX()) {
                // simple bounds check
                if (Math.min(p1.getX(), t1.getX()) > p2.getX() || Math.max(p1.getX(), t1.getX()) < p2.getX()) {
                    continue;
                }

                if (p1.getX() == t1.getX()) {
                    if (Math.min(p1.getY(), t1.getY()) > Math.max(p2.getY(), t2.getY())
                            || Math.max(p1.getY(), t1.getY()) < Math.min(p2.getY(), t2.getY())) {
                        continue;
                    }
                    return true;
                }
                if (p1.getY() == t1.getY()) {
                    if (Math.min(p2.getY(), t2.getY()) > p1.getY() || Math.max(p2.getY(), t2.getY()) < p1.getY()) {
                        continue;
                    }
                    return true;
                }
                int ang = DBMath.figureAngle(p1, t1);
                Point2D inter = DBMath.intersect(p2, 900, p1, ang);
                if (inter == null) {
                    continue;
                }
                if (inter.getX() != p2.getX() || inter.getY() < Math.min(p2.getY(), t2.getY()) || inter.getY() > Math.max(p2.getY(), t2.getY())) {
                    continue;
                }
                return true;
            }

            // special case: this line is horizontal
            if (p2.getY() == t2.getY()) {
                // simple bounds check
                if (Math.min(p1.getY(), t1.getY()) > p2.getY() || Math.max(p1.getY(), t1.getY()) < p2.getY()) {
                    continue;
                }

                if (p1.getY() == t1.getY()) {
                    if (Math.min(p1.getX(), t1.getX()) > Math.max(p2.getX(), t2.getX())
                            || Math.max(p1.getX(), t1.getX()) < Math.min(p2.getX(), t2.getX())) {
                        continue;
                    }
                    return true;
                }
                if (p1.getX() == t1.getX()) {
                    if (Math.min(p2.getX(), t2.getX()) > p1.getX() || Math.max(p2.getX(), t2.getX()) < p1.getX()) {
                        continue;
                    }
                    return true;
                }
                int ang = DBMath.figureAngle(p1, t1);
                Point2D inter = DBMath.intersect(p2, 0, p1, ang);
                if (inter == null) {
                    continue;
                }
                if (inter.getY() != p2.getY() || inter.getX() < Math.min(p2.getX(), t2.getX()) || inter.getX() > Math.max(p2.getX(), t2.getX())) {
                    continue;
                }
                return true;
            }

            // simple bounds check
            if (Math.min(p1.getX(), t1.getX()) > Math.max(p2.getX(), t2.getX()) || Math.max(p1.getX(), t1.getX()) < Math.min(p2.getX(), t2.getX())
                    || Math.min(p1.getY(), t1.getY()) > Math.max(p2.getY(), t2.getY()) || Math.max(p1.getY(), t1.getY()) < Math.min(p2.getY(), t2.getY())) {
                continue;
            }

            // general case of line intersection
            int ang1 = DBMath.figureAngle(p1, t1);
            int ang2 = DBMath.figureAngle(p2, t2);
            Point2D inter = DBMath.intersect(p2, ang2, p1, ang1);
            if (inter == null) {
                continue;
            }
            if (inter.getX() < Math.min(p2.getX(), t2.getX()) || inter.getX() > Math.max(p2.getX(), t2.getX())
                    || inter.getY() < Math.min(p2.getY(), t2.getY()) || inter.getY() > Math.max(p2.getY(), t2.getY())
                    || inter.getX() < Math.min(p1.getX(), t1.getX()) || inter.getX() > Math.max(p1.getX(), t1.getX())
                    || inter.getY() < Math.min(p1.getY(), t1.getY()) || inter.getY() > Math.max(p1.getY(), t1.getY())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Method to compute the perimeter of this Poly.
     * @return the perimeter of this Poly.
     */
    public double getPerimeter() {
        double perim = 0;
        int start = 0;
        if (style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1 || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3) {
            start = 1;
        }
        for (int i = start; i < points.length; i++) {
            int j = i - 1;
            if (j < 0) {
                j = points.length - 1;
            }
            perim += points[i].distance(points[j]);
        }
        return perim;
    }

    /**
     * Method to compute longest edge.
     * @return the longest edge in this PolyBase.
     */
    public double getMaxLength() {
        double max = 0;
        int start = 0;
        if (style == Poly.Type.OPENED || style == Poly.Type.OPENEDT1 || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3) {
            start = 1;
        }
        for (int i = start; i < points.length; i++) {
            int j = i - 1;
            if (j < 0) {
                j = points.length - 1;
            }
            double distance = points[i].distance(points[j]);
            if (max < distance) {
                max = distance;
            }
        }
        return max;
    }

    /**
     * Method to compute the area of this Poly.
     * @return the area of this Poly. Return always a positive number
     */
    public double getArea() {
        if (style == Poly.Type.FILLED || style == Poly.Type.CLOSED || style == Poly.Type.CROSSED || style.isText()) {
            Rectangle2D bounds = getBox();
            if (bounds != null) {
                double area = GenMath.getArea(bounds);
                return Math.abs(area);
            }
            return GenMath.getAreaOfPoints(points);
        }
        if (style == Poly.Type.CIRCLE || style == Poly.Type.THICKCIRCLE || style == Poly.Type.DISC) {
            Rectangle2D bounds = getBounds2D();
			double area = GenMath.getArea(bounds);
			return Math.abs(area)*Math.PI/4;
        }
        return 0;
    }

    /**
     * Method to return the X center coordinate of this Poly.
     * @return the X center coordinate of this Poly.
     */
    public double getCenterX() {
        Rectangle2D b = getBounds2D();
        return b.getCenterX();
    }

    /**
     * Method to return the Y center coordinate of this Poly.
     * @return the Y center coordinate of this Poly.
     */
    public double getCenterY() {
        Rectangle2D b = getBounds2D();
        return b.getCenterY();
    }

    /**
     * Method to return the center of the bounding box containing this PolyBase
     * @return EPoint representing the center of the PolyBase bounding box.
     */
    public EPoint getCenter() {
        Rectangle2D b = getBounds2D();
        return EPoint.fromLambda(b.getCenterX(), b.getCenterY());
    }

    /**
     * Method to return the bounds of this Poly.
     * @return the bounds of this Poly.
     */
    @Override
    public FixpRectangle getBounds2D() {
        if (bounds == null) {
            calcBounds();
        }
        return bounds;
    }

    /**
     * Method to return the bounds of this Poly.
     * Nobody really uses this, but it is necessary for the implementation of Shape.
     * @return the bounds of this Poly.
     * @deprecated this is only implemented because Poly extends Shape. You should
     * be using getBounds2D() instead.
     */
    @Override
    @Deprecated
    public Rectangle getBounds() {
        if (bounds == null) {
            calcBounds();
        }
        Rectangle2D r = getBounds2D();
        return new Rectangle((int) r.getMinX(), (int) r.getMinY(), (int) r.getWidth(), (int) r.getHeight());
    }

    /**
     * Method to change the value of a point in the PolyBase.
     * @param pt the index of the point to change.
     * @param x the new X value.
     * @param y the new Y value.
     */
    public void setPoint(int pt, double x, double y) {
        points[pt].setLocation(x, y);
        bounds = null;
    }

    private void calcBounds() {
        bounds = null;
        if (style == Poly.Type.CIRCLE || style == Poly.Type.THICKCIRCLE || style == Poly.Type.DISC) {
            long cX = points[0].getFixpX();
            long cY = points[0].getFixpY();
            long radius = FixpCoord.lambdaToFixp(points[0].distance(points[1]));
            bounds = FixpRectangle.fromFixpDiagonal(cX - radius, cY - radius, cX + radius, cY + radius);
            return;
        }
        if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC) {
            bounds = FixpRectangle.from(GenMath.arcBBox(points[1], points[2], points[0]));
            return;
        }
        if (points.length > 0) {
            long lX = points[0].getFixpX();
            long hX = lX;
            long lY = points[0].getFixpY();
            long hY = lY;
            for (int i = 1; i < points.length; i++) {
                long x = points[i].getFixpX();
                long y = points[i].getFixpY();
                if (x < lX) {
                    lX = x;
                }
                if (x > hX) {
                    hX = x;
                }
                if (y < lY) {
                    lY = y;
                }
                if (y > hY) {
                    hY = y;
                }
            }
            bounds = FixpRectangle.fromFixpDiagonal(lX, lY, hX, hY);
        } else {
            bounds = FixpRectangle.fromFixpDiagonal(0, 0, 0, 0);
        }
    }

    /**
     * Attempt to control rounding errors in input libraries
     */
    public void roundPoints() {
        bounds = null;
        for (Point2D point : points) {
            point.setLocation(DBMath.round(point.getX()), DBMath.round(point.getY()));
        }
    }

    /**
     * Class to compare PolyBase objects
     */
    private static Comparator<PolyBase> AREA_COMPARATOR = new Comparator<PolyBase>() {

        /**
         * Compares PolyBase objects based on area.
         * This method doesn't guarantee (compare(x, y)==0) == (x.equals(y))
         * @param p1 first object to be compared.
         * @param p2 second object to be compared.
         * @return Returns a negative integer, zero, or a positive integer as the
         * first object has smaller than, equal to, or greater area than the second.
         * @throws ClassCastException if the arguments' types are not PolyBase.
         */
        @Override
        public int compare(PolyBase p1, PolyBase p2) {
            double diff = p1.getArea() - p2.getArea();
            if (diff < 0.0) {
                return -1;
            }
            if (diff > 0.0) {
                return 1;
            }
            return 0;
        }
    };

    /**
	 * Static method to get PolyBaseTree roots associated with an Area.
     * @param area Java2D structure containing the geometrical information
     * @param layer the Layer to examine.
     * @return List of PolyBaseTree elements.
	 */
    public static List<PolyBaseTree> getPolyTrees(Area area, Layer layer) {
        List<PolyBase> list = getLoopsFromArea(area, layer);
        List<PolyBaseTree> roots = getTreesFromLoops(list);
        return roots;
    }

    /**
     * Static method to get PolyBase elements associated with an Area.
     * @param area Java2D structure containing the geometrical information
     * @param layer the Layer to examine.
     * @param simple if true, polygons with inner loops will return in sample Poly.
     * @param includeLastPoint true to include the last point.
     * @return List of PolyBase elements.
     */
    public static List<PolyBase> getPointsInArea(Area area, Layer layer, boolean simple, boolean includeLastPoint) {
		// Get simple loops - sorted by size
        List<PolyBase> loops = getLoopsFromArea(area, layer);
		// Arrange in tree structures
        List<PolyBaseTree> roots = getTreesFromLoops(loops);
        // Get loops from all tree roots.
        List<PolyBase> list = new ArrayList<PolyBase>();
		for (PolyBaseTree root : roots) {
			if (simple) {
				list.add(root.poly);
			} else {
				// An island in a lake on an island in a lake... 
				Stack<PolyBaseTree> stack = new Stack<PolyBaseTree>();
				root.getLoops(0, stack);
				for (PolyBaseTree tree : stack) {
					list.add(tree.getPoly());
				}
			}
		}
        return list;
    }

	// Extended sanity check for points
	public static boolean isCoincident(Point p1, Point p2) {
		if (p1 == null || p2 == null) return false;
		return p1.equals(p2);
			
	}
	public static boolean isCollinear(Point p0, Point p1, Point p2) {
		if (p0 == null || p1 == null || p2 == null) return false;
		double area =
			(p0.getX()-p1.getX())*(p0.getY()-p2.getY())-
			(p0.getY()-p1.getY())*(p0.getX()-p2.getX());
		return area == 0;
	}
	public static boolean isNonManhattan(Point p0, Point p1) {
		if (p0 == null || p1 == null) return false;
		if (p0.getX() == p1.getX()) return false;
		if (p0.getY() == p1.getY()) return false;
		return true;
	}
	public static boolean isHorizontalBox(Point p0, Point p1, Point p2, Point p3) {
		if (p0 == null || p1 == null || p2 == null || p3 == null) return false;
		return
			p0.getX() == p1.getX() && p2.getX() == p3.getX() &&
			p0.getY() == p3.getY() && p2.getY() == p1.getY();
	}
	public static boolean isVerticalBox(Point p0, Point p1, Point p2, Point p3) {
		if (p0 == null || p1 == null || p2 == null || p3 == null) return false;
		return
			p0.getX() == p3.getX() && p2.getX() == p1.getX() &&
			p0.getY() == p1.getY() && p2.getY() == p3.getY();
	}

	// Make a hole in area
	public static Point[] subtractPoints(Point[] loop, Point[] hole) {
		// Both must be non-degenerate
		assert(loop.length >= 1);
		assert(hole.length >= 1);
		// Will need lengths
		int nl = loop.length;
		int nh = hole.length;
		// Search for point on hole closest to loop
		int l0 = 0;
		int h0 = 0;
		double d0 = Double.MAX_VALUE;
		for (int l1 = 0; l1 < nl; l1++) {
			double d1 = loop[l1].distance(hole[h0]);
			if (DBMath.isLessThan(d1, d0)) {
				l0 = l1;
				d0 = d1;
			}
		}
		for (int h1 = 0; h1 < nh; h1++) {
			double d1 = loop[l0].distance(hole[h1]);
			if (DBMath.isLessThan(d1, d0)) {
				h0 = h1;
				d0 = d1;
			}
		}
		for (int l1 = 0; l1 < nl; l1++) {
			double d1 = loop[l1].distance(hole[h0]);
			if (DBMath.isLessThan(d1, d0)) {
				l0 = l1;
				d0 = d1;
			}
		}
		// Reserve space for points, closing loops if required
		int np = nl + nh;
		if (loop[l0] != loop[(l0+nl-1)%nl]) np += 1;
		if (hole[h0] != hole[(h0+nh-1)%nh]) np += 1;
		Point[] hoop = new Point[np];
		// Traverse loop and hole in clockwise direction
		int ip = 0;
		for (int l1 = 0; l1 < nl; l1++) hoop[ip++] = from(loop[(l0+l1)%nl]);
		if (loop[l0] != loop[(l0+nl-1)%nl]) hoop[ip++] = from(loop[l0]);
		for (int h1 = 0; h1 < nh; h1++) hoop[ip++] = from(hole[(h0+h1)%nh]);
		if (hole[h0] != hole[(h0+nh-1)%nh]) hoop[ip++] = from(hole[h0]);
		return hoop;
	}
				

    // Creating a tree for finding the loops
    public static class PolyBaseTree {
        PolyBase poly;
        List<PolyBaseTree> sons;

        public PolyBaseTree(PolyBase poly) {
            if (poly == null) throw new NullPointerException();
            this.poly = poly;
			this.sons = new ArrayList<PolyBaseTree>();
        }

        public Iterable<PolyBaseTree> getSons() {
			return sons;
        }

		public PolyBase getLoop() {
			return poly;
		}

		public List<PolyBase> getHoles() {
			List<PolyBase> holes = new ArrayList<PolyBase>();
			for (PolyBaseTree son : sons) {
				holes.add(son.poly);
			}
			return holes;
		}

        public PolyBase getPoly() {
			Point [] area = poly.getPoints();
			for (PolyBaseTree son : sons) {
				Point [] hole = son.poly.getPoints();
				area = subtractPoints(area, hole);
			}
			PolyBase poly = new PolyBase(area);
			poly.setLayer(poly.getLayer());
			return poly;
        }
		
        public void getLoops(int level, Stack<PolyBaseTree> list) {
            if (level % 2 == 0) {
				// Starting of a new polygon
                list.push(this);
            }
			for (PolyBaseTree son : sons) {
				// Handle next level polygons
				son.getLoops(level + 1, list);
			}
        }
		
        public boolean addTree(PolyBaseTree tree) {
     		if (poly.intersects(tree.poly)) System.out.println("*** INTERSECTING LOOPS "+poly.getLayer()+" "+tree.poly.getBounds2D());
			if (tree.poly.getPoints().length == 0) return false;
			if (!poly.contains(tree.poly.getPoints()[0])) return false;
			for (PolyBaseTree son : sons) {
				if (son.poly.intersects(tree.poly)) System.out.println("*** INTERSECTING HOLES "+son.poly.getLayer()+" "+son.poly.getBounds2D());
				if (son.poly.contains(tree.poly.getPoints()[0])) return son.addTree(tree);
				if (tree.poly.contains(son.poly.getPoints()[0])) System.out.println("*** INCONSISTENT HOLES "+son.poly.getLayer()+" "+son.poly.getBounds2D());
            }
			sons.add(tree);
            return true;
        }
    }

    // Get trees from loops
    public static List<PolyBaseTree> getTreesFromLoops(List<PolyBase> list) {
        List<PolyBaseTree> roots = new ArrayList<PolyBaseTree>();
        // areas are sorted from min to max
        // Build the hierarchy with loops
        for (int i = list.size() - 1; i > -1; i--) {
            PolyBaseTree tree = new PolyBaseTree(list.get(i));
            // Check all possible roots
            boolean added = false;
            for (PolyBaseTree root : roots) {
                if (root.addTree(tree)) {
                    added = true;
                    break;
                }
            }
            if (!added) roots.add(tree);
        }
        return roots;
    }

    // Get Points
    public static PolyBase getPointsFromPoly(PolyBase poly) {
		Area area = new Area(poly);
		Layer layer = poly.getLayer();
		return getPointsFromArea(area, layer);
	}
    public static PolyBase getPointsFromArea(Area area, Layer layer) {
        List<Point> pointList = new ArrayList<Point>();
		PathIterator pIt = area.getPathIterator(null);
        while (!pIt.isDone()) {
			int loop = pointList.size();
			int type = getPointsFromPath(pIt, pointList);
			switch (type) {
			case PathIterator.SEG_CLOSE:
				if (loop < pointList.size()) {
					Point head = pointList.get(loop);
					Point tail = pointList.get(pointList.size()-1);
					if (!head.equals(tail)) pointList.add(from(head));
				}
				break;
			case PathIterator.SEG_MOVETO:
			default:
				System.out.println("PolyBase.getPointsFromArea(" + area + ", " + layer + "): " + 
								   " unexpected PathIterator type " + type);
			}
		}
		PolyBase poly = new PolyBase(pointList.toArray(new Point[pointList.size()]));
		poly.setLayer(layer);
		poly.setStyle(Poly.Type.FILLED);
        return poly;
    }

    // Get Loops
    public static List<PolyBase> getLoopsFromPoly(PolyBase poly) {
		Area area = new Area(poly);
		Layer layer = poly.getLayer();
		return getLoopsFromArea(area, layer);
	}
    public static List<PolyBase> getLoopsFromArea(Area area, Layer layer) {
        List<Point> pointList = new ArrayList<Point>();
        List<PolyBase> list = new ArrayList<PolyBase>();

		PathIterator pIt = area.getPathIterator(null);		
        while (!pIt.isDone()) {
			int loop = pointList.size();
            int type = getPointsFromPath(pIt, pointList);
			switch (type) {
			case PathIterator.SEG_CLOSE:
				if (loop < pointList.size()) {
					Point head = pointList.get(loop);
					Point tail = pointList.get(pointList.size()-1);
					if (!head.equals(tail)) pointList.add(from(head));
				}
				Point[] points = pointList.toArray(new Point[pointList.size()]);
                pointList.clear();
                if (!ALLOWTINYPOLYGONS && !DBMath.isGreaterThan(DBMath.getAreaOfPoints(points), 0)) break;
				PolyBase poly = new PolyBase(points);
				poly.setLayer(layer);
				poly.setStyle(Poly.Type.FILLED);
				list.add(poly);
				break;
			case PathIterator.SEG_MOVETO:
			default:
				System.out.println("PolyBase.getLoopsFromArea(" + area + ", " + layer + ",...): " + 
								   " unexpected PathIterator type " + type);
			}
        }

        Collections.sort(list, AREA_COMPARATOR);
        return list;
    }

	// Check if point is on path
	public static boolean isPointOnPath(PathIterator pIt, Point2D aPt) {
		int type = -1;
		Point [] pt = new Point[3];
		double [] coords = new double[6];
        while (!pIt.isDone()) {
			type = pIt.currentSegment(coords);
			pt[0] = pt[1];
			pt[1] = fromLambda(coords[0], coords[1]);
			pIt.next();
			switch (type) {
			case PathIterator.SEG_CLOSE:
				pt[1] = pt[2];
				if (DBMath.isOnLine(pt[0], pt[1], aPt)) return true;
				break;
			case PathIterator.SEG_MOVETO:
				pt[2] = pt[1];
				break;
			case PathIterator.SEG_LINETO:
				if (DBMath.isOnLine(pt[0], pt[1], aPt)) return true;
				break;
			default:
				if (DBMath.isOnLine(pt[0], pt[1], aPt)) return true;
				break;
			}
        }
		return false;
    }
	
	// Get lines on a single loop
    public static int getLinesFromEdge(PolyPathIterator pIt, List<Line2D> pLs) {
		int nm = 4;
		int type = -1;
		Point [] pt = new Point[4];
		Point [] coords = new Point[3];
        while (!pIt.isDone()) {
			type = pIt.currentSegment(coords);
			pt[2] = coords[1];
			pIt.next();
			switch (type) {
			case PathIterator.SEG_CLOSE:
				pt[2] = pt[3];
				pIt.next();
				if (pt[1] != null && pt[2] != null) pLs.add(new Line2D.Double(pt[1], pt[2]));
				return type;
			case PathIterator.SEG_MOVETO:
				pt[0] = pt[1];
				pt[1] = pt[2];
				pt[3] = pt[2];
				break;
			case PathIterator.SEG_LINETO:
				if (isCoincident(pt[2], pt[3])) return PathIterator.SEG_CLOSE;
				if (pt[1] != null && pt[2] != null) pLs.add(new Line2D.Double(pt[1], pt[2]));
				pt[0] = pt[1];
				pt[1] = pt[2];
				break;
			default:
				if (DEBUGMERGE) System.out.println("PolyBase.getPointsFromPath(" + pIt + ", " + pLs + "): unknown PathIterator type " + type);
				if (pt[1] != null && pt[2] != null) pLs.add(new Line2D.Double(pt[1], pt[2]));
				pt[0] = pt[1];
				pt[1] = pt[2];
				break;
			}
        }
		return type;
    }

    // Get points on a single loop
    public static int getPointsFromPath(PathIterator pIt, List<Point> pLs) {
		int nm = 4;
		int type = -1;
		Point [] pt = new Point[4];
		double [] coords = new double[6];
        while (!pIt.isDone()) {
			type = pIt.currentSegment(coords);
			pt[2] = fromLambda(coords[0], coords[1]);
			pIt.next();
			switch (type) {
			case PathIterator.SEG_CLOSE:
				pt[2] = pt[3];
				if (DEBUGMERGE && pt[3] == null)
					System.out.println("PolyBase.getPointsFromPath: SEG_CLOSE path not open at " + pt[0] + " " + pt[1] + " "+ pt[2]);
				if (DEBUGMERGE && pLs.size() <= 2)
					System.out.println("PolyBase.getPointsFromPath: degenerate path " + pt[0] + " " + pt[1] + " " + pt[2]);
				if (DEBUGCOINCIDENT && isCoincident(pt[1], pt[2]) && nm-- > 0)
					System.out.println("PolyBase.getPointsFromPath: coincicent points " + pt[1] + " " + pt[2]);
				if (DEBUGCOLLINEAR && isCollinear(pt[0], pt[1], pt[2]) && nm-- > 0)
					System.out.println("PolyBase.getPointsFromPath: collinear points " + pt[0] + " " + pt[1] + " " + pt[2]);
				if (DEBUGMANHATTAN && isNonManhattan(pt[1], pt[2]) && nm-- > 0)
					System.out.println("PolyBase.getPointsFromPath: non-Manhattan segment " + pt[1] + " " + pt[2]);
				if (isCoincident(pt[1], pt[2])) pLs.remove(pLs.size() - 1);
				return type;
			case PathIterator.SEG_MOVETO:
				if (DEBUGMERGE && pt[3] != null)
					System.out.println("PolyBase.getPointsFromPath: SEG_MOVETO path already open at " + pt[0] + " " + pt[1] + " "+ pt[2]);
				if (pt[2] != null) pLs.add(pt[2]);
				pt[0] = pt[1];
				pt[1] = pt[2];
				pt[3] = pt[2];
				break;
			case PathIterator.SEG_LINETO:
				if (DEBUGMERGE && pt[3] == null)
					System.out.println("PolyBase.getPointsFromPath: SEG_LINETO path not open at " + pt[0] + " " + pt[1] + " " + pt[2]);
				if (DEBUGMERGE && isCoincident(pt[2], pt[3]))
					System.out.println("PolyBase.getPointsFromPath: SEG_LINETO intersects loop at " + pt[2] + " " + pt[2]);
				if (DEBUGCOINCIDENT && isCoincident(pt[1], pt[2]) && nm-- > 0)
					System.out.println("PolyBase.getPointsFromPath: coincicent points " + pt[1] + " " + pt[2]);
				if (DEBUGCOLLINEAR && isCollinear(pt[0], pt[1], pt[2]) && nm-- > 0)
					System.out.println("PolyBase.getPointsFromPath: collinear points " + pt[0] + " " + pt[1] + " " + pt[2]);
				if (DEBUGMANHATTAN && isNonManhattan(pt[1], pt[2]) && nm-- > 0)
					System.out.println("PolyBase.getPointsFromPath: non-Manhattan segment " + pt[1] + " " + pt[2]);
				if (isCoincident(pt[1], pt[2])) pLs.remove(pLs.size() - 1);
				if (pt[2] != null) pLs.add(pt[2]);
				pt[0] = pt[1];
				pt[1] = pt[2];
				break;
			default:
				if (DEBUGMERGE) System.out.println("PolyBase.getPointsFromPath(" + pIt + ", " + pLs + "): unknown PathIterator type " + type);
				if (isCoincident(pt[1], pt[2])) pLs.remove(pLs.size() - 1);
				if (pt[2] != null) pLs.add(pt[2]);
				pt[0] = pt[1];
				pt[1] = pt[2];
				break;
			}
        }
		return type;
    }

    private class PolyPathIterator implements PathIterator {

        int idx = 0;
		int sms = 3;
		Point prev = null;
		Point next = null;
		Point last = null;
        AffineTransform trans;

        public PolyPathIterator(AffineTransform at) {
            this.trans = at;
        }

        @Override
        public int getWindingRule() {
            return WIND_NON_ZERO;
        }

        @Override
        public boolean isDone() {
			Point next = (idx < points.length) ? points[idx] : last;
			if (next == null) return true;
			return false;
        }

        @Override
        public void next() {
			prev = next;
			next = (idx < points.length) ? points[idx] : last;
			if (next == null) return;
			if (last == null) last = next;
			else if (last.equals(next)) last = null;
			idx++;
        }

        @Override
        public int currentSegment(float[] coords) {
			next = (idx < points.length) ? points[idx] : last;
			if (next == null) return -1;
			coords[0] = (float) next.getX();
			coords[1] = (float) next.getY();
			if (trans != null) trans.transform(coords, 0, coords, 0, 1);
			if (last == null) return SEG_MOVETO;
			else if (last.equals(next)) return SEG_CLOSE;
			return SEG_LINETO;
        }

        @Override
        public int currentSegment(double[] coords) {
			next = (idx < points.length) ? points[idx] : last;
			if (next == null) return -1;
			coords[0] = next.getX();
			coords[1] = next.getY();
			if (trans != null) trans.transform(coords, 0, coords, 0, 1);
			if (last == null) return SEG_MOVETO;
			else if (last.equals(next)) return SEG_CLOSE;
			return SEG_LINETO;
        }

		public int currentSegment(Point[] coords) {
			next = (idx < points.length) ? points[idx] : last;
			if (next == null) return -1;
			coords[0] = prev;
			coords[1] = next;
			coords[2] = last;
			if (trans != null) trans.transform(coords, 0, coords, 0, 1);
			if (last == null) return SEG_MOVETO;
			if (last.equals(next)) return SEG_CLOSE;
			return SEG_LINETO;
		}
	}

    /**
     * Method to return a PathIterator for this Poly after a transformation.
     * This method is a requirement of the Shape implementation.
     * @param at the transformation to apply.
     * @return the PathIterator.
     */
    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new PolyPathIterator(at);
    }

    /**
     * Method to return an EdgeIterator for this Poly
     * This method gives direct access to Point [] points
      * @return the PolyPathIterator.
     */
    public PolyPathIterator getEdgeIterator() {
        return new PolyPathIterator(null);
    }

    /**
     * Method to return a PathIterator with a particular flatness for this Poly after a transformation.
     * This method is a requirement of the Shape implementation.
     * @param at the transformation to apply.
     * @param flatness the required flatness.
     * @return the PathIterator.
     */
    @Override
    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return getPathIterator(at);
    }

    /**
     * Initiative CrossLibCopy.
     * It should be equals
     */
    public boolean compare(Object obj, StringBuffer buffer) {
        if (this == obj) {
            return (true);
        }

        if (obj == null || getClass() != obj.getClass()) {
            return (false);
        }

        Poly poly = (Poly) obj;
        if (getLayerOrPseudoLayer() != poly.getLayerOrPseudoLayer()) {
            // Don't put until polys are sorted by layer
	        /*
            if (buffer != null)
            buffer.append("Elements belong to different layers " + getLayer().getName() + " found in " + poly.getLayer().getName() + "\n");
             */
            return (false);
        }
        // It should be covered by previous comparison
        //if (layer.getFunction() != poly.getLayer().getFunction()) return (false);

        boolean geometryCheck = polySame(poly);

        /*
        if (!geometryCheck && buffer != null)
        buffer.append("Elements don't represent same geometry " + getName() + " found in " + poly.getName() + "\n");
         */
        return (geometryCheck);
    }

    /**
     * Method to crop the box in the reference parameters (lx-hx, ly-hy)
     * against the box in (bx-ux, by-uy).  If the box is cropped into oblivion,
     * returns 1.  If the boxes overlap but cannot be cleanly cropped,
     * returns -1.  Otherwise the box is cropped and zero is returned
     */
    public static int cropBox(Rectangle2D bounds, Rectangle2D PUBox) {
        // if the two boxes don't touch, just return
        double bX = PUBox.getMinX();
        double uX = PUBox.getMaxX();
        double bY = PUBox.getMinY();
        double uY = PUBox.getMaxY();
        double lX = bounds.getMinX();
        double hX = bounds.getMaxX();
        double lY = bounds.getMinY();
        double hY = bounds.getMaxY();

        if (!DBMath.isGreaterThan(hX, bX) || !DBMath.isGreaterThan(hY, bY)
                || !DBMath.isGreaterThan(uX, lX) || !DBMath.isGreaterThan(uY, lY)) {
            return 0;
        }

        // if the box to be cropped is within the other, say so
        boolean blX = !DBMath.isGreaterThan(bX, lX);
        boolean uhX = !DBMath.isGreaterThan(hX, uX);
        boolean blY = !DBMath.isGreaterThan(bY, lY);
        boolean uhY = !DBMath.isGreaterThan(hY, uY);
        if (blX && uhX && blY && uhY) {
            return 1;
        }

        // see which direction is being cropped
        double xoverlap = Math.min(hX, uX) - Math.max(lX, bX);
        double yoverlap = Math.min(hY, uY) - Math.max(lY, bY);
        if (xoverlap > yoverlap) {
            // one above the other: crop in Y
            if (blX && uhX) {
                // it covers in X...do the crop
                if (!DBMath.isGreaterThan(hY, uY)) {
                    hY = bY;
                }
                if (blY) {
                    lY = uY;
                }
                if (!DBMath.isGreaterThan(hY, lY)) {
                    return 1;
                }
                bounds.setRect(lX, lY, hX - lX, hY - lY);
                return 0;
            }
        } else {
            // one next to the other: crop in X
            if (blY && uhY) {
                // it covers in Y...crop in X
                if (!DBMath.isGreaterThan(hX, uX)) {
                    hX = bX;
                }
                if (blX) {
                    lX = uX;
                }
                if (!DBMath.isGreaterThan(hX, lX)) {
                    return 1;
                }
                bounds.setRect(lX, lY, hX - lX, hY - lY);
                return 0;
            }
        }
        return -1;
    }

    /**
     * Method to crop the box in the reference parameters (lx-hx, ly-hy)
     * against the box in (bx-ux, by-uy). If the box is cropped into oblivion,
     * returns 1. If the boxes overlap but cannot be cleanly cropped,
     * returns -1. If boxes don't overlap, returns -2.
     * Otherwise the box is cropped and zero is returned
     */
    public static int cropBoxComplete(Rectangle2D bounds, Rectangle2D PUBox) {
        // if the two boxes don't touch, just return
        double bX = PUBox.getMinX();
        double uX = PUBox.getMaxX();
        double bY = PUBox.getMinY();
        double uY = PUBox.getMaxY();
        double lX = bounds.getMinX();
        double hX = bounds.getMaxX();
        double lY = bounds.getMinY();
        double hY = bounds.getMaxY();
        if (!DBMath.isGreaterThan(hX, bX) || !DBMath.isGreaterThan(hY, bY)
                || !DBMath.isGreaterThan(uX, lX) || !DBMath.isGreaterThan(uY, lY)) {
            return -2;
        }

        // if the box to be cropped is within the other, say so
        boolean blX = !DBMath.isGreaterThan(bX, lX);
        boolean uhX = !DBMath.isGreaterThan(hX, uX);
        boolean blY = !DBMath.isGreaterThan(bY, lY);
        boolean uhY = !DBMath.isGreaterThan(hY, uY);
        if (blX && uhX && blY && uhY) {
            return 1;
        }

        // Crop in both directions if possible, self-contained case
        // covered already
        if (bX <= lX) {
            lX = uX;
        }
        if (bY >= lY) {
            hY = bY;
        }
        if (uY <= hY) {
            lY = hY;
        }
        if (hX <= uX) {
            hX = bX;
        }
        bounds.setRect(lX, lY, hX - lX, hY - lY);

        return 0;
    }

    /**
     * Method to crop the box in the reference parameters (lx-hx, ly-hy)
     * against the box in (bx-ux, by-uy).  If the box is cropped into oblivion,
     * returns 1.  If the boxes overlap but cannot be cleanly cropped,
     * returns -1.  Otherwise the box is cropped and zero is returned
     */
    public static int halfCropBox(Rectangle2D bounds, Rectangle2D limit) {
        double bX = limit.getMinX();
        double uX = limit.getMaxX();
        double bY = limit.getMinY();
        double uY = limit.getMaxY();
        double lX = bounds.getMinX();
        double hX = bounds.getMaxX();
        double lY = bounds.getMinY();
        double hY = bounds.getMaxY();

        // if the two boxes don't touch, just return
        if (!DBMath.isGreaterThan(hX, bX) || !DBMath.isGreaterThan(hY, bY)
                || !DBMath.isGreaterThan(uX, lX) || !DBMath.isGreaterThan(uY, lY)) {
            return 0;
        }

        // if the box to be cropped is within the other, figure out which half to remove
        boolean blX = !DBMath.isGreaterThan(bX, lX);
        boolean uhX = !DBMath.isGreaterThan(hX, uX);
        boolean blY = !DBMath.isGreaterThan(bY, lY);
        boolean uhY = !DBMath.isGreaterThan(hY, uY);

        if (blX && uhX && blY && uhY) {
            double lxe = lX - bX;
            double hxe = uX - hX;
            double lye = lY - bY;
            double hye = uY - hY;
            double biggestExt = Math.max(Math.max(lxe, hxe), Math.max(lye, hye));
            if (DBMath.areEquals(biggestExt, 0)) {
                return 1;
            }
            if (DBMath.areEquals(lxe, biggestExt)) {
                lX = (lX + uX) / 2;
                if (!DBMath.isGreaterThan(hX, lX)) {
                    return 1;
                }
                bounds.setRect(lX, lY, hX - lX, hY - lY);
                return 0;
            }
            if (DBMath.areEquals(hxe, biggestExt)) {
                hX = (hX + bX) / 2;
                if (!DBMath.isGreaterThan(hX, lX)) {
                    return 1;
                }
                bounds.setRect(lX, lY, hX - lX, hY - lY);
                return 0;
            }

            if (DBMath.areEquals(lye, biggestExt)) {
                lY = (lY + uY) / 2;
                if (!DBMath.isGreaterThan(hY, lY)) {
                    return 1;
                }
                bounds.setRect(lX, lY, hX - lX, hY - lY);
                return 0;
            }
            if (DBMath.areEquals(hye, biggestExt)) {
                hY = (hY + bY) / 2;
                if (!DBMath.isGreaterThan(hY, lY)) {
                    return 1;
                }
                bounds.setRect(lX, lY, hX - lX, hY - lY);
                return 0;
            }
        }

        // reduce (lx-hx,lY-hy) bY (bX-uX,bY-uY)
        boolean crops = false;
        if (blX && uhX) {
            // it covers in X...crop in Y
            if (!DBMath.isGreaterThan(hY, uY)) {
                hY = (hY + bY) / 2;
            }
            if (blY) {
                lY = (lY + uY) / 2;
            }
            bounds.setRect(lX, lY, hX - lX, hY - lY);
            crops = true;
        }
        if (blY && uhY) {
            // it covers in Y...crop in X
            if (!DBMath.isGreaterThan(hX, uX)) {
                hX = (hX + bX) / 2;
            }
            if (blX) {
                lX = (lX + uX) / 2;
            }
            bounds.setRect(lX, lY, hX - lX, hY - lY);
            crops = true;
        }
        if (!crops) {
            return -1;
        }
        return 0;
    }

    private static class AngleList {

        private double angle;
        private double x, y;

        AngleList(Point2D pt) {
            x = pt.getX();
            y = pt.getY();
        }
    }

    /**
     * Method to clip a curved polygon (CIRCLE, THICKCIRCLE, DISC, CIRCLEARC, or THICKCIRCLEARC)
     * against the rectangle lx <= X <= hx and ly <= Y <= hy.
     * Adjusts the polygon to contain the visible portions.
     */
    public void clipArc(double lx, double hx, double ly, double hy) {
        double plx = bounds.getMinX();
        double phx = bounds.getMaxX();
        double ply = bounds.getMinY();
        double phy = bounds.getMaxY();

        // if not clipped, stop now
        if (plx >= lx && phx <= hx && ply >= ly && phy <= hy) {
            return;
        }

        // if totally invisible, blank the polygon
        if (plx > hx || phx < lx || ply > hy || phy < ly) {
            points = new Point[0];
            return;
        }

        // initialize list of relevant points
        double xc = points[0].getX();
        double yc = points[0].getY();
        double xp = points[1].getX();
        double yp = points[1].getY();
        List<AngleList> curveList = new ArrayList<AngleList>();
        if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC) {
            // include arc endpoints
            AngleList al1 = new AngleList(points[1]);
            double dx = xp - xc;
            double dy = yp - yc;
            if (dx == 0.0 && dy == 0.0) {
                System.out.println("Domain error doing circle/circle tangents");
                points = new Point[0];
                return;
            }
            al1.angle = Math.atan2(dy, dx);
            curveList.add(al1);

            AngleList al2 = new AngleList(points[2]);
            dx = al1.x - xc;
            dy = al1.y - yc;
            if (dx == 0.0 && dy == 0.0) {
                System.out.println("Domain error doing circle/circle tangents");
                points = new Point[0];
                return;
            }
            al2.angle = Math.atan2(dy, dx);
            curveList.add(al2);
        }
        int initialCount = curveList.size();

        // find intersection points along left edge
        Point2D i1 = new Point2D.Double(lx, ly);
        Point2D i2 = new Point2D.Double(lx, hy);
        int ints = circlelineintersection(points[0], points[1], i1, i2, 0);
        if (ints > 0) {
            curveList.add(new AngleList(i1));
        }
        if (ints > 1) {
            curveList.add(new AngleList(i2));
        }

        // find intersection points along top edge
        i1 = new Point2D.Double(lx, hy);
        i2 = new Point2D.Double(hx, hy);
        ints = circlelineintersection(points[0], points[1], i1, i2, 0);
        if (ints > 0) {
            curveList.add(new AngleList(i1));
        }
        if (ints > 1) {
            curveList.add(new AngleList(i2));
        }

        // find intersection points along right edge
        i1 = new Point2D.Double(hx, hy);
        i2 = new Point2D.Double(hx, ly);
        ints = circlelineintersection(points[0], points[1], i1, i2, 0);
        if (ints > 0) {
            curveList.add(new AngleList(i1));
        }
        if (ints > 1) {
            curveList.add(new AngleList(i2));
        }

        // find intersection points along bottom edge
        i1 = new Point2D.Double(hx, ly);
        i2 = new Point2D.Double(lx, ly);
        ints = circlelineintersection(points[0], points[1], i1, i2, 0);
        if (ints > 0) {
            curveList.add(new AngleList(i1));
        }
        if (ints > 1) {
            curveList.add(new AngleList(i2));
        }

        // if there are no intersections, arc is invisible
        if (curveList.size() == initialCount) {
            points = new Point[0];
            return;
        }

        // determine angle of intersection points
        for (int i = initialCount; i < curveList.size(); i++) {
            AngleList al = curveList.get(i);
            if (al.y == yc && al.x == xc) {
                System.out.println("Warning: instability ahead");
                points = new Point[0];
                return;
            }
            double dx = al.x - xc;
            double dy = al.y - yc;
            if (dx == 0.0 && dy == 0.0) {
                System.out.println("Domain error doing circle/circle tangents");
                points = new Point[0];
                return;
            }
            al.angle = Math.atan2(dy, dx);
        }

        // reject points not on the arc
        if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC) {
            int j = 2;
            AngleList al0 = curveList.get(0);
            AngleList al1 = curveList.get(1);
            for (int i = 2; i < curveList.size(); i++) {
                AngleList al = curveList.get(i);
                if (al0.angle > al1.angle) {
                    if (al.angle > al0.angle
                            || al.angle < al1.angle) {
                        continue;
                    }
                } else {
                    if (al.angle > al0.angle
                            && al.angle < al1.angle) {
                        continue;
                    }
                }
                AngleList alj = curveList.get(j);
                alj.x = al.x;
                alj.y = al.y;
                alj.angle = al.angle;
                j++;
            }
            while (curveList.size() > j) {
                curveList.remove(curveList.size() - 1);
            }

            // make sure the start of the arc is the first point
            al0 = curveList.get(0);
            for (AngleList al : curveList) {
                if (al.angle > al0.angle) {
                    al.angle -= Math.PI * 2.0;
                }
            }
        } else {
            // make sure all angles are negative
            for (AngleList al : curveList) {
                if (al.angle > 0.0) {
                    al.angle -= Math.PI * 2.0;
                }
            }
        }

        // sort by angle
        Collections.sort(curveList, new AngleListDescending());

        // for full circles, add in starting point to complete circle
        if (style != Poly.Type.CIRCLEARC && style != Poly.Type.THICKCIRCLEARC) {
            AngleList al0 = curveList.get(0);
            AngleList alNew = new AngleList(new Point2D.Double(al0.x, al0.y));
            alNew.angle = al0.angle - Math.PI * 2.0;
            curveList.add(alNew);
        }

        // now examine each segment and add it, if it is in the window
        double radius = points[0].distance(points[1]);
        List<Point> newIn = new ArrayList<Point>();
        for (int i = 1; i < curveList.size(); i++) {
            int prev = i - 1;
            AngleList al = curveList.get(i);
            AngleList alP = curveList.get(prev);
            double midAngle = (alP.angle + al.angle) / 2.0;
            while (midAngle < -Math.PI) {
                midAngle += Math.PI * 2.0;
            }
            double midx = xc + radius * Math.cos(midAngle);
            double midy = yc + radius * Math.sin(midAngle);
            if (midx < lx || midx > hx || midy < ly || midy > hy) {
                continue;
            }

            // add this segment
            newIn.add(fromLambda(xc, yc));
            newIn.add(fromLambda(alP.x, alP.y));
            newIn.add(fromLambda(al.x, al.y));
        }
        points = newIn.toArray(new Point[newIn.size()]);
        if (style == Poly.Type.THICKCIRCLE) {
            style = Poly.Type.THICKCIRCLEARC;
        } else if (style == Poly.Type.CIRCLE) {
            style = Poly.Type.CIRCLEARC;
        }
    }

    /**
     * Helper class for doing curve clipping.
     */
    private static class AngleListDescending implements Comparator<AngleList> {

        @Override
        public int compare(AngleList c1, AngleList c2) {
            double diff = c2.angle - c1.angle;
            if (diff < 0.0) {
                return -1;
            }
            if (diff > 0.0) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Method to find the intersection points between the circle at (icx,icy) with point (isx,isy)
     * and the line from (lx1,ly1) to (lx2,ly2).  Returns the two points in (ix1,iy1) and (ix2,iy2).
     * Allows intersection tolerance of "tolerance".
     * Returns the number of intersection points (0 if none, 1 if tangent, 2 if intersecting).
     */
    private int circlelineintersection(Point2D ctr, Point2D edge, Point2D from, Point2D to, double tolerance) {
        double icx = ctr.getX();
        double icy = ctr.getY();
        double isx = edge.getX();
        double isy = edge.getY();
        double lx1 = from.getX();
        double ly1 = from.getY();
        double lx2 = to.getX();
        double ly2 = to.getY();

        // construct a line that is perpendicular to the intersection line and passes
        // through the circle center.  It meets the intersection line at (segx, segy)
        double segx = 0, segy = 0;
        if (ly1 == ly2) {
            segx = icx;
            segy = ly1;
        } else if (lx1 == lx2) {
            segx = lx1;
            segy = icy;
        } else {
            // compute equation of the line
            double fx = lx1 - lx2;
            double fy = ly1 - ly2;
            double m = fy / fx;
            double b = -lx1;
            b *= m;
            b += ly1;

            // compute perpendicular to line through the point
            double mi = -1.0 / m;
            double bi = -icx;
            bi *= mi;
            bi += icy;

            // compute intersection of the lines
            segx = (bi - b) / (m - mi);
            segy = m * segx + b;
        }

        // special case when line passes through the circle center
        if (segx == icx && segy == icy) {
            double fx = isx - icx;
            double fy = isy - icy;
            double radius = Math.hypot(fx, fy);
            fx = lx2 - lx1;
            fy = ly2 - ly1;
            if (fx == 0.0 && fy == 0.0) {
                System.out.println("Domain error doing circle/line intersection");
                return 0;
            }
            double angle = Math.atan2(fy, fx);

            from.setLocation(icx + Math.cos(angle) * radius, icy + Math.sin(angle) * radius);
            to.setLocation(icx + Math.cos(-angle) * radius, icy + Math.sin(-angle) * radius);
        } else {
            // construct a right triangle with the three points: (icx, icy), (segx, segy), and (ix1,iy1)
            // the right angle is at the point (segx, segy) and the hypotenuse is the circle radius
            // The unknown point is (ix1, iy1), the intersection of the line and the circle.
            // To find it, determine the angle at the point (icx, icy)
            double fx = isx - icx;
            double fy = isy - icy;
            double radius = Math.hypot(fx, fy);
            fx = segx - icx;
            fy = segy - icy;
            double adjacent = Math.hypot(fx, fy);

            // if they are within tolerance, accept
            if (Math.abs(adjacent - radius) < tolerance) {
                from.setLocation(segx, segy);
                return 1;
            }

            // if the point is outside of the circle, quit
            if (adjacent > radius) {
                return 0;
            }

            // for zero radius, use circle center
            if (radius == 0.0) {
                from.setLocation(icx, icy);
                to.setLocation(icx, icy);
            } else {
                // now determine the angle from the center to the point (segx, segy) and offset that angle
                // by "angle".  Then project it by "radius" to get the two intersection points
                double angle = Math.acos(adjacent / radius);
                fx = segx - icx;
                fy = segy - icy;
                if (fx == 0.0 && fy == 0.0) {
                    System.out.println("Domain error doing line/circle intersection");
                    return 0;
                }
                double intangle = Math.atan2(fy, fx);
                double a1 = intangle - angle;
                double a2 = intangle + angle;
                from.setLocation(icx + Math.cos(a1) * radius, icy + Math.sin(a1) * radius);
                to.setLocation(icx + Math.cos(a2) * radius, icy + Math.sin(a2) * radius);
            }
        }

        if (from.getX() == to.getX() && from.getY() == to.getY()) {
            return 1;
        }
        return 2;
    }

    //------------------------------- PolyMerge Interface -------------------------------
    /**
     * Method to satisfy the PolyMerge interface by return the polygon (this object).
     * @return this object.
     */
    @Override
    public PolyBase getPolygon() {
        return this;
    }
}
