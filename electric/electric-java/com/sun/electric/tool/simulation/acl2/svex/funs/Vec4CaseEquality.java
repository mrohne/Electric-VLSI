/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4CaseEquality.java
 *
 * Copyright (c) 2017, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.simulation.acl2.svex.funs;

import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexFunction;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import java.math.BigInteger;
import java.util.Map;

/**
 * Unsafe, Verilog-style "case equality" of 4vecs.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-_D3_D3_D3>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4CaseEquality<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> x;
    public final Svex<N> y;

    private Vec4CaseEquality(Svex<N> x, Svex<N> y)
    {
        super(FUNCTION, x, y);
        this.x = x;
        this.y = y;
    }

    @Override
    public Vec4 xeval(Map<Svex<N>, Vec4> memoize)
    {
        Vec4 result = memoize.get(this);
        if (result == null)
        {
            result = Vec4Equality.FUNCTION.apply(Svex.listXeval(args, memoize));
            memoize.put(this, result);
        }
        return result;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_EQ_EQ_EQ, 2, "4vec-===");
        }

        @Override
        public <N extends SvarName> Vec4CaseEquality<N> build(Svex<N>[] args)
        {
            return new Vec4CaseEquality<>(args[0], args[1]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 x = args[0];
            Vec4 y = args[1];
            if (x.isVec2() && y.isVec2())
            {
                BigInteger xv = ((Vec2)x).getVal();
                BigInteger yv = ((Vec2)y).getVal();
                return Vec2.valueOf(xv.equals(yv));
            }
            return Vec2.valueOf(x.getUpper().equals(y.getUpper())
                && x.getLower().equals(y.getLower()));
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            return new BigInteger[]
            {
                v4maskAllOrNone(mask), v4maskAllOrNone(mask)
            };
        }
    }
}
