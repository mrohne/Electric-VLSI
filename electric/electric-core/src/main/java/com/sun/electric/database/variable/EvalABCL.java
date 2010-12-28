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
package com.sun.electric.database.variable;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.util.List;
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

public class EvalABCL {

    private static boolean abclChecked = false;
    private static boolean abclInited = false;
    private static ScriptEngineFactory abclFactory;
    private static ScriptEngine abclEngine;

    public static boolean hasABCL() {
		// find the ABCL class
		if (!abclChecked) {
            abclChecked = true;

            // find the ABCL class
			ScriptEngineManager mgr = new ScriptEngineManager();
            // find the ABCL class
			List<ScriptEngineFactory> scriptFactories =  mgr.getEngineFactories();
			for (ScriptEngineFactory factory: scriptFactories) {
				System.out.println("getEngineName(): "+factory.getEngineName());
				if (factory.getEngineName().equals("ABCL Script") &&
					factory.getEngineVersion().equals("0.1")) {
					abclFactory = factory;
					break;
				}
			} 			
        }

        // if already initialized, return state
        return abclFactory != null;
    }

    private static void initABCL() {
        if (!hasABCL()) {
            return;
        }
        if (abclInited) {
            return;
        }
        try {
            abclEngine = abclFactory.getScriptEngine();
			abclInited = true;
        } catch (Throwable e) {
            abclEngine = null;
        }
    }

    /**
     * Method to execute a script file without starting a new Job.
     * @param fileName the script file name.
     */
    public static void runScriptNoJob(String fileName) {
		initABCL();
        try {
			URL url = TextUtils.makeURLToFile(fileName);
            URLConnection con = url.openConnection();
            InputStream str = con.getInputStream();
			InputStreamReader rdr = new InputStreamReader(str);
			abclEngine.eval(rdr);
        } catch (Throwable e) {
			e.printStackTrace();
			Throwable ourCause = e.getCause();
			if (ourCause != null) {
				System.out.println("ABCL: " + ourCause);
			} else {
				System.out.println("ABCL: " + e);
			}
		}
    }

    /**
     * Method to execute a script file in a Job.
     * @param fileName the script file name.
     */
    public static void runScript(String fileName) {
        (new EvalABCL.RunABCLScriptJob(fileName)).startJob();
    }

    /**
     * Display specified Cell after termination of currently running script
     * @param cell the Cell to display.
     */
    public static void displayCell(Cell cell) {
        Job curJob = Job.getRunningJob();
        if (curJob instanceof RunABCLScriptJob) {
            ((RunABCLScriptJob) curJob).displayCell(cell);
        }
    }

    private static class RunABCLScriptJob extends Job {

        private String script;
        private Cell cellToDisplay;

        public RunABCLScriptJob(String script) {
            super("ABCL script: " + script, User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.VISCHANGES);
            this.script = script;
            cellToDisplay = null;
        }

        public boolean doIt() throws JobException {
            runScriptNoJob(script);
            return true;
        }

        private void displayCell(Cell cell) {
            if (cellToDisplay != null) {
                return;
            }
            cellToDisplay = cell;
            fieldVariableChanged("cellToDisplay");
        }

        @Override
        public void terminateOK() {
            if (cellToDisplay != null) {
                Job.getUserInterface().displayCell(cellToDisplay);
            }
        }
    }
}
