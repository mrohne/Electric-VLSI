/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: JoSimOut.java
 * Input/output tool: reader for JoSIM text output (.csv)
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2019, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.input;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.simulation.ScalarSample;
import com.sun.electric.tool.simulation.Signal;
import com.sun.electric.tool.simulation.SignalCollection;
import com.sun.electric.tool.simulation.Stimuli;
import com.sun.electric.util.TextUtils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Class for reading and displaying waveforms from JoSIM output.
 * These are contained in .csv files.
 *
 * Here is an example file:
 * time,RRBEG,RRENDA,RRENDB,RRENDC
 * 0.0000000000000000e+00,0.0000000000000000e+00,0.0000000000000000e+00,0.0000000000000000e+00,0.0000000000000000e+00
 * 2.4999999999999999e-13,0.0000000000000000e+00,2.6271616224140834e-06,2.6244679390407456e-06,2.6244679390407456e-06
 */
public class JoSimOut extends Input<Stimuli>
{
	JoSimOut()
	{
		super(null);
	}

	/**
	 * Method to read an JoSIM output file.
	 */
	protected Stimuli processInput(URL fileURL, Cell cell, Stimuli sd)
		throws IOException
	{
		sd.setNetDelimiter(" ");

		// open the file
		if (openTextInput(fileURL)) return sd;

		// show progress reading .csv file
		System.out.println("Reading JoSIM output file: " + fileURL.getFile());
		startProgressDialog("JoSIM output", fileURL.getFile());

		// read the actual signal data from the .csv file
		readJoSimFile(cell, sd);

		// stop progress dialog, close the file
		stopProgressDialog();
		closeInput();
		return sd;
	}

	private void readJoSimFile(Cell cell, Stimuli sd)
		throws IOException
	{
		boolean first = true;
		SignalCollection sc = Stimuli.newSignalCollection(sd, "SIGNALS");
		sd.setCell(cell);
		List<String> signalNames = new ArrayList<String>();
		List<Double> [] values = null;
		int numSignals = 0;
		for(;;)
		{
			String line = getLine();
			if (line == null) break;

			if (first)
			{
				// check the first line for HSPICE format possibility
				first = false;
				if (line.length() >= 20)
				{
					String hsFormat = line.substring(16, 20);
					if (hsFormat.equals("9007") || hsFormat.equals("9601"))
					{
						System.out.println("This is an HSPICE file, not a JoSIM file");
						System.out.println("Change the SPICE format (in Preferences) and reread");
						return;
					}
				}

				// parse the signal names on the first line
				int ptr = 0;
				for(;;)
				{
					int start = ptr;
					while (ptr < line.length() && line.charAt(ptr) != ',') ptr++;
					signalNames.add(line.substring(start, ptr));
					ptr++;
					if (ptr >= line.length()) break;
				}
				numSignals = signalNames.size();
				values = new List[numSignals];
				for(int i=0; i<numSignals; i++)
					values[i] = new ArrayList<Double>();
				continue;
			}

			// skip first word if there is an "=" in the line
			int equalPos = line.indexOf("=");

			if (equalPos >= 0)
			{
				if (line.length() > (equalPos+3))
					line = line.substring(equalPos+3);
				else
				{
					System.out.println("Missing value after '='.  This may not be a JoSIM output file.");
					return;
				}
			}

			// read the data values
			int ptr = 0;
			int position = 0;
			for(;;)
			{
				int start = ptr;
				while (ptr < line.length() && line.charAt(ptr) != ',') ptr++;
				double value = TextUtils.atof(line.substring(start, ptr));
				values[position++].add(Double.valueOf(value));
				ptr++;
				if (ptr >= line.length()) break;
			}
			if (position != numSignals)
			{
				System.out.println("Line of data has " + position + " values, but expect " + numSignals +
					". Unable to recover from error.  This may not be a JoSIM output file.");
				return;
			}
		}

		// convert lists to arrays
		if (numSignals == 0)
		{
			System.out.println("No data found in the file.  This may not be a JoSIM output file.");
			return;
		}
		int numEvents = values[0].size();
		double[] time = new double[numEvents];
		for(int i=0; i<numEvents; i++)
			time[i] = values[0].get(i).doubleValue();
		for(int j=1; j<numSignals; j++)
		{
			double[] doubleValues = new double[numEvents];
			for(int i=0; i<numEvents; i++)
				doubleValues[i] = values[j].get(i).doubleValue();
			ScalarSample.createSignal(sc, sd, signalNames.get(j), null, time, doubleValues);
		}
	}

}
