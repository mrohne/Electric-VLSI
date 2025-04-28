/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Highlight.java
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.electric.tool.user.Highlight;
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
 *  A Highlight which calls the user's attention to an ElectricObject.
 */
class HighlightEOBJ extends Highlight
{
	/** The highlighted object. */								protected final ElectricObject eobj;
	/** For Highlighted networks, this prevents excess highlights */ private final boolean highlightConnected;
	/** The highlighted outline point (only for NodeInst). */	protected final int point;

	public HighlightEOBJ(ElectricObject e, Cell c, boolean connected, int p)
	{
		super(c, null, false);
		this.eobj = e;
		this.highlightConnected = connected;
		this.point = p;
	}

	public HighlightEOBJ(ElectricObject e, Cell c, boolean connected, int p, boolean isError)
	{
		super(c, null, isError);
		this.eobj = e;
		this.highlightConnected = connected;
		this.point = p;
	}

	public HighlightEOBJ(ElectricObject e, Cell c, boolean connected, int p, Color col)
	{
		super(c, col, false);
		this.eobj = e;
		this.highlightConnected = connected;
		this.point = p;
	}

	public HighlightEOBJ(HighlightEOBJ h, ElectricObject eobj, int p)
	{
		super(h.cell, h.color, h.isError);
		this.eobj = eobj;
		this.highlightConnected = h.highlightConnected;
		this.point = p;
	}

    @Override
	void internalDescribe(StringBuffer desc)
	{
		if (eobj instanceof PortInst) {
			desc.append(((PortInst)eobj).describe(true));
		}
		if (eobj instanceof NodeInst) {
			desc.append(((NodeInst)eobj).describe(true));
		}
		if (eobj instanceof ArcInst) {
			desc.append(((ArcInst)eobj).describe(true));
		}
	}

    @Override
	public ElectricObject getElectricObject() { return eobj; }

    @Override
	public boolean isHighlightEOBJ() { return true; }

    @Override
	public int getPoint() { return point; }

    @Override
	public boolean isValid()
	{
		if (!super.isValid()) return false;
		return eobj.isLinked();
	}

    @Override
    public boolean showInRaster() {
        return !isError && color == null && eobj instanceof NodeInst && point < 0;
    }

    @Override
    public boolean sameThing(Highlight obj, boolean exact)
	{
		if (this == obj) return (true);

		// Consider already obj==null
	    if (obj == null || getClass() != obj.getClass())
            return (false);

        ElectricObject realEObj = eobj;
        if (!exact && realEObj instanceof PortInst) realEObj = ((PortInst)realEObj).getNodeInst();

        HighlightEOBJ other = (HighlightEOBJ)obj;
        ElectricObject realOtherEObj = other.eobj;
        if (!exact && realOtherEObj instanceof PortInst) realOtherEObj = ((PortInst)realOtherEObj).getNodeInst();
        if (realEObj != realOtherEObj) return false;
        return true;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        if (eobj == null || !eobj.isLinked()) return;

		// switch colors if specified
        Color oldColor = null;
        if (color != null)
        {
            oldColor = g.getColor();
            g.setColor(color);
        }

        // highlight ArcInst
		if (eobj instanceof ArcInst)
		{
			ArcInst ai = (ArcInst)eobj;

            try {
                // construct the polygons that describe the basic arc
                Poly poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
                if (poly == null) return;
                drawOutlineFromPoints(wnd, g, poly.getPoints(), highOffX, highOffY, false, false);

                if (onlyHighlight)
                {
                    // this is the only thing highlighted: give more information about constraints
                    String constraints = "X";
                    if (ai.isRigid()) constraints = "R"; else
                    {
                        if (ai.isFixedAngle())
                        {
                            if (ai.isSlidable()) constraints = "FS"; else
                                constraints = "F";
                        } else if (ai.isSlidable()) constraints = "S";
                    }
                    ScreenPoint p = wnd.databaseToScreen(ai.getTrueCenterX(), ai.getTrueCenterY());
                    Font font = wnd.getFont(null);
                    if (font != null)
                    {
                        GlyphVector gv = wnd.getGlyphs(constraints, font);
                        Rectangle2D glyphBounds = gv.getVisualBounds();
                        g.drawString(constraints, (int)(p.getX() - glyphBounds.getWidth()/2 + highOffX),
                            (int)(p.getY() + font.getSize()/2 + highOffY));
                    }
                }
            } catch (Error e) {
                throw e;
            }
			return;
		}

		// highlight NodeInst
		PortProto pp = null;
		ElectricObject realEObj = eobj;
        PortInst originalPi = null;
        if (realEObj instanceof PortInst)
		{
            originalPi = ((PortInst)realEObj);
            pp = originalPi.getPortProto();
			realEObj = ((PortInst)realEObj).getNodeInst();
		}
		if (realEObj instanceof NodeInst)
		{
			NodeInst ni = (NodeInst)realEObj;
			FixpTransform trans = ni.rotateOutAboutTrueCenter();
			long offX = highOffX;
			long offY = highOffY;

			// draw the selected point
			if (point >= 0)
			{
				EPoint [] points = ni.getTrace();
				if (points != null)
				{
					if (points.length <= point)
					{
						System.err.println("Invalid index " + point + " in trace point ");
						return;
					}
					
					// if this is a spline, highlight the true shape
					if (ni.getProto() == Artwork.tech().splineNode)
					{
						EPoint [] changedPoints = new EPoint[points.length];
						for(int i=0; i<points.length; i++)
						{
							changedPoints[i] = points[i];
							if (i == point)
							{
								double x = ni.getAnchorCenterX() + points[point].getX();
								double y = ni.getAnchorCenterY() + points[point].getY();
								Point2D thisPt = new Point2D.Double(x, y);
								trans.transform(thisPt, thisPt);
								ScreenPoint cThis = wnd.databaseToScreen(thisPt);
								Point2D db = wnd.screenToDatabase(cThis.getX()+offX, cThis.getY()+offY);
								changedPoints[i] = EPoint.fromLambda(db.getX() - ni.getAnchorCenterX(), db.getY() - ni.getAnchorCenterY());
							}
						}
						Point2D [] spPoints = Artwork.tech().fillSpline(ni.getAnchorCenter(), changedPoints);
						ScreenPoint cLast = wnd.databaseToScreen(spPoints[0]);
						for(int i=1; i<spPoints.length; i++)
						{
							ScreenPoint cThis = wnd.databaseToScreen(spPoints[i]);
							drawLine(g, wnd, cLast.getX(), cLast.getY(), cThis.getX(), cThis.getY());
							cLast = cThis;
						}
					}

					// draw an "x" through the selected point
					if (points[point] != null)
					{
						double x = ni.getAnchorCenterX() + points[point].getX();
						double y = ni.getAnchorCenterY() + points[point].getY();
						Point2D thisPt = new Point2D.Double(x, y);
						trans.transform(thisPt, thisPt);
						ScreenPoint cThis = wnd.databaseToScreen(thisPt);
						int size = 3;
						drawLine(g, wnd, cThis.getX() + size + offX, cThis.getY() + size + offY, cThis.getX() - size + offX, cThis.getY() - size + offY);
						drawLine(g, wnd, cThis.getX() + size + offX, cThis.getY() - size + offY, cThis.getX() - size + offX, cThis.getY() + size + offY);

						// find previous and next point, and draw lines to them
						boolean showWrap = ni.traceWraps();
						Point2D prevPt = null, nextPt = null;
						int prevPoint = point - 1;
						if (prevPoint < 0 && showWrap) prevPoint = points.length - 1;
						if (prevPoint >= 0 && points[prevPoint] != null)
						{
							prevPt = new Point2D.Double(ni.getAnchorCenterX() + points[prevPoint].getX(),
								ni.getAnchorCenterY() + points[prevPoint].getY());
							trans.transform(prevPt, prevPt);
							if (prevPt.getX() == thisPt.getX() && prevPt.getY() == thisPt.getY()) prevPoint = -1; else
							{
								ScreenPoint cPrev = wnd.databaseToScreen(prevPt);
								drawLine(g, wnd, cThis.getX() + offX, cThis.getY() + offY, cPrev.getX(), cPrev.getY());
							}
						}
						int nextPoint = point + 1;
						if (nextPoint >= points.length)
						{
							if (showWrap) nextPoint = 0; else
								nextPoint = -1;
						}
						if (nextPoint >= 0 && points[nextPoint] != null)
						{
							nextPt = new Point2D.Double(ni.getAnchorCenterX() + points[nextPoint].getX(),
								ni.getAnchorCenterY() + points[nextPoint].getY());
							trans.transform(nextPt, nextPt);
							if (nextPt.getX() == thisPt.getX() && nextPt.getY() == thisPt.getY()) nextPoint = -1; else
							{
								ScreenPoint cNext = wnd.databaseToScreen(nextPt);
								drawLine(g, wnd, cThis.getX() + offX, cThis.getY() + offY, cNext.getX(), cNext.getY());
							}
						}

						// draw arrows on the lines
						if (offX == 0 && offY == 0 && points.length > 2 && prevPt != null && nextPt != null)
						{
							double arrowLen = Double.MAX_VALUE;
							if (prevPoint >= 0) arrowLen = Math.min(thisPt.distance(prevPt), arrowLen);
							if (nextPoint >= 0) arrowLen = Math.min(thisPt.distance(nextPt), arrowLen);
							arrowLen /= 10;
							double angleOfArrow = Math.PI * 0.8;
							if (prevPoint >= 0)
							{
								Point2D prevCtr = new Point2D.Double((prevPt.getX()+thisPt.getX()) / 2,
									(prevPt.getY()+thisPt.getY()) / 2);
								double prevAngle = DBMath.figureAngleRadians(prevPt, thisPt);
								Point2D prevArrow1 = new Point2D.Double(prevCtr.getX() + Math.cos(prevAngle+angleOfArrow) * arrowLen,
									prevCtr.getY() + Math.sin(prevAngle+angleOfArrow) * arrowLen);
								Point2D prevArrow2 = new Point2D.Double(prevCtr.getX() + Math.cos(prevAngle-angleOfArrow) * arrowLen,
									prevCtr.getY() + Math.sin(prevAngle-angleOfArrow) * arrowLen);
								ScreenPoint cPrevCtr = wnd.databaseToScreen(prevCtr);
								ScreenPoint cPrevArrow1 = wnd.databaseToScreen(prevArrow1);
								ScreenPoint cPrevArrow2 = wnd.databaseToScreen(prevArrow2);
								drawLine(g, wnd, cPrevCtr.getX(), cPrevCtr.getY(), cPrevArrow1.getX(), cPrevArrow1.getY());
								drawLine(g, wnd, cPrevCtr.getX(), cPrevCtr.getY(), cPrevArrow2.getX(), cPrevArrow2.getY());
							}

							if (nextPoint >= 0)
							{
								Point2D nextCtr = new Point2D.Double((nextPt.getX()+thisPt.getX()) / 2,
									(nextPt.getY()+thisPt.getY()) / 2);
								double nextAngle = DBMath.figureAngleRadians(thisPt, nextPt);
								Point2D nextArrow1 = new Point2D.Double(nextCtr.getX() + Math.cos(nextAngle+angleOfArrow) * arrowLen,
									nextCtr.getY() + Math.sin(nextAngle+angleOfArrow) * arrowLen);
								Point2D nextArrow2 = new Point2D.Double(nextCtr.getX() + Math.cos(nextAngle-angleOfArrow) * arrowLen,
									nextCtr.getY() + Math.sin(nextAngle-angleOfArrow) * arrowLen);
								ScreenPoint cNextCtr = wnd.databaseToScreen(nextCtr);
								ScreenPoint cNextArrow1 = wnd.databaseToScreen(nextArrow1);
								ScreenPoint cNextArrow2 = wnd.databaseToScreen(nextArrow2);
								drawLine(g, wnd, cNextCtr.getX(), cNextCtr.getY(), cNextArrow1.getX(), cNextArrow1.getY());
								drawLine(g, wnd, cNextCtr.getX(), cNextCtr.getY(), cNextArrow2.getX(), cNextArrow2.getY());
							}
						}
					}

					// do not offset the node, just this point
					offX = offY = 0;
				}
			}

            // draw nodeInst outline
            if ((offX == 0 && offY == 0) || point < 0)
            {
            	Poly niPoly = getNodeInstOutline(ni);
                boolean niOpened = (niPoly.getStyle() == Poly.Type.OPENED);
            	Point2D [] points = niPoly.getPoints();
            	drawOutlineFromPoints(wnd, g, points, offX, offY, niOpened, false);
            }

			// draw the selected port
			if (pp != null)
			{
				Poly poly = ni.getShapeOfPort(pp);
				boolean opened = true;
				Point2D [] points = poly.getPoints();
				if (poly.getStyle() == Poly.Type.FILLED || poly.getStyle() == Poly.Type.CLOSED) opened = false;
				if (poly.getStyle() == Poly.Type.CIRCLE || poly.getStyle() == Poly.Type.THICKCIRCLE ||
					poly.getStyle() == Poly.Type.DISC)
				{
					double sX = points[0].distance(points[1]) * 2;
					Poly.Point [] pts = Artwork.fillEllipse(points[0], sX, sX, 0, 360);
					poly = new Poly(pts);
					poly.transform(ni.rotateOut());
					points = poly.getPoints();
				} else if (poly.getStyle() == Poly.Type.CIRCLEARC)
				{
					double [] angles = ni.getArcDegrees();
					double sX = points[0].distance(points[1]) * 2;
					Poly.Point [] pts = Artwork.fillEllipse(points[0], sX, sX, angles[0], angles[1]);
					poly = new Poly(pts);
					poly.transform(ni.rotateOut());
					points = poly.getPoints();
				}
				drawOutlineFromPoints(wnd, g, points, offX, offY, opened, false);

                // show name of port
                if (ni.isCellInstance() && (g instanceof Graphics2D))
				{
					// only show name if port is wired (because all other situations already show the port)
					boolean wired = false;
					for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
					{
						Connection con = cIt.next();
						if (con.getPortInst().getPortProto() == pp) { wired = true;   break; }
					}
					if (wired)
					{
	                    Font font = new Font(User.getDefaultFont(), Font.PLAIN, (int)(1.5*EditWindow.getDefaultFontSize()));
    	                GlyphVector v = wnd.getGlyphs(pp.getName(), font);
        	            ScreenPoint point = wnd.databaseToScreen(poly.getCenterX(), poly.getCenterY());
            	        ((Graphics2D)g).drawGlyphVector(v, (float)point.getX()+offX, (float)point.getY()+offY);
					}
                }
			}
		}

		// switch back to old color if switched
		if (oldColor != null)
			g.setColor(oldColor);
	}

    /**
     * highlight objects that are electrically connected to this object
     * unless specified not to. HighlightConnected is set to false by addNetwork when
     * it figures out what's connected and adds them manually. Because they are added
     * in addNetwork, we shouldn't try and add connected objects here.
     * @param g2
     * @param wnd
     */
    @Override
    void showHighlightsConnected(Graphics2D g2, EditWindow wnd) {
        if (!isValid() || !(eobj instanceof PortInst) || !highlightConnected) return;
        PortInst originalPi = (PortInst)eobj;
        Netlist netlist = cell.getNetlist();
        if (netlist == null) return;
        NodeInst ni = originalPi.getNodeInst();
        NodeInst originalNI = ni;
        PortProto pp = originalPi.getPortProto();
        if (ni.isIconOfParent())
        {
            // find export in parent
            Export equiv = (Export)cell.findPortProto(pp.getName());
            if (equiv != null)
            {
                originalPi = equiv.getOriginalPort();
                ni = originalPi.getNodeInst();
                pp = originalPi.getPortProto();
            }
        }
        Set<Network> networks = new HashSet<Network>();
        networks = NetworkTool.getNetworksOnPort(originalPi, netlist, networks);

        Set<Geometric> markObj = new HashSet<Geometric>();
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            Name arcName = ai.getNameKey();
            for (int i=0; i<arcName.busWidth(); i++) {
                if (networks.contains(netlist.getNetwork(ai, i))) {
                    markObj.add(ai);
                    markObj.add(ai.getHeadPortInst().getNodeInst());
                    markObj.add(ai.getTailPortInst().getNodeInst());
                    break;
                }
            }
        }

        for (Iterator<Nodable> it = netlist.getNodables(); it.hasNext(); ) {
            Nodable no = it.next();
            NodeInst oNi = no.getNodeInst();
            if (oNi == originalNI) continue;
            if (markObj.contains(ni)) continue;

            boolean highlightNo = false;
            for(Iterator<PortProto> eIt = no.getProto().getPorts(); eIt.hasNext(); )
            {
                PortProto oPp = eIt.next();
                Name opName = oPp.getNameKey();
                for (int j=0; j<opName.busWidth(); j++) {
                    if (networks.contains(netlist.getNetwork(no, oPp, j))) {
                        highlightNo = true;
                        break;
                    }
                }
                if (highlightNo) break;
            }
            if (highlightNo)
                markObj.add(oNi);
        }

        // draw lines along all of the arcs on the network
        Stroke origStroke = g2.getStroke();
        g2.setStroke(dashedLine);
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            if (!markObj.contains(ai)) continue;
            ScreenPoint c1 = wnd.databaseToScreen(ai.getHeadLocation());
            ScreenPoint c2 = wnd.databaseToScreen(ai.getTailLocation());
            drawLine(g2, wnd, c1.getX(), c1.getY(), c2.getX(), c2.getY());
        }

        // draw dots in all connected nodes
        g2.setStroke(solidLine);
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
        {
            NodeInst oNi = it.next();
            if (!markObj.contains(oNi)) continue;

            ScreenPoint c = wnd.databaseToScreen(oNi.getTrueCenter());
            g2.fillOval(c.getIntX()-4, c.getIntY()-4, 8, 8);

            // connect the center dots to the input arcs
            Point2D nodeCenter = oNi.getTrueCenter();
            for(Iterator<Connection> pIt = oNi.getConnections(); pIt.hasNext(); )
            {
                Connection con = pIt.next();
                ArcInst ai = con.getArc();
                if (!markObj.contains(ai)) continue;
                Point2D arcEnd = con.getLocation();
                if (arcEnd.getX() != nodeCenter.getX() || arcEnd.getY() != nodeCenter.getY())
                {
                    ScreenPoint c1 = wnd.databaseToScreen(arcEnd);
                    ScreenPoint c2 = wnd.databaseToScreen(nodeCenter);
                    drawLine(g2, wnd, c1.getX(), c1.getY(), c2.getX(), c2.getY());
                }
            }
        }
        g2.setStroke(origStroke);
    }

    @Override
    public void showHighlight(FixpTransform outOfPlaceTransform, AbstractLayerDrawing  ald, ERaster raster) {
        if (eobj == null || !eobj.isLinked()) {
            return;
        }
        // highlight NodeInst
        NodeInst ni = (NodeInst) eobj;

        // switch colors if specified
        assert color == null;

        // draw nodeInst outline
        Poly niPoly = getNodeInstOutline(ni);
        boolean niOpened = (niPoly.getStyle() == Poly.Type.OPENED);
        drawOutlineFromPoints(outOfPlaceTransform, ald, raster, niPoly.getPoints(), 0, 0, niOpened, false);
    }

    /**
     * highlight objects that are electrically connected to this object
     * unless specified not to. HighlightConnected is set to false by addNetwork when
     * it figures out what's connected and adds them manually. Because they are added
     * in addNetwork, we shouldn't try and add connected objects here.
     * @param g2
     * @param wnd
     */
    @Override
    public void showHighlightsConnected(FixpTransform outOfPlaceTransform, AbstractLayerDrawing  ald, ERaster raster) {
        if (!isValid() || !(eobj instanceof PortInst) || !highlightConnected) return;
        PortInst originalPi = (PortInst)eobj;
        Netlist netlist = cell.getNetlist();
        if (netlist == null) return;
        NodeInst ni = originalPi.getNodeInst();
        NodeInst originalNI = ni;
        PortProto pp = originalPi.getPortProto();
        if (ni.isIconOfParent())
        {
            // find export in parent
            Export equiv = (Export)cell.findPortProto(pp.getName());
            if (equiv != null)
            {
                originalPi = equiv.getOriginalPort();
                ni = originalPi.getNodeInst();
                pp = originalPi.getPortProto();
            }
        }
        Set<Network> networks = new HashSet<Network>();
        networks = NetworkTool.getNetworksOnPort(originalPi, netlist, networks);

        Set<Geometric> markObj = new HashSet<Geometric>();
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            Name arcName = ai.getNameKey();
            for (int i=0; i<arcName.busWidth(); i++) {
                if (networks.contains(netlist.getNetwork(ai, i))) {
                    markObj.add(ai);
                    markObj.add(ai.getHeadPortInst().getNodeInst());
                    markObj.add(ai.getTailPortInst().getNodeInst());
                    break;
                }
            }
        }

        for (Iterator<Nodable> it = netlist.getNodables(); it.hasNext(); ) {
            Nodable no = it.next();
            NodeInst oNi = no.getNodeInst();
            if (oNi == originalNI) continue;
            if (markObj.contains(ni)) continue;

            boolean highlightNo = false;
            for(Iterator<PortProto> eIt = no.getProto().getPorts(); eIt.hasNext(); )
            {
                PortProto oPp = eIt.next();
                Name opName = oPp.getNameKey();
                for (int j=0; j<opName.busWidth(); j++) {
                    if (networks.contains(netlist.getNetwork(no, oPp, j))) {
                        highlightNo = true;
                        break;
                    }
                }
                if (highlightNo) break;
            }
            if (highlightNo)
                markObj.add(oNi);
        }
        //System.out.println("Search took "+com.sun.electric.database.text.TextUtils.getElapsedTime(System.currentTimeMillis()-start));

        // draw lines along all of the arcs on the network
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            if (!markObj.contains(ai)) continue;
            EPoint c1 = ai.getHeadLocation();
            EPoint c2 = ai.getTailLocation();
            if (outOfPlaceTransform != null) {
                c1 = (EPoint)outOfPlaceTransform.transform(c1, null);
                c2 = (EPoint)outOfPlaceTransform.transform(c2, null);
            }
            ald.drawLine((int)c1.getGridX(), (int)c1.getGridY(), (int)c2.getGridX(), (int)c2.getGridY(), 2, raster);
        }

        // draw dots in all connected nodes
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
        {
            NodeInst oNi = it.next();
            if (!markObj.contains(oNi)) continue;

            EPoint c = EPoint.snap(oNi.getTrueCenter());
            if (outOfPlaceTransform != null) {
                c = (EPoint)outOfPlaceTransform.transform(c, null);
            }
            ald.drawOval((int)c.getGridX(), (int)c.getGridY(), 4, raster);

            // connect the center dots to the input arcs
            Point2D nodeCenter = oNi.getTrueCenter();
            for(Iterator<Connection> pIt = oNi.getConnections(); pIt.hasNext(); )
            {
                Connection con = pIt.next();
                ArcInst ai = con.getArc();
                if (!markObj.contains(ai)) continue;
                EPoint arcEnd = con.getLocation();
                if (arcEnd.getGridX() != nodeCenter.getX() || arcEnd.getY() != nodeCenter.getY())
                {
                    EPoint c1 = arcEnd;
                    EPoint c2 = EPoint.snap(nodeCenter);
                    if (outOfPlaceTransform != null) {
                        c1 = (EPoint)outOfPlaceTransform.transform(c1, null);
                        c2 = (EPoint)outOfPlaceTransform.transform(c2, null);
                    }
                    ald.drawLine((int)c1.getGridX(), (int)c1.getGridY(), (int)c2.getGridX(), (int)c2.getGridY(), 0, raster);
                }
            }
        }
    }

    @Override
	void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs)
    {
        getHighlightedEObjsInternal(getGeometric(), list, wantNodes, wantArcs);
    }

    @Override
    void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set)
    {
        getHighlightedNodesInternal(getGeometric(), set);
    }

    @Override
    void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set)
    {
        getHighlightedArcsInternal(getGeometric(), set);
    }

    @Override
    void getHighlightedNetworks(Set<Network> nets, Netlist netlist)
    {
        ElectricObject eObj = eobj;
        if (eObj instanceof NodeInst)
        {
            NodeInst ni = (NodeInst)eObj;
            if (ni.getNumPortInsts() == 1)
            {
                PortInst pi = ni.getOnlyPortInst();
                if (pi != null) eObj = pi;
            }
        }
        if (eObj instanceof PortInst)
        {
            PortInst pi = (PortInst)eObj;
            nets = NetworkTool.getNetworksOnPort(pi, netlist, nets);
        } else if (eObj instanceof ArcInst)
        {
            ArcInst ai = (ArcInst)eObj;
            int width = netlist.getBusWidth(ai);
            for(int i=0; i<width; i++)
            {
                Network net = netlist.getNetwork((ArcInst)eObj, i);
                if (net != null) nets.add(net);
            }
        }
    }

    @Override
    Rectangle2D getHighlightedArea(EditWindow wnd)
    {
        ElectricObject eObj = eobj;
        if (eObj instanceof PortInst) eObj = ((PortInst)eObj).getNodeInst();
        if (eObj instanceof Geometric)
        {
            Geometric geom = (Geometric)eObj;
            return geom.getBounds();
        }
        return null;
    }

    @Override
    public Geometric getGeometric()
    {
    	Geometric retVal = null;
        if (eobj instanceof PortInst) retVal = ((PortInst)eobj).getNodeInst(); else
        	if (eobj instanceof Geometric) retVal = (Geometric)eobj;
        return retVal;
    }

    @Override
    Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change)
    {
        Point2D slop = wnd.deltaScreenToDatabase(Highlighter.EXACTSELECTDISTANCE*2, Highlighter.EXACTSELECTDISTANCE*2);
        double directHitDist = slop.getX();
        Point2D start = wnd.screenToDatabase(x, y);
        Rectangle2D searchArea = new Rectangle2D.Double(start.getX(), start.getY(), 0, 0);

        ElectricObject eobj = this.eobj;
        if (eobj instanceof PortInst) eobj = ((PortInst)eobj).getNodeInst();
        if (eobj instanceof Geometric)
        {
        	boolean specialSelect = ToolBar.isSelectSpecial();
            List<Highlight> gotAll = Highlighter.checkOutObject((Geometric)eobj, true, false, specialSelect,
                    searchArea, wnd, directHitDist, false, wnd.getGraphicsPreferences().isShowTempNames());
            if (gotAll.isEmpty()) return null;
            boolean found = false;
            Highlight result = this;
            for(Highlight got : gotAll)
            {
	            if (!(got instanceof HighlightEOBJ))
	                System.out.println("Error?");
	            ElectricObject hObj = got.getElectricObject();
	            ElectricObject hReal = hObj;
	            if (hReal instanceof PortInst) hReal = ((PortInst)hReal).getNodeInst();
	            for(Highlight alreadyDone : highlighter.getHighlights())
	            {
	                if (!(alreadyDone instanceof HighlightEOBJ)) continue;
	                HighlightEOBJ alreadyHighlighted = (HighlightEOBJ)alreadyDone;
	                ElectricObject aHObj = alreadyHighlighted.getElectricObject();
	                ElectricObject aHReal = aHObj;
	                if (aHReal instanceof PortInst) aHReal = ((PortInst)aHReal).getNodeInst();
	                if (hReal == aHReal)
	                {
	                    // found it: adjust the port/point
	                	found = true;
	                    if (hObj != aHObj || alreadyHighlighted.point != ((HighlightEOBJ)got).point)
	                    {
	                    	if (change)
	                    	{
	                            Highlight updated = highlighter.setPoint(alreadyHighlighted, got.getElectricObject(), ((HighlightEOBJ)got).point);
	                            if (alreadyHighlighted == this) result = updated;
	                    	} else
	                    	{
	                    		result = new HighlightEOBJ(alreadyHighlighted, got.getElectricObject(), ((HighlightEOBJ)got).point);
	                    	}
	                    }
	                    break;
	                }
	            }
	            if (found) break;
            }
            return result;
        }
        return null;
    }

    @Override
    public String getInfo()
    {
        String description = "";
        ElectricObject realObj = eobj;

        if (realObj instanceof PortInst)
            realObj = ((PortInst)realObj).getNodeInst();
        if (realObj instanceof NodeInst)
        {
            NodeInst ni = (NodeInst)realObj;
            description = "Node " + ni.describe(true);
        } else if (realObj instanceof ArcInst)
        {
            ArcInst ai = (ArcInst)eobj;
            description = "Arc " + ai.describe(true);
        }
        return description;
    }
}

