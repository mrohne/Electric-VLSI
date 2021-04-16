/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Inductor.java
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

public class Inductor extends Part {
	private static class InductorPinType implements PinType {
		private Function type;
		public InductorPinType(Function t) {type=t;}
		public String description() {return type.getShortName();}
	}
	
	private static class InductorPinTypeCache {
		private final Map<Function,InductorPinType> typeToPinType = 
	                                   new HashMap<Function,InductorPinType>();
		synchronized InductorPinType get(Function f) {
			InductorPinType t = typeToPinType.get(f);
			if (t==null) {
				t = new InductorPinType(f);
				typeToPinType.put(f, t);
			}
			return t;
		}
	}

    // ---------- private data -------------
	private static final InductorPinTypeCache TYPE_TO_PINTYPE = 
                                                    new InductorPinTypeCache();
    private static final int PIN_COEFFS[] = 
    	{Primes.get(1), Primes.get(1)}; //inductors are symmetric
    private final double length;

    // ---------- public methods ----------
	public Inductor(Function type, PartNameProxy name, VarContext cont,
			        double length, Wire w1, Wire w2) {
		super(name, cont, type, new Wire[]{w1, w2});
		this.length = length;
	}

    // ---------- abstract commitment ----------
	@Override
    public int[] getPinCoeffs(){return PIN_COEFFS;}
	@Override
	public String valueDescription(){
		String sz= "IND="+length;
		return sz;
	}
	@Override
	public Integer hashCodeForParallelMerge() {
		int hc = pins[0].hashCode() + pins[1].hashCode() +
				 getClass().hashCode();
		return new Integer(hc);
	}

    // ---------- public methods ----------
//	@Override
//    public double getWidth() {return width;}
	@Override
    public double getLength() {return length;}

    public void connect(Wire ss, Wire ee){
        pins[0] = ss;
        pins[1] = ee;
		ss.add(this);
		ee.add(this);
    }
	
    //merge with this inductor
//    public boolean parallelMerge(Part p){
//        if(p.getClass() != getClass()) return false;
//        if(this == p)return false;
//        //its the same class but a different one
//        Inductor r= (Inductor)p;
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
    /** Never perform series/parallel combination of inductors. For layout
     * inductors, inductance is 
     * a vendor dependent function of inductor length. We'll simply
     * annotate inductor length and compare these between schematic
     * and layout. See Jon Lexau for rationale. */
	@Override
    public boolean parallelMerge(Part p, NccOptions nccOpt) {return false;}

	@Override
	public int typeCode() {return type().ordinal();}
	
	// Both pins of inductor have same PinType
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

