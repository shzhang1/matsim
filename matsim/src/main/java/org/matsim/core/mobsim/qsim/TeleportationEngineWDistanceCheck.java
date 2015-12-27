package org.matsim.core.mobsim.qsim;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.interfaces.DepartureHandler;
import org.matsim.core.mobsim.qsim.interfaces.MobsimEngine;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;
import org.matsim.vis.snapshotwriters.TeleportationVisData;
import org.matsim.vis.snapshotwriters.VisData;

import javax.inject.Inject;
import java.util.*;

public final class TeleportationEngineWDistanceCheck implements DepartureHandler, MobsimEngine,
VisData {
	/**
	 * Includes all agents that have transportation modes unknown to the
	 * QueueSimulation (i.e. != "car") or have two activities on the same link
	 */
	private final Queue<Tuple<Double, MobsimAgent>> teleportationList = new PriorityQueue<>(
			30, new Comparator<Tuple<Double, MobsimAgent>>() {

		@Override
		public int compare(Tuple<Double, MobsimAgent> o1, Tuple<Double, MobsimAgent> o2) {
			int ret = o1.getFirst().compareTo(o2.getFirst()); // first compare time information
			if (ret == 0) {
				ret = o2.getSecond().getId().compareTo(o1.getSecond().getId()); // if they're equal, compare the Ids: the one with the larger Id should be first
			}
			return ret;
		}
	});
	private final LinkedHashMap<Id<Person>, TeleportationVisData> teleportationData = new LinkedHashMap<>();
	private InternalInterface internalInterface;
	private Scenario scenario;
	private EventsManager eventsManager;

	@Inject
	public TeleportationEngineWDistanceCheck(Scenario scenario, EventsManager eventsManager) {
		this.scenario = scenario;
		this.eventsManager = eventsManager;
	}

	@Override
	public boolean handleDeparture(double now, MobsimAgent agent, Id<Link> linkId) {
		if ( agent.getExpectedTravelTime()==null || agent.getExpectedTravelTime()==Time.UNDEFINED_TIME ) {
			Logger.getLogger( this.getClass() ).info( "mode: " + agent.getMode() );
			throw new RuntimeException("teleportation does not work when travel time is undefined.  There is also really no magic fix for this,"
					+ " since otherwise mode choice optimization will eventually lead to all legs teleported.  kai/mz, apr'15") ;
		}
		
		Double travelTime = null ;

		Double speed = scenario.getConfig().plansCalcRoute().getOrCreateModeRoutingParams( agent.getMode() ).getTeleportedModeSpeed() ;
		// note: freespeed factor does not work for the bushwhacking modes we are interested in here. kai, dec'15

		Facility<?> dpfac = agent.getCurrentFacility() ;
		Facility<?> arfac = agent.getDestinationFacility() ;
		
		if ( speed != null && dpfac != null && arfac != null ) {
			// what do we do if the facilities don't exist??  The router constructs them on the fly; what does the mobsim do?  And
			// we probably can't retrieve them from IDs even if we have coords?

			double dist = NetworkUtils.getEuclidianDistance( dpfac.getCoord(), arfac.getCoord() ) ;
			travelTime = dist / speed ; 
		}
		if ( travelTime != null && travelTime < agent.getExpectedTravelTime() ) {
			travelTime = agent.getExpectedTravelTime() ;
		}
		double arrivalTime = now + travelTime ;
		this.teleportationList.add(new Tuple<>(arrivalTime, agent));

		
		// --- no dynamics below (only vis)
		Link currLink = this.scenario.getNetwork().getLinks().get(linkId);
		Link destLink = this.scenario.getNetwork().getLinks().get(agent.getDestinationLinkId());
		Coord fromCoord = currLink.getToNode().getCoord();
		Coord toCoord = destLink.getToNode().getCoord();
		TeleportationVisData agentInfo = new TeleportationVisData(now, agent.getId(), fromCoord, toCoord, agent.getExpectedTravelTime());
		this.teleportationData.put(agent.getId(), agentInfo);
		return true;
	}

	@Override
	public Collection<AgentSnapshotInfo> addAgentSnapshotInfo(Collection<AgentSnapshotInfo> snapshotList) {
		double time = internalInterface.getMobsim().getSimTimer().getTimeOfDay();
		for (TeleportationVisData teleportationVisData : teleportationData.values()) {
			teleportationVisData.calculatePosition(time);
			snapshotList.add(teleportationVisData);
		}
		return snapshotList;
	}

	@Override
	public void doSimStep(double time) {
		handleTeleportationArrivals();
	}

	private void handleTeleportationArrivals() {
		double now = internalInterface.getMobsim().getSimTimer().getTimeOfDay();
		while (teleportationList.peek() != null) {
			Tuple<Double, MobsimAgent> entry = teleportationList.peek();
			if (entry.getFirst() <= now) {
				teleportationList.poll();
				MobsimAgent personAgent = entry.getSecond();
				personAgent.notifyArrivalOnLinkByNonNetworkMode(personAgent.getDestinationLinkId());
				double distance = personAgent.getExpectedTravelDistance();
				this.eventsManager.processEvent(new TeleportationArrivalEvent(now, personAgent.getId(), distance));
				personAgent.endLegAndComputeNextState(now);
				this.teleportationData.remove(personAgent.getId());
				internalInterface.arrangeNextAgentState(personAgent);
			} else {
				break;
			}
		}
	}

	@Override
	public void onPrepareSim() {

	}

	@Override
	public void afterSim() {
		double now = internalInterface.getMobsim().getSimTimer().getTimeOfDay();
		for (Tuple<Double, MobsimAgent> entry : teleportationList) {
			MobsimAgent agent = entry.getSecond();
			eventsManager.processEvent(new PersonStuckEvent(now, agent.getId(), agent.getDestinationLinkId(), agent.getMode()));
		}
		teleportationList.clear();
	}

	@Override
	public void setInternalInterface(InternalInterface internalInterface) {
		this.internalInterface = internalInterface;
	}

}