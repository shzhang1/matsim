/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.core.mobsim.qsim.agents;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.mobsim.framework.AgentSource;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

public class PopulationAgentSource implements AgentSource {

	private Population population;
	private AgentFactory agentFactory;
	private QSim qsim;
	private Map<String, VehicleType> modeVehicleTypes;
	private Collection<String> mainModes;

	public PopulationAgentSource(Population population, AgentFactory agentFactory, QSim qsim) {
		this.population = population;
		this.agentFactory = agentFactory;
		this.qsim = qsim;  
		this.modeVehicleTypes = new HashMap<String, VehicleType>();
		this.mainModes = qsim.getScenario().getConfig().qsim().getMainModes();
		for (String mode : mainModes) {
			modeVehicleTypes.put(mode, VehicleUtils.getDefaultVehicleType());
		}
	}

	@Override
	public void insertAgentsIntoMobsim() {
		for (Person p : population.getPersons().values()) {
			MobsimAgent agent = this.agentFactory.createMobsimAgentFromPerson(p);
			Plan plan = p.getSelectedPlan();
			Set<String> seenModes = new HashSet<String>();
			for (PlanElement planElement : plan.getPlanElements()) {
				if (planElement instanceof Leg) {
					Leg leg = (Leg) planElement;
					if (this.mainModes.contains(leg.getMode())) { // only simulated modes get vehicles
						if (!seenModes.contains(leg.getMode())) { // create one vehicle per simulated mode, put it on the home location
							Id vehicleLink = findVehicleLink(p);
							qsim.createAndParkVehicleOnLink(VehicleUtils.getFactory().createVehicle(p.getId(), modeVehicleTypes.get(leg.getMode())), vehicleLink);
							seenModes.add(leg.getMode());
						}
					}
				}
			}
			// When the agent is inserted, it immediately starts its first activity, and
			// possibly ends it (if it is 0-duration or if the simulation start time is later than
			// the activity end time), so the action really starts here!
			// E.g. the vehicle must already be in place at this point.
			qsim.insertAgentIntoMobsim(agent);
		}
	}

	/**
	 *	A more careful way to decide where this agent should have its vehicles created
	 *  than to ask agent.getCurrentLinkId() after creation.
	 */
	public static final Id findVehicleLink(Person p) {
		// hope it is ok to make this public as long as it is static final. kai, mar'14
		
		for (PlanElement planElement : p.getSelectedPlan().getPlanElements()) {
			if (planElement instanceof Activity) {
				Activity activity = (Activity) planElement;
				if (activity.getLinkId() != null) {
					return activity.getLinkId();
				}
			} else if (planElement instanceof Leg) {
				Leg leg = (Leg) planElement;
				if (leg.getRoute().getStartLinkId() != null) {
					return leg.getRoute().getStartLinkId();
				}
			}
		}
		throw new RuntimeException("Don't know where to put a vehicle for this agent.");
	}

	public void setModeVehicleTypes(Map<String, VehicleType> modeVehicleTypes) {
		this.modeVehicleTypes = modeVehicleTypes;
	}

}
