/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Highlight.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.user.redisplay.AbstractLayerDrawing;
import com.sun.electric.tool.user.redisplay.ERaster;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.tool.user.ui.Util;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A Highlight (or subclass thereof) includes a reference to something
 * to which the user's attention is being called (an ElectricObject,
 * some text, a region, etc) and enough information to render the
 * highlighting (boldness, etc) on any given window.  It is not
 * specific to any given EditWindow.
 */
public abstract class Highlight implements Cloneable{

	/** for drawing solid lines */		public static final BasicStroke solidLine = new BasicStroke(0);
	/** for drawing dotted lines */		public static final BasicStroke dottedLine = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {1}, 0);
	/** for drawing dashed lines */		public static final BasicStroke dashedLine = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {10}, 0);
	/** for drawing dashed lines */		public static final BasicStroke boldLine = new BasicStroke(3);

	/** The Cell containing the selection. */	protected final Cell cell;
	/** The color used when drawing */			protected final Color color;
    /** The highlight is an error */		    public final boolean isError;
	private static final int CROSSSIZE = 3;

	Highlight(Cell c, Color color, boolean isError)
	{
		this.cell = c;
		this.color = color;
        this.isError = isError;
	}

	public Cell getCell() { return cell; }

	public boolean isValid()
	{
		if (cell != null)
			if (!cell.isLinked()) return false;
		return true;
	}

    public boolean showInRaster() {
        return false;
    }

    // creating so HighlightPoly is not a public class
    public boolean isHighlightPoly() { return false; }

    // creating so HighlightLine is not a public class
    public boolean isHighlightLine() { return false; }

    // creating so HighlightObject is not a public class
    public boolean isHighlightObject() { return false; }

    // creating so HighlightArea is not a public class
    public boolean isHighlightArea() { return false; }

    // creating so HighlightEOBJ is not a public class
    public boolean isHighlightEOBJ() { return false; }

    // creating so HighlightText is not a public class
    public boolean isHighlightText() { return false; }

    public Object getObject() { return null; }

    public Variable.Key getVarKey() { return null; }

    // point variable, only useful for HighlightEOBJ?
    public int getPoint() { return -1; }

    @Override
    public Object clone()
    {
        try {
			return super.clone();
		}
		catch (CloneNotSupportedException e) {
            e.printStackTrace();
		}
        return null;
    }

    /**
	 * Method to tell whether two Highlights are the same.
	 * @param obj the Highlight to compare to this one.
	 * @param exact true to ensure that even ports are the same.
	 * @return true if the two refer to the same thing.
	 */
    public boolean sameThing(Highlight obj, boolean exact)
    {
        return false;
    }

    /**
	 * Method to tell whether this Highlight is text that stays with its node.
	 * The two possibilities are (1) text on invisible pins
	 * (2) export names, when the option to move exports with their labels is requested.
	 * @return true if this Highlight is text that should move with its node.
	 */
    public boolean nodeMovesWithText()
	{
		return false;
	}

    /** the highlight pattern will repeat itself rotationally every PULSATE_ROTATE_PERIOD milliseconds */
    private static final int PULSATE_ROTATE_PERIOD = 1000;

    /** the length of the rotating pattern */
    private static final int PULSATE_STRIPE_LENGTH = 30;

    /** the number of "segments" in each stripe; increasing this number slows down rendering*/
    private static final int PULSATE_STRIPE_SEGMENTS = 10;

    /**
	 * Method to display this Highlight in a window.
	 * @param wnd the window in which to draw this highlight.
	 * @param g_ the Graphics associated with the window.
	 */
	public void showHighlight(EditWindow wnd, Graphics g_, long highOffX, long highOffY, boolean onlyHighlight,
                              Color mainColor, Stroke primaryStroke)
    {
        if (!isValid()) return;
		g_.setColor(mainColor);
        Graphics2D g2 = (Graphics2D)g_;
        g2.setStroke(primaryStroke);
        if (User.isErrorHighlightingPulsate() && isError) {
            long now = System.currentTimeMillis();
            for(int i=0; i<PULSATE_STRIPE_SEGMENTS; i++) {
                float h = Util.hueFromColor(mainColor);
                float s = 1;
                float v = (i / ((float)PULSATE_STRIPE_SEGMENTS));
                g2.setColor(Util.colorFromHSV(h, s, v));
                float segment_length = PULSATE_STRIPE_LENGTH / ((float)PULSATE_STRIPE_SEGMENTS);
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND,  BasicStroke.JOIN_ROUND, 20,
                     new float[] { segment_length, PULSATE_STRIPE_LENGTH-segment_length },
                     (((now % PULSATE_ROTATE_PERIOD) * PULSATE_STRIPE_LENGTH) / ((float)PULSATE_ROTATE_PERIOD)) + i));
                showInternalHighlight(wnd, g2, highOffX, highOffY, onlyHighlight);
            }
		} else {
			showInternalHighlight(wnd, g_, highOffX, highOffY, onlyHighlight);
		}
    }

    abstract void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                        boolean onlyHighlight);

    public void showHighlight(FixpTransform outOfPlaceTransfrom, AbstractLayerDrawing  ald, ERaster raster) {
        throw new UnsupportedOperationException();
    }

    /**
     * highlight objects that are electrically connected to this object
     * unless specified not to. HighlightConnected is set to false by addNetwork when
     * it figures out what's connected and adds them manually. Because they are added
     * in addNetwork, we shouldn't try and add connected objects here.
     * @param g2
     * @param wnd
     */
    void showHighlightsConnected(Graphics2D g2, EditWindow wnd) {
    }

    public void showHighlightsConnected(FixpTransform outOfPlaceTransform, AbstractLayerDrawing  ald, ERaster raste) {
    }

    /**
	 * Method to populate a List of all highlighted Geometrics.
     * @param list the list to populate
	 * @param wantNodes true if NodeInsts should be included in the list.
	 * @param wantArcs true if ArcInsts should be included in the list.
	 */
    void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs) {;}

    static void getHighlightedEObjsInternal(Geometric geom, List<Geometric> list, boolean wantNodes, boolean wantArcs)
    {
        if (geom == null) return;
        if (!wantNodes && geom instanceof NodeInst) return;
        if (!wantArcs && geom instanceof ArcInst) return;

        if (list.contains(geom)) return;
        list.add(geom);
    }

    /**
	 * Method to return the Geometric object that is in this Highlight.
	 * If the highlight is a PortInst, an Export, or annotation text, its base NodeInst is returned.
	 * @return the Geometric object that is in this Highlight.
	 * Returns null if this Highlight is not on a Geometric.
	 */
    public Geometric getGeometric() { return null; }

    /**
	 * Method to return a List of all highlighted NodeInsts.
	 * Return a list with the highlighted NodeInsts.
	 */
	void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set) {;}

    static void getHighlightedNodesInternal(Geometric geom, Set<NodeInst> set)
    {
        if (geom == null || !(geom instanceof NodeInst)) return;
        NodeInst ni = (NodeInst)geom;
        set.add(ni);
    }

    /**
	 * Method to return a List of all highlighted ArcInsts.
	 * Return a list with the highlighted ArcInsts.
	 */
    void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set) {;}

    static void getHighlightedArcsInternal(Geometric geom, Set<ArcInst> set)
    {
        if (geom == null || !(geom instanceof ArcInst)) return;
        ArcInst ai = (ArcInst)geom;

        set.add(ai);
    }

    /**
	 * Method to return a set of the currently selected networks.
	 * Return a set of the currently selected networks.
	 * If there are no selected networks, the list is empty.
	 */
    void getHighlightedNetworks(Set<Network> nets, Netlist netlist) {;}

    /**
	 * Method to return a List of all highlighted text.
     * @param list list to populate.
	 * @param unique true to request that the text objects be unique,
	 * and not attached to another object that is highlighted.
	 * For example, if a node and an export on that node are selected,
	 * the export text will not be included if "unique" is true.
	 * Return a list with the Highlight objects that point to text.
	 */
    void getHighlightedText(List<DisplayedText> list, boolean unique, List<Highlight> getHighlights) {;}

    /**
	 * Method to return the bounds of the highlighted objects.
	 * @param wnd the window in which to get bounds.
	 * @return the bounds of the highlighted objects (null if nothing is highlighted).
	 */
    Rectangle2D getHighlightedArea(EditWindow wnd) { return null; }

    /**
	 * Method to return the ElectricObject associated with this Highlight object.
	 * @return the ElectricObject associated with this Highlight object.
	 */
    public ElectricObject getElectricObject() { return null; }

    /**
	 * Method to tell whether a point is over this Highlight.
	 * @param wnd the window being examined.
	 * @param x the X screen coordinate of the point.
	 * @param y the Y screen coordinate of the point.
	 * @param change true to update the highlight; false to leave things alone.
	 * @return (possible updated) this Highlight if the point is over this Highlight, null otherwise
	 */
    Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change) { return null; }

    public String getInfo() { return null;}

    /**
     * Method to get a list of Arcs and Nodes from a Highlight list. 
     * If a port is found, it adds the node instance associated to.
     * @param list the list of highlighted objects.
     * @return a list with nodes and arcs
     */
    public static List<ElectricObject> getEOBJElements(List<Highlight> list)
    {
    	List<ElectricObject> l = new ArrayList<ElectricObject>();
    	
    	for(Highlight h : list)
        {
            ElectricObject eobj = h.getElectricObject();
            if (h.isHighlightEOBJ())
            {
                if (eobj instanceof PortInst)
                {
                	l.add(((PortInst)eobj).getNodeInst());
                } else if (eobj instanceof ArcInst || eobj instanceof NodeInst)
                {
                	l.add(eobj);
                }
            }
        }
    	return l;
    }
    
    /**
     * Method to load an array of counts with the number of highlighted objects in a list.
     * arc = 0, node = 1, export = 2, text = 3, graphics = 4
     * @param list the list of highlighted objects.
     * @param counts the array of counts to set.
     * @return a NodeInst, if it is in the list.
     */
    public static NodeInst getInfoCommand(List<Highlight> list, int[] counts)
    {
        // information about the selected items
        NodeInst theNode = null;
        for(Highlight h : list)
        {
            ElectricObject eobj = h.getElectricObject();
            if (h.isHighlightEOBJ())
            {
                if (eobj instanceof NodeInst || eobj instanceof PortInst)
                {
                    counts[1]++;
                    if (eobj instanceof NodeInst) theNode = (NodeInst)eobj; else
                        theNode = ((PortInst)eobj).getNodeInst();
                } else if (eobj instanceof ArcInst)
                {
                    counts[0]++;
                }
            } else if (h.isHighlightText())
            {
            	if (h.getVarKey() == Export.EXPORT_NAME) counts[2]++; else
            	{
            		if (h.getElectricObject() instanceof NodeInst)
            			theNode = (NodeInst)h.getElectricObject();
                    counts[3]++;
            	}
            } else if (h instanceof HighlightArea)
            {
                counts[4]++;
            } else if (h instanceof HighlightLine)
            {
                counts[4]++;
            }
        }
        return theNode;
    }

    /**
	 * Method to draw an array of points as highlighting.
	 * @param wnd the window in which drawing is happening.
     * @param g the Graphics for the window.
     * @param points the array of points being drawn.
     * @param offX the X offset of the drawing.
     * @param offY the Y offset of the drawing.
     * @param opened true if the points are drawn "opened".
     * @param thickLine
     */
	public static void drawOutlineFromPoints(EditWindow wnd, Graphics g, Point2D[] points, long offX, long offY,
                                             boolean opened, boolean thickLine)
	{
		boolean onePoint = true;
		if (points.length <= 0)
			return;
		ScreenPoint firstP = wnd.databaseToScreen(points[0].getX(), points[0].getY());
		for(int i=1; i<points.length; i++)
		{
			ScreenPoint p = wnd.databaseToScreen(points[i].getX(), points[i].getY());
			if (DBMath.doublesEqual(p.getX(), firstP.getX()) &&
				DBMath.doublesEqual(p.getY(), firstP.getY())) continue;
			onePoint = false;
			break;
		}
		if (onePoint)
		{
			drawLine(g, wnd, firstP.getX() + offX-CROSSSIZE, firstP.getY() + offY, firstP.getX() + offX+CROSSSIZE, firstP.getY() + offY);
			drawLine(g, wnd, firstP.getX() + offX, firstP.getY() + offY-CROSSSIZE, firstP.getX() + offX, firstP.getY() + offY+CROSSSIZE);
			return;
		}

		// find the center
		int cX = 0, cY = 0;
		Point p = new Point(0, 0);
		Point2D ptXF = new Point2D.Double(0, 0);
		for(int i=0; i<points.length; i++)
		{
			int lastI = i-1;
			if (lastI < 0)
			{
				if (opened) continue;
				lastI = points.length - 1;
			}
			Point2D pt = points[lastI];
			if (wnd.isInPlaceEdit())
			{
		   		wnd.getInPlaceTransformOut().transform(pt, ptXF);
		   		pt = ptXF;
			}
			wnd.gridToScreen(DBMath.lambdaToGrid(pt.getX()), DBMath.lambdaToGrid(pt.getY()), p);
			long fX = p.x + offX;   long fY = p.y + offY;
			pt = points[i];
			if (wnd.isInPlaceEdit())
			{
		   		wnd.getInPlaceTransformOut().transform(pt, ptXF);
		   		pt = ptXF;
			}
			wnd.gridToScreen(DBMath.lambdaToGrid(pt.getX()), DBMath.lambdaToGrid(pt.getY()), p);
			long tX = p.x + offX;    long tY = p.y + offY;
			drawLine(g, wnd, fX, fY, tX, tY);
			if (thickLine)
			{
				if (fX < cX) fX--; else fX++;
				if (fY < cY) fY--; else fY++;
				if (tX < cX) tX--; else tX++;
				if (tY < cY) tY--; else tY++;
				drawLine(g, wnd, fX, fY, tX, tY);
			}
		}
	}

    /**
	 * Method to draw an array of points as highlighting.
     * @param points the array of points being drawn.
     * @param offX the X offset of the drawing.
     * @param offY the Y offset of the drawing.
     * @param opened true if the points are drawn "opened".
     * @param thickLine true to draw the line thick.
     */
	public static void drawOutlineFromPoints(FixpTransform outOfPlaceTransform, AbstractLayerDrawing ald, ERaster raster,
		Point2D[] points, int offX, int offY, boolean opened, boolean thickLine)
	{
		boolean onePoint = true;
		if (points.length <= 0)
			return;
        Point firstP = new Point();
        Point p = new Point();
		ald.databaseToScreen(points[0].getX(), points[0].getY(), firstP);
		for(int i=1; i<points.length; i++)
		{
			ald.databaseToScreen(points[i].getX(), points[i].getY(), p);
			if (p.equals(firstP)) continue;
			onePoint = false;
			break;
		}
		if (onePoint)
		{
            Point2D pt = points[0];
            if (outOfPlaceTransform != null)
                outOfPlaceTransform.transform(pt, pt);
            ald.drawCross((int)DBMath.lambdaToGrid(pt.getX()), (int)DBMath.lambdaToGrid(pt.getY()), CROSSSIZE, raster);
			return;
		}

        if (outOfPlaceTransform != null) {
            for (int i = 0; i < points.length; i++) {
                outOfPlaceTransform.transform(points[i], points[i]);
            }
        }
		for(int i=0; i<points.length; i++)
		{
			int lastI = i-1;
			if (lastI < 0)
			{
				if (opened) continue;
				lastI = points.length - 1;
			}
			Point2D pt1 = points[lastI];
			Point2D pt2 = points[i];
			ald.drawLine((int)DBMath.lambdaToGrid(pt1.getX()), (int)DBMath.lambdaToGrid(pt1.getY()),
                    (int)DBMath.lambdaToGrid(pt2.getX()), (int)DBMath.lambdaToGrid(pt2.getY()),
                0, raster);
		}
	}

	void internalDescribe(StringBuffer desc) {}

    /**
     * Describe the Highlight
     * @return a string describing the highlight
     */
    public String describe() {
        StringBuffer desc = new StringBuffer();
        desc.append(this.getClass().getName());
        if (cell != null)
        {
	        desc.append(" in ");
	        desc.append(cell);
        }
        desc.append(": ");
        internalDescribe(desc);
        return desc.toString();
    }

    /**
     * Gets a poly that describes the Highlight for the NodeInst.
     * @param ni the nodeinst to get a poly that will be used to highlight it
     * @return a poly outlining the nodeInst.
     */
    public static Poly getNodeInstOutline(NodeInst ni)
    {
        FixpTransform trans = ni.rotateOutAboutTrueCenter();

        Poly poly = null;
        if (!ni.isCellInstance())
        {
        	PrimitiveNode pn = (PrimitiveNode)ni.getProto();

        	// special case for outline nodes
            if (pn.isHoldsOutline())
            {
                EPoint [] outline = ni.getTrace();
                if (outline != null)
                {
                    int numPoints = outline.length;
                    boolean whole = true;
                    for(int i=1; i<numPoints; i++)
                    {
                        if (outline[i] == null)
                        {
                            whole = false;
                            break;
                        }
                    }
					if (whole)
					{
	                    Poly.Point [] pointList = new Poly.Point[numPoints];
	                    for(int i=0; i<numPoints; i++)
	                    {
                            EPoint anchor = ni.getAnchorCenter();
	                        pointList[i] = Poly.fromFixp(anchor.getFixpX() + outline[i].getFixpX(),
	                            anchor.getFixpY() + outline[i].getFixpY());
	                    }
	                    trans.transform(pointList, 0, pointList, 0, numPoints);
	                    poly = new Poly(pointList);
	    				if (ni.getFunction() == PrimitiveNode.Function.NODE)
	    				{
	    					poly.setStyle(Poly.Type.FILLED);
	    				} else
	    				{
	    					poly.setStyle(Poly.Type.OPENED);
	    				}
	    				return poly;
					}
                }
            }

            // special case for circular Artwork nodes
            if (pn.getTechnology() == Artwork.tech())
            {
	            Poly[] polys = pn.getTechnology().getShapeOfNode(ni);
	            if (polys.length == 1)
	            {
	            	Poly.Type type = polys[0].getStyle();
	            	if (type == Poly.Type.CIRCLE || type == Poly.Type.DISC || type == Poly.Type.CIRCLEARC ||
	            		type == Poly.Type.THICKCIRCLE || type == Poly.Type.THICKCIRCLEARC)
	            	{
	        			double [] angles = ni.getArcDegrees();
        				Poly.Point [] pointList = Artwork.fillEllipse(ni.getAnchorCenter(), ni.getXSize(), ni.getYSize(), angles[0], angles[1]);
        				poly = new Poly(pointList);
        				poly.setStyle(Poly.Type.OPENED);
        				poly.transform(ni.rotateOut());
	            	}
	            }
            }

            // special case for curved pins
            if (pn.isCurvedPin())
            {
	            Poly[] polys = pn.getTechnology().getShapeOfNode(ni);
				poly = polys[0];
				poly.transform(ni.rotateOut());
            }
        }

        // setup outline of node with standard offset
        if (poly == null)
            poly = ni.getBaseShape();
        return poly;
    }

    /**
     * Implementing clipping here speeds things up a lot if there are
     * many large highlights off-screen
     */
    public static void drawLine(Graphics g, EditWindow wnd, long x1, long y1, long x2, long y2)
    {
        Dimension size = wnd.getScreenSize();

        // first clip the line
        Point pt1 = new Point((int)x1, (int)y1);
        Point pt2 = new Point((int)x2, (int)y2);
//        if (x1 != pt1.x || y1 != pt1.y || x2 != pt2.x || y2 != pt2.y)
//        {
//        	System.out.println("OVERFLOW!!!!!");
//        }
		if (GenMath.clipLine(pt1, pt2, 0, size.width-1, 0, size.height-1)) return;
		g.drawLine(pt1.x, pt1.y, pt2.x, pt2.y);
    }


    /**
     * General purpose function to sort Highlight objects based on their getInfo output
     */
    public static class HighlightSorting implements Comparator<Highlight>
    {
        @Override
        public int compare(Highlight h1, Highlight h2)
        {
        	String h1Info = h1.getInfo();
        	String h2Info = h2.getInfo();
        	if (h1Info == null) h1Info = "";
        	if (h2Info == null) h2Info = "";
            return h1Info.compareTo(h2Info);
        }
    }

    /**
     *  A Highlight which calls the user's attention to a Point2D and includes a text message.
     */
    public static class Message extends Highlight
    {
    	/** The highlighted message. */								protected final String msg;
        /** Location of the message highlight */                    protected final Point2D loc;
        /** Corner of text: 0=lowerLeft, 1=upperLeft, 2=upperRight, 3=lowerRight */ protected final int corner;
        private final Color backgroundColor;

        Message(Cell c, String m, Point2D p, int co, Color backgroundColor)
        {
            super(c, null, false);
            this.msg = m;
            this.loc = p;
            this.corner = co;
            this.backgroundColor = backgroundColor;
        }

        @Override
        void internalDescribe(StringBuffer desc)
        {
            desc.append(msg);
        }

        @Override
        public String getInfo() { return msg; }

        @Override
        public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                          boolean onlyHighlight)
        {
            ScreenPoint location = wnd.databaseToScreen(loc.getX(), loc.getY());
            long locX = location.getX(), locY = location.getY();
            int width=0, height=0;
            if (corner != 0 || backgroundColor != null)
            {
            	// determine the size of the text
    			Font font = g.getFont();
    			FontRenderContext frc = new FontRenderContext(null, true, true);
    			GlyphVector gv = font.createGlyphVector(frc, msg);
    			LineMetrics lm = font.getLineMetrics(msg, frc);
    			Rectangle2D rasRect = gv.getLogicalBounds();
    			width = (int)rasRect.getWidth();
    			height = (int)(lm.getHeight()+0.5);
            }
        	switch (corner)
        	{
        		case 1:		// put upper-left corner of text at drawing coordinate
        			locY += height;
        			break;
        		case 2:		// put upper-right corner of text at drawing coordinate
        			locY += height;
        			locX -= width;
        			break;
        		case 3:		// put lower-right corner of text at drawing coordinate
        			locY += height;
        			locX -= width;
        			break;
        	}
            Color oldColor = g.getColor();
            Color mainColor;
            if (color != null) mainColor = color; else
            	mainColor = new Color(User.getColor(User.ColorPrefType.TEXT));
            int mainColorRed = mainColor.getRed() & 0xFF;
            int mainColorGreen = mainColor.getGreen() & 0xFF;
            int mainColorBlue = mainColor.getBlue() & 0xFF;
            Color shadowColor = new Color(255-mainColorRed, 255-mainColorGreen, 255-mainColorBlue);
            if (backgroundColor == null)
            {
	            g.setColor(shadowColor);
	            g.drawString(msg, (int)(locX+1), (int)(locY+1));
            } else
            {
            	g.setColor(backgroundColor);
            	g.fillRect((int)locX, (int)(locY-height), width, height);
            }
            g.setColor(mainColor);
            g.drawString(msg, (int)locX, (int)locY);
            g.setColor(oldColor);
        }

        @Override
        Rectangle2D getHighlightedArea(EditWindow wnd)
        {
            return new Rectangle2D.Double(loc.getX(), loc.getY(), 0, 0);
        }
    }
}

