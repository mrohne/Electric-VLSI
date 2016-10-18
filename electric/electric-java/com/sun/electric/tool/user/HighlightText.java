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
 *  A Highlight which calls the user's attention to an ElectricObject which happens to be a piece of text.
 */
class HighlightText extends Highlight
{
	/** The highlighted object. */								protected final ElectricObject eobj;
	/** The highlighted variable. */							protected final Variable.Key varKey;

    public HighlightText(ElectricObject e, Cell c, Variable.Key key)
    {
        super(c, null, false);
        this.eobj = e;
        this.varKey = key;
        Class<?> cls = null;
        if (key == NodeInst.NODE_NAME || key == NodeInst.NODE_PROTO)
            cls = NodeInst.class;
        else if (key == ArcInst.ARC_NAME)
            cls = ArcInst.class;
        else if (key == Export.EXPORT_NAME)
            cls = Export.class;
        else if (key == null)
            throw new NullPointerException();
        if (cls != null && !cls.isInstance(e))
            throw new IllegalArgumentException(key + " in " + e);
    }

    @Override
    void internalDescribe(StringBuffer desc)
    {
        if (varKey != null)
        {
        	if (varKey == NodeInst.NODE_NAME)
        	{
	            desc.append("name: ");
	            desc.append(((NodeInst)eobj).getName());
        	} else if (varKey == NodeInst.NODE_PROTO)
        	{
	            desc.append("instance: ");
	            desc.append(((NodeInst)eobj).getProto().getName());
        	} else if (varKey == ArcInst.ARC_NAME)
        	{
	            desc.append("name: ");
	            desc.append(((ArcInst)eobj).getName());
        	} else if (varKey == Export.EXPORT_NAME)
        	{
	            desc.append("export: ");
	            desc.append(((Export)eobj).getName());
        	} else
        	{
	            desc.append("var: ");
	            desc.append(eobj.getParameterOrVariable(varKey).describe(-1));
        	}
        }
    }

    @Override
    public ElectricObject getElectricObject() { return eobj; }

    // creating so HighlightText is not a public class
    @Override
    public boolean isHighlightText() { return true; }

    @Override
    public Variable.Key getVarKey() { return varKey; }

    @Override
    public boolean isValid()
    {
        if (!super.isValid()) return false;
        if (eobj == null || varKey == null) return false;
        if (!eobj.isLinked()) return false;

    	if (varKey == NodeInst.NODE_NAME ||
			varKey == ArcInst.ARC_NAME ||
			varKey == NodeInst.NODE_PROTO ||
			varKey == Export.EXPORT_NAME) return true;
    	return eobj.getParameterOrVariable(varKey) != null;
    }

    @Override
    public boolean sameThing(Highlight obj, boolean exact)
    {
        if (this == obj) return (true);

		// Consider already obj==null
        if (obj == null || getClass() != obj.getClass())
            return (false);

        HighlightText other = (HighlightText)obj;
        if (eobj != other.eobj) return false;
        if (cell != other.cell) return false;
        if (varKey != other.varKey) return false;
        return true;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        Graphics2D g2 = (Graphics2D)g;
        Point2D [] points = Highlighter.describeHighlightText(wnd, eobj, varKey);
        if (points == null) return;
        Point2D [] linePoints = new Point2D[2];
        for(int i=0; i<points.length; i += 2)
        {
            linePoints[0] = points[i];
            linePoints[1] = points[i+1];
            drawOutlineFromPoints(wnd, g, linePoints, highOffX, highOffY, false, false);
        }
        if (onlyHighlight)
        {
            // this is the only thing highlighted: show the attached object
            ElectricObject eObj = eobj;
            if (eObj != null && eObj instanceof Geometric)
            {
                Geometric geom = (Geometric)eObj;
                if (geom instanceof ArcInst || !((NodeInst)geom).isInvisiblePinWithText())
                {
                    Point2D objCtr = geom.getTrueCenter();
                    ScreenPoint c = wnd.databaseToScreen(objCtr);

                    TextDescriptor td = eobj.getTextDescriptor(varKey);
                    Point2D offset = new Point2D.Double(td.getXOff(), td.getYOff());
                    if (geom instanceof NodeInst)
                    {
                    	NodeInst ni = (NodeInst)geom;
                    	FixpTransform trans = ni.pureRotateOut();
                    	trans.transform(offset, offset);
                    }
                    double locX = objCtr.getX() + offset.getX();
                    double locY = objCtr.getY() + offset.getY();
                    Point2D txtAnchor = new Point2D.Double(locX, locY);
                    ScreenPoint a = wnd.databaseToScreen(txtAnchor);
                    long cX = a.getX(), cY = a.getY();
                    if (Math.abs(cX - c.getX()) > 4 || Math.abs(cY - c.getY()) > 4)
                    {
                        g.fillOval(c.getIntX()-4, c.getIntY()-4, 8, 8);
                        g2.setStroke(dottedLine);
                        drawLine(g, wnd, c.getX(), c.getY(), cX, cY);
                        g2.setStroke(solidLine);
                    }
                }
            }
        }
    }

    @Override
    public Geometric getGeometric()
    {
        if (DisplayedText.objectMovesWithText(eobj, varKey, User.isMoveNodeWithExport()))
        {
            if (eobj instanceof Export) return ((Export)eobj).getOriginalPort().getNodeInst();
            if (eobj instanceof Geometric) return (Geometric)eobj;
        }
        return null;
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
        if (/*varKey == null &&*/ eobj instanceof Export)
        {
            Export pp = (Export)eobj;
            int width = netlist.getBusWidth(pp);
            for(int i=0; i<width; i++)
            {
                Network net = netlist.getNetwork(pp, i);
                if (net != null) nets.add(net);
            }
        }
    }

    DisplayedText makeDisplayedText()
    {
    	if (varKey != null)
    		return new DisplayedText(eobj, varKey);
    	return null;
    }

    @Override
    void getHighlightedText(List<DisplayedText> list, boolean unique, List<Highlight> getHighlights)
    {
    	DisplayedText dt = makeDisplayedText();
    	if (dt == null) return;
        if (list.contains(dt)) return;

        // if this text is on a selected object, don't include the text
        if (unique)
        {
            ElectricObject onObj = null;
            if (varKey != null)
            {
                if (eobj instanceof Export)
                {
                    onObj = ((Export)eobj).getOriginalPort().getNodeInst();
                } else if (eobj instanceof PortInst)
                {
                    onObj = ((PortInst)eobj).getNodeInst();
                } else if (eobj instanceof Geometric)
                {
                    onObj = eobj;
                }
            }

            // now see if the object is in the list
            if (eobj != null)
            {
                boolean found = false;
                for(Highlight oH : getHighlights)
                {
                    if (!(oH instanceof HighlightEOBJ)) continue;
                    ElectricObject fobj = ((HighlightEOBJ)oH).eobj;
                    if (fobj instanceof PortInst) fobj = ((PortInst)fobj).getNodeInst();
                    if (fobj == onObj) { found = true;   break; }
                }
                if (found) return;
            }
        }

        // add this text
        list.add(dt);
    }

    @Override
    Rectangle2D getHighlightedArea(EditWindow wnd)
    {
        if (wnd != null)
        {
            Poly poly = eobj.computeTextPoly(wnd, varKey);
            if (poly != null) return poly.getBounds2D();
        }
        return null;
    }

    @Override
    Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change)
    {
        Point2D start = wnd.screenToDatabase(x, y);
        Poly poly = eobj.computeTextPoly(wnd, varKey);
        if (poly != null)
            if (poly.isInside(start)) return this;
        return null;
    }

    @Override
    public String describe()
    {
        String description = "Unknown";
        if (varKey != null && eobj != null)
        {
        	if (varKey == NodeInst.NODE_NAME)
        	{
        		description = "Node name for " + ((NodeInst)eobj).describe(true);
        	} else if (varKey == ArcInst.ARC_NAME)
        	{
        		description = "Arc name for " + ((ArcInst)eobj).describe(true);
        	} else if (varKey == Export.EXPORT_NAME)
        	{
        		description = "Export '" + ((Export)eobj).getName() + "'";
        	} else if (varKey == NodeInst.NODE_PROTO)
        	{
        		description = "Cell instance name " + ((NodeInst)eobj).describe(true);
        	} else
        	{
        		Variable var = eobj.getParameterOrVariable(varKey);
        		if (var != null) description = eobj.getFullDescription(var);
        	}
        }
        return description;
    }

    @Override
    public String getInfo()
    {
        return "Text: " + describe();
    }
}
