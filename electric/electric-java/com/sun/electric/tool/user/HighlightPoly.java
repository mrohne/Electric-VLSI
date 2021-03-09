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
 *  A Highlight which calls the user's attention to a Poly.
 */
class HighlightPoly extends Highlight
{
    /** The highlighted polygon */                              private final Poly polygon;
    HighlightPoly(Cell c, Poly p, Color col)
    {
        super(c, col, false);
        this.polygon = p;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        // switch colors if specified
        Color oldColor = null;
        if (color != null)
        {
            oldColor = g.getColor();
            g.setColor(color);
        }

        // draw poly
    	Point2D[] points = polygon.getPoints();
        if (polygon.getStyle() == Poly.Type.FILLED)
        {
        	int [] xPoints = new int[points.length];
        	int [] yPoints = new int[points.length];
    		for(int i=0; i<points.length; i++)
    		{
    			ScreenPoint p = wnd.databaseToScreen(points[i].getX(), points[i].getY());
    			xPoints[i] = p.getIntX();
    			yPoints[i] = p.getIntY();
    		}
			g.fillPolygon(xPoints, yPoints, points.length);
        } else if (polygon.getStyle() == Poly.Type.DISC)
        {
        	long radius = Math.round(points[0].distance(points[1]) * wnd.getScale());
        	ScreenPoint ctr = wnd.databaseToScreen(points[0].getX(), points[0].getY());
        	g.fillOval((int)(ctr.getX()-radius), (int)(ctr.getY()-radius), (int)radius*2, (int)radius*2);
        } else
        {
	        boolean opened = (polygon.getStyle() == Poly.Type.OPENED);
	        drawOutlineFromPoints(wnd, g, points, highOffX, highOffY, opened, false);
        }

        // switch back to old color if switched
        if (oldColor != null)
            g.setColor(oldColor);
    }

	@Override
	public boolean isHighlightPoly() { return true; }

}
