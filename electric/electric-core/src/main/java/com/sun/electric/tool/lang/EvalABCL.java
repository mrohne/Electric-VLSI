/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EvalABCL.java
 *
 * Copyright (c) 2009 Sun Microsystems and Static Free Software
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
package com.sun.electric.tool.lang;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.util.List;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.CompiledScript;
import javax.script.Compilable;

public class EvalABCL {

    private static boolean abclChecked = false;
    private static ScriptEngineFactory abclFactory;

    public static boolean hasABCL() {
		// find the ABCL class
		if (!abclChecked) {
            abclChecked = true;
			ScriptEngineManager mgr = new ScriptEngineManager();
			System.out.println("Scanning scriptFactories");
			for (ScriptEngineFactory factory: mgr.getEngineFactories()) {
				System.out.println("getEngineName(): "+factory.getEngineName());
				if (factory.getEngineName().equals("ABCL Script") &&
					factory.getEngineVersion().equals("0.1")) {
					abclFactory = factory;
				}
			}
			System.out.println("Found abclFactory: "+abclFactory);
        }
        // if already initialized, return state
        return abclFactory != null;
    }

    public static boolean runScriptNoJob(String string) {
		try {
			return abclFactory.getScriptEngine().eval(string) != null;
		} catch (ScriptException e) {
			Throwable c = e.getCause();
			System.out.println("ABCL: " + c != null ? c : e);
			e.printStackTrace();
			return false;
		}
			
    }

    public static void runScript(String string, Job.Type jobType, Job.Priority jobPriority) {
		(new ScriptJob(string, jobType, jobPriority)).startJob();
	}

    public static void displayCell(Cell cell) {
        Job curJob = Job.getRunningJob();
        if (curJob instanceof ScriptJob) {
            ((ScriptJob) curJob).displayCell(cell);
        }
    }

    private static class ScriptJob extends Job {
		private String string;
        private Cell cell;

		public ScriptJob(String string, Job.Type jobType, Job.Priority jobPriority) {
			super("ABCL script", User.getUserTool(), jobType, null, null, jobPriority);
			this.string = string;
            this.cell = null;
			System.out.println("EvalABCL.ScriptJob(\""+string+"\", "+jobType+", "+jobPriority+")");
        }

        public boolean doIt() {
			return runScriptNoJob(string);
		}

        private void displayCell(Cell cell) {
            this.cell = cell;
            fieldVariableChanged("cell");
        }

        public void terminateOK() {
            if (cell != null) {
                Job.getUserInterface().displayCell(cell);
            }
        }
    }
}
