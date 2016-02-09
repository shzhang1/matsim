/* *********************************************************************** *
 * project: org.matsim.*
 * ExampleWithinDayController.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.withinday.controller;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.functions.OnlyTravelTimeDependentScoringFunctionFactory;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.identifiers.ActivityEndIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.InitialIdentifierImplFactory;
import org.matsim.withinday.replanning.identifiers.LeaveLinkIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.filter.ProbabilityFilterFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityAgentSelector;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegAgentSelector;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.InitialIdentifier;
import org.matsim.withinday.replanning.identifiers.interfaces.InitialIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.tools.ActivityReplanningMap;
import org.matsim.withinday.replanning.identifiers.tools.LinkReplanningMap;
import org.matsim.withinday.replanning.replanners.CurrentLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.InitialReplannerFactory;
import org.matsim.withinday.replanning.replanners.NextLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayInitialReplannerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * This class should give an example what is needed to run
 * simulations with WithinDayReplanning.
 *
 * The path to a config file is needed as argument to run the
 * simulation.
 * 
 * It should be possible to run this class with
 * "src/test/resources/test/scenarios/berlin/config_withinday.xml"
 * as argument.
 *
 * @author Christoph Dobler
 */
@Singleton
public class ExampleWithinDayController implements StartupListener {

	/*
	 * Define the Probability that an Agent uses the
	 * Replanning Strategy. It is possible to assign
	 * multiple Strategies to the Agents.
	 */
	protected double pInitialReplanning = 0.0;
	protected double pDuringActivityReplanning = 1.0;
	protected double pDuringLegReplanning = 0.10;
	
	protected InitialIdentifierFactory initialIdentifierFactory;
	protected DuringActivityIdentifierFactory duringActivityIdentifierFactory;
	protected DuringLegIdentifierFactory duringLegIdentifierFactory;
	protected InitialIdentifier initialIdentifier;
	protected DuringActivityAgentSelector duringActivityIdentifier;
	protected DuringLegAgentSelector duringLegIdentifier;
	protected WithinDayInitialReplannerFactory initialReplannerFactory;
	protected WithinDayDuringActivityReplannerFactory duringActivityReplannerFactory;
	protected WithinDayDuringLegReplannerFactory duringLegReplannerFactory;
	protected ProbabilityFilterFactory initialProbabilityFilterFactory;
	protected ProbabilityFilterFactory duringActivityProbabilityFilterFactory;
	protected ProbabilityFilterFactory duringLegProbabilityFilterFactory;
	
	protected Scenario scenario;
	protected WithinDayControlerListener withinDayControlerListener;
	private Provider<TripRouter> tripRouterProvider;
	private MobsimDataProvider mobsimDataProvider;
	private WithinDayEngine withinDayEngine;
	private ActivityReplanningMap activityReplanningMap;
	private LinkReplanningMap linkReplanningMap;


	/*
	 * ===================================================================
	 * main
	 * ===================================================================
	 */
	public static void main(final String[] args) {
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
			System.out.println("Usage: Controler config-file [dtd-file]");
			System.out.println();
		} else {
			final Controler controler = new Controler(args);
			configure(controler);
			controler.run();
		}
		System.exit(0);
	}

	static void configure(Controler controler) {
		controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new WithinDayModule());
                addControlerListenerBinding().to(ExampleWithinDayController.class);
                // Use a Scoring Function, that only scores the travel times!
                bind(ScoringFunctionFactory.class).toInstance(new OnlyTravelTimeDependentScoringFunctionFactory());
                addTravelDisutilityFactoryBinding(TransportMode.car).toInstance(new OnlyTimeDependentTravelDisutilityFactory());
            }
        });
	}

	@Inject
	ExampleWithinDayController(Scenario scenario, WithinDayControlerListener withinDayControlerListener, Provider<TripRouter> tripRouterProvider, MobsimDataProvider mobsimDataProvider, WithinDayEngine withinDayEngine, ActivityReplanningMap activityReplanningMap, LinkReplanningMap linkReplanningMap) {
		this.scenario = scenario;
		this.withinDayControlerListener = withinDayControlerListener;
		this.tripRouterProvider = tripRouterProvider;
		this.mobsimDataProvider = mobsimDataProvider;
		this.withinDayEngine = withinDayEngine;
		this.activityReplanningMap = activityReplanningMap;
		this.linkReplanningMap = linkReplanningMap;
	}

	@Override
	public void notifyStartup(StartupEvent event) {
		this.initReplanners();
	}
	
	private void initReplanners() {
		this.initialIdentifierFactory = new InitialIdentifierImplFactory(this.mobsimDataProvider);
		this.initialProbabilityFilterFactory = new ProbabilityFilterFactory(this.pInitialReplanning);
		this.initialIdentifierFactory.addAgentFilterFactory(this.initialProbabilityFilterFactory);
		this.initialIdentifier = initialIdentifierFactory.createIdentifier();
		this.initialReplannerFactory = new InitialReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
		this.initialReplannerFactory.addIdentifier(this.initialIdentifier);
		this.withinDayEngine.addIntialReplannerFactory(this.initialReplannerFactory);

		this.duringActivityIdentifierFactory = new ActivityEndIdentifierFactory(this.activityReplanningMap);
		this.duringActivityProbabilityFilterFactory = new ProbabilityFilterFactory(this.pDuringActivityReplanning);
		this.duringActivityIdentifierFactory.addAgentFilterFactory(this.duringActivityProbabilityFilterFactory);
		this.duringActivityIdentifier = duringActivityIdentifierFactory.createIdentifier();
		this.duringActivityReplannerFactory = new NextLegReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
		this.duringActivityReplannerFactory.addIdentifier(this.duringActivityIdentifier);
		this.withinDayEngine.addDuringActivityReplannerFactory(this.duringActivityReplannerFactory);

		this.duringLegIdentifierFactory = new LeaveLinkIdentifierFactory(this.linkReplanningMap, this.mobsimDataProvider);
		this.duringLegProbabilityFilterFactory = new ProbabilityFilterFactory(this.pDuringLegReplanning);
		this.duringLegIdentifierFactory.addAgentFilterFactory(this.duringLegProbabilityFilterFactory);
		this.duringLegIdentifier = this.duringLegIdentifierFactory.createIdentifier();
		this.duringLegReplannerFactory = new CurrentLegReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
		this.duringLegReplannerFactory.addIdentifier(this.duringLegIdentifier);
		this.withinDayEngine.addDuringLegReplannerFactory(this.duringLegReplannerFactory);
	}

}