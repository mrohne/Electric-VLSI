/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ManageInductors.java
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.math.Orientation;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "Manage Inductors" dialog.
 */
public class ManageInductors extends EModelessDialog implements /*HighlightListener,*/ DatabaseChangeListener
{
	private static double lastAreaFactor = 1;
	private static double lastLengthFactor = 1;
	private static double lastSquaresPerCorner = 0.5587;
	private static Map<Cell,CellInductance> allInductanceData = new HashMap<Cell,CellInductance>();
	private CellInductance curInductanceData;
	private NodeInst inductorNode;
	private double computedInductance;
	private boolean noHighlightUpdate = false, noInductListUpdate = false, noInductArcsUpdate = false;
	private static final int PRECISION = 3;

	/**
	 * Method to display the dialog for filling-in inductor values.
	 */
	public static void showInductorManagementDialog()
	{
		ManageInductors dialog = new ManageInductors(TopLevel.getCurrentJFrame());
		dialog.setVisible(true);
	}

	/** Creates new form Calculate Inductances */
	private ManageInductors(Frame parent)
	{
		super(parent);
		initComponents();

		// make all text fields select-all when entered
		EDialog.makeTextFieldSelectAllOnTab(areaFactor);
		EDialog.makeTextFieldSelectAllOnTab(lengthFactor);
		EDialog.makeTextFieldSelectAllOnTab(perCornerFactor);

		areaFactor.setText(lastAreaFactor+"");
		lengthFactor.setText(lastLengthFactor+"");
		perCornerFactor.setText(lastSquaresPerCorner+"");

		fasthenryDefWidthSubdivs.setText("default=" + SimulationTool.getFastHenryWidthSubdivisions());
		fasthenryDefHeightSubdivs.setText("default=" + SimulationTool.getFastHenryHeightSubdivisions());
		fasthenryDefThickness.setText("default=" + TextUtils.formatDistance(SimulationTool.getFastHenryDefThickness()));
		fasthenryDefZHeight.setText("");

		UserInterfaceMain.addDatabaseChangeListener(this);
//		Highlighter.addHighlightListener(this);
		listOfInductors.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent e) { inductorSelected(); }
		});
		listOfArcsOnInductor.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent e) { inductorArcSelected(); }
		});

		finishInitialization();
		showSelected();
		pack();
	}

	protected void escapePressed() { closeDialog(null); }

	/**
	 * Respond to database changes and updates the dialog.
	 * @param e database change event.
	 */
	public void databaseChanged(DatabaseChangeEvent e)
	{
		if (!isVisible()) return;
		if (!stayCurrent.isSelected()) return;
		updateCurrentCell(false);
	}

	private String updateCurrentCell(boolean brief)
	{
		// update all inductors in the cell
		EditWindow curWnd = EditWindow.getCurrent();
		if (curWnd == null) return null;
		Cell cell = curWnd.getCell();
		if (cell == null) return null;
		if (curInductanceData == null) allInductanceData.put(cell, curInductanceData = new CellInductance(cell));
		if (curInductanceData.cell != cell) return null;
		curInductanceData.recalculate();

		// update list of inductors
		noInductArcsUpdate = true;
		String[] inductorArray = new String[curInductanceData.inductorNames.size()];
		int j = 0;
		for(String str : curInductanceData.inductorNames) inductorArray[j++] = str;
		String formerSelection = listOfInductors.getSelectedValue();
		listOfInductors.setListData(inductorArray);
		if (formerSelection != null && curInductanceData.inductorNames.contains(formerSelection))
		{
			listOfInductors.setSelectedValue(formerSelection, true);
		} else
		{
			if (listOfInductors.getComponentCount() > 0)
				listOfInductors.setSelectedIndex(0);
		}
		noInductArcsUpdate = false;

		// get default area and length factors
		double defaultAreaFactor = TextUtils.atof(areaFactor.getText());
		double defaultLengthFactor = TextUtils.atof(lengthFactor.getText());

		Map<NodeInst,Double> newValues = new HashMap<NodeInst,Double>();
		NodeInst sellectedInductor = null;
		List<ArcInst> selectedArcs = null;
		List<ArcInst> deletedArcs = null;
		double selectedAreaFactor = 0, selectedLengthFactor = 0;
		StringBuffer changedExplanation = new StringBuffer();
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			NodeProto np = ni.getProto();
			if (np.getFunction() == PrimitiveNode.Function.INDUCT)
			{
				List<ArcInst> arcsOnInductor = new ArrayList<ArcInst>();
				List<ArcInst> arcsToDelete = new ArrayList<ArcInst>();
				getArcsOnInductor(ni, ni.getName(), arcsOnInductor, arcsToDelete);
				StringBuffer sb = new StringBuffer();
				double areaFactor = defaultAreaFactor;
				double lengthFactor = defaultLengthFactor;
				Layer lay = getLayerWithFactors(ni);
				if (lay != null)
				{
					double area = lay.getInductanceAreaFactor();
					double length = lay.getInductanceLengthFactor();
					if (area != 0) areaFactor = area;
					if (length != 0) lengthFactor = length;
				}
				double inductance = analyzeInductor(sb, ni, arcsOnInductor, areaFactor, lengthFactor, calculateInductorWidth(arcsOnInductor));

				Variable exists = ni.getVar(Schematics.SCHEM_INDUCTANCE);
				if (exists != null)
				{
					Object obj = exists.getObject();
					double val = 0;
					if (obj instanceof Double) val = ((Double)obj).doubleValue();
					if (obj instanceof String) val = Double.parseDouble((String)obj);
					if (val != inductance)
					{
						sellectedInductor = ni;
						newValues.put(ni, inductance);
						selectedArcs = arcsOnInductor;
						deletedArcs = arcsToDelete;
						selectedAreaFactor = areaFactor;
						selectedLengthFactor = lengthFactor;
						if (brief) changedExplanation.append("Updated " + ni.getName() + " inductance from " +
							TextUtils.formatDouble(val, PRECISION) + " to " + TextUtils.formatDouble(inductance, PRECISION) + "\n"); else
								changedExplanation.append(sb.toString());
					}
				}
			}
		}
		if (newValues.size() > 0)
		{
			if (newValues.size() == 1)
			{
				// highlight the inductor and update the arcs on it
				noInductArcsUpdate = noHighlightUpdate = true;
				String inductorName = sellectedInductor.getName();
				listOfInductors.setSelectedValue(inductorName, true);
				String[] arcNames = new String[selectedArcs.size()];
				for(int i=0; i<selectedArcs.size(); i++) arcNames[i] = selectedArcs.get(i).getName();
				listOfArcsOnInductor.clearSelection();
				listOfArcsOnInductor.setListData(arcNames);
				(new UpdateInductanceNames(selectedArcs, deletedArcs, inductorName, null)).startJob();
				noInductArcsUpdate = noHighlightUpdate = false;
				if (selectedAreaFactor == 0)
				{
					areaFactorSuggestion.setText("No suggestion");
					useAreaFactor.setEnabled(false);
				} else
				{
					areaFactorSuggestion.setText("Suggest: " + selectedAreaFactor);
					useAreaFactor.setEnabled(true);
				}
				if (selectedLengthFactor == 0)
				{
					lengthFactorSuggestion.setText("No suggestion");
					useLengthFactor.setEnabled(false);
				} else
				{
					lengthFactorSuggestion.setText("Suggest: " + selectedLengthFactor);
					useLengthFactor.setEnabled(true);
				}
			}
			if (!brief)
			{
				inductorInfo.setText(changedExplanation.toString());
				System.out.println("Inductance management: Updating " + newValues.size() + " inductor values");
			}
			(new AnnotateCellInductance(newValues)).startJob();
		}
		if (brief)
		{
			if (changedExplanation.length() == 0) return "Nothing changed in cell " + cell.describe(false);
			return "Changes in cell " + cell.describe(false) + ":\n" + changedExplanation.toString();
		}
		return null;
	}

//	/**
//	 * Recache the current Cell data when Highlights change.
//	 */
//	public void highlightChanged(Highlighter which)
//	{
//		if (!isVisible()) return;
//		if (stayCurrent.isSelected()) return;
//		makeInductanceDataCurrent();
//	}

	/**
	 * Called when by a Highlighter when it loses focus. The argument
	 * is the Highlighter that has gained focus (may be null).
	 * @param highlighterGainedFocus the highlighter for the current window (may be null).
	 */
	public void highlighterLostFocus(Highlighter highlighterGainedFocus) {}

	/********************************** MANAGE LIST OF INDUCTORS **********************************/

	private List<ArcInst> getArcNamesOnInductor(String inductorName)
	{
		List<ArcInst> selectedArcs = new ArrayList<ArcInst>();
		for(Iterator<ArcInst> it = curInductanceData.cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			Variable var = ai.getVar(Schematics.INDUCTOR_NAME);
			if (var == null) continue;
			if (inductorName.equals(var.getPureValue(-1)))
				selectedArcs.add(ai);
		}
		return selectedArcs;
	}

	/**
	 * Method called when a name is selected from the list of inductors.
	 */
	private void inductorSelected()
	{
		if (curInductanceData == null) return;
		if (noInductArcsUpdate) return;
		int index = listOfInductors.getSelectedIndex();
		if (index < 0) return;
		String inductorName = listOfInductors.getSelectedValue();
		List<ArcInst> selectedArcs = getArcNamesOnInductor(inductorName);
		String[] arcNames = new String[selectedArcs.size()];
		for(int i=0; i<selectedArcs.size(); i++) arcNames[i] = selectedArcs.get(i).getName();
		listOfArcsOnInductor.clearSelection();
		listOfArcsOnInductor.setListData(arcNames);

		NodeInst inductorNode = null;
		for(Iterator<NodeInst> it = curInductanceData.cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance()) continue;
			if (ni.getName().equals(inductorName))
			{
				inductorNode = ni;
				break;
			}
		}
		curInductanceData.suggestedAreaFactor = curInductanceData.suggestedLengthFactor = 0;
		if (inductorNode != null)
		{
			Layer lay = getLayerWithFactors(inductorNode);
			if (lay != null)
			{
				double area = lay.getInductanceAreaFactor();
				double length = lay.getInductanceLengthFactor();
				if (area != 0) curInductanceData.suggestedAreaFactor = area;
				if (length != 0) curInductanceData.suggestedLengthFactor = length;
			}
		}
		if (curInductanceData.suggestedAreaFactor == 0)
		{
			areaFactorSuggestion.setText("No suggestion");
			useAreaFactor.setEnabled(false);
		} else
		{
			areaFactorSuggestion.setText("Suggest: " + curInductanceData.suggestedAreaFactor);
			useAreaFactor.setEnabled(true);
		}
		if (curInductanceData.suggestedLengthFactor == 0)
		{
			lengthFactorSuggestion.setText("No suggestion");
			useLengthFactor.setEnabled(false);
		} else
		{
			lengthFactorSuggestion.setText("Suggest: " + curInductanceData.suggestedLengthFactor);
			useLengthFactor.setEnabled(true);
		}

		EditWindow wnd = EditWindow.getCurrent();
		if (wnd != null && !noHighlightUpdate)
		{
			noInductListUpdate = true;
			Highlighter highlighter = wnd.getHighlighter();
			highlighter.clear();
			for(ArcInst ai : selectedArcs)
				highlighter.addElectricObject(ai, curInductanceData.cell);
			if (inductorNode != null)
			{
				highlighter.addElectricObject(inductorNode, curInductanceData.cell);
				highlighter.addText(inductorNode, curInductanceData.cell, NodeInst.NODE_NAME);
			}
			highlighter.finished();
			noInductListUpdate = false;
		}

		// set the FastHenry parameters if found on any arcs
		double thickness = -1, zHeight = -1;
		int widthSubdivs = -1, heightSubdivs = -1;
		for(ArcInst ai : selectedArcs)
		{
			Technology tech = ai.getProto().getTechnology();
			Variable var = ai.getVar(Schematics.INDUCTOR_THICKNESS);
			if (var != null)
			{
				double t;
				if (var.getObject() instanceof Integer) t = ((Integer)var.getObject()).intValue() / tech.getScale(); else
					t = TextUtils.atof(var.getPureValue(-1));
				thickness = Math.max(thickness, t);
			}

			// get the width subdivisions
			var = ai.getVar(Schematics.INDUCTOR_WIDTH_SUBDIVS);
			if (var != null)
			{
				int w = TextUtils.atoi(var.getPureValue(-1));
				widthSubdivs = Math.max(widthSubdivs, w);
			}

			// get the height subdivisions
			var = ai.getVar(Schematics.INDUCTOR_HEIGHT_SUBDIVS);
			if (var != null)
			{
				int h = TextUtils.atoi(var.getPureValue(-1));
				heightSubdivs = Math.max(heightSubdivs, h);
			}
		}
		fasthenryThickness.setText(thickness < 0 ? "" : TextUtils.formatDistance(thickness));
		fasthenryZHeight.setText(zHeight < 0 ? "" : TextUtils.formatDistance(zHeight));
		fasthenryWidthSubdivs.setText(widthSubdivs < 0 ? "" : widthSubdivs+"");
		fasthenryHeightSubdivs.setText(heightSubdivs < 0 ? "" : heightSubdivs+"");

		// set the default Z height if an arc was found
		fasthenryDefZHeight.setText("");
		if (selectedArcs.size() > 0)
		{
			ArcInst ai = selectedArcs.get(0);
			Technology tech = ai.getProto().getTechnology();
			Poly [] polys = tech.getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				Layer layer = poly.getLayer();
				if (layer == null) continue;
				double zDefault = layer.getDepth();
				fasthenryDefZHeight.setText("default=" + TextUtils.formatDistance(zDefault));
				break;
			}
		}
	}

	private Layer getLayerWithFactors(NodeInst ni)
	{
		PrimitiveNode np = (PrimitiveNode)ni.getProto();
		for(Iterator<Layer> it = np.getLayerIterator(); it.hasNext(); )
		{
			Layer lay = it.next();
			double area = lay.getInductanceAreaFactor();
			double length = lay.getInductanceLengthFactor();
			if (area != 0 || length != 0) return lay;
		}
		return null;
	}

	private void useSuggestedAreaFactor()
	{
		areaFactor.setText(curInductanceData.suggestedAreaFactor+"");
	}

	private void useSuggestedLengthFactor()
	{
		lengthFactor.setText(curInductanceData.suggestedLengthFactor+"");
	}

	/**
	 * Method called when the user clicks "New..." to create a new inductor name.
	 */
	private void addNewInductor()
	{
		for(;;)
		{
			String newName = Job.getUserInterface().askForInput("Inductor Name:", "Create New Inductor", "");
			if (newName == null) return;
			newName = newName.trim();
			if (newName.length() == 0) return;

			// see if the name is unique
			if (curInductanceData.inductorNames.contains(newName))
			{
				Job.getUserInterface().showErrorMessage("There is already an inductor called " + newName, "Duplicate Inductor Name");
				continue;
			}
			curInductanceData.addDefinedInductorName(newName);
			showSelected();
			break;
		}
	}

	/**
	 * Method called when the user clicks "Rename..." to rename an inductor.
	 */
	private void renameInductor()
	{
		String inductorName = needSelectedInductor();
		if (inductorName == null) return;

		String newName = Job.getUserInterface().askForInput("New Name for Inductor " + inductorName + ":", "Rename Inductor", "");
		if (newName == null) return;
		newName = newName.trim();
		if (newName.length() == 0) return;

		// see if the name is unique
		if (curInductanceData.inductorNames.contains(newName))
		{
			Job.getUserInterface().showErrorMessage("There is already an inductor called " + newName, "Duplicate Inductor Name");
			return;
		}
		String[] inductorNames = {inductorName};
		String[] newNames = {newName};
		(new RenameInductor(curInductanceData.cell, inductorNames, newNames, this)).startJob();
	}

	/**
	 * Method called when the user clicks "Rename All..." to rename all inductors.
	 */
	private void renameAllInductors()
	{
		String namePattern = Job.getUserInterface().askForInput("New name for inductors (use '*' where numbers will go):", "Rename Inductors", "");
		if (namePattern == null) return;
		namePattern = namePattern.trim();
		if (namePattern.length() == 0) return;
		int starPos = namePattern.indexOf("*");
		if (starPos < 0)
		{
			Job.getUserInterface().showErrorMessage("Inductor name pattern must have a '*' where the numbers will go", "Bad Pattern Name");
			return;
		}
		String left = namePattern.substring(0, starPos);
		String right = namePattern.substring(starPos+1);

		// rename all the inductors
		int numInds = listOfInductors.getModel().getSize();
		List<String> oldNameList = new ArrayList<String>();
		List<String> newNameList = new ArrayList<String>();
		for(int i = 0; i < numInds; i++)
		{
			String oldName = listOfInductors.getModel().getElementAt(i);
			String newName = left + (i+1) + right;
			if (oldName.equals(newName)) continue;
			oldNameList.add(oldName);
			newNameList.add(newName);
		}
		String[] oldNames = new String[oldNameList.size()];
		for(int i=0; i<oldNameList.size(); i++) oldNames[i] = oldNameList.get(i);
		String[] newNames = new String[newNameList.size()];
		for(int i=0; i<newNameList.size(); i++) newNames[i] = newNameList.get(i);
		(new RenameInductor(curInductanceData.cell, oldNames, newNames, this)).startJob();
	}

	/**
	 * This class finishes the "Rename..." function by renaming arcs and nodes.
	 */
	public static class RenameInductor extends Job
	{
		private String[] oldNames, newNames;
		private Cell cell;
		private transient ManageInductors dialog;

		public RenameInductor(Cell c, String[] oNames, String[] nNames, ManageInductors mi)
		{
			super("Rename Inductor", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			cell = c;
			oldNames = oNames;
			newNames = nNames;
			dialog = mi;
		}

		@Override
		public boolean doIt() throws JobException
		{
			for(int i=0; i<oldNames.length; i++)
			{
				for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
				{
					NodeInst ni = nIt.next();
					if (ni.getName().equals(oldNames[i])) ni.setName(newNames[i]);
				}
				for(Iterator<ArcInst> aIt = cell.getArcs(); aIt.hasNext(); )
				{
					ArcInst ai = aIt.next();
					Variable var = ai.getVar(Schematics.INDUCTOR_NAME);
					if (var != null)
					{
						String iName = var.getPureValue(-1);
						if (iName.equals(oldNames[i]))
							ai.newVar(Schematics.INDUCTOR_NAME, newNames[i], var.getTextDescriptor());
					}
				}
			}
			return true;
		}

		public void terminateOK()
		{
			for(int i=0; i<oldNames.length; i++)
			{
				dialog.listOfInductors.setSelectedValue(newNames[i], true);
				dialog.curInductanceData.inductorNames.remove(oldNames[i]);
				dialog.curInductanceData.inductorNames.add(newNames[i]);
			}
			dialog.showSelected();
		}
	}

	/**
	 * Method called when the user clicks "Delete" to remove an inductor name.
	 */
	private void deleteInductor()
	{
		String inductorName = needSelectedInductor();
		if (inductorName == null) return;
		if (curInductanceData.inductorNamesonNodes.contains(inductorName))
		{
			Job.getUserInterface().showErrorMessage("This inductor is defined by the node named " + inductorName +
				". Delete the inductor node or rename it.", "Cannot Delete Inductor");
			return;
		}
		if (curInductanceData.inductorNamesDefined.contains(inductorName))
		{
			curInductanceData.inductorNamesDefined.remove(inductorName);
			showSelected();
			return;
		}
		if (curInductanceData.inductorNamesonArcs.contains(inductorName))
		{
			// must delete this name on all arcs
			List<ArcInst> disconnectedArcs = new ArrayList<ArcInst>();
			for(Iterator<ArcInst> it = curInductanceData.cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				Variable var = ai.getVar(Schematics.INDUCTOR_NAME);
				if (var == null) continue;
				String name = var.getPureValue(-1);
				if (name.equals(inductorName)) disconnectedArcs.add(ai);
			}

			// remove the inductor name on these arcs
			(new UpdateInductanceNames(null, disconnectedArcs, null, this)).startJob();
			return;
		}
	}

	private void showSelected()
	{
		makeInductanceDataCurrent();

		curInductanceData.recalculate();
		noHighlightUpdate = true;
		listOfArcsOnInductor.clearSelection();
		listOfArcsOnInductor.setListData(new String[0]);
		TitledBorder border = (TitledBorder)inductorListPanel.getBorder();
		if (curInductanceData.cell == null)
		{
			border.setTitle("NO CURRENT CELL");
			listOfInductors.setListData(new String[0]);
		} else
		{
			border.setTitle("Inductors in Cell " + curInductanceData.cell.describe(false));
			String[] inductorArray = new String[curInductanceData.inductorNames.size()];
			int i = 0;
			for(String str : curInductanceData.inductorNames) inductorArray[i++] = str;
			String formerSelection = listOfInductors.getSelectedValue();
			listOfInductors.setListData(inductorArray);
			if (formerSelection != null && curInductanceData.inductorNames.contains(formerSelection))
			{
				listOfInductors.setSelectedValue(formerSelection, true);
			} else
			{
				if (listOfInductors.getComponentCount() > 0)
					listOfInductors.setSelectedIndex(0);
			}
		}
		inductorListPanel.repaint();
		noHighlightUpdate = false;
	}

	/********************************** MANAGE LIST OF ARCS ON THE SELECTED INDUCTOR **********************************/

	/**
	 * Method called when an arc name on the selected inductor is clicked.
	 */
	private void inductorArcSelected()
	{
		if (curInductanceData == null) return;
		String arcName = listOfArcsOnInductor.getSelectedValue();
		if (arcName == null) return;
		ArcInst ai = curInductanceData.cell.findArc(arcName);
		if (ai == null) return;

		if (noHighlightUpdate) return;
		EditWindow wnd = EditWindow.getCurrent();
		if (wnd != null)
		{
			Highlighter highlighter = wnd.getHighlighter();
			highlighter.clear();
			highlighter.addElectricObject(ai, curInductanceData.cell);
			highlighter.finished();
		}
	}

	/**
	 * Method to compute the arcs connected to an inductor node.
	 * @param ni the inductor node.
	 * @param inductorName the name of the inductor node.
	 * @param arcsToAdd a List that gets filled with ArcInst objects on the inductor.
	 * @param arcsToDelete a List that gets filled with ArcInst objects no longer on the inductor.
	 */
	private void getArcsOnInductor(NodeInst ni, String inductorName, List<ArcInst> arcsToAdd, List<ArcInst> arcsToDelete)
	{
		// determine arc type that matters
		double inductorSize = ni.getYSize();
		ArcProto ap = ni.getProto().getPort(0).getBasePort().getConnection();
		PrimitiveNode pnp = ap.findPinProto();

		for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
		{
			Connection con = cIt.next();
			ArcInst followArc = con.getArc();
			if (followArc.getLambdaBaseWidth() != inductorSize) continue;
			arcsToAdd.add(followArc);
			NodeInst followNode = ni;
			for(;;)
			{
				NodeInst nextNode = followArc.getHeadPortInst().getNodeInst() == followNode ?
					followArc.getTailPortInst().getNodeInst() : followArc.getHeadPortInst().getNodeInst();
				if (nextNode.getProto() != pnp) break;

				PortInst pi = nextNode.getOnlyPortInst();
				ArcInst otherArc = null;
				for(Iterator<Connection> c2It = pi.getConnections(); c2It.hasNext(); )
				{
					Connection con2 = c2It.next();
					ArcInst ai2 = con2.getArc();
					if (ai2 == followArc) continue;
					if (ai2.getLambdaBaseWidth() != inductorSize) { otherArc = null;   break; }
					if (otherArc != null) { otherArc = null;   break; }
					otherArc = ai2;
				}
				if (otherArc == null) break;
				arcsToAdd.add(otherArc);
				followArc = otherArc;
				followNode = nextNode;
			}
		}
		// figure out which arcs should no longer be connected
		Set<ArcInst> connectedArcSet = new HashSet<ArcInst>();
		for(ArcInst ai : arcsToAdd) connectedArcSet.add(ai);
		for(Iterator<ArcInst> aIt = curInductanceData.cell.getArcs(); aIt.hasNext(); )
		{
			ArcInst ai = aIt.next();
			if (connectedArcSet.contains(ai)) continue;
			Variable var = ai.getVar(Schematics.INDUCTOR_NAME);
			if (var != null && var.getPureValue(-1).equals(inductorName)) arcsToDelete.add(ai);
		}
	}

	/**
	 * Method when the "Detect" button is clicked. Figures out which arcs connect to this inductor.
	 */
	private void detectArcsOnInductor()
	{
		String inductorName = needSelectedInductor();
		if (inductorName == null) return;
		if (!curInductanceData.inductorNamesonNodes.contains(inductorName))
		{
			Job.getUserInterface().showErrorMessage("There is no inductor node named " + inductorName +
				" so the arcs on it cannot be detected.", "Cannot Detect Inductor");
			return;
		}
		NodeInst ni = curInductanceData.cell.findNode(inductorName);
		if (ni == null) return;
		List<ArcInst> connectedArcs = new ArrayList<ArcInst>();
		List<ArcInst> disconnectedArcs = new ArrayList<ArcInst>();
		getArcsOnInductor(ni, inductorName, connectedArcs, disconnectedArcs);

		// assign the inductor name on these arcs
		(new UpdateInductanceNames(connectedArcs, disconnectedArcs, inductorName, this)).startJob();
	}

	/**
	 * Method when "Add" button is clicked to add the selected ArcInst to this inductor.
	 */
	private void addArcToInductor()
	{
		// make sure an inductor is selected
		String inductorName = needSelectedInductor();
		if (inductorName == null) return;

		// get selected arcs to add to inductor
		EditWindow curWnd = EditWindow.needCurrent();
		if (curWnd == null) return;
		List<Geometric> arcs = curWnd.getHighlightedEObjs(false, true);
		if (arcs.size() == 0)
		{
			Job.getUserInterface().showErrorMessage("Must select arcs first to add them to the inductor", "No Arcs Selected");
			return;
		}
		List<ArcInst> arcSet = new ArrayList<ArcInst>();
		for(Geometric geom : arcs) arcSet.add((ArcInst)geom);

		// assign the inductor name on these arcs
		(new UpdateInductanceNames(arcSet, null, inductorName, this)).startJob();
	}

	/**
	 * Method when "Remove" button is clicked to remove a named arc from this inductor.
	 */
	private void removeArcFromInductor()
	{
		if (curInductanceData == null) return;
		String arcName = listOfArcsOnInductor.getSelectedValue();
		if (arcName == null)
		{
			Job.getUserInterface().showErrorMessage("Must first select an arc name from this list in order to remove it", "No Arc Name Selected");
			return;
		}
		ArcInst ai = curInductanceData.cell.findArc(arcName);
		if (ai == null) return;

		// assign the inductor name on these arcs
		List<ArcInst> arcNameToDelete = new ArrayList<ArcInst>();
		arcNameToDelete.add(ai);
		(new UpdateInductanceNames(null, arcNameToDelete, null, this)).startJob();
	}

	/********************************** FASTHENRY PARAMETERS **********************************/

	/**
	 * Method called when the "Update FastHenry Factors" button is clicked.
	 */
	private void updateFastHenryFactors()
	{
		String inductorName = needSelectedInductor();
		if (inductorName == null) return;
		List<ArcInst> inductorArcs = getArcsOnInductor();
		if (inductorArcs.size() == 0)
		{
			Job.getUserInterface().showErrorMessage("Must first assign arcs to the inductor with either 'Detect' or 'Add'",
				"No Arcs on Selected Inductor");
			return;
		}
		String thicknessStr = fasthenryThickness.getText().trim();
		Double thickness = null;
		if (thicknessStr.length() > 0) thickness = TextUtils.atofDistance(thicknessStr);

		String zHeightStr = fasthenryZHeight.getText().trim();
		Double zHeight = null;
		if (zHeightStr.length() > 0) zHeight = TextUtils.atofDistance(zHeightStr);

		String widthSubdivsStr = fasthenryWidthSubdivs.getText().trim();
		Integer widthSubdivs = null;
		if (widthSubdivsStr.length() > 0) widthSubdivs = TextUtils.atoi(widthSubdivsStr);

		String heightSubdivsStr = fasthenryHeightSubdivs.getText().trim();
		Integer heightSubdivs = null;
		if (heightSubdivsStr.length() > 0) heightSubdivs = TextUtils.atoi(heightSubdivsStr);

		(new StoreFastHenryFactors(inductorArcs, thickness, zHeight, widthSubdivs, heightSubdivs)).startJob();
	}

	/**
	 * This class finishes the "Update FastHenry Factors" function by filling in values.
	 */
	public static class StoreFastHenryFactors extends Job
	{
		private List<ArcInst> inductorArcs;
		private Double thickness, zHeight;
		private Integer widthSubdivs, heightSubdivs;

		public StoreFastHenryFactors(List<ArcInst> arcs, Double th, Double zh, Integer ws, Integer hs)
		{
			super("Analyze Cell Inductors", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			inductorArcs = arcs;
			thickness = th;
			zHeight = zh;
			widthSubdivs = ws;
			heightSubdivs = hs;
		}

		@Override
		public boolean doIt() throws JobException
		{
			EditingPreferences ep = getEditingPreferences();
			for(ArcInst ai : inductorArcs)
			{
				if (thickness != null) ai.newVar(Schematics.INDUCTOR_THICKNESS, thickness, ep);
				if (zHeight != null) ai.newVar(Schematics.INDUCTOR_Z, zHeight, ep);
				if (widthSubdivs != null) ai.newVar(Schematics.INDUCTOR_WIDTH_SUBDIVS, widthSubdivs, ep);
				if (heightSubdivs != null) ai.newVar(Schematics.INDUCTOR_HEIGHT_SUBDIVS, heightSubdivs, ep);
			}
			return true;
		}
	}

	/********************************** INDUCTANCE COMPUTATION **********************************/

	/**
	 * Method to apply the override inductor width to all arcs and the inductor node
	 */
	private void applyInductorWidth()
    {
		// make a list of arcs on the inductor
		List<ArcInst> inductorArcs = getArcsOnInductor();

		// get the new inductor width
		double inductorWidth = TextUtils.atof(widthValue.getText());

		// make the change
		(new ApplyInductorWidth(inductorNode, inductorArcs, inductorWidth)).startJob();
	}

	/**
	 * This class finishes the job of changing the inductor width by manipulating the database.
	 */
	public static class ApplyInductorWidth extends Job
	{
		private NodeInst inductorNode;
		private List<ArcInst> inductorArcs;
		private double newWidth;

		public ApplyInductorWidth(NodeInst in, List<ArcInst> ia, double nw)
		{
			super("Change Inductor Width", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			inductorNode = in;
			inductorArcs = ia;
			newWidth = nw;
		}

		@Override
		public boolean doIt() throws JobException
		{
			if (inductorNode != null)
			{
				// change Y size to the value
				double ySize = inductorNode.getYSize();
				inductorNode.modifyInstance(0, 0, 0, newWidth - ySize, Orientation.IDENT);
			}
			for(ArcInst ai : inductorArcs)
			{
				// change width to the value
				ai.setLambdaBaseWidth(newWidth);
			}
			return true;
		}
	}

	/**
	 * Method called when the "Analyze" button is clicked.
	 */
	private void analyzeInductors(boolean useOverride)
	{
		lastAreaFactor = TextUtils.atof(areaFactor.getText());
		lastLengthFactor = TextUtils.atof(lengthFactor.getText());

		// find inductor to change
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;

		// make sure an inductor is selected
		String inductorName = needSelectedInductor();
		if (inductorName == null) return;

		// make a list of arcs on the inductor
		List<ArcInst> inductorArcs = getArcsOnInductor();

		// see if there is an inductor node
		inductorNode = null;
		if (curInductanceData.inductorNamesonNodes.contains(inductorName))
		{
			inductorNode = curInductanceData.cell.findNode(inductorName);
		}

		double inductorWidth;
		if (useOverride) inductorWidth = TextUtils.atof(widthValue.getText()); else
			inductorWidth = calculateInductorWidth(inductorArcs);

		StringBuffer sb = new StringBuffer();
		computedInductance = analyzeInductor(sb, inductorNode, inductorArcs, lastAreaFactor, lastLengthFactor, inductorWidth);
		inductorInfo.setText(sb.toString());
	}

	double calculateInductorWidth(List<ArcInst> inductorArcs)
	{
		// determine inductor width
		double inductorWidth = 0;
		if (inductorNode != null)
		{
			double wid = inductorNode.getXSize(), hei = inductorNode.getYSize();
			inductorWidth = Math.min(wid, hei);
		} else
		{
			if (inductorArcs != null && inductorArcs.size() > 0)
			{
				inductorWidth = Double.MAX_VALUE;
				for(ArcInst ai : inductorArcs)
				{
					double wid = ai.getLambdaBaseWidth();
					if (wid < inductorWidth) inductorWidth = wid;
				}
			}
		}
		if (inductorWidth == 0) inductorWidth = 1;
		widthValue.setText(TextUtils.formatDouble(inductorWidth));
		return inductorWidth;
	}

	/**
	 * Method called when the "Analyze And Annotate All" button is clicked.
	 */
	private void analyzeAndAnnotateAllInductors()
	{
		String news = updateCurrentCell(true);
		if (news == null) return;
		inductorInfo.setText(news);
	}

	double analyzeInductor(StringBuffer explanation, NodeInst inductorNode, List<ArcInst> inductorArcs,
		double areaFactor, double lengthFactor, double inductorWidth)
	{
		lastSquaresPerCorner = TextUtils.atof(perCornerFactor.getText());

		// count the number of corners
		int cornerCount = 0;
		if (inductorArcs != null)
		{
			for(int i=1; i<inductorArcs.size(); i++)
			{
				ArcInst ai1 = inductorArcs.get(i);
				NodeInst ni1a = ai1.getHeadPortInst().getNodeInst();
				NodeInst ni1b = ai1.getTailPortInst().getNodeInst();
				for(int j=0; j<i; j++)
				{
					// see if these arcs connect at a node
					ArcInst ai2 = inductorArcs.get(j);
					NodeInst ni2a = ai2.getHeadPortInst().getNodeInst();
					NodeInst ni2b = ai2.getTailPortInst().getNodeInst();
					if (ni2a == ni1a || ni2a == ni1b || ni2b == ni1a || ni2b == ni1b)
					{
						int angle1 = ai1.getAngle()%1800, angle2 = ai2.getAngle()%1800;
						if (Math.abs(angle1 - angle2) == 900) cornerCount++;
					}
				}
			}
		}

		// compute area component of the inductor
		explanation.append("======= COMPUTATION OF INDUCTOR" + (inductorNode != null ? ": " + inductorNode.describe(false) : "") + " =======\n");
		explanation.append("Area component:\n");
		double edgeLength = 0;
		if (inductorNode != null)
		{
			edgeLength = Math.max(inductorNode.getXSize(), inductorNode.getYSize());
			explanation.append("  Node " + inductorNode.describe(false) +" edge-length=" + TextUtils.formatDouble(edgeLength, PRECISION) + "\n");
		}
		if (inductorArcs != null)
		{
			for(ArcInst ai : inductorArcs)
			{
				double arcEdge = ai.getHeadLocation().distance(ai.getTailLocation());
				edgeLength += arcEdge;
				explanation.append("  Arc " + ai.describe(false) + " edge-length=" + TextUtils.formatDouble(arcEdge, PRECISION) + "\n");
			}
		}
		explanation.append("    Total Edge-length = " + TextUtils.formatDouble(edgeLength, PRECISION) + "\n");

		// compute the number of squares
		double squareCount = edgeLength / inductorWidth;
		explanation.append("    Square-count = Edge-length (" + TextUtils.formatDouble(edgeLength, PRECISION) + ") / " +
			"Inductor width (" + TextUtils.formatDouble(inductorWidth, PRECISION) + ") = " +
			TextUtils.formatDouble(squareCount, PRECISION) + "\n");

		// compute the corner component
		double cornerComponent = cornerCount * lastSquaresPerCorner;
		explanation.append("    Corner-squares = Corner-count (" + cornerCount + ") x " +
			"Squares-per-Corner (" + TextUtils.formatDouble(lastSquaresPerCorner, PRECISION*2) + ") = " +
			TextUtils.formatDouble(cornerComponent, PRECISION) + "\n");

		// compute the total number of squares
		double totalSquareComponent = squareCount + cornerComponent;
		explanation.append("    Total-squares = Square-count (" + squareCount + ") + " +
			"Corner-squares (" + TextUtils.formatDouble(cornerComponent, PRECISION) + ") = " +
			TextUtils.formatDouble(totalSquareComponent, PRECISION) + "\n");

		// compute the area component
		double areaComponent = totalSquareComponent * areaFactor;
		explanation.append("    Final Area-component = Total-squares (" + TextUtils.formatDouble(totalSquareComponent, PRECISION) + ") * " +
			"Area-factor (" + TextUtils.formatDouble(areaFactor, PRECISION) + ") = " +
			TextUtils.formatDouble(areaComponent, PRECISION) + "\n");

		// compute the length information
		explanation.append("Length component:\n");
		double lengthComponent = edgeLength * lengthFactor;
		explanation.append("  Edge-length (" + TextUtils.formatDouble(edgeLength, PRECISION) + ") x " +
			"Length-factor (" + TextUtils.formatDouble(lengthFactor, PRECISION) + ") = " +
			TextUtils.formatDouble(lengthComponent, PRECISION) + " (in Electric units)\n");

		explanation.append("Computed inductance:\n");
		double denom = lengthComponent + areaComponent;
		double inductance = 0;
		if (denom != 0) inductance = (lengthComponent * areaComponent) / denom;
		explanation.append("  (Area-component x length) / (Area-component + length) = " +
			TextUtils.formatDouble(inductance, PRECISION) + "\n");
		return inductance;
	}

	/**
	 * Method called when the "Annotate" button is clicked.
	 */
	private void assignValue()
	{
		Map<NodeInst,Double> newValues = new HashMap<NodeInst,Double>();
		newValues.put(inductorNode, computedInductance);
		(new AnnotateCellInductance(newValues)).startJob();
	}

	/**
	 * This class finishes the Annotate function by filling in inductance value.
	 */
	public static class AnnotateCellInductance extends Job
	{
		private Map<NodeInst,Double> newValues;

		public AnnotateCellInductance(Map<NodeInst,Double> nv)
		{
			super("Analyze Cell Inductors", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			newValues = nv;
		}

		@Override
		public boolean doIt() throws JobException
		{
			for(NodeInst ni : newValues.keySet())
			{
				Double ind = newValues.get(ni);
				Variable exists = ni.getVar(Schematics.SCHEM_INDUCTANCE);
				if (exists != null)
					ni.newVar(Schematics.SCHEM_INDUCTANCE, ind, exists.getTextDescriptor()); else
						ni.newDisplayVar(Schematics.SCHEM_INDUCTANCE, ind, getEditingPreferences());
			}
			return true;
		}
	}

	/********************************** CELL INDUCTANCE DATA **********************************/

	/**
	 * Method called when highlighting changes.
	 * Makes sure "curInductanceData" is correct for the current Cell.
	 */
	private void makeInductanceDataCurrent()
	{
		if (noInductListUpdate) return;
		EditWindow curWnd = EditWindow.getCurrent();
		if (curWnd == null) return;
		Cell cell = curWnd.getCell();
		curInductanceData = allInductanceData.get(cell);
		if (curInductanceData == null) allInductanceData.put(cell, curInductanceData = new CellInductance(cell));

		// default area and length
		areaFactorSuggestion.setText("No suggestion");
		useAreaFactor.setEnabled(false);
		lengthFactorSuggestion.setText("No suggestion");
		useLengthFactor.setEnabled(false);

		// see if an inductor node was selected
		Highlighter highlighter = curWnd.getHighlighter();
		List<Geometric> highlighted = highlighter.getHighlightedEObjs(true, true);
		for(Geometric g : highlighted)
		{
			String iName = null;
			String aName = null;
			if (g instanceof NodeInst)
			{
				NodeInst ni = (NodeInst)g;
				if (ni.getFunction() != PrimitiveNode.Function.INDUCT) continue;
				iName = ni.getName();
			} else if (g instanceof ArcInst)
			{
				ArcInst ai = (ArcInst)g;
				Variable var = ai.getVar(Schematics.INDUCTOR_NAME);
				if (var != null)
				{
					iName = var.getPureValue(-1);
					aName = ai.getName();
				}
			}
			if (iName != null && curInductanceData.inductorNames.contains(iName))
			{
				noHighlightUpdate = true;
				listOfInductors.setSelectedValue(iName, true);
				listOfArcsOnInductor.setSelectedValue(aName, true);
				noHighlightUpdate = false;
				break;
			}
		}
	}

	/**
	 * Class to hold inductance information for a Cell.
	 */
	private static class CellInductance
	{
		private Cell cell;
		private Set<String> inductorNamesonNodes;
		private Set<String> inductorNamesonArcs;
		private Set<String> inductorNamesDefined;
		private Set<String> inductorNames;
		private double suggestedAreaFactor, suggestedLengthFactor;

		public CellInductance(Cell c)
		{
			cell = c;
			inductorNamesonNodes = new HashSet<String>();
			inductorNamesonArcs = new HashSet<String>();
			inductorNamesDefined = new HashSet<String>();
			inductorNames = new TreeSet<String>();
		}

		public void recalculate()
		{
			// gather all inductor names in the cell
			inductorNamesonArcs.clear();
			inductorNamesonNodes.clear();
			inductorNames.clear();
			if (cell == null) return;

			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				Variable var = ai.getVar(Schematics.INDUCTOR_NAME);
				if (var == null) continue;
				inductorNamesonArcs.add(var.getPureValue(-1));
				inductorNames.add(var.getPureValue(-1));
			}
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				NodeProto np = ni.getProto();
				if (np.getFunction() == PrimitiveNode.Function.INDUCT)
				{
					inductorNamesonNodes.add(ni.getName());
					inductorNames.add(ni.getName());
				}
			}
			List<String> removeDefinedNames = new ArrayList<String>();
			for(String definedName : inductorNamesDefined)
			{
				if (inductorNames.contains(definedName)) removeDefinedNames.add(definedName); else
					inductorNames.add(definedName);
			}
			for(String definedName : removeDefinedNames) inductorNamesDefined.remove(definedName);
		}

		public void addDefinedInductorName(String name)
		{
			inductorNamesDefined.add(name);
			inductorNames.add(name);
		}
	}

	/********************************** UTILITIES **********************************/

	/**
	 * This class finishes the "Detect" function by filling in names on arcs.
	 */
	public static class UpdateInductanceNames extends Job
	{
		private List<ArcInst> connectedArcs;
		private List<ArcInst> disconnectedArcs;
		private String nodeName;
		private transient ManageInductors dialog;

		public UpdateInductanceNames(List<ArcInst> arcsPlus, List<ArcInst> arcsMinus, String name, ManageInductors mi)
		{
			super("Update Inductor Names", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			connectedArcs = arcsPlus;
			disconnectedArcs = arcsMinus;
			nodeName = name;
			dialog = mi;
		}

		@Override
		public boolean doIt() throws JobException
		{
			TextDescriptor td = getEditingPreferences().getNodeTextDescriptor().withOff(0, 2).withDisplay(TextDescriptor.Display.SHOWN).
				withDispPart(TextDescriptor.DispPos.NAMEVALUE);
			if (connectedArcs != null)
			{
				for(ArcInst ai : connectedArcs)
					ai.newVar(Schematics.INDUCTOR_NAME, nodeName, td);
			}
			if (disconnectedArcs != null)
			{
				for(ArcInst ai : disconnectedArcs)
					ai.delVar(Schematics.INDUCTOR_NAME);
			}
			return true;
		}

		public void terminateOK()
		{
			if (dialog != null) dialog.inductorSelected();
		}
	}

	/**
	 * Method to get the name of the selected inductor in the top list.
	 * Displays an error message if there is none.
	 * @return the current inductor name (null if none selected).
	 */
	private String needSelectedInductor()
	{
		String inductorName = listOfInductors.getSelectedValue();
		if (inductorName == null)
		{
			Job.getUserInterface().showErrorMessage("Must create an inductor in the top list first", "No Inductor Selected");
			return null;
		}
		return inductorName;
	}

	private List<ArcInst> getArcsOnInductor()
	{
		// make a list of arcs on the inductor
		List<ArcInst> inductorArcs = new ArrayList<ArcInst>();
		for(int i = 0; i < listOfArcsOnInductor.getModel().getSize(); i++)
		{
			 String item = listOfArcsOnInductor.getModel().getElementAt(i);
			 ArcInst ai = curInductanceData.cell.findArc(item);
			 inductorArcs.add(ai);
		}
		return inductorArcs;
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel1 = new javax.swing.JPanel();
        analyzeInductor = new javax.swing.JButton();
        annotateInductor = new javax.swing.JButton();
        infoScroll = new javax.swing.JScrollPane();
        inductorInfo = new javax.swing.JTextArea();
        jLabel6 = new javax.swing.JLabel();
        areaFactor = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        lengthFactor = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        perCornerFactor = new javax.swing.JTextField();
        areaFactorSuggestion = new javax.swing.JLabel();
        useAreaFactor = new javax.swing.JButton();
        lengthFactorSuggestion = new javax.swing.JLabel();
        useLengthFactor = new javax.swing.JButton();
        clearComputationArea = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        analyzeInductorNewWidth = new javax.swing.JButton();
        widthValue = new javax.swing.JTextField();
        applyNewWidth = new javax.swing.JButton();
        inductorListPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        listOfInductors = new javax.swing.JList<>();
        addInductor = new javax.swing.JButton();
        deleteInductor = new javax.swing.JButton();
        renameInductor = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        recacheCell = new javax.swing.JButton();
        stayCurrent = new javax.swing.JCheckBox();
        analyzeAndAnnotateAll = new javax.swing.JButton();
        renameAllInductors = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        listOfArcsOnInductor = new javax.swing.JList<>();
        addArcToInductor = new javax.swing.JButton();
        removeArcFromInductor = new javax.swing.JButton();
        detectArcsOnInductor = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        fasthenryZHeight = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        fasthenryWidthSubdivs = new javax.swing.JTextField();
        fasthenryHeightSubdivs = new javax.swing.JTextField();
        fasthenryDefThickness = new javax.swing.JLabel();
        fasthenryDefZHeight = new javax.swing.JLabel();
        fasthenryDefWidthSubdivs = new javax.swing.JLabel();
        fasthenryDefHeightSubdivs = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        fasthenryThickness = new javax.swing.JTextField();
        updateFasthenryData = new javax.swing.JButton();

        setTitle("Manage Inductors");
        setMinimumSize(new java.awt.Dimension(353, 150));
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Inductance Computation", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        analyzeInductor.setText("Analyze");
        analyzeInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                analyzeInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(analyzeInductor, gridBagConstraints);
        analyzeInductor.getAccessibleContext().setAccessibleDescription("");

        annotateInductor.setText("Annotate");
        annotateInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                annotateInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(annotateInductor, gridBagConstraints);

        infoScroll.setMinimumSize(new java.awt.Dimension(500, 300));
        infoScroll.setPreferredSize(new java.awt.Dimension(500, 300));
        infoScroll.setViewportView(inductorInfo);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(infoScroll, gridBagConstraints);

        jLabel6.setText("Area factor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.weightx = 0.3;
        jPanel1.add(jLabel6, gridBagConstraints);

        areaFactor.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(areaFactor, gridBagConstraints);

        jLabel1.setText("Length factor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.weightx = 0.3;
        jPanel1.add(jLabel1, gridBagConstraints);

        lengthFactor.setText("    ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(lengthFactor, gridBagConstraints);

        jLabel2.setText("Squares per Corner:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.weightx = 0.3;
        jPanel1.add(jLabel2, gridBagConstraints);

        perCornerFactor.setText("    ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(perCornerFactor, gridBagConstraints);

        areaFactorSuggestion.setText("No Default Value");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(areaFactorSuggestion, gridBagConstraints);

        useAreaFactor.setText("Use");
        useAreaFactor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                useAreaFactorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(useAreaFactor, gridBagConstraints);

        lengthFactorSuggestion.setText("No Default Value");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(lengthFactorSuggestion, gridBagConstraints);

        useLengthFactor.setText("Use");
        useLengthFactor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                useLengthFactorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(useLengthFactor, gridBagConstraints);

        clearComputationArea.setText("Clear");
        clearComputationArea.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                clearComputationAreaActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(clearComputationArea, gridBagConstraints);

        jLabel8.setText("Inductor width:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        jPanel1.add(jLabel8, gridBagConstraints);

        analyzeInductorNewWidth.setText("Analyze with new Width");
        analyzeInductorNewWidth.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                analyzeInductorNewWidthActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        jPanel1.add(analyzeInductorNewWidth, gridBagConstraints);

        widthValue.setColumns(5);
        widthValue.setToolTipText("");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel1.add(widthValue, gridBagConstraints);

        applyNewWidth.setText("Apply new Width");
        applyNewWidth.setToolTipText("");
        applyNewWidth.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                applyNewWidthActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        jPanel1.add(applyNewWidth, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jPanel1, gridBagConstraints);

        inductorListPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Inductors in Cell", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));
        inductorListPanel.setLayout(new java.awt.GridBagLayout());

        listOfInductors.setModel(new javax.swing.AbstractListModel<String>()
        {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane2.setViewportView(listOfInductors);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        inductorListPanel.add(jScrollPane2, gridBagConstraints);

        addInductor.setText("New...");
        addInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                addInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weighty = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        inductorListPanel.add(addInductor, gridBagConstraints);

        deleteInductor.setText("Delete");
        deleteInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deleteInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.weighty = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        inductorListPanel.add(deleteInductor, gridBagConstraints);

        renameInductor.setText("Rename...");
        renameInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                renameInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        inductorListPanel.add(renameInductor, gridBagConstraints);

        jPanel4.setLayout(new java.awt.GridBagLayout());

        recacheCell.setText("Recache Current Cell");
        recacheCell.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                recacheCellActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 10);
        jPanel4.add(recacheCell, gridBagConstraints);

        stayCurrent.setText("Update All Inductors in Cell");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(4, 10, 4, 4);
        jPanel4.add(stayCurrent, gridBagConstraints);

        analyzeAndAnnotateAll.setText("Analyze & Annotate All");
        analyzeAndAnnotateAll.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                analyzeAndAnnotateAllActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(4, 10, 4, 4);
        jPanel4.add(analyzeAndAnnotateAll, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        inductorListPanel.add(jPanel4, gridBagConstraints);

        renameAllInductors.setText("Rename All...");
        renameAllInductors.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                renameAllInductorsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        inductorListPanel.add(renameAllInductors, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(inductorListPanel, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Arcs in Selected Inductor", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));
        jPanel3.setLayout(new java.awt.GridBagLayout());

        listOfArcsOnInductor.setModel(new javax.swing.AbstractListModel<String>()
        {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(listOfArcsOnInductor);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(jScrollPane1, gridBagConstraints);

        addArcToInductor.setText("Add");
        addArcToInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                addArcToInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(addArcToInductor, gridBagConstraints);

        removeArcFromInductor.setText("Remove");
        removeArcFromInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                removeArcFromInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(removeArcFromInductor, gridBagConstraints);

        detectArcsOnInductor.setText("Detect");
        detectArcsOnInductor.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                detectArcsOnInductorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(detectArcsOnInductor, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jPanel3, gridBagConstraints);

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "FastHenry Factors", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION));
        jPanel2.setLayout(new java.awt.GridBagLayout());

        jLabel3.setText("Thickness:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Z Height:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel4, gridBagConstraints);

        fasthenryZHeight.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(fasthenryZHeight, gridBagConstraints);

        jLabel5.setText("Width subdivisions:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel5, gridBagConstraints);

        jLabel7.setText("Height subdivisions:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel7, gridBagConstraints);

        fasthenryWidthSubdivs.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(fasthenryWidthSubdivs, gridBagConstraints);

        fasthenryHeightSubdivs.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(fasthenryHeightSubdivs, gridBagConstraints);

        fasthenryDefThickness.setText("default=");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        jPanel2.add(fasthenryDefThickness, gridBagConstraints);

        fasthenryDefZHeight.setText("default=");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        jPanel2.add(fasthenryDefZHeight, gridBagConstraints);

        fasthenryDefWidthSubdivs.setText("default=");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 0;
        jPanel2.add(fasthenryDefWidthSubdivs, gridBagConstraints);

        fasthenryDefHeightSubdivs.setText("default=");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 1;
        jPanel2.add(fasthenryDefHeightSubdivs, gridBagConstraints);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
        jPanel2.add(jSeparator1, gridBagConstraints);

        fasthenryThickness.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(fasthenryThickness, gridBagConstraints);

        updateFasthenryData.setText("Update FastHenry Factors");
        updateFasthenryData.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                updateFasthenryDataActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 7;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(updateFasthenryData, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jPanel2, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /** Closes the dialog */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
		setVisible(false);
		dispose();
    }//GEN-LAST:event_closeDialog

    private void analyzeInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_analyzeInductorActionPerformed
    {//GEN-HEADEREND:event_analyzeInductorActionPerformed
		analyzeInductors(false);
    }//GEN-LAST:event_analyzeInductorActionPerformed

    private void annotateInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_annotateInductorActionPerformed
    {//GEN-HEADEREND:event_annotateInductorActionPerformed
		assignValue();
    }//GEN-LAST:event_annotateInductorActionPerformed

    private void deleteInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteInductorActionPerformed
    {//GEN-HEADEREND:event_deleteInductorActionPerformed
		deleteInductor();
    }//GEN-LAST:event_deleteInductorActionPerformed

    private void addInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_addInductorActionPerformed
    {//GEN-HEADEREND:event_addInductorActionPerformed
		addNewInductor();
    }//GEN-LAST:event_addInductorActionPerformed

    private void addArcToInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_addArcToInductorActionPerformed
    {//GEN-HEADEREND:event_addArcToInductorActionPerformed
	   addArcToInductor();
    }//GEN-LAST:event_addArcToInductorActionPerformed

    private void removeArcFromInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_removeArcFromInductorActionPerformed
    {//GEN-HEADEREND:event_removeArcFromInductorActionPerformed
		removeArcFromInductor();
    }//GEN-LAST:event_removeArcFromInductorActionPerformed

    private void detectArcsOnInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_detectArcsOnInductorActionPerformed
    {//GEN-HEADEREND:event_detectArcsOnInductorActionPerformed
		detectArcsOnInductor();
    }//GEN-LAST:event_detectArcsOnInductorActionPerformed

    private void updateFasthenryDataActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_updateFasthenryDataActionPerformed
    {//GEN-HEADEREND:event_updateFasthenryDataActionPerformed
		updateFastHenryFactors();
    }//GEN-LAST:event_updateFasthenryDataActionPerformed

    private void renameInductorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_renameInductorActionPerformed
    {//GEN-HEADEREND:event_renameInductorActionPerformed
		renameInductor();
    }//GEN-LAST:event_renameInductorActionPerformed

    private void recacheCellActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_recacheCellActionPerformed
    {//GEN-HEADEREND:event_recacheCellActionPerformed
		showSelected();
    }//GEN-LAST:event_recacheCellActionPerformed

    private void useAreaFactorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useAreaFactorActionPerformed
		useSuggestedAreaFactor();
    }//GEN-LAST:event_useAreaFactorActionPerformed

    private void useLengthFactorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_useLengthFactorActionPerformed
    {//GEN-HEADEREND:event_useLengthFactorActionPerformed
		useSuggestedLengthFactor();
    }//GEN-LAST:event_useLengthFactorActionPerformed

    private void clearComputationAreaActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_clearComputationAreaActionPerformed
    {//GEN-HEADEREND:event_clearComputationAreaActionPerformed
		inductorInfo.setText("");
    }//GEN-LAST:event_clearComputationAreaActionPerformed

    private void analyzeInductorNewWidthActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_analyzeInductorNewWidthActionPerformed
    {//GEN-HEADEREND:event_analyzeInductorNewWidthActionPerformed
		analyzeInductors(true);
    }//GEN-LAST:event_analyzeInductorNewWidthActionPerformed

    private void applyNewWidthActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_applyNewWidthActionPerformed
    {//GEN-HEADEREND:event_applyNewWidthActionPerformed
        applyInductorWidth();
    }//GEN-LAST:event_applyNewWidthActionPerformed

    private void analyzeAndAnnotateAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_analyzeAndAnnotateAllActionPerformed
    	analyzeAndAnnotateAllInductors();
    }//GEN-LAST:event_analyzeAndAnnotateAllActionPerformed

    private void renameAllInductorsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_renameAllInductorsActionPerformed
    {//GEN-HEADEREND:event_renameAllInductorsActionPerformed
    	renameAllInductors();
    }//GEN-LAST:event_renameAllInductorsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addArcToInductor;
    private javax.swing.JButton addInductor;
    private javax.swing.JButton analyzeAndAnnotateAll;
    private javax.swing.JButton analyzeInductor;
    private javax.swing.JButton analyzeInductorNewWidth;
    private javax.swing.JButton annotateInductor;
    private javax.swing.JButton applyNewWidth;
    private javax.swing.JTextField areaFactor;
    private javax.swing.JLabel areaFactorSuggestion;
    private javax.swing.JButton clearComputationArea;
    private javax.swing.JButton deleteInductor;
    private javax.swing.JButton detectArcsOnInductor;
    private javax.swing.JLabel fasthenryDefHeightSubdivs;
    private javax.swing.JLabel fasthenryDefThickness;
    private javax.swing.JLabel fasthenryDefWidthSubdivs;
    private javax.swing.JLabel fasthenryDefZHeight;
    private javax.swing.JTextField fasthenryHeightSubdivs;
    private javax.swing.JTextField fasthenryThickness;
    private javax.swing.JTextField fasthenryWidthSubdivs;
    private javax.swing.JTextField fasthenryZHeight;
    private javax.swing.JTextArea inductorInfo;
    private javax.swing.JPanel inductorListPanel;
    private javax.swing.JScrollPane infoScroll;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JTextField lengthFactor;
    private javax.swing.JLabel lengthFactorSuggestion;
    private javax.swing.JList<String> listOfArcsOnInductor;
    private javax.swing.JList<String> listOfInductors;
    private javax.swing.JTextField perCornerFactor;
    private javax.swing.JButton recacheCell;
    private javax.swing.JButton removeArcFromInductor;
    private javax.swing.JButton renameAllInductors;
    private javax.swing.JButton renameInductor;
    private javax.swing.JCheckBox stayCurrent;
    private javax.swing.JButton updateFasthenryData;
    private javax.swing.JButton useAreaFactor;
    private javax.swing.JButton useLengthFactor;
    private javax.swing.JTextField widthValue;
    // End of variables declaration//GEN-END:variables
}
