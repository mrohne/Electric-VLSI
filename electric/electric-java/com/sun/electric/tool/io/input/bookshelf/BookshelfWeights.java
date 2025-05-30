/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BookshelfWeights.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.input.bookshelf;

import com.sun.electric.tool.io.input.bookshelf.BookshelfNodes.BookshelfNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * @author Felix Schmidt
 * 
 */
public class BookshelfWeights implements BookshelfInputParser<Void> {

	private String nodesFile;

	public BookshelfWeights(String nodesFile) {
		this.nodesFile = nodesFile;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.io.input.bookshelf.BookshelfInputParser#parse()
	 */
	public Void parse() throws IOException {

		// Job.getUserInterface().setProgressNote("Parse Weights File");
		BufferedReader rin;
		try
		{
			File file = new File(nodesFile);
			FileReader freader = new FileReader(file);
			rin = new BufferedReader(freader);
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Cannot find Bookshelf Weights file: " + nodesFile);
			return null;
		}

		String line;
		while ((line = rin.readLine()) != null) {
			if (line.startsWith("   ")) {
				StringTokenizer tokenizer = new StringTokenizer(line, " ");
				int i = 0;
				String node = null;
				int weight = 1;
				while (tokenizer.hasMoreElements())
                {
                    String nt = tokenizer.nextToken();
					if (i == 0) {
						node = nt;
					} else if (i == 1)
                    {
                        try{
						    weight = Integer.parseInt(nt);
                        } catch (Exception e)
                        {
                            weight = Integer.MAX_VALUE;
                            System.out.println("Invalid weight integer for node '" + node + "' : " + nt +
                                    ". Assigning " + weight);

                        }
					} else {
						; //tokenizer.nextToken(); // nothing to do
					}
					i++;
				}

				BookshelfNode bn = BookshelfNode.findNode(node);

				if (bn != null)
					bn.setWeight(weight);
			}
		}
		rin.close();

		return null;
	}

}