/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PartReport.java
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
package com.sun.electric.tool.ncc.result;

import com.sun.electric.tool.ncc.netlist.NccNameProxy.PartNameProxy;
import com.sun.electric.tool.ncc.netlist.Part;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.Job;

/** Save Part information needed by the NCC GUI when reporting
 * mismatches to the user.
 */
public class PartReport extends NetObjReport {
	static final long serialVersionUID = 0;
	public interface PartReportable extends NetObjReportable {
		PartNameProxy getNameProxy();
		String typeString();
		boolean isMos();
		boolean isResistor();
		boolean isInductor();
		boolean isJosephson();
		double getWidth();
		double getLength();
	}
	
	private final PartNameProxy nameProxy;
	private final String typeString;
	public VarContext pContext;
	private boolean isMos, isResistor, isInductor, isJosephson;
	private double width, length;
	private void checkLenWidValid() {
		Job.error(!isMos && !isResistor && !isInductor && !isJosephson,
				        "PartReport has no width or length");
	}
	public PartReport(PartReportable p) {
		super(p);
		if (p instanceof Part) pContext = ((Part)p).getContext();
		nameProxy = p.getNameProxy();
		typeString = p.typeString();
		isMos = p.isMos();
		isResistor = p.isResistor();
		isInductor = p.isInductor();
		isJosephson = p.isJosephson();
		if (isMos || isResistor) {
			width = p.getWidth();
			length = p.getLength();
		}
		if (isInductor || isJosephson) {
			length = p.getLength();
		}
	}

	public PartNameProxy getNameProxy() {return nameProxy;}
	public boolean isMos() {return isMos;}
	public boolean isResistor() {return isResistor;}
	public boolean isInductor() {return isInductor;}
	public boolean isJosephson() {return isJosephson;}
	public double getWidth() {
		checkLenWidValid();
		return width;
	}
	public double getLength() {
		checkLenWidValid();
		return length;
	}
	public String getTypeString() {
		return typeString;
	}
}
