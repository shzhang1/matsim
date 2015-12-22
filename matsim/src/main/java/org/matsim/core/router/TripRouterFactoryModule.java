/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * TripRouterFactoryModule.java
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2015 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */

package org.matsim.core.router;

import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterModule;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TripRouterFactoryModule extends AbstractModule {
    @Override
    public void install() {
        install(new LeastCostPathCalculatorModule());
        install(new TransitRouterModule());
        bind(SingleModeNetworksCache.class).asEagerSingleton();
        PlansCalcRouteConfigGroup routeConfigGroup = getConfig().plansCalcRoute();
        
        // bind all freespeed factor routers from config:
        for (String mode : routeConfigGroup.getTeleportedModeFreespeedFactors().keySet()) {
            if (getConfig().transit().isUseTransit() && getConfig().transit().getTransitModes().contains(mode)) {
                // default config contains "pt" as teleported mode, but if we have simulated transit, this is supposed to override it
                // better solve this on the config level eventually.
                continue;
            }
            addRoutingModuleBinding(mode).toProvider(new PseudoTransitRoutingModuleProvider(routeConfigGroup.getModeRoutingParams().get(mode)));
        }
        
        // bind all beeline speed routers from config:
        // (I assume this overwrites freespeed factor routers, but maybe it throws an exception instead. kai, dec'15)
        for (String mode : routeConfigGroup.getTeleportedModeSpeeds().keySet()) {
            addRoutingModuleBinding(mode).toProvider(new TeleportationRoutingModuleProvider(routeConfigGroup.getModeRoutingParams().get(mode)));
        }
        
        // bind all network mode routers from config: 
        for (String mode : routeConfigGroup.getNetworkModes()) {
            addRoutingModuleBinding(mode).toProvider(new NetworkRoutingModuleProvider(mode));
        }
        
        // bind the transit router if transit is set:
        if (getConfig().transit().isUseTransit()) {
            for (String mode : getConfig().transit().getTransitModes()) {
                addRoutingModuleBinding(mode).toProvider(TransitRoutingModuleProvider.class);
            }
            // bind the transit-walk router to whatever is there as walk router:
            addRoutingModuleBinding(TransportMode.transit_walk).to(Key.get(RoutingModule.class, Names.named(TransportMode.walk)));
            // (I think this works since we assume that somewhere above the walk router is already defined. kai, dec'15)
            // (yy may also mean that we cannot define transit walk speed separately from normal walk speed?!  This is, however, not
            // totally terrible: You could just define your own install method, based on the above.  kai, dec'15)
        }
    }

    private static class TransitRoutingModuleProvider implements Provider<RoutingModule> {

        private final TransitRouter transitRouter;

        private final Scenario scenario;

        private final RoutingModule transitWalkRouter;

        @Inject
        TransitRoutingModuleProvider(TransitRouter transitRouter, Scenario scenario, @Named(TransportMode.transit_walk) RoutingModule transitWalkRouter) {
            this.transitRouter = transitRouter;
            this.scenario = scenario;
            this.transitWalkRouter = transitWalkRouter;
        }

        @Override
        public RoutingModule get() {
            return new TransitRouterWrapper(transitRouter,
                        scenario.getTransitSchedule(),
                        scenario.getNetwork(),
                        transitWalkRouter);
        }
    }

    public static class NetworkRoutingModuleProvider implements Provider<RoutingModule> {

        @Inject
        Map<String, TravelTime> travelTimes;

        @Inject
        Map<String, TravelDisutilityFactory> travelDisutilityFactories;

        @Inject
        SingleModeNetworksCache singleModeNetworksCache;

        @Inject
        PlanCalcScoreConfigGroup planCalcScoreConfigGroup;

        @Inject
        Network network;

        @Inject
        PopulationFactory populationFactory;

        @Inject
        LeastCostPathCalculatorFactory leastCostPathCalculatorFactory;

        public NetworkRoutingModuleProvider(String mode) {
            this.mode = mode;
        }

        private String mode;

        @Override
        public RoutingModule get() {
            Network filteredNetwork = null;

            // Ensure this is not performed concurrently by multiple threads!
            synchronized (this.singleModeNetworksCache.getSingleModeNetworksCache()) {
                filteredNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(mode);
                if (filteredNetwork == null) {
                    TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
                    Set<String> modes = new HashSet<>();
                    modes.add(mode);
                    filteredNetwork = NetworkUtils.createNetwork();
                    filter.filter(filteredNetwork, modes);
                    this.singleModeNetworksCache.getSingleModeNetworksCache().put(mode, filteredNetwork);
                }
            }

            TravelDisutilityFactory travelDisutilityFactory = this.travelDisutilityFactories.get(mode);
            if (travelDisutilityFactory == null) {
                throw new RuntimeException("No TravelDisutilityFactory bound for mode "+mode+".");
            }
            TravelTime travelTime = travelTimes.get(mode);
            if (travelTime == null) {
                throw new RuntimeException("No TravelTime bound for mode "+mode+".");
            }
            LeastCostPathCalculator routeAlgo =
                    leastCostPathCalculatorFactory.createPathCalculator(
                            filteredNetwork,
                            travelDisutilityFactory.createTravelDisutility(travelTime, planCalcScoreConfigGroup),
                            travelTime);

            return DefaultRoutingModules.createNetworkRouter(mode, populationFactory,
                    filteredNetwork, routeAlgo);
        }
    }

    private static class PseudoTransitRoutingModuleProvider implements Provider<RoutingModule> {

        private final PlansCalcRouteConfigGroup.ModeRoutingParams params;

        public PseudoTransitRoutingModuleProvider(PlansCalcRouteConfigGroup.ModeRoutingParams params) {
            this.params = params;
        }

        @Inject
        private Network network;

        @Inject
        private PopulationFactory populationFactory;

        @Inject
        private LeastCostPathCalculatorFactory leastCostPathCalculatorFactory;

        @Override
        public RoutingModule get() {
            FreespeedTravelTimeAndDisutility ptTimeCostCalc =
                    new FreespeedTravelTimeAndDisutility(-1.0, 0.0, 0.0);
            LeastCostPathCalculator routeAlgoPtFreeFlow =
                    leastCostPathCalculatorFactory.createPathCalculator(
                            network,
                            ptTimeCostCalc,
                            ptTimeCostCalc);
            return DefaultRoutingModules.createPseudoTransitRouter(params.getMode(), populationFactory,
                    network, routeAlgoPtFreeFlow, params);
        }
    }

    private static class TeleportationRoutingModuleProvider implements Provider<RoutingModule> {

        private final PlansCalcRouteConfigGroup.ModeRoutingParams params;

        public TeleportationRoutingModuleProvider(PlansCalcRouteConfigGroup.ModeRoutingParams params) {
            this.params = params;
        }

        @Inject
        private PopulationFactory populationFactory;

        @Override
        public RoutingModule get() {
            return DefaultRoutingModules.createTeleportationRouter(params.getMode(), populationFactory, params);
        }
    }

}
