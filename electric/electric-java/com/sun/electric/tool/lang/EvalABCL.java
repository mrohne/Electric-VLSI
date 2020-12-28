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

import com.sun.electric.util.TextUtils;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;

import java.util.List;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;
import java.awt.EventQueue;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.ScriptContext;
import javax.script.CompiledScript;
import javax.script.Compilable;
import javax.script.Invocable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvalABCL {

    private static boolean abclChecked = false;
    private static ScriptEngineFactory abclFactory = null;
    private static final Logger logger = LoggerFactory.getLogger(EvalABCL.class);

    public static boolean hasABCL() {
		// find the ABCL class
		if (!abclChecked) {
            abclChecked = true;
			ScriptEngineManager mgr = new ScriptEngineManager();
			System.out.println("Scanning scriptFactories for ABCL Script");
			for (ScriptEngineFactory factory: mgr.getEngineFactories()) {
				System.out.println("getEngineName(): "+factory.getEngineName());
				if (factory.getEngineName().equals("ABCL Script")) {
					abclFactory = factory;
				}
			}
			if (abclFactory != null) {
				System.out.println("Found ABCL Script: "+abclFactory);
				ScriptContext ctx = abclFactory.getScriptEngine().getContext();
				ctx.setReader(new InputStreamReader(System.in));
				ctx.setWriter(new OutputStreamWriter(System.out));
				ctx.setErrorWriter(new PrintWriter(System.out, true));
				abclFactory.getScriptEngine().setContext(ctx);
			}
        }
        // if already initialized, return state
        return abclFactory != null;
    }

	public static class Bypass extends Error {
		public Bypass(Throwable e) {
			super(e);
		}
	}

    public static boolean runScriptNoJob(final String string) {
		Runnable thunk = new Runnable() {
				public void run() {
					try {
						System.out.println("runScriptNoJob("+string+")");
						abclFactory.getScriptEngine().eval(string);
					} catch (Throwable e) {
						System.out.println("runScriptNoJob: " + e);
					}
				}
			};
		return runScriptNoJob(thunk);
	}

    public static boolean runScriptNoJob(final CompiledScript script) {
		Runnable thunk = new Runnable() {
				public void run() {
					try {
						System.out.println("runScriptNoJob: "+script);
						script.eval();
					} catch (Throwable e) {
						System.out.println("runScriptNoJob: " + e);
					}
				}
			};
		return runScriptNoJob(thunk);
	}

	public static boolean runScriptNoJob(Runnable thunk) {
		if (EventQueue.isDispatchThread()) thunk.run();
		else EventQueue.invokeLater(thunk);
		return true;
	}

    public static void runScript(final String string, final Job.Type jobType, final Job.Priority jobPriority) {
		Runnable run = 
			new Runnable() {
				public void run() {
					ScriptJob job = new ScriptJob(string, jobType, jobPriority);
					job.startJob();
				}
			};
		runScriptNoJob(run);
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

		public ScriptJob(final String string, Job.Type jobType, Job.Priority jobPriority) {
			super("ABCL script", User.getUserTool(), jobType, null, null, jobPriority);
			this.string = string;
            this.cell = null;
			System.out.println("EvalABCL.ScriptJob(\""+string+"\", "+jobType+", "+jobPriority+")");
        }

        public boolean doIt() {
			try {
				abclFactory.getScriptEngine().eval(string);
				return true;
			} catch (ScriptException e) {
				System.out.println("ScriptJob: "+e);
				return false;
			}
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

    private static class ThunkJob extends Job {
		private CompiledScript thunk;
        private Cell cell;

		public ThunkJob(CompiledScript thunk, Job.Type jobType, Job.Priority jobPriority) {
			super("ABCL thunk", User.getUserTool(), jobType, null, null, jobPriority);
			this.thunk = thunk;
            this.cell = null;
			System.out.println("EvalABCL.ThunkJob(\""+thunk+"\", "+jobType+", "+jobPriority+")");
        }

        public boolean doIt() {
			try {
				thunk.eval();
				return true;
			} catch (Throwable e) {
				return false;
			}
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

	public static void enumerate(Cell cell, Object enter, Object exit, Object visit) throws Throwable {
		try {
			VarContext context = VarContext.globalContext;
			Visitor visitor = new Visitor(enter, exit, visit);
			HierarchyEnumerator.enumerateCell(cell, context, visitor);
		}
		catch (Bypass e) {
			System.out.println("enumerate: " + e);
			throw e.getCause();
		}
	}

	public static void enumerate(Netlist netlist, Object enter, Object exit, Object visit) throws Throwable {
		try {
			VarContext context = VarContext.globalContext;
			Visitor visitor = new Visitor(enter, exit, visit);
			HierarchyEnumerator.enumerateCell(netlist, context, visitor);
		}
		catch (Bypass e) {
			System.out.println("enumerate: " + e);
			throw e.getCause();
		}
	}

	public static class CellInfo extends HierarchyEnumerator.CellInfo {
		public Object info;
		public CellInfo() {
			this.info = null;
		}
		public Object getInfo() {
			return this.info;
		}
		public void setInfo(Object info) {
			this.info = info;
		}
	}

	public static class Visitor extends HierarchyEnumerator.Visitor {
		public Visitor(Object enter, Object exit, Object visit) throws ScriptException {
			engine = abclFactory.getScriptEngine();
			dunno = engine.eval("CL:NIL");
			this.enter = enter;
			this.exit = exit;
			this.visit = visit;
		}
		private ScriptEngine engine;
		private Object dunno;
		private Object enter;
		private Object exit;
		private Object visit; 
		
		public boolean enterCell(HierarchyEnumerator.CellInfo info) {
			try {
				if (enter == null) return true;
				Object retval = ((Invocable) engine).invokeFunction("CL:FUNCALL", enter, (Object) info);
				return !retval.equals(dunno);
			}
			catch (Throwable e) {
				System.out.println("enterCell: " + e);
				throw new Bypass(e);
			}		
		}
		public void exitCell(HierarchyEnumerator.CellInfo info) {
			try {
				if (exit == null) return;
				Object retval = ((Invocable) engine).invokeFunction("CL:FUNCALL", exit, (Object) info);
				return;
			}
			catch (Throwable e) {
				System.out.println("exitCell: " + e);
				throw new Bypass(e);
			}		
		}
		public boolean visitNodeInst(Nodable node, HierarchyEnumerator.CellInfo info) {
			try {
				if (visit == null) return true;
				Object retval = ((Invocable) engine).invokeFunction("CL:FUNCALL", visit, (Object) node, (Object) info);
				return !retval.equals(dunno);
			}
			catch (Throwable e) {
				System.out.println("visitNodeInst: " + e);
				throw new Bypass(e);
			}			
		}
		public HierarchyEnumerator.CellInfo newCellInfo() {
			return new CellInfo();
		}
	}
}
