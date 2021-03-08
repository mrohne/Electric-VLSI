/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MacOSXInterface.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

/*
 * HAVING TROUBLE COMPILING THIS MODULE?
 *
 * If the following import statements are failing to compile,
 * you are probably building Electric on a non-Macintosh system.
 * To solve the errors, simply delete this entire module from the build.
 *
 * See http://www.staticfreesoft.com/jmanual/mchap01-03.html for details.
 */
import com.apple.eawt.Application;
import com.apple.eawt.QuitResponse;
import com.apple.eawt.AppEvent;
import com.sun.electric.Main;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.menus.FileMenu;
import com.sun.electric.tool.user.menus.HelpMenu;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * Class for initializing the Macintosh OS X world.
 */
class MacOSXInterface
{
	private static MacOSXInterface adapter = null;
	private static Application application = null;
    private static List<String> argsList; // references to args list to add the file that triggers the opening
//    protected Job initJob;

	/**
	 * Method to initialize the Mac OS/X interface.
	 * @param list the arguments to the application.
	 */
	private MacOSXInterface(List<String> list)
    {
        argsList = list;
    }

	/**
	 * Method to initialize the Macintosh OS X environment.
	 * @param argsList the arguments to the application.
	 */
	@SuppressWarnings("deprecation")
	public static void registerMacOSXApplication(List<String> argsList)
	{
		// tell it to use the system menubar
		System.setProperty("com.apple.macos.useScreenMenuBar", "true");
		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Electric");

		// create Mac objects for handling the "Electric" menu
		if (application == null) application = new Application();
		if (adapter == null) adapter = new MacOSXInterface(argsList);
		application.setEnabledPreferencesMenu(true);
	}
}
