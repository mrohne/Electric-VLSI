/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: OverlapHistoryLockFree.java
 * Written by Team 7: Felix Schmidt, Daniel Lechner
 * 
 * This code has been developed at the Karlsruhe Institute of Technology (KIT), Germany, 
 * as part of the course "Multicore Programming in Practice: Tools, Models, and Languages".
 * Contact instructor: Dr. Victor Pankratius (pankratius@ipd.uka.de)
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement.forceDirected2.forceDirected.util.history;

import com.sun.electric.tool.placement.PlacementFrame.PlacementNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Parallel Placement
 */
public class OverlapHistoryLockFree extends OverlapHistory<PlacementNode> {

	private static Map<Integer, OverlapHistoryLockFree> histories = new HashMap<Integer, OverlapHistoryLockFree>();

	public static OverlapHistoryLockFree getInstance(int id) {

		OverlapHistoryLockFree history;
		if ((history = histories.get(Integer.valueOf(id))) == null) {
			history = new OverlapHistoryLockFree();
			histories.put(Integer.valueOf(id), history);
		}

		return history;
	}

	private OverlapHistoryLockFree() {

	}

}
