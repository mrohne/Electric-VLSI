/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WaveformZoom.java
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

import com.sun.electric.tool.user.waveform.Panel;
import com.sun.electric.tool.user.waveform.WaveformWindow;
import com.sun.electric.util.TextUtils;

import java.awt.Frame;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Class to handle the dialog for precise control of Waveform window extents.
 */
public class WaveformZoom extends EDialog
{
	private Panel curPanel;
	private WaveformWindow curWindow;
	private double trueLowY, trueHighY, trueScale = 1;
	private boolean updatingTextFields = false;

	/**
	 * Create a new Waveform Zoom dialog
	 */
	public WaveformZoom(Frame parent, double lowVert, double highVert, double scaleVert, boolean logVert,
		double lowHoriz, double highHoriz, WaveformWindow curWindow, Panel curPanel)
	{
		super(parent, true);
		this.curPanel = curPanel;
		this.curWindow = curWindow;
		initComponents();
		getRootPane().setDefaultButton(ok);

		// make all text fields select-all when entered
		EDialog.makeTextFieldSelectAllOnTab(verticalLow);
		EDialog.makeTextFieldSelectAllOnTab(verticalHigh);
		EDialog.makeTextFieldSelectAllOnTab(verticalScale);
		EDialog.makeTextFieldSelectAllOnTab(horizontalLow);
		EDialog.makeTextFieldSelectAllOnTab(horizontalHigh);
		YScaleListener valueListener = new YScaleListener(false);
		YScaleListener scaleListener = new YScaleListener(true);

		trueLowY = lowVert;   trueHighY = highVert;
		if (scaleVert != 0 && !logVert) { lowVert /= scaleVert;   highVert /= scaleVert; }
		verticalLow.setText(Double.toString(lowVert));
		verticalHigh.setText(Double.toString(highVert));
		verticalScale.setText(Double.toString(scaleVert));
		horizontalLow.setText(Double.toString(lowHoriz));
		horizontalHigh.setText(Double.toString(highHoriz));
		if (logVert) verticalLogarithmic.setSelected(true); else
			verticalLinear.setSelected(true);

		verticalLow.getDocument().addDocumentListener(valueListener);
		verticalHigh.getDocument().addDocumentListener(valueListener);
		verticalScale.getDocument().addDocumentListener(scaleListener);

		finishInitialization();
		setVisible(true);
	}

	/**
	 * Method called when the "Linear" or "Log" radio buttons are clicked.
	 */
	private void linearLogClicked()
	{
		double lowVert = trueLowY, highVert = trueHighY;
		if (verticalLinear.isSelected())
		{
			// doing linear, enable scaling
			verticalScaleLabel.setEnabled(true);
			verticalScale.setEnabled(true);
			setScaleTo2Pi.setEnabled(true);
			if (trueScale != 0) { lowVert /= trueScale;   highVert /= trueScale; }
		} else
		{
			// doing logarithmic, disable scaling
			verticalScaleLabel.setEnabled(false);
			verticalScale.setEnabled(false);
			setScaleTo2Pi.setEnabled(false);
			verticalScale.setText("");
		}
		updatingTextFields = true;
		verticalLow.setText(Double.toString(lowVert));
		verticalHigh.setText(Double.toString(highVert));
		verticalScale.setText(trueScale+"");
		updatingTextFields = false;
	}

	/**
	 * Method called when the "Scale to 2 Pi" is clicked.
	 */
	private void setScaleTo2Pi()
	{
		verticalScale.setText((Math.PI * 2) + "");
	}

	/**
	 * Method called when "OK" is clicked to set the values.
	 */
	private void okayClicked()
	{
		double scaleVert = TextUtils.atof(verticalScale.getText());
		double lowHoriz = TextUtils.atof(horizontalLow.getText());
		double highHoriz = TextUtils.atof(horizontalHigh.getText());
		boolean logVert = verticalLogarithmic.isSelected();
		curWindow.setZoomExtents(trueLowY, trueHighY, scaleVert, logVert, lowHoriz, highHoriz, curPanel);
		closeDialog(null);
	}

	protected void escapePressed() { cancel(null); }

	/**
	 * Class for handling changes to the Y axis text fields.
	 */
	class YScaleListener implements DocumentListener
	{
		private boolean scaleField;
		public YScaleListener(boolean sf) { scaleField = sf; }

		public void insertUpdate(DocumentEvent e) { updateYScale(); }

		public void removeUpdate(DocumentEvent e) { updateYScale(); }

		public void changedUpdate(DocumentEvent e) { updateYScale(); }

		protected void updateYScale()
		{
			if (updatingTextFields) return;
			if (scaleField)
			{
				trueScale = TextUtils.atof(verticalScale.getText());
				if (trueScale == 0) trueScale = 1;
				if (verticalLinear.isSelected())
				{
					updatingTextFields = true;
					verticalLow.setText(Double.toString(trueLowY / trueScale));
					verticalHigh.setText(Double.toString(trueHighY / trueScale));
					updatingTextFields = false;
				}
			} else
			{
				double lowValue = TextUtils.atof(verticalLow.getText());
				double highValue = TextUtils.atof(verticalHigh.getText());
				if (trueScale != 0 && verticalLinear.isSelected())
				{
					lowValue *= trueScale;
					highValue *= trueScale;
				}
				trueLowY = lowValue;
				trueHighY = highValue;
			}
		}
	};

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup1 = new javax.swing.ButtonGroup();
        cancel = new javax.swing.JButton();
        ok = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        verticalLow = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        verticalHigh = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        horizontalLow = new javax.swing.JTextField();
        horizontalHigh = new javax.swing.JTextField();
        verticalScaleLabel = new javax.swing.JLabel();
        verticalScale = new javax.swing.JTextField();
        setScaleTo2Pi = new javax.swing.JButton();
        verticalLogarithmic = new javax.swing.JRadioButton();
        verticalLinear = new javax.swing.JRadioButton();
        jSeparator1 = new javax.swing.JSeparator();

        setTitle("Set Window Extents");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        cancel.setText("Cancel");
        cancel.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                cancel(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(cancel, gridBagConstraints);

        ok.setText("OK");
        ok.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                ok(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(ok, gridBagConstraints);

        jLabel1.setText("Low:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        verticalLow.setColumns(15);
        verticalLow.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(verticalLow, gridBagConstraints);

        jLabel2.setText("High:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel2, gridBagConstraints);

        verticalHigh.setColumns(15);
        verticalHigh.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(verticalHigh, gridBagConstraints);

        jLabel3.setText("Vertical axis");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel3, gridBagConstraints);

        jLabel4.setText("Horizontal axis (time)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel4, gridBagConstraints);

        horizontalLow.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(horizontalLow, gridBagConstraints);

        horizontalHigh.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(horizontalHigh, gridBagConstraints);

        verticalScaleLabel.setText("Scale:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(verticalScaleLabel, gridBagConstraints);

        verticalScale.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(verticalScale, gridBagConstraints);

        setScaleTo2Pi.setText("Set to 2 Pi");
        setScaleTo2Pi.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                setScaleTo2PiActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(setScaleTo2Pi, gridBagConstraints);

        buttonGroup1.add(verticalLogarithmic);
        verticalLogarithmic.setText("Logarithmic");
        verticalLogarithmic.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                verticalLogarithmicActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(verticalLogarithmic, gridBagConstraints);

        buttonGroup1.add(verticalLinear);
        verticalLinear.setText("Linear");
        verticalLinear.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                verticalLinearActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(verticalLinear, gridBagConstraints);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jSeparator1, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void cancel(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancel
	{//GEN-HEADEREND:event_cancel
		closeDialog(null);
	}//GEN-LAST:event_cancel

	private void ok(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ok
	{//GEN-HEADEREND:event_ok
		okayClicked();
	}//GEN-LAST:event_ok

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    private void setScaleTo2PiActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_setScaleTo2PiActionPerformed
    {//GEN-HEADEREND:event_setScaleTo2PiActionPerformed
    	setScaleTo2Pi();
    }//GEN-LAST:event_setScaleTo2PiActionPerformed

    private void verticalLogarithmicActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_verticalLogarithmicActionPerformed
    	linearLogClicked();
    }//GEN-LAST:event_verticalLogarithmicActionPerformed

    private void verticalLinearActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_verticalLinearActionPerformed
    {//GEN-HEADEREND:event_verticalLinearActionPerformed
    	linearLogClicked();
    }//GEN-LAST:event_verticalLinearActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cancel;
    private javax.swing.JTextField horizontalHigh;
    private javax.swing.JTextField horizontalLow;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton ok;
    private javax.swing.JButton setScaleTo2Pi;
    private javax.swing.JTextField verticalHigh;
    private javax.swing.JRadioButton verticalLinear;
    private javax.swing.JRadioButton verticalLogarithmic;
    private javax.swing.JTextField verticalLow;
    private javax.swing.JTextField verticalScale;
    private javax.swing.JLabel verticalScaleLabel;
    // End of variables declaration//GEN-END:variables
}
