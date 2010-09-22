/* *********************************************************************** *
 * project: org.matsim.*
 * NetworkUtilsTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.utils.misc;

import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.testcases.MatsimTestCase;

/**
 * @author mrieser
 */
public class NetworkUtilsTest extends MatsimTestCase {

	private final static Logger log = Logger.getLogger(NetworkUtilsTest.class);

	public void testGetNodes_Empty() {
		NetworkImpl network = getTestNetwork();
		List<Node> nodes = NetworkUtils.getNodes(network, "");
		assertEquals(0, nodes.size());

		List<Node> nodes2 = NetworkUtils.getNodes(network, " ");
		assertEquals(0, nodes2.size());

		List<Node> nodes3 = NetworkUtils.getNodes(network, " \t\r\n \t  \t ");
		assertEquals(0, nodes3.size());
	}

	public void testGetNodes_Null() {
		NetworkImpl network = getTestNetwork();
		List<Node> nodes = NetworkUtils.getNodes(network, null);
		assertEquals(0, nodes.size());
	}

	public void testGetNodes_mixedDelimiters() {
		NetworkImpl network = getTestNetwork();
		List<Node> nodes = NetworkUtils.getNodes(network, " 1\t\t2 \n4\t \t5      3 ");
		assertEquals(5, nodes.size());
		assertEquals(network.getNodes().get(new IdImpl(1)), nodes.get(0));
		assertEquals(network.getNodes().get(new IdImpl(2)), nodes.get(1));
		assertEquals(network.getNodes().get(new IdImpl(4)), nodes.get(2));
		assertEquals(network.getNodes().get(new IdImpl(5)), nodes.get(3));
		assertEquals(network.getNodes().get(new IdImpl(3)), nodes.get(4));
	}

	public void testGetNodes_NonExistant() {
		NetworkImpl network = getTestNetwork();
		try {
			NetworkUtils.getNodes(network, "1 3 ab 5");
			fail("expected Exception, but didn't happen.");
		} catch (IllegalArgumentException e) {
			log.info("catched expected exception: " + e.getMessage());
		}
	}
	
	public void testGetLinks_Empty() {
		NetworkImpl network = getTestNetwork();
		List<Link> links = NetworkUtils.getLinks(network, "");
		assertEquals(0, links.size());
		
		List<Link> links2 = NetworkUtils.getLinks(network, " ");
		assertEquals(0, links2.size());
		
		List<Link> links3 = NetworkUtils.getLinks(network, " \t\r\n \t  \t ");
		assertEquals(0, links3.size());
	}
	
	public void testGetLinks_StringNull() {
		NetworkImpl network = getTestNetwork();
		List<Link> links = NetworkUtils.getLinks(network, (String)null);
		assertEquals(0, links.size());
	}
	
	public void testGetLinks_mixedDelimiters() {
		NetworkImpl network = getTestNetwork();
		List<Link> links = NetworkUtils.getLinks(network, " 1\t\t2 \n4\t \t      3 ");
		assertEquals(4, links.size());
		assertEquals(network.getLinks().get(new IdImpl(1)), links.get(0));
		assertEquals(network.getLinks().get(new IdImpl(2)), links.get(1));
		assertEquals(network.getLinks().get(new IdImpl(4)), links.get(2));
		assertEquals(network.getLinks().get(new IdImpl(3)), links.get(3));
	}
	
	public void testGetLinks_NonExistant() {
		NetworkImpl network = getTestNetwork();
		try {
			NetworkUtils.getLinks(network, "1 3 ab 4");
			fail("expected Exception, but didn't happen.");
		} catch (IllegalArgumentException e) {
			log.info("catched expected exception: " + e.getMessage());
		}
	}
	
	private NetworkImpl getTestNetwork() {
		int numOfLinks = 5;
		
		NetworkImpl network = NetworkImpl.createNetwork();
		Node[] nodes = new Node[numOfLinks+1];
		for (int i = 0; i <= numOfLinks; i++) {
			nodes[i] = network.createAndAddNode(new IdImpl(i), new CoordImpl(1000 * i, 0));
		}
		for (int i = 0; i < numOfLinks; i++) {
			network.createAndAddLink(new IdImpl(i), nodes[i], nodes[i+1], 1000.0, 10.0, 3600.0, 1);
		}
		return network;
	}
}
