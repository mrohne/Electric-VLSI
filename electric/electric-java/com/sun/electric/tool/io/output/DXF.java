/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DXF.java
 * Input/output tool: DXF output
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.JavaCompatiblity;
import com.sun.electric.util.math.FixpTransform;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This is the netlister for DXF.
 */
public class DXF extends Output
{
	/** key of Variable holding DXF layer name. */			public static final Variable.Key DXF_LAYER_KEY = Variable.newKey("IO_dxf_layer");
	/** key of Variable holding DXF header text. */			public static final Variable.Key DXF_HEADER_TEXT_KEY = Variable.newKey("IO_dxf_header_text");
	/** key of Variable holding DXF header information. */	public static final Variable.Key DXF_HEADER_ID_KEY = Variable.newKey("IO_dxf_header_ID");
	private int dxfEntityHandle;
	private Set<Cell> cellsSeen;
	private TextUtils.UnitScale dxfDispUnit;
	private String defaultDXFLayerName;
	private static String [] ignorefromheader = {"$DWGCODEPAGE", "$HANDSEED", "$SAVEIMAGES"};

	private DXFPreferences localPrefs;

	public static class DXFPreferences extends OutputPreferences
    {
        // DXF Settings
		int dxfScale = IOTool.getDXFScale();
        public Technology tech;

        public DXFPreferences(boolean factory) {
            super(factory);
            tech = Technology.getCurrent();
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		DXF out = new DXF(this);
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();

    		out.writeDXF(cell);

    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of the DXF netlister.
	 */
	DXF(DXFPreferences dp) { localPrefs = dp; }

	private void writeDXF(Cell cell)
	{
		// set the scale
		dxfDispUnit = TextUtils.UnitScale.findFromIndex(localPrefs.dxfScale);

		// get the bounding box
		ERectangle bb = cell.getBounds();
		double minX = TextUtils.convertDistance(bb.getMinX(), localPrefs.tech, dxfDispUnit);
		double minY = TextUtils.convertDistance(bb.getMinY(), localPrefs.tech, dxfDispUnit);
		double maxX = TextUtils.convertDistance(bb.getMaxX(), localPrefs.tech, dxfDispUnit);
		double maxY = TextUtils.convertDistance(bb.getMaxY(), localPrefs.tech, dxfDispUnit);
		
		// write the header
		printWriter.print("  0\nSECTION\n");
		printWriter.print("  2\nHEADER\n");
		printWriter.print("  9\n$LIMMIN\n");
		printWriter.print(" 10\n" + TextUtils.formatDouble(minX) + "\n");
		printWriter.print(" 20\n" + TextUtils.formatDouble(minY) + "\n");
		printWriter.print("  9\n$LIMMAX\n");
		printWriter.print(" 10\n" + TextUtils.formatDouble(maxX) + "\n");
		printWriter.print(" 20\n" + TextUtils.formatDouble(maxY) + "\n");
		printWriter.print("  9\n$INSUNITS\n");
		if (dxfDispUnit.getMultiplier().doubleValue() == 1.000) printWriter.print(" 70\n6\n");
		else if (dxfDispUnit.getMultiplier().doubleValue() == 0.001) printWriter.print(" 70\n4\n");
		else if (dxfDispUnit.getMultiplier().doubleValue() == 1.e-6) printWriter.print(" 70\n13\n");
		else printWriter.print(" 70\n0\n");
		Variable varheadertext = cell.getLibrary().getVar(DXF_HEADER_TEXT_KEY);
		Variable varheaderid = cell.getLibrary().getVar(DXF_HEADER_ID_KEY);
		Layer defLay = Artwork.tech().findLayer("Graphics");
		defaultDXFLayerName = defLay.getDXFLayer();
		if (varheadertext != null && varheaderid != null)
		{
			int len = Math.min(varheadertext.getLength(), varheaderid.getLength());
			for(int i=0; i<len; i++)
			{
				// remove entries that confuse the issues
				String pt = (String)varheadertext.getObject(i);
				int code = ((Integer)varheaderid.getObject(i)).intValue();
				if (code == 9 && i <= len-2)
				{
					boolean found = false;
					for(int j=0; j<ignorefromheader.length; j++)
					{
						if (pt.equals(ignorefromheader[j]) || pt.substring(1).equals(ignorefromheader[j]))
						{
							found = true;
							break;
						}
					}
					if (found)
					{
						i++;
						continue;
					}
				}

				// make sure Autocad version is correct
				if (pt.equals("$ACADVER") && i <= len-2)
				{
					printWriter.print(getThreeDigit(code) + "\n" + pt + "\n");
					printWriter.print("  1\nAC1009\n");
					i++;
					continue;
				}

				printWriter.print(getThreeDigit(code) + "\n" + pt + "\n");
			}
		}
		printWriter.print("  0\nENDSEC\n");
			
		// write any tables
		dxfEntityHandle = 0x100;
		printWriter.print("  0\nSECTION\n");
		printWriter.print("  2\nTABLES\n");
		// VPORT
		printWriter.print("  0\nTABLE\n");
		printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
		printWriter.print("  2\nVPORT\n");
		printWriter.print("  0\nVPORT\n");
		printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
		printWriter.print("  2\n*Active\n");
		printWriter.print(" 12\n" + TextUtils.formatDouble((minX+maxX)/2.0) + "\n");
		printWriter.print(" 22\n" + TextUtils.formatDouble((minY+maxY)/2.0) + "\n");
		printWriter.print(" 40\n" + TextUtils.formatDouble((maxX-minX)/1.0) + "\n");
		printWriter.print("  0\nENDTAB\n");
		// LAYER
		printWriter.print("  0\nTABLE\n");
		printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
		printWriter.print("  2\nLAYER\n");
		for (Iterator<Layer> it = cell.getTechnology().getLayers(); it.hasNext(); )
			writeDXFLayer(cell, it.next());
		printWriter.print("  0\nENDTAB\n");
		printWriter.print("  0\nENDSEC\n");

		// write any subcells
		printWriter.print("  0\nSECTION\n");
		printWriter.print("  2\nBLOCKS\n");

		cellsSeen = new HashSet<Cell>();
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance())
			{
				NodeProto np = ni.getProto();
				if (!cellsSeen.contains(np)) writeDXFCell((Cell)np, true);
			}
		}
		printWriter.print("  0\nENDSEC\n");

		// write any entities
		printWriter.print("  0\nSECTION\n");
		printWriter.print("  2\nENTITIES\n");
		writeDXFCell(cell, false);
		printWriter.print("  0\nENDSEC\n");
		printWriter.print("  0\nEOF\n");
	}

	/**
	 * Method to transform double into String and control number of digits to write. Introduced while
	 * porting to Java 8.
	 * @param value Double to transform
	 * @return String representing the double provided
	 */
	private String formatDoubleJavaVersion(double value)
	{
		// do rounding (in Java8, some rounding issues have been detected. round to 2 digits)
		double scale = (JavaCompatiblity.JAVA8 ? 100 : 1000);
		double rounded = Math.round(value * scale) / scale;
		String str = TextUtils.formatDouble(rounded);
		return str;
	}
	
	/**
	 * Method to write the contents of cell "np".  If "subCells" is nonzero, do a recursive
	 * descent through the subcells in this cell, writing out "block" definitions.
	 */
	private void writeDXFCell(Cell cell, boolean subCells)
	{
		if (subCells) {
			cellsSeen.add(cell);
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); ) {
				NodeInst ni = it.next();
				NodeProto np = ni.getProto();
				if (ni.isCellInstance() && !cellsSeen.contains(np))
					writeDXFCell((Cell)np, true);
			}
			printWriter.print("  0\nBLOCK\n");
			printWriter.print("  2\n" + getDXFCellName(cell) + "\n");
			printWriter.print(" 10\n0\n");
			printWriter.print(" 20\n0\n");
			printWriter.print(" 30\n0\n");
			printWriter.print(" 70\n0\n");
		}

		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); ) {
			ArcInst ai = it.next();			
			PortInst p0 = ai.getHeadPortInst();
			PortInst p1 = ai.getTailPortInst();
			// write line
			writeDXFLine(cell, p0.getCenter(), p1.getCenter(), defaultDXFLayerName);
			// write variables
			Poly[] texts = ai.getDisplayableVariables(ai.getBounds(), null, false, false);
			for(int i=0; i<texts.length; i++) {
				Poly poly = texts[i];
				writeDXFText(cell, poly, defaultDXFLayerName);
			}
			// write geometry
			Poly [] polys = ai.getProto().getTechnology().getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++) {
				Poly poly = polys[i];
				Layer layer = poly.getLayer();
				if (layer == null) {
					errorLogger.logError("Null poly.getLayer()", poly, cell, dxfEntityHandle);
					continue;
				}
				String layerName = defaultDXFLayerName;
				if (layer.getDXFLayer() != null && layer.getDXFLayer().length() != 0)
					layerName = layer.getDXFLayer();
				writeDXFPoly(cell, poly, layerName);
			}
		}

		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); ) {
			NodeInst ni = it.next();
			NodeProto np = ni.getProto();
			FixpTransform trans = ni.rotateOut();
			String layerName = defaultDXFLayerName;
			if (ni.getVar(DXF_LAYER_KEY) != null) layerName = ni.getVar(DXF_LAYER_KEY).getPureValue(-1);
			// cell instance
			if (ni.isCellInstance()) {
				Cell subCell = (Cell)np;
				printWriter.print("  0\nINSERT\n");
				printWriter.print("  8\n" + defaultDXFLayerName + "\n");
				printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
				printWriter.print("  2\n" + getDXFCellName(subCell) + "\n");
				Rectangle2D cellBounds = subCell.getBounds();
				double xC = TextUtils.convertDistance(ni.getAnchorCenterX() - cellBounds.getCenterX(), localPrefs.tech, dxfDispUnit);
				double yC = TextUtils.convertDistance(ni.getAnchorCenterY() - cellBounds.getCenterY(), localPrefs.tech, dxfDispUnit);
				printWriter.print(" 10\n" + formatDoubleJavaVersion(xC) + "\n");
				printWriter.print(" 20\n" + formatDoubleJavaVersion(yC) + "\n");
				printWriter.print(" 30\n0\n");
				double rot = ni.getAngle() / 10.0;
				printWriter.print(" 50\n" + formatDoubleJavaVersion(rot) + "\n");
				continue;
			}
			// write variables
			Poly[] texts = ni.getDisplayableVariables(ni.getBounds(), null, false, false);
			for(int i=0; i<texts.length; i++) {
				Poly poly = texts[i];
				writeDXFText(cell, poly, defaultDXFLayerName);
			}
			// write geometry
			Poly [] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
			for(int i=0; i<polys.length; i++) {
				Poly poly = polys[i];
				Layer layer = poly.getLayer();
				if (layer == null) {
					errorLogger.logError("Null poly.getLayer()", poly, cell, dxfEntityHandle);
					continue;
				}
				if (layer.getDXFLayer() != null && layer.getDXFLayer().length() != 0) layerName = layer.getDXFLayer();
				poly.transform(trans);
				writeDXFPoly(cell, poly, layerName);
			}
		}
		if (subCells) printWriter.print("  0\nENDBLK\n");
	}

	private void writeDXFLayer (Cell cell, Layer layer) {
		// traverse the standard palette
		int red = layer.getGraphics().getColor().getRed();
		int green = layer.getGraphics().getColor().getGreen();
		int blue = layer.getGraphics().getColor().getBlue();
		float hsb[] = Color.RGBtoHSB(red, green, blue, null);
		int hsbcol = (int)(10+10*Math.floor(24*hsb[0])+1*Math.floor(5*(1.0-hsb[1])+5*(1.0-hsb[2])));
		int rgbcol = (red<<16|green<<8|blue<<0);
		printWriter.print("  0\nLAYER\n");
		printWriter.print("  2\n" + layer.getDXFLayer() + "\n");
		printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
		printWriter.print(" 62\n" + hsbcol + "\n");
		printWriter.print("420\n" + rgbcol + "\n");
	}


	private void writeDXFText (Cell cell, Poly poly, String layer) {
		// extract data
		TextDescriptor desc = poly.getTextDescriptor();
		String text = poly.getString();
		double xC = TextUtils.convertDistance(poly.getCenterX(), localPrefs.tech, dxfDispUnit);
		double yC = TextUtils.convertDistance(poly.getCenterY(), localPrefs.tech, dxfDispUnit);
		double yH = TextUtils.convertDistance(desc.getSize().getSize(), localPrefs.tech, dxfDispUnit);
		// ship out entity
		printWriter.print("  0\nTEXT\n");
		printWriter.print("  1\n" + text +"\n");
		printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
		printWriter.print("  8\n" + layer + "\n");
		printWriter.print(" 10\n" + formatDoubleJavaVersion(xC) + "\n");
		printWriter.print(" 20\n" + formatDoubleJavaVersion(yC) + "\n");
		printWriter.print(" 11\n" + formatDoubleJavaVersion(xC) + "\n");
		printWriter.print(" 21\n" + formatDoubleJavaVersion(yC) + "\n");
		printWriter.print(" 40\n" + formatDoubleJavaVersion(yH) + "\n");
		printWriter.print(" 50\n" + formatDoubleJavaVersion(desc.getRotation().getAngle()) + "\n");
		// switch over horizontal alignment
		switch (poly.getStyle()) {
		case TEXTLEFT:
		case TEXTTOPLEFT:
		case TEXTBOTLEFT:
			printWriter.print(" 72\n0\n"); break;
		case TEXTBOX:
		case TEXTCENT:
		case TEXTTOP:
		case TEXTBOT:
			printWriter.print(" 72\n1\n"); break;
		case TEXTRIGHT:
		case TEXTTOPRIGHT:
		case TEXTBOTRIGHT:
			printWriter.print(" 72\n2\n"); break;
		}
		// switch on vertical alignment
		switch (poly.getStyle()) {
		case TEXTBOX:
		case TEXTCENT:
			printWriter.print(" 73\n0\n"); break;
		case TEXTBOT:
		case TEXTBOTLEFT:
		case TEXTBOTRIGHT:
			printWriter.print(" 73\n1\n"); break;
		case TEXTLEFT:
		case TEXTRIGHT:
			printWriter.print(" 73\n2\n"); break;
		case TEXTTOP:
		case TEXTTOPLEFT:
		case TEXTTOPRIGHT:
			printWriter.print(" 73\n3\n"); break;
		}
	}

	private void writeDXFLine (Cell cell, Point2D p0, Point2D p1, String layer) {
		// extract data
		double x0 = TextUtils.convertDistance(p0.getX(), localPrefs.tech, dxfDispUnit);
		double y0 = TextUtils.convertDistance(p0.getY(), localPrefs.tech, dxfDispUnit);
		double x1 = TextUtils.convertDistance(p1.getX(), localPrefs.tech, dxfDispUnit);
		double y1 = TextUtils.convertDistance(p1.getY(), localPrefs.tech, dxfDispUnit);
		// ship out entity
		printWriter.print("  0\nLINE\n");
		printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
		printWriter.print("  8\n" + layer + "\n");
		printWriter.print(" 10\n" + formatDoubleJavaVersion(x0) + "\n");
		printWriter.print(" 20\n" + formatDoubleJavaVersion(y0) + "\n");
		printWriter.print(" 11\n" + formatDoubleJavaVersion(x1) + "\n");
		printWriter.print(" 21\n" + formatDoubleJavaVersion(y1) + "\n");
	}

	private void writeDXFPoly (Cell cell, Poly poly, String layer) {
		// extract data
		Point2D [] points = poly.getPoints();
		if (points.length == 0) {
			errorLogger.logError("Null poly.Points()", poly, cell, dxfEntityHandle);
			return;
		}
		// ship out entity
		switch (poly.getStyle()) {
		case CROSS:
		case BIGCROSS:
			if (points.length >= 1) {
				double x0 = TextUtils.convertDistance(points[0].getX(), localPrefs.tech, dxfDispUnit);
				double y0 = TextUtils.convertDistance(points[0].getY(), localPrefs.tech, dxfDispUnit);
				printWriter.print("  0\nPOINT\n");
				printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
				printWriter.print("  8\n" + layer + "\n");
				printWriter.print(" 10\n" + formatDoubleJavaVersion(x0) + "\n");
				printWriter.print(" 20\n" + formatDoubleJavaVersion(y0) + "\n");
			}
			else errorLogger.logError("Malformed POINT", poly, cell, dxfEntityHandle);
			break;
		case DISC:
		case CIRCLE:
		case THICKCIRCLE:
			if (points.length == 2) {
				double x0 = TextUtils.convertDistance(points[0].getX(), localPrefs.tech, dxfDispUnit);
				double y0 = TextUtils.convertDistance(points[0].getY(), localPrefs.tech, dxfDispUnit);
				double r0 = TextUtils.convertDistance(points[0].distance(points[1]), localPrefs.tech, dxfDispUnit);
				printWriter.print("  0\nCIRCLE\n");
				printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
				printWriter.print("  8\n" + layer + "\n");
				printWriter.print(" 10\n" + formatDoubleJavaVersion(x0) + "\n");
				printWriter.print(" 20\n" + formatDoubleJavaVersion(y0) + "\n");
				printWriter.print(" 40\n" + formatDoubleJavaVersion(r0) + "\n");
			}
			else errorLogger.logError("Malformed CIRCLE", poly, cell, dxfEntityHandle);
			break;
		case CIRCLEARC:
		case THICKCIRCLEARC:
			if (points.length == 3) {	
				double x0 = TextUtils.convertDistance(points[0].getX(), localPrefs.tech, dxfDispUnit);
				double y0 = TextUtils.convertDistance(points[0].getY(), localPrefs.tech, dxfDispUnit);
				double r0 = TextUtils.convertDistance(points[0].distance(points[1]), localPrefs.tech, dxfDispUnit);
				double a0 = DBMath.figureAngle(points[0], points[2])/10;
				double a1 = DBMath.figureAngle(points[0], points[1])/10;
				printWriter.print("  0\nARC\n");
				printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
				printWriter.print("  8\n" + layer + "\n");
				printWriter.print(" 10\n" + formatDoubleJavaVersion(x0) + "\n");
				printWriter.print(" 20\n" + formatDoubleJavaVersion(y0) + "\n");
				printWriter.print(" 40\n" + formatDoubleJavaVersion(r0) + "\n");
				printWriter.print(" 50\n" + formatDoubleJavaVersion(a0) + "\n");
				printWriter.print(" 51\n" + formatDoubleJavaVersion(a1) + "\n");
			}
			else errorLogger.logError("Malformed ARC", poly, cell, dxfEntityHandle);
			break;
		case VECTORS:
			if (points.length % 2 == 0) {
				for (int i = 0; i < points.length; i += 2) {
					printWriter.print("  0\nLINE\n");
					printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
					Point2D pt = new Point2D.Double(points[0].getX() + xC, points[0].getY() + yC);
					trans.transform(pt, pt);
					double x = TextUtils.convertDistance(pt.getX(), localPrefs.tech, dxfDispUnit);
					double y = TextUtils.convertDistance(pt.getY(), localPrefs.tech, dxfDispUnit);
					printWriter.print(" 10\n" + formatDoubleJavaVersion(x) + "\n");
					printWriter.print(" 20\n" + formatDoubleJavaVersion(y) + "\n");
					printWriter.print(" 30\n0\n");
					pt = new Point2D.Double(points[1].getX() + xC, points[1].getY() + yC);
					trans.transform(pt, pt);
					x = TextUtils.convertDistance(pt.getX(), localPrefs.tech, dxfDispUnit);
					y = TextUtils.convertDistance(pt.getY(), localPrefs.tech, dxfDispUnit);
					printWriter.print(" 11\n" + formatDoubleJavaVersion(x) + "\n");
					printWriter.print(" 21\n" + formatDoubleJavaVersion(y) + "\n");
					printWriter.print(" 31\n0\n");
				} else
				{
					// should write a polyline here
					for(int i=0; i<len-1; i++)
					{
						// line
						if (points[i] == null || points[i+1] == null) continue;
						printWriter.print("  0\nLINE\n");
						printWriter.print("  8\n" + layerName + "\n");
						printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
						Point2D pt = new Point2D.Double(points[i].getX() + xC, points[i].getY() + yC);
						trans.transform(pt, pt);
						double x = TextUtils.convertDistance(pt.getX(), localPrefs.tech, dxfDispUnit);
						double y = TextUtils.convertDistance(pt.getY(), localPrefs.tech, dxfDispUnit);
						printWriter.print(" 10\n" + formatDoubleJavaVersion(x) + "\n");
						printWriter.print(" 20\n" + formatDoubleJavaVersion(y) + "\n");
						printWriter.print(" 30\n0\n");
						pt = new Point2D.Double(points[i+1].getX() + xC, points[i+1].getY() + yC);
						trans.transform(pt, pt);
						x = TextUtils.convertDistance(pt.getX(), localPrefs.tech, dxfDispUnit);
						y = TextUtils.convertDistance(pt.getY(), localPrefs.tech, dxfDispUnit);
						printWriter.print(" 11\n" + formatDoubleJavaVersion(x) + "\n");
						printWriter.print(" 21\n" + formatDoubleJavaVersion(y) + "\n");
						printWriter.print(" 31\n0\n");
					}
					if (np == Artwork.tech().closedPolygonNode)
					{
						printWriter.print("  0\nLINE\n");
						printWriter.print("  8\n" + layerName + "\n");
						printWriter.print("  5\n" + getThreeDigitHex(dxfEntityHandle++) + "\n");
						Point2D pt = new Point2D.Double(points[len-1].getX() + xC, points[len-1].getY() + yC);
						trans.transform(pt, pt);
						double x = TextUtils.convertDistance(pt.getX(), localPrefs.tech, dxfDispUnit);
						double y = TextUtils.convertDistance(pt.getY(), localPrefs.tech, dxfDispUnit);
						printWriter.print(" 10\n" + formatDoubleJavaVersion(x) + "\n");
						printWriter.print(" 20\n" + formatDoubleJavaVersion(y) + "\n");
						printWriter.print(" 30\n0\n");
						pt = new Point2D.Double(points[0].getX() + xC, points[0].getY() + yC);
						trans.transform(pt, pt);
						x = TextUtils.convertDistance(pt.getX(), localPrefs.tech, dxfDispUnit);
						y = TextUtils.convertDistance(pt.getY(), localPrefs.tech, dxfDispUnit);
						printWriter.print(" 11\n" + formatDoubleJavaVersion(x) + "\n");
						printWriter.print(" 21\n" + formatDoubleJavaVersion(y) + "\n");
						printWriter.print(" 31\n0\n");
					}
				}
			}

			// write all other nodes
			Poly [] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
			FixpTransform trans = ni.rotateOut();
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				poly.transform(trans);
				if (poly.getStyle() == Poly.Type.FILLED)
				{
					printWriter.print("  0\nSOLID\n");
					printWriter.print("  8\n" + layerName + "\n");
					Point2D [] points = poly.getPoints();
					for(int j=0; j<points.length; j++)
					{
						printWriter.print(" 1" + j + "\n" + formatDoubleJavaVersion(points[j].getX()) + "\n");
						printWriter.print(" 2" + j + "\n" + formatDoubleJavaVersion(points[j].getY()) + "\n");
						printWriter.print(" 3" + j + "\n0\n");
					}
				}
				printWriter.print("  0\nSEQEND\n");
			}
			else errorLogger.logError("Malformed POLYLINE", poly, cell, dxfEntityHandle);
			break;
		default:
			errorLogger.logError("Please add unknown poly", poly, cell, dxfEntityHandle);
			break;
		}
	}

	private String getDXFCellName(Cell cell)
	{
		if (cell.getName().equalsIgnoreCase(cell.getLibrary().getName()))
		{
			// use another name
			String buf = null;
			for(int i=1; i<1000; i++)
			{
				buf = cell.getName() + i;
				boolean found = false;
				for(Iterator<Cell> it = cell.getLibrary().getCells(); it.hasNext(); )
				{
					Cell oCell = it.next();
					if (oCell.getName().equalsIgnoreCase(buf)) { found = true;   break; }
				}
				if (!found) break;
			}
			return buf;
		}

		// just return the cell name
		return cell.getName();
	}

	private String getThreeDigit(int value)
	{
		String result = Integer.toString(value);
		while (result.length() < 3) result = " " + result;
		return result;
	}

	private String getThreeDigitHex(int value)
	{
		String result = Integer.toHexString(value).toUpperCase();
		while (result.length() < 3) result = " " + result;
		return result;
	}
}
