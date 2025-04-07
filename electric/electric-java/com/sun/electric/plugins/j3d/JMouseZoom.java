/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: JMouseZoom.java
 * Written by Gilda Garreton.
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

package com.sun.electric.plugins.j3d;

import java.awt.Component;

import org.jogamp.java3d.utils.behaviors.mouse.MouseZoom;
import org.jogamp.vecmath.Matrix4d;

/**
 * Extending original zoom class to allow zoom not from original behavior
 * @author  Gilda Garreton
 * @version 0.1
 */
public class JMouseZoom extends MouseZoom
{
    public JMouseZoom(Component c, int flags) {super(c, flags);}

    public void setZoom(double factor)
    {
        // Remember old matrix
        transformGroup.getTransform(currXform);
        Matrix4d mat = new Matrix4d();
        currXform.get(mat);
        double dy = currXform.getScale() * factor;
        currXform.setScale(dy);
        transformGroup.setTransform(currXform);
        transformChanged( currXform );
    }

    void zoomInOut(boolean out)
    {
        double z_factor = Math.abs(getFactor());
//        double factor = (out) ? (0.5/z_factor) : (2*z_factor);
        double factor1 = (out) ? (1/z_factor) : (z_factor);
        setZoom(factor1);

    }
}
