/* *********************************************************************** *
 * project: org.matsim.*
 * RunEmissionToolOffline.java
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

package playground.jbischoff.taxi.usability;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PopulationFactoryImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.EmptyStageActivityTypes;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.facilities.Facility;

/**
 * @author balac,jbischoff
 *
 */
public class TaxiserviceRoutingModule implements RoutingModule {

	private final MatsimServices controler;
	public TaxiserviceRoutingModule (MatsimServices controler) {
		
		this.controler = controler;
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public List<? extends PlanElement> calcRoute(Facility fromFacility,
			Facility toFacility, double departureTime, Person person) {
		double travelTime = 0.0;
	
		List<PlanElement> trip = new ArrayList<PlanElement>();

		
		Leg taxiLeg = new LegImpl("taxi");
		taxiLeg.setTravelTime( travelTime );
		NetworkRoute route = 
				(NetworkRoute) ((PopulationFactoryImpl)controler.getScenario().getPopulation().getFactory()).getModeRouteFactory().createRoute(NetworkRoute.class, fromFacility.getLinkId(), toFacility.getLinkId());
		route.setTravelTime( travelTime);
		
		taxiLeg.setRoute(route);
		
		trip.add(taxiLeg);
		return trip;
		
	}

	@Override
	public StageActivityTypes getStageActivityTypes() {
		return EmptyStageActivityTypes.INSTANCE;
	}
	
	
	

}
