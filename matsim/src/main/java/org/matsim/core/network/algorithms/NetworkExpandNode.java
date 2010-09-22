/* *********************************************************************** *
 * project: org.matsim.*
 * NetworkExpandNode.java
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

package org.matsim.core.network.algorithms;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.internal.NetworkRunnable;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;

/**
 * A Procedure to expand a node of the {@link Network network}.
 *
 * <p><b>Note:</b> it is actually not completely clear that this should be a
 * MATSim network module. It could also be a method in the network layer
 * or---probably even better---a MATSim utility/procedure/algo/etc... The
 * logical organization of <code>org.matsim</code> still needs to be discussed</p>
 *
 * @author balmermi
 */
public class NetworkExpandNode implements NetworkRunnable {

	//////////////////////////////////////////////////////////////////////
	// member variables
	//////////////////////////////////////////////////////////////////////

	private final static Logger log = Logger.getLogger(NetworkExpandNode.class);

	private Id nId = null;
	private ArrayList<Tuple<Id,Id>> turns = null;
	private double radius = Double.NaN;
	private double offset = Double.NaN;

	//////////////////////////////////////////////////////////////////////
	// constructors
	//////////////////////////////////////////////////////////////////////

	public NetworkExpandNode() {
		log.info("init " + this.getClass().getName() + " module...");
		log.info("done.");
	}

	//////////////////////////////////////////////////////////////////////
	// set methods
	//////////////////////////////////////////////////////////////////////

	public final void setNodeId(Id nodeId) {
		this.nId = nodeId;
	}

	public final void setTurns(ArrayList<Tuple<Id,Id>> turns) {
		this.turns = turns;
	}

	public final void setNodeExpansionRadius(double radius) {
		this.radius = radius;
	}

	public final void setNodeExpansionOffset(double offset) {
		this.offset = offset;
	}

	//////////////////////////////////////////////////////////////////////
	// run method
	//////////////////////////////////////////////////////////////////////

	/**
	 * The run method such that it is still consistent with the other network algorithms.
	 * But rather use {@link #expandNode(Network,Id,ArrayList,double,double)}.
	 *
	 * @param network
	 */
	@Override
	public void run(final Network network) {
		log.info("running " + this.getClass().getName() + " module...");
		this.expandNode(network,this.nId,this.turns,this.radius,this.offset);
		log.info("running " + this.getClass().getName() + " module...");
	}

	//////////////////////////////////////////////////////////////////////
	// expand method
	//////////////////////////////////////////////////////////////////////

	/**
	 * Expands the {@link Node node} that is part of the {@link Network network} and holds
	 * {@link Id nodeId}.
	 *
	 * <p>It is done in the following way:
	 * <ol>
	 * <li>creates for each in- and out-{@link Link link} a new {@link Node node} with
	 * <ul>
	 * <li><code>{@link Id new_nodeId} = {@link Id nodeId+"-"+index}; index=[0..#incidentLinks]</code></li>
	 * <li><code>{@link Coord new_coord}</code> with distance <code>r</code> to the given {@link Node node}
	 * in direction of the corresponding incident {@link Link link} of the given {@link Node node} with
	 * offset <code>e</code>.</li>
	 * </ul>
	 * <pre>
	 * <-----12------         <----21-------
	 *
	 *            x-0 o     o 1-5
	 *                   O nodeId = x
	 *            x-1 o     o x-4
	 *             x-2 o   o x-3
	 * ------11----->         -----22------>
	 *               |       ^
	 *               |       |
	 *              32       31
	 *               |       |
	 *               |       |
	 *               v       |
	 * </pre>
	 * </li>
	 * <li>connects each incident {@link Link link} of the given {@link Node node} with the corresponding
	 * {@link Node new_node}
	 * <pre>
	 * <-----12------ o     o <----21-------
	 *                   O
	 * ------11-----> o     o -----22------>
	 *                 o   o
	 *                 |   ^
	 *                 |   |
	 *                32   31
	 *                 |   |
	 *                 |   |
	 *                 v   |
	 * </pre>
	 * </li>
	 * <li>removes the given {@link Node node} from the {@link Network network}
	 * <pre>
	 * <-----12------ o     o <----21-------
	 *
	 * ------11-----> o     o -----22------>
	 *                 o   o
	 *                 |   ^
	 *                 |   |
	 *                32   31
	 *                 |   |
	 *                 |   |
	 *                 v   |
	 * </pre>
	 * </li>
	 * <li>inter-connects the {@link Node new_nodes} with {@link Link new_links} as defined in the
	 * <code>turns</code> list, with:<br>
	 * <ul>
	 * <li><code>{@link Id new_linkId} = {@link Id fromLinkId+"-"+index}; index=[0..#turn-tuples]</code></li>
	 * <li>length equals the Euclidean distance</li>
	 * <li>freespeed, capacity, permlanes, origId and type are equals to the attributes of the fromLink.</li>
	 * </ul>
	 * <pre>
	 * <-----12------ o <--21-0-- o <----21-------
	 *
	 * ------11-----> o --11-1--> o -----22------>
	 *                 \         ^
	 *              11-2\       /31-3
	 *                   \     /
	 *                    v   /
	 *                    o   o
	 *                    |   ^
	 *                    |   |
	 *                   32   31
	 *                    |   |
	 *                    |   |
	 *                    v   |
	 * </pre>
	 * </li>
	 * </ol>
	 * </p>
	 *
	 * @param network MATSim {@link Network network} DB
	 * @param nodeId the {@link Id} of the {@link Node} to expand
	 * @param turns The {@link ArrayList} of {@link Tuple tuples} of {@link Id linkIds}
	 * of the incident {@link Link links} of the given {@link Node node} that define
	 * which driving direction is allowed on that {@link Node node}.
	 * @param r the expansion radius. If zero, all new nodes have the same coordinate
	 * and the new links with have length equals zero
	 * @param e the offset between a link pair with the same incident nodes. If zero, the two new
	 * nodes created for that link pair will have the same coordinates
	 * @return The {@link Tuple} of {@link ArrayList array lists} containing the new
	 * {@link Node nodes}, new {@link Link links} resp.
	 */
	public final Tuple<ArrayList<Node>,ArrayList<Link>> expandNode(final Network network, final Id nodeId, final ArrayList<Tuple<Id,Id>> turns, final double r, final double e) {
		// check the input
		if (Double.isNaN(r)) { throw new IllegalArgumentException("nodeid="+nodeId+": expansion radius is NaN."); }
		if (Double.isNaN(e)) { throw new IllegalArgumentException("nodeid="+nodeId+": expansion radius is NaN."); }
		if (network == null) { throw new IllegalArgumentException("network not defined."); }
		Node node = network.getNodes().get(nodeId);
		if (node == null) { throw new IllegalArgumentException("nodeid="+nodeId+": not found in the network."); }
		if (turns == null) {throw new IllegalArgumentException("nodeid="+nodeId+": turn list not defined!"); }
		for (int i=0; i<turns.size(); i++) {
			Id first = turns.get(i).getFirst();
			if (first == null) { throw new IllegalArgumentException("given list contains 'null' values."); }
			if (!node.getInLinks().containsKey(first)) { throw new IllegalArgumentException("nodeid="+nodeId+", linkid="+first+": link not an inlink of given node."); }
			Id second = turns.get(i).getSecond();
			if (second == null) { throw new IllegalArgumentException("given list contains 'null' values."); }
			if (!node.getOutLinks().containsKey(second)) { throw new IllegalArgumentException("nodeid="+nodeId+", linkid="+second+": link not an outlink of given node."); }
		}

		// remove the node
		Map<Id,Link> inlinks = new TreeMap<Id, Link>(node.getInLinks());
		Map<Id,Link> outlinks = new TreeMap<Id, Link>(node.getOutLinks());
		if (network.removeNode(node.getId()) == null) { throw new RuntimeException("nodeid="+nodeId+": Failed to remove node from the network."); }

		ArrayList<Node> newNodes = new ArrayList<Node>(inlinks.size()+outlinks.size());
		ArrayList<Link> newLinks = new ArrayList<Link>(turns.size());
		// add new nodes and connect them with the in and out links
		int nodeIdCnt = 0;
		double d = Math.sqrt(r*r-e*e);
		for (Link inlink : inlinks.values()) {
			Coord c = node.getCoord();
			Coord p = inlink.getFromNode().getCoord();
			Coord pc = new CoordImpl(c.getX()-p.getX(),c.getY()-p.getY());
			double lpc = Math.sqrt(pc.getX()*pc.getX()+pc.getY()*pc.getY());
			double x = p.getX()+(1-d/lpc)*pc.getX()+e/lpc*pc.getY();
			double y = p.getY()+(1-d/lpc)*pc.getY()-e/lpc*pc.getX();
			Node n = network.getFactory().createNode(new IdImpl(node.getId()+"-"+nodeIdCnt),new CoordImpl(x,y));
			network.addNode(n);
			newNodes.add(n);
			nodeIdCnt++;
			Link l = network.getFactory().createLink(inlink.getId(), inlink.getFromNode().getId(), n.getId());
			l.setLength(inlink.getLength());
			l.setFreespeed(inlink.getFreespeed());
			l.setCapacity(inlink.getCapacity());
			l.setNumberOfLanes(inlink.getNumberOfLanes());
			((LinkImpl) l).setOrigId(((LinkImpl) inlink).getOrigId());
			((LinkImpl) l).setType(((LinkImpl) inlink).getType());
			network.addLink(l);
		}
		for (Link outlink : outlinks.values()) {
			Coord c = node.getCoord();
			Coord p = outlink.getToNode().getCoord();
			Coord cp = new CoordImpl(p.getX()-c.getX(),p.getY()-c.getY());
			double lcp = Math.sqrt(cp.getX()*cp.getX()+cp.getY()*cp.getY());
			double x = c.getX()+d/lcp*cp.getX()+e/lcp*cp.getY();
			double y = c.getY()+d/lcp*cp.getY()-e/lcp*cp.getX();
			Node n = network.getFactory().createNode(new IdImpl(node.getId()+"-"+nodeIdCnt),new CoordImpl(x,y));
			network.addNode(n);
			newNodes.add(n);
			nodeIdCnt++;
			Link l = network.getFactory().createLink(outlink.getId(), n.getId(), outlink.getToNode().getId());
			l.setLength(outlink.getLength());
			l.setFreespeed(outlink.getFreespeed());
			l.setCapacity(outlink.getCapacity());
			l.setNumberOfLanes(outlink.getNumberOfLanes());
			((LinkImpl) l).setOrigId(((LinkImpl) outlink).getOrigId());
			((LinkImpl) l).setType(((LinkImpl) outlink).getType());
			network.addLink(l);
		}

		// add virtual links for the turn restrictions
		for (int i=0; i<turns.size(); i++) {
			Tuple<Id,Id> turn = turns.get(i);
			Link fromLink = network.getLinks().get(turn.getFirst());
			Link toLink = network.getLinks().get(turn.getSecond());
			Link l = network.getFactory().createLink(new IdImpl(fromLink.getId()+"-"+i), fromLink.getToNode().getId(), toLink.getFromNode().getId());
			l.setLength(CoordUtils.calcDistance(toLink.getFromNode().getCoord(), fromLink.getToNode().getCoord()));
			l.setFreespeed(fromLink.getFreespeed());
			l.setCapacity(fromLink.getCapacity());
			l.setNumberOfLanes(fromLink.getNumberOfLanes());
			((LinkImpl) l).setOrigId(((LinkImpl) fromLink).getOrigId());
			((LinkImpl) l).setType(((LinkImpl) fromLink).getType());
			network.addLink(l);
			newLinks.add(l);
		}
		return new Tuple<ArrayList<Node>, ArrayList<Link>>(newNodes,newLinks);
	}
}
