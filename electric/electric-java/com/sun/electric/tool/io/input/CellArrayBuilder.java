/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellArray.java
 * Written by: Adam Megacz
 *
 * Copyright (c) 2014 Static Free Software
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
 * along with Electric(tm); see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, Mass 02111-1307, USA.
 */
package com.sun.electric.tool.io.input;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.Orientation;

import java.lang.Integer;
import java.util.HashMap;
import java.util.Map;
/**
 * This class builds large instance arrays by creating a
 * logarithmic-depth tree of bisections; this makes it possible to
 * import complex foundry-provided cells (such as IO pads) without
 * creating cripplingly-huge cells.
 *
 * For example, a 256x256 GDS array reference will result in four
 * instances of a 128x128 cell; the 128x128 cell will contain four
 * instances of a 64x64 cell, and so on.
 *
 * If the requested dimensions are not square then only the larger
 * dimension is bisected; for example a 256x8 array contains a pair of
 * 128x8 instances (not four 128x4 instances) -- this ensures that the
 * minimum possible amount of non-square cells are created and
 * maximizes the reuse of cells.
 *
 * If one or both of the requested dimensions are not a power of two
 * then that dimension is partitioned into two unequal parts: one
 * which is the largest possible power of two and the other which
 * contains the remainder (for example, 9x1 becomes 8x1+1x1).  This
 * too ensures that the minimum amount of non-power-of-two-dimension
 * cells are created, which also maximizes reuse of cells.
 *
 * Arrays smaller than 4x4 are realized directly.
 */

public class CellArrayBuilder {

	public static final Variable.Key ARRAY_COLS = Variable.newKey("ATTR_GDS_array_cols");
	public static final Variable.Key ARRAY_ROWS = Variable.newKey("ATTR_GDS_array_rows");
	public static final Variable.Key ARRAY_COLSPACE = Variable.newKey("ATTR_GDS_array_colspace");
	public static final Variable.Key ARRAY_ROWSPACE = Variable.newKey("ATTR_GDS_array_rowspace");

    public final Library theLibrary;

    protected CellArrayBuilder(Library theLibrary) { this.theLibrary = theLibrary; }

    private static HashMap<Map.Entry<NodeProto, String>, CellArray> cellArrayCache =
        new HashMap<Map.Entry<NodeProto, String>, CellArray>();

    public class CellArray {

        public final NodeProto proto;
		public final Orientation orient;
        public final int cols;
        public final int rows;
        public final EPoint colspace;
        public final EPoint rowspace;
        private Cell cell = null;

        public CellArray(NodeProto proto, Orientation orient, int cols, int rows, EPoint colspace, EPoint rowspace) {
            this.proto = proto;
            this.orient = orient;
            this.cols = cols;
            this.rows = rows;
            this.colspace = colspace;
            this.rowspace = rowspace;
        }

        public NodeProto makeCell(EditingPreferences ep) {
            if (cell != null) return cell;
            String name = proto.getName();
            if (name.indexOf('{') != -1) name = name.substring(0, name.indexOf('{'));
            name += "_" + makeArrayName(orient, cols, rows, colspace, rowspace) + "{lay}";
            this.cell = Cell.newInstance(theLibrary, name);
            if (cell == null) throw new RuntimeException("Cell.newInstance("+name+") returned null");
            EPoint loc = EPoint.ORIGIN;
            buildArrayUsingSubcells(proto, cell, loc, orient, cols, rows, colspace, rowspace, ep);
            return cell;
        }
    }

    private String makeArrayName(Orientation orient, int cols, int rows, EPoint colspace, EPoint rowspace) {
    	return cols + "x" + rows + "sep" + 
			TextUtils.formatDouble(colspace.getX()) + "," + TextUtils.formatDouble(colspace.getY()) + "x" + 
			TextUtils.formatDouble(rowspace.getX()) + "," + TextUtils.formatDouble(rowspace.getY()) + "x" +
			orient.toString();
    }

    private CellArray getCellArray(NodeProto proto, Orientation orient, int cols, int rows, EPoint colspace, EPoint rowspace) {
        Map.Entry<NodeProto, String> key =
            new java.util.AbstractMap.SimpleEntry<NodeProto, String>(proto, makeArrayName(orient, cols, rows, colspace, rowspace));
        CellArray ret = cellArrayCache.get(key);
        if (ret == null) {
            ret = new CellArray(proto, orient, cols, rows, colspace, rowspace);
            cellArrayCache.put(key, ret);
        }
        return ret;
    }

    public void buildArrayUsingSubcells(NodeProto proto, Cell parent, EPoint startLoc, Orientation orient,
                                        int cols, int rows, EPoint colspace, EPoint rowspace, EditingPreferences ep) {
		if (rows < 4 && cols < 4) {
			// leaf cell of the bisection hierarchy
			buildFlatArray(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
		} else {
			// non-leaf cell of the bisection hierarchy
			buildArrayBisected(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
		}
    }

    /** makes an array with subcells */
    public void buildArrayBisected(NodeProto proto, Cell parent, EPoint startLoc, Orientation orient,
                                   int cols, int rows, EPoint colspace, EPoint rowspace, EditingPreferences ep) {
		EPoint colLoc = startLoc;
        for(int x=0; x<cols; ) {
            int width = 1;
			EPoint colOffset = EPoint.snap(colspace);
            while ((width<<1)+x <= cols && (width<<1)<=(cols>=rows?cols/2:cols)) {
				width = width<<1;
				colOffset = EPoint.fromFixp(colOffset.getFixpX()<<1, colOffset.getFixpY()<<1);
			}
			EPoint rowLoc = colLoc;
            for(int y=0; y<rows; ) {
                int height = 1;
				EPoint rowOffset = EPoint.snap(rowspace);
                while ((height<<1)+y <= rows && (height<<1)<=(rows>=cols?rows/2:rows)) {
					height = height<<1;
					rowOffset = EPoint.fromFixp(rowOffset.getFixpX()<<1, rowOffset.getFixpY()<<1);
				}
				NodeProto arrCell = getCellArray(proto, orient, width, height, colspace, rowspace).makeCell(ep);
				NodeInst.makeInstance(arrCell, ep, rowLoc, arrCell.getDefWidth(null), arrCell.getDefHeight(null), 
									  parent, Orientation.IDENT, null);
                y += height;
				rowLoc = EPoint.fromFixp(rowLoc.getFixpX()+rowOffset.getFixpX(), rowLoc.getFixpY()+rowOffset.getFixpY());
            }
            x += width;
			colLoc = EPoint.fromFixp(colLoc.getFixpX()+colOffset.getFixpX(), colLoc.getFixpY()+colOffset.getFixpY());
        }
    }

    /** makes an array the "dumb way" */
    public void buildFlatArray(NodeProto proto, Cell parent, EPoint startLoc, Orientation orient,
                               int cols, int rows, EPoint colspace, EPoint rowspace, EditingPreferences ep) {
		EPoint colLoc = startLoc;
        for (int ic = 0; ic < cols; ic++) {
			EPoint colOffset = EPoint.snap(colspace);
			EPoint rowLoc = colLoc;
            for (int ir = 0; ir < rows; ir++) {
				EPoint rowOffset = EPoint.snap(rowspace);
                NodeInst ni = NodeInst.makeInstance(proto, ep,
													rowLoc,
													proto.getDefWidth(null), proto.getDefHeight(null), 
													parent, orient, null);
                rowLoc = EPoint.fromFixp(rowLoc.getFixpX()+rowOffset.getFixpX(), rowLoc.getFixpY()+rowOffset.getFixpY());
            }
			colLoc = EPoint.fromFixp(colLoc.getFixpX()+colOffset.getFixpX(), colLoc.getFixpY()+colOffset.getFixpY());
        }
    }

    /** makes an annotate array */
    public void buildAnnotateArray(NodeProto proto, Cell parent, EPoint startLoc, Orientation orient,
                               int cols, int rows, EPoint colspace, EPoint rowspace, EditingPreferences ep) {
		NodeInst ni = NodeInst.makeInstance(proto, ep,
											startLoc,
											proto.getDefWidth(null), proto.getDefHeight(null), 
											parent, orient, null);
		ni.newDisplayVar(ARRAY_COLS, new Integer(cols), ep);
		ni.newDisplayVar(ARRAY_ROWS, new Integer(rows), ep);
		ni.newDisplayVar(ARRAY_COLSPACE, colspace.toString(), ep);
		ni.newDisplayVar(ARRAY_ROWSPACE, rowspace.toString(), ep);
    }

	public void buildArray(NodeProto proto, Cell parent,
						   EPoint startLoc, Orientation orient,
						   int cols, int rows,
						   EPoint colspace, EPoint rowspace, EditingPreferences ep) {
		System.err.println("CellArrayBuilder: "+proto+" "+parent);
	}

	public static class Simple extends CellArrayBuilder {
		public Simple(Library theLibrary) { super(theLibrary); }
		/** makes an array as simple as possible */
		@Override
		public void buildArray(NodeProto proto, Cell parent,
							   EPoint startLoc, Orientation orient,
							   int cols, int rows,
							   EPoint colspace, EPoint rowspace, EditingPreferences ep) {
			if (cols<1) throw new Error();
			if (rows<1) throw new Error();
			buildFlatArray(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
		}
	}
	public static class Bisection extends CellArrayBuilder {
		public Bisection(Library theLibrary) { super(theLibrary); }
		/** makes an array as intelligently as possible */
		@Override
		public void buildArray(NodeProto proto, Cell parent,
							   EPoint startLoc, Orientation orient,
							   int cols, int rows,
							   EPoint colspace, EPoint rowspace, EditingPreferences ep) {
			if (cols<1) throw new Error();
			if (rows<1) throw new Error();
			buildArrayUsingSubcells(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
		}
	}
	public static class Annotate extends CellArrayBuilder {
		public Annotate(Library theLibrary) { super(theLibrary); }
		@Override
		public void buildArray(NodeProto proto, Cell parent,
							   EPoint startLoc, Orientation orient,
							   int cols, int rows,
							   EPoint colspace, EPoint rowspace, EditingPreferences ep) {
			if (cols<1) throw new Error();
			if (rows<1) throw new Error();
			buildAnnotateArray(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
		}
	}
}
