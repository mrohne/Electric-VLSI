/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TestLauncher.java
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
package com.sun.electric;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.sun.electric.util.CollectionFactory;

/**
 * @author Felix Schmidt*/
public class TestLauncher {
    
    private List<String> args;
    
    public TestLauncher() {
        args = CollectionFactory.createArrayList();
        args.add("-anything=test");
        args.add("-anything2=test");
        args.add("-springconfig=testconfig");
        args.add("-additionalfolder=testfolder");
    }
    
    @Ignore
    @Test
    public void testGetSpringConfig() throws Exception {
        Class<?> clazz = Class.forName("com.sun.electric.Electric");
        Electric launcher = (Electric) clazz.newInstance();
    } 
}
