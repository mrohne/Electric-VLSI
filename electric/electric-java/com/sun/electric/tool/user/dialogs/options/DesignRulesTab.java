/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DesignRulesTab.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.dialogs.options;

import com.sun.electric.database.text.TextUtils;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.XMLRules;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.user.dialogs.DesignRulesPanel;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.EditWindow;

import com.sun.electric.util.math.ECoord;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Iterator;

import javax.swing.JPanel;

/**
 * Class to handle the "Design Rules" tab of the Preferences dialog.
 */
public class DesignRulesTab extends PreferencePanel
{
	DesignRulesPanel rulesPanel;
	private XMLRules drRules;
	private boolean designRulesFactoryReset = false;

	/** Creates new form DesignRulesTab */
	public DesignRulesTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);

		initComponents();

        // Adding the node and layer panels
        rulesPanel = new DesignRulesPanel();
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.gridheight = 1;
        gridBagConstraints.weightx = gridBagConstraints.weighty = 1;
        gridBagConstraints.insets = new Insets(0, 0, 0, 0);
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        designRules.add(rulesPanel, gridBagConstraints);
	}

	/** return the panel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return designRules; }

	/** return the name of this preferences tab. */
	public String getName() { return "Design Rules"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Design Rules tab.
	 */
	public void init()
	{
        DRC.DRCPreferences dp = new DRC.DRCPreferences(false);
		// get the design rules for the current technology
        XMLRules rules = DRC.getRules(curTech);
		if (rules == null)
		{
			drTechName.setText("Technology " + curTech.getTechName() + " HAS NO DESIGN RULES");
			return;
		}

        drRules = rules;
        Foundry.Type foundry = curTech.getSelectedFoundry().getType();
        rulesPanel.init(curTech, foundry, drRules);

		// load the dialog
        String text = "Design Rules for Technology '" + curTech.getTechName() + "'";
        if (foundry != Foundry.Type.NONE) text += " with foundry " + foundry.getName();
		drTechName.setText(text);

        // Resolution
		drResolutionValue.setText(TextUtils.formatDistance(dp.getResolution(curTech).getLambda()));

        // AngleStep
		drAngleStepValue.setText(TextUtils.formatDistance(dp.getAngleStep(curTech)));
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Design Rules tab.
	 */
    @Override
	public void term()
	{
        DRC.DRCPreferences dp = new DRC.DRCPreferences(false);
		double currentResolution = TextUtils.atofDistance(drResolutionValue.getText());
		dp.setResolution(curTech, ECoord.fromLambdaRoundSizeGrid(currentResolution));
		double currentAngleStep = TextUtils.atofDistance(drAngleStepValue.getText());
		dp.setAngleStep(curTech, currentAngleStep);

        // Getting last changes
		if (designRulesFactoryReset)
		{
			DRC.resetDRCDates(true);
            drRules = curTech.getFactoryDesignRules();
		}
		DRC.setRules(dp, curTech, drRules);
        putPrefs(dp);

        // Repaint primitives
        EditWindow wnd = EditWindow.needCurrent();
        if (wnd != null) wnd.fullRepaint();
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
        DRC.DRCPreferences dp = new DRC.DRCPreferences(false);
		for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
		{
			Technology tech = it.next();
            dp.setResolution(tech, tech.getFactoryResolution());
            dp.setAngleStep(tech, tech.getFactoryAngleStep());
	        XMLRules rules = tech.getFactoryDesignRules();
			DRC.setRules(dp, tech, rules);
			tech.setCachedRules(rules);
		}
        putPrefs(dp);
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        designRules = new javax.swing.JPanel();
        drResolutionLabel = new javax.swing.JLabel();
        drResolutionValue = new javax.swing.JTextField();
        drAngleStepLabel = new javax.swing.JLabel();
        drAngleStepValue = new javax.swing.JTextField();
        drTechName = new javax.swing.JLabel();

        setTitle("Tool Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        designRules.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        designRules.setLayout(new java.awt.GridBagLayout());

        drResolutionLabel.setText("Min. resolution in lambda:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        designRules.add(drResolutionLabel, gridBagConstraints);

        drResolutionValue.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        designRules.add(drResolutionValue, gridBagConstraints);

        drAngleStepLabel.setText("Min. angle step in degrees:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        designRules.add(drAngleStepLabel, gridBagConstraints);

        drAngleStepValue.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        designRules.add(drAngleStepValue, gridBagConstraints);

        drTechName.setText("jLabel1");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        designRules.add(drTechName, gridBagConstraints);

        getContentPane().add(designRules, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel designRules;
    private javax.swing.JLabel drResolutionLabel;
    private javax.swing.JTextField drResolutionValue;
    private javax.swing.JLabel drAngleStepLabel;
    private javax.swing.JTextField drAngleStepValue;
    private javax.swing.JLabel drTechName;
    // End of variables declaration//GEN-END:variables

    /****************************** Reset default arc widths ******************************/

}
