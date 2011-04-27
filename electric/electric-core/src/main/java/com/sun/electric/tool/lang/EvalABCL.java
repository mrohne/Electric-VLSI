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

    /**
     * Method to compile a script file
     * @param script the script URL
     */
    public static CompiledScript compileScript(URL url) throws Exception {
		URLConnection conn = url.openConnection();
		InputStream stream = conn.getInputStream();
		InputStreamReader reader = new InputStreamReader(stream);
		return compileScript(reader);
    }

    /**
     * Method to compile a script file
     * @param script the script reader
     */
    public static CompiledScript compileScript(Reader reader) throws Exception {
		try {
			return ((Compilable)abclFactory.getScriptEngine()).compile(reader);
		} catch (Exception e) {
			Throwable c = e.getCause();
			System.out.println("ABCL: " + c != null ? c : e);
			e.printStackTrace();
			throw e;
		}
    }

    /**
     * Method to compile a script file
     * @param script the script string
     */
    public static CompiledScript compileScript(String string)  throws Exception {
		try {
			return ((Compilable)abclFactory.getScriptEngine()).compile(string);
		} catch (Exception e) {
			Throwable c = e.getCause();
			System.out.println("ABCL: " + c != null ? c : e);
			e.printStackTrace();
			throw e;
		}
    }

    /**
     * Method to execute a script file without starting a new Job.
     * @param url the script file name.
     */
    public static Object evalScript(URL url) throws Exception {
		URLConnection conn = url.openConnection();
		InputStream stream = conn.getInputStream();
		InputStreamReader reader = new InputStreamReader(stream);
		return evalScript(reader);
    }

    /**
     * Method to execute a script file without starting a new Job.
     * @param script the script reader
     */
    public static Object evalScript(Reader reader) throws Exception {
		try {
			return abclFactory.getScriptEngine().eval(reader);
		} catch (Exception e) {
			Throwable c = e.getCause();
			System.out.println("ABCL: " + c != null ? c : e);
			e.printStackTrace();
			throw e;
		}
	}

    /**
     * Method to execute a script file without starting a new Job.
     * @param script the script string
     */
    public static Object evalScript(String string) throws Exception {
		try {
			return abclFactory.getScriptEngine().eval(string);
		} catch (Exception e) {
			Throwable c = e.getCause();
			System.out.println("ABCL: " + c != null ? c : e);
			e.printStackTrace();
			throw e;
		}
    }

    /**
     * Method to execute a script file without starting a new Job.
     * @param script the compiled script
     */
    public static Object evalScript(CompiledScript script) throws Exception {
        try {
			return script.eval();
        } catch (Exception e) {
			Throwable c = e.getCause();
			System.out.println("ABCL: " + c != null ? c : e);
			e.printStackTrace();
			throw e;
		}
    }

    /**
     * Method to execute a script file in a Job.
     * @param fileName the script file name.
     */
    public static void runScript(URL url) {
        (new URLJob(url)).startJob();
    }

    /**
     * Display specified Cell after termination of currently running script
     * @param cell the Cell to display.
     */
    public static void displayCell(Cell cell) {
        Job curJob = Job.getRunningJob();
        if (curJob instanceof ScriptJob) {
            ((ScriptJob) curJob).displayCell(cell);
        }
    }

    private static abstract class ScriptJob extends Job {

        private Cell cellToDisplay;

        public ScriptJob(String name) {
            super(name, User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.VISCHANGES);
            cellToDisplay = Job.getUserInterface().needCurrentCell();
        }

        private void displayCell(Cell cell) {
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

    private static class URLJob extends ScriptJob {
		private URL url;
        public URLJob(URL url) {
            super("ABCL script: " + url.toString());
            this.url = url;
        }
        public boolean doIt() throws JobException {
			try {
				evalScript(url);
			} catch (Exception e) {
				throw new JobException(e);
			}
			return true;
        }
    }
}
