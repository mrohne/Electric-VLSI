/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NCCAnnotations.java
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.ncc.basic.NccCellAnnotations;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.User;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;


/**
 * Class to handle the "NCC Annotations" dialog.
 */
public class NCCAnnotations extends EDialog
{
	private List<Library> libList;
	private static Library curLib = null;
	private static List<JButton> editButtons, deleteButtons;
	private static List<JLabel> cellNames, cellAnnotations;
	private static boolean lastOnlyShowAnnotated = false;
	private Frame parentFrame;
	private static final ImageIcon iconDelete = Resources.getResource(NCCAnnotations.class, "IconDelete.gif");
	private static final ImageIcon iconEdit = Resources.getResource(NCCAnnotations.class, "IconDraw.gif");

	/** Creates new form NCCAnnotations */
	public NCCAnnotations(Frame parent)
	{
		super(parent, true);
		parentFrame = parent;
		setPreferredSize(new Dimension(650, 400));
		initComponents();

		// determine the library to show
		libList = Library.getVisibleLibraries();
		if (curLib == null) curLib = Library.getCurrent();

		// setup the library popups
		Library saveLeft = curLib;
		for(Library lib : libList)
			libraries.addItem(lib.getName());
		int curIndex = libList.indexOf(saveLeft);
		if (curIndex >= 0) libraries.setSelectedIndex(curIndex);

		showCells();

		// show the check boxes
		onlyAnnotatedCells.setSelected(lastOnlyShowAnnotated);
		onlyAnnotatedCells.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { onlyAnnotatedCellsChanged(); }
		});
		getRootPane().setDefaultButton(done);

		finishInitialization();
	}

	private void onlyAnnotatedCellsChanged()
	{
		lastOnlyShowAnnotated = onlyAnnotatedCells.isSelected();
		showCells();
	}

	private void showCells()
	{
		JPanel pan = new JPanel();
		pan.setLayout(new GridBagLayout());
		int rowIndex = 0;
		cellNames = new ArrayList<JLabel>();
		editButtons = new ArrayList<JButton>();
		deleteButtons = new ArrayList<JButton>();
		cellAnnotations = new ArrayList<JLabel>();
		for (Iterator<Cell> it = curLib.getCells(); it.hasNext(); )
		{
			Cell c = it.next();
			Variable var = c.getVar(NccCellAnnotations.NCC_ANNOTATION_KEY);
			String ann = "";
			if (var != null) ann = var.getPureValue(-1); else
			{
				if (lastOnlyShowAnnotated) continue;
			}

			JLabel labCName = new JLabel(c.describe(false));
			cellNames.add(labCName);
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;       gbc.gridy = rowIndex;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(2, 2, 2, 2);
			pan.add(labCName, gbc);

			JSeparator sep1 = new JSeparator(SwingConstants.VERTICAL);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;       gbc.gridy = rowIndex;
			gbc.weighty = 1;
			gbc.fill = GridBagConstraints.VERTICAL;
			pan.add(sep1, gbc);

			JButton editBut = new JButton(iconEdit);
			editBut.setBorder(BorderFactory.createEmptyBorder());
			editBut.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { doEdit(editBut); }
			});
			editButtons.add(editBut);
			gbc = new GridBagConstraints();
			gbc.gridx = 2;       gbc.gridy = rowIndex;
			gbc.insets = new Insets(2, 6, 2, 4);
			pan.add(editBut, gbc);

			JButton deleteBut = new JButton(iconDelete);
			deleteBut.setBorder(BorderFactory.createEmptyBorder());
			deleteBut.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { doDelete(deleteBut); }
			});
			deleteButtons.add(deleteBut);
			gbc = new GridBagConstraints();
			gbc.gridx = 3;       gbc.gridy = rowIndex;
			gbc.insets = new Insets(2, 4, 2, 6);
			pan.add(deleteBut, gbc);

			JSeparator sep2 = new JSeparator(SwingConstants.VERTICAL);
			gbc = new GridBagConstraints();
			gbc.gridx = 4;       gbc.gridy = rowIndex;
			gbc.weighty = 1;
			gbc.fill = GridBagConstraints.VERTICAL;
			pan.add(sep2, gbc);

			JLabel labAnn = new JLabel(ann);
			cellAnnotations.add(labAnn);
			gbc = new GridBagConstraints();
			gbc.gridx = 5;       gbc.gridy = rowIndex;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(2, 2, 2, 2);
			pan.add(labAnn, gbc);

			rowIndex++;
		}
		cells.setViewportView(pan);
	}

	public void doEdit(JButton but)
	{
		for(int i=0; i<editButtons.size(); i++)
		{
			if (editButtons.get(i) == but)
			{
				String cellName = cellNames.get(i).getText();
				Cell c = curLib.findNodeProto(cellName);
				if (c == null)
				{
					System.out.println("Error: can't find cell " + cellName + " in library " + curLib.getName());
					return;
				}

				Variable var = c.getVar(NccCellAnnotations.NCC_ANNOTATION_KEY);
				String [] anns;
				TextDescriptor td = null;
				if (var == null) anns = new String[0]; else
				{
					Object oldObj = var.getObject();
					if (oldObj instanceof String) anns = new String[] {(String)oldObj}; else
						anns = (String[])oldObj;
					td = var.getTextDescriptor();
				}

				new EditAnnotationDialog(parentFrame, i, c, anns, td);
				return;
			}
		}
	}

	public class EditAnnotationDialog extends EDialog
	{
		private int index;
		private Cell cell;
		private TextDescriptor td;
		private JTextArea ta;

		/** Creates new form EditAnnotationDialog */
		public EditAnnotationDialog(Frame parent, int i, Cell c, String[] ann, TextDescriptor t)
		{
			super(parent, true);
			index = i;
			cell = c;
			td = t;
			setTitle("Edit NCC Annotation");
			Dimension size = new Dimension(450, 200);
			setMinimumSize(size);
			setPreferredSize(size);
			setLayout(new GridBagLayout());

			JLabel labCName = new JLabel("Annotation for cell " + c.describe(false));
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;       gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.EAST;
			gbc.insets = new Insets(2, 2, 2, 2);
			add(labCName, gbc);

			ta = new JTextArea();
			String text = "";
			for(int j=0; j<ann.length; j++)
			{
				if (j > 0) text += "\n";
				text += ann[j];
			}
			ta.setText(text);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;       gbc.gridy = 0;
			gbc.weightx = gbc.weighty = 1;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.anchor = GridBagConstraints.EAST;
			gbc.insets = new Insets(2, 2, 2, 2);
			add(ta, gbc);

			JButton cancelBut = new JButton("Cancel");
			cancelBut.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { closeDialog(); }
			});
			gbc = new GridBagConstraints();
			gbc.gridx = 0;       gbc.gridy = 1;
			gbc.insets = new Insets(2, 2, 2, 2);
			add(cancelBut, gbc);

			JButton okayBut = new JButton("OK");
			okayBut.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { okayBut(); }
			});
			gbc = new GridBagConstraints();
			gbc.gridx = 1;       gbc.gridy = 1;
			gbc.insets = new Insets(2, 2, 2, 2);
			add(okayBut, gbc);
			getRootPane().setDefaultButton(okayBut);

			finishInitialization();
			pack();
			setVisible(true);
		}

		public void okayBut()
		{
			String anns = ta.getText().trim();
			String[] newAnns = anns.split("\n");
			new UpdateAnnotationJob(index, cell, newAnns, td);
			closeDialog();
		}
	}

	public static class UpdateAnnotationJob extends Job
	{
		private int index;
		private Cell cell;
		private String[] ann;
		private TextDescriptor td;

		public UpdateAnnotationJob(int i, Cell c, String[] a, TextDescriptor t)
		{
			super("Change NCC Annotation", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			index = i;
			cell = c;
			ann = a;
			td = t;
			startJob();
		}

		@Override
		public boolean doIt() throws JobException
		{
			EditingPreferences ep = getEditingPreferences();
			if (td == null) td = ep.getCellTextDescriptor().withInterior(true).withDispPart(TextDescriptor.DispPos.NAMEVALUE);
			cell.newVar(NccCellAnnotations.NCC_ANNOTATION_KEY, ann, td);
			return true;
		}

		@Override
		public void terminateOK()
		{
			Variable var = cell.getVar(NccCellAnnotations.NCC_ANNOTATION_KEY);
			String ann = "";
			if (var != null) ann = var.getPureValue(-1);
			cellAnnotations.get(index).setText(ann);
		}
	}

	public void doDelete(JButton but)
	{
		for(int i=0; i<deleteButtons.size(); i++)
		{
			if (deleteButtons.get(i) == but)
			{
				String cellName = cellNames.get(i).getText();
				Cell c = curLib.findNodeProto(cellName);
				if (c == null)
				{
					System.out.println("Error: can't find cell "+cellName + " in library " + curLib.getName());
					return;
				}
				new DeleteAnnotationJob(i, c);
				return;
			}
		}
	}

	public static class DeleteAnnotationJob extends Job
	{
		private int index;
		private Cell cell;

		public DeleteAnnotationJob(int i, Cell c)
		{
			super("Delete NCC Annotation", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			index = i;
			cell = c;
			startJob();
		}

		@Override
		public boolean doIt() throws JobException
		{
			cell.delVar(NccCellAnnotations.NCC_ANNOTATION_KEY);
			return true;
		}

		@Override
		public void terminateOK()
		{
			cellAnnotations.get(index).setText("");
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        Top = new javax.swing.JPanel();
        libraries = new javax.swing.JComboBox();
        done = new javax.swing.JButton();
        cells = new javax.swing.JScrollPane();
        onlyAnnotatedCells = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();

        setTitle("NCC Annotations");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.BorderLayout(0, 10));

        Top.setLayout(new java.awt.GridBagLayout());

        libraries.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                librariesActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(libraries, gridBagConstraints);

        done.setText("Done");
        done.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doneActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(done, gridBagConstraints);

        cells.setPreferredSize(new java.awt.Dimension(200, 350));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(cells, gridBagConstraints);

        onlyAnnotatedCells.setText("Only show cells with annotations");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(onlyAnnotatedCells, gridBagConstraints);

        jLabel1.setText("Libraries:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        Top.add(jLabel1, gridBagConstraints);

        getContentPane().add(Top, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void librariesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_librariesActionPerformed
	{//GEN-HEADEREND:event_librariesActionPerformed
		// the left popup of libraries changed
		JComboBox cb = (JComboBox)evt.getSource();
		int index = cb.getSelectedIndex();
		curLib = libList.get(index);
		showCells();
	}//GEN-LAST:event_librariesActionPerformed

	private void doneActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_doneActionPerformed
	{//GEN-HEADEREND:event_doneActionPerformed
		closeDialog(null);
	}//GEN-LAST:event_doneActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		lastOnlyShowAnnotated = onlyAnnotatedCells.isSelected();
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel Top;
    private javax.swing.JScrollPane cells;
    private javax.swing.JButton done;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JComboBox libraries;
    private javax.swing.JCheckBox onlyAnnotatedCells;
    // End of variables declaration//GEN-END:variables

}
