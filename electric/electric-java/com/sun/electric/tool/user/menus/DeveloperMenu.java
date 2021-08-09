	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DeveloperMenu.java
 *
 * Copyright (c) 2021, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.menus;

import static com.sun.electric.tool.user.menus.EMenuItem.SEPARATOR;

/**
 * Class to collect all developer operations in a pulldown menu.
 */
public class DeveloperMenu
{
	public static EMenu makeMenu()
	{
		return new EMenu("Developer",

			new EMenuItem("Ivan Sandbox") { public void run() { doIvanSandbox(); }},

			SEPARATOR,

			new EMenuItem("Maria Sandbox") { public void run() { doMariaSandbox(); }}
		);
	}

	/**
	 * Method to do a custom operation for Ivan.
	 */
	private static void doIvanSandbox()
	{
		System.out.println("Hello, Ivan");
	}

	/**
	 * Method to do a custom operation for Maria.
	 */
	private static void doMariaSandbox()
	{
		System.out.println("Hello, Maria");
	}
}
