/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Josephson.java
 *
 * Copyright (c) 2020, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.ncc.netlist;
import java.util.HashMap;
import java.util.Map;

import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.PrimitiveNode.Function;
import com.sun.electric.tool.ncc.NccOptions;
import com.sun.electric.tool.ncc.basic.Primes;
import com.sun.electric.tool.ncc.netlist.NccNameProxy.PartNameProxy;

public class Josephson extends Part {
	private static class JosephsonPinType implements PinType {
		private Function type;
		public JosephsonPinType(Function t) {type=t;}
		public String description() {return type.getShortName();}
	}
	
	private static class JosephsonPinTypeCache {
		private final Map<Function,JosephsonPinType> typeToPinType = 
	                                   new HashMap<Function,JosephsonPinType>();
		synchronized JosephsonPinType get(Function f) {
			JosephsonPinType t = typeToPinType.get(f);
			if (t==null) {
				t = new JosephsonPinType(f);
				typeToPinType.put(f, t);
			}
			return t;
		}
	}

    // ---------- private data -------------
	private static final JosephsonPinTypeCache TYPE_TO_PINTYPE = new JosephsonPinTypeCache();
    private static final int PIN_COEFFS[] = {Primes.get(1), Primes.get(1)}; // Josephsons are symmetric
    private final double area;

    // ---------- public methods ----------
	public Josephson(Function type, PartNameProxy name, VarContext cont, double area, Wire w1, Wire w2) {
		super(name, cont, type, new Wire[]{w1, w2});
		this.area = area;
	}

    // ---------- abstract commitment ----------
	@Override
    public int[] getPinCoeffs(){return PIN_COEFFS;}
	@Override
	public String valueDescription(){
		String sz= "JJ="+area;
		return sz;
	}
	@Override
	public Integer hashCodeForParallelMerge() {
		int hc = pins[0].hashCode() + pins[1].hashCode() +
				 getClass().hashCode();
		return Integer.valueOf(hc);
	}

    // ---------- public methods ----------
//	@Override
//    public double getWidth() {return area;}
	@Override
    public double getLength() {return area;}

    public void connect(Wire ss, Wire ee){
        pins[0] = ss;
        pins[1] = ee;
		ss.add(this);
		ee.add(this);
    }
	
    //merge with this Josephson
//    public boolean parallelMerge(Part p){
//        if(p.getClass() != getClass()) return false;
//        if(this == p)return false;
//        //its the same class but a different one
//        Josephson r= (Josephson)p;
//        if(pins[0]!=r.pins[0])  r.flip();
//		if(pins[0]!=r.pins[0] || pins[1]!=r.pins[1])  return false;
//
//        //OK to merge
//        float ff= 0;
//        float pp= r.resistance();
//        float mm= resistance();
//        if(pp != 0 && mm != 0)ff= (ff * mm)/(ff + mm);
//        resistance= ff;
//        r.setDeleted();
//        return true; //return true if merged
//    }

    /** Never perform series/parallel combination of Josephsons. For layout Josephsons, inductance is 
     * a vendor dependent function of Josephson area. We'll simply annotate Josephson area
     * and compare these between schematic and layout.
     */
	@Override
    public boolean parallelMerge(Part p, NccOptions nccOpt) {return false;}

	@Override
	public int typeCode() {return type().ordinal();}
	
	// Both pins of Josephson have same PinType
	@Override
	public PinType getPinTypeOfNthPin(int n) {
		return TYPE_TO_PINTYPE.get(type());
	}

    // ---------- printing methods ----------

	@Override
    public String typeString() {return type().getShortName();}

	@Override
    public String connectionDescription(int n){
		String s = pins[0].getName();
		String e = pins[1].getName();
		return ("S= " + s + " E= " + e);
    }
    
	@Override
    public String connectionDescription(Wire w) {
    	String s = "";
    	for (int i=0; i<pins.length; i++) {
    		if (pins[i]!=w) continue;
    		if (s.length()!=0) s+=",";
    		s += i==0 ? "S" : "E";
    	}
    	return s;
    }
}

