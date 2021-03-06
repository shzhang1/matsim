/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package playground.polettif.multiModalMap.mapping;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.*;
import playground.polettif.multiModalMap.mapping.pseudoPTRouter.DijkstraAlgorithm;
import playground.polettif.multiModalMap.mapping.pseudoPTRouter.PseudoGraph;
import playground.polettif.multiModalMap.mapping.pseudoPTRouter.LinkCandidate;
import playground.polettif.multiModalMap.mapping.pseudoPTRouter.LinkCandidatePath;
import playground.polettif.multiModalMap.mapping.router.FastAStarLandmarksRouting;
import playground.polettif.multiModalMap.mapping.router.Router;

import java.util.*;

/**
 * References an unmapped transit schedule to a  b network. Combines routing and referencing of stopFacilities. Creates additional
 * stop facilities if a stopFacility has more than one plausible link. @see main()
 *
 * Creates pseudo paths via all link candidates, chooses the path with the lowest travel time.
 * <p>
 * TODO doc input is modified
 *
 * @author polettif
 */
public class PTMapperPseudoShortestPath extends PTMapper {

	// TODO ensure coordinate system is not WGS84 since this is not suitable for coordinate calculations (or warn accordingly)
	// TODO move params to a config?

	/**
	 * Defines the radius [meter] from a stop facility within nodes are searched.
	 * Mainly a maximum value for performance.
	 */
	private final static double NODE_SEARCH_RADIUS = 300;

	/**
	 * Number of link candidates considered for all stops, depends on accuracy of
	 * stops and desired performance. Somewhere between 4 and 10 seems reasonable,
	 * depending on the accuracy of the stop facility coordinates.
	 */
	private final static int MAX_N_CLOSEST_LINKS = 8;

	/**
	 * The maximal distance [meter] a link candidate is allowed to have from the stop facility.
	 */
	private final static int MAX_STOPFACILITY_LINK_DISTANCE = 50;

	/**
	 * ID prefix used for artificial link created if no nodes are found within {@link #NODE_SEARCH_RADIUS}
	 */
	private final static String PREFIX_ARTIFICIAL_LINKS = "pt_";

	/**
	 * Suffix used for child stop facilities. A number for each child of a parent stop facility is appended (i.e. stop0123_fac:2)
	 */
	private final static String SUFFIX_CHILD_STOPFACILITIES = "_fac:";

	/**
	 * if two link candidates are the same travel time is multiplied by this factor. Otherwise travel time
	 * would just be the link traveltime since routing works with nodes.
	 */
	private static final double SAME_LINK_PUNISHMENT = 3;

	/**
	 * fields
	 */

	/**
	 * Constructor
	 */
	public PTMapperPseudoShortestPath(TransitSchedule schedule) {
		super(schedule);
	}

	/**
	 * Routes the unmapped MATSim Transit Schedule file to the network given by file. Writes the resulting
	 * schedule and network to xml files.<p/>
	 * <p/>
	 *
	 * @param args <br/>[0] unmapped MATSim Transit Schedule file<br/>
	 *             [1] MATSim network file<br/>
	 *             [2] output schedule file path<br/>
	 *             [3] output network file path
	 */
	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("Incorrect number of arguments\n[0] unmapped schedule file\n[1] network file\n[2] output schedule path\n[3]output network path");
		} else {
			mapFromFiles(args[0], args[1], args[2], args[3]);
		}
	}

	/**
	 * Routes the unmapped MATSim Transit Schedule file to the network given by file. Writes the resulting
	 * schedule and network to xml files.<p/>
	 *
	 * @param matsimTransitScheduleFile unmapped MATSim Transit Schedule (unmapped: stopFacilities are not referenced to links and routes do not have a network route (linkSequence) yet.
	 * @param networkFile               MATSim network file
	 * @param outputScheduleFile        the resulting MATSim Transit Schedule
	 * @param outputNetworkFile         the resulting MATSim network (might have some additional links added)
	 */
	public static void mapFromFiles(String matsimTransitScheduleFile, String networkFile, String outputScheduleFile, String outputNetworkFile) {
		log.info("Reading schedule and network file...");
		Scenario mainScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Network mainNetwork = mainScenario.getNetwork();
		new TransitScheduleReader(mainScenario).readFile(matsimTransitScheduleFile);
		new MatsimNetworkReader(mainNetwork).readFile(networkFile);
		TransitSchedule mainSchedule = mainScenario.getTransitSchedule();

		log.info("Mapping transit schedule to network...");
		new PTMapperPseudoShortestPath(mainSchedule).mapScheduleToNetwork(mainNetwork);

		log.info("Writing schedule and network to file...");
		new TransitScheduleWriter(mainSchedule).writeFile(outputScheduleFile);
		new NetworkWriter(mainNetwork).write(outputNetworkFile);

		log.info("Mapping public transit to network successful!");
	}


	@Override
	public void mapScheduleToNetwork(Network networkParam) {
		setNetwork(networkParam);

		log.info("Creating PT lines...");

		Counter counterLine = new Counter("route # ");

		/** [.]
		 * preload closest links and create child StopFacilities
		 * if a stop facility is already referenced (manually beforehand for example) no child facilities are created
		 * stopfacilities with no links within search radius need artificial links and nodes before routing starts
		 */
		StopFacilityTree stopFacilityTree = new StopFacilityTree(schedule, network, NODE_SEARCH_RADIUS, MAX_N_CLOSEST_LINKS, MAX_STOPFACILITY_LINK_DISTANCE);

		Map<Tuple<Link, Link>, LeastCostPathCalculator.Path> pathsStorage = new HashMap<>();

		// initiate router
		Router router = new FastAStarLandmarksRouting(this.network);

		/** [3]-[5]
		 * iterate through
		 * - lines
		 *   - routes
		 * 	   - stops
		 * 	   > calculate link weights
		 * 	   > get best scoring link and assign child stop facility
		 */
		for (TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				// todo modes!
				if (transitRoute.getTransportMode().equals("bus")) {

					List<TransitRouteStop> routeStops = transitRoute.getStops();

					counterLine.incCounter();

					PseudoGraph pseudoGraph = new PseudoGraph();
					DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(pseudoGraph);

					/** [.]
					 * calculate shortest paths between each link candidate
					 */

					// add dummy edges and nodes to pseudoGraph before the transitRoute
					pseudoGraph.addDummyBefore(stopFacilityTree.getLinkCandidates(routeStops.get(0).getStopFacility()));

					for (int i = 0; i < routeStops.size()-1; i++) {
						boolean firstPath = false, lastPath = false;
						List<LinkCandidate> linkCandidatesCurrent = stopFacilityTree.getLinkCandidates(routeStops.get(i).getStopFacility());
						List<LinkCandidate> linkCandidatesNext = stopFacilityTree.getLinkCandidates(routeStops.get(i+1).getStopFacility());

						if(i == 0) { firstPath = true; }
						if(i == routeStops.size()-2) { lastPath = true; }

						for(LinkCandidate linkCandidateCurrent : linkCandidatesCurrent) {
							for(LinkCandidate linkCandidateNext : linkCandidatesNext) {
								LeastCostPathCalculator.Path leastCostPath = router.calcLeastCostPath(linkCandidateCurrent.getLink().getToNode(), linkCandidateNext.getLink().getFromNode());

								double travelTime = leastCostPath.travelTime;

								// if both links are the same and to link are the same, travel time should get higher since those
								if(linkCandidateCurrent.getLink().equals(linkCandidateNext.getLink()))	{
									travelTime = travelTime*SAME_LINK_PUNISHMENT;
								}

								pseudoGraph.addPath(new LinkCandidatePath(linkCandidateCurrent, linkCandidateNext, travelTime, firstPath, lastPath));
							}
						}
					}

					// add dummy edges and nodes to pseudoGraph before the transitRoute
					pseudoGraph.addDummyAfter(stopFacilityTree.getLinkCandidates(routeStops.get(routeStops.size()-1).getStopFacility()));

					/* [.]
					 * build pseudo network and find shortest path => List<LinkCandidate>
					 */
					dijkstra.run();
					stopFacilityTree.setReplacementPairs(transitLine, transitRoute, dijkstra.getBestLinkCandidates());
				}
			} // - transitRoute loop
		} // - line loop

		/** [6]
		 *
		 */
		log.info("Replacing parent StopFacilities with child StopFacilities...");
		stopFacilityTree.replaceParentWithChildStopFacilities();


		/** [7]
		 * route all routes with the new referenced links
		 */
		PTMapperUtils.routeSchedule(schedule, network, router);

		/** [8]
		 * After all lines created, clean all non-linked stations, all pt-exclusive links (check allowed modes)
		 * and all nodes which are non-linked to any link after the above cleaning...
		 * Clean also the allowed modes for only the modes, no line-number any more...
		 */
		log.info("Clean Stations and Network...");
//		cleanSchedule();
//		addPTModeToNetwork();
		PTMapperUtils.removeNonUsedStopFacilities(schedule);
//		setConnectedStopFacilitiesToIsBlocking();
		log.info("Clean Stations and Network... done.");

		log.info("Creating PT lines... done.");
	}




	/**
	 * Container class to store the lines in with routes which have parent stop facilities needing to be replaced
	 * with child stop facilities.
	 */
	private class ReplacementStorageLines {

		private Map<Id<TransitLine>, ReplacementStorageRoutes> lines = new HashMap<>();

		public void putReplacementPair(Id<TransitLine> lineId, Id<TransitRoute> routeId, TransitStopFacility parentStopFacility, TransitStopFacility childStopFacility) {
			ReplacementStorageRoutes storageRoutes;
			if (lines.containsKey(lineId)) {
				storageRoutes = lines.get(lineId);
			} else {
				storageRoutes = new ReplacementStorageRoutes();
			}
			storageRoutes.addPair(routeId, parentStopFacility, childStopFacility);
			lines.put(lineId, storageRoutes);
		}

		public Set<Id<TransitLine>> getLineIds() {
			return lines.keySet();
		}

		public Set<Id<TransitRoute>> getRouteIds(Id<TransitLine> lineId) {
			return lines.get(lineId).getRouteIds();
		}

		public ReplacementStorageRoutes getStoredRoute(Id<TransitLine> lineId) {
			return lines.get(lineId);
		}
	}

	/**
	 * Container class to store routes with parent stop facilities that need to be replaced with child stop facilities.
	 */
	private class ReplacementStorageRoutes {

		private Map<Id<TransitRoute>, Map<TransitStopFacility, TransitStopFacility>> routes = new HashMap<>();

		public void addPair(Id<TransitRoute> routeId, TransitStopFacility parentStopFacility, TransitStopFacility childStopFacility) {
			Map<TransitStopFacility, TransitStopFacility> tmp;
			if (routes.containsKey(routeId)) {
				tmp = routes.get(routeId);
			} else {
				tmp = new HashMap<>();
			}

			tmp.put(parentStopFacility, childStopFacility);

			routes.put(routeId, tmp);
		}

		public Set<Id<TransitRoute>> getRouteIds() {
			return routes.keySet();
		}

		public Map<TransitStopFacility, TransitStopFacility> getReplacementPairs(Id<TransitRoute> routeId) {
			return routes.get(routeId);
		}
	}
}
