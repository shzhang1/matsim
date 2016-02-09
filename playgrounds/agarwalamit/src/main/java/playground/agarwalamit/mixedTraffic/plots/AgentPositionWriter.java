/* *********************************************************************** *
 * project: org.matsim.*
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
 * *********************************************************************** */
package playground.agarwalamit.mixedTraffic.plots;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.QSimConfigGroup.SnapshotStyle;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.tabularFileParser.TabularFileHandler;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;
import org.matsim.run.Events2Snapshot;
import org.matsim.vis.snapshotwriters.TransimsSnapshotWriter.Labels;

import playground.agarwalamit.utils.LoadMyScenarios;

/**
 * Create Transims snapshot file from events and 
 * read it again to write data in simpler form for space time plotting.
 * <b>At the moment, this should be only used for race track experiment. The methodology can work for other scenarios.
 * But, some paraments many need to change and yet to be tested. 
 * @author amit 
 */

public class AgentPositionWriter {

	private final static double SANPSOHOT_PERIOD = 1;
	private final double trackLength = 3000;
	private final double maxSpeed = 60/3.6;

	public static void main(String[] args) 
	{
		final String dir = "../../../../repos/shared-svn/projects/mixedTraffic/triangularNetwork/run313/singleModes/holes/car/";
		final String networkFile = dir+"/network.xml";
		final String configFile = dir+"/config.xml";
		final String prefix = "events[40]";
		final String eventsFile = dir+"/events/"+prefix+".xml";
		
		Scenario sc = LoadMyScenarios.loadScenarioFromNetworkAndConfig(networkFile, configFile);

		//sc.getConfig().qsim().setSnapshotStyle(SnapshotStyle.withHoles);// not supported
		sc.getConfig().qsim().setSnapshotStyle(SnapshotStyle.queue);
		sc.getConfig().qsim().setSnapshotPeriod(SANPSOHOT_PERIOD);
		sc.getConfig().qsim().setLinkWidthForVis((float)0);
		((NetworkImpl)sc.getNetwork()).setEffectiveLaneWidth(0.);
		
		sc.getConfig().controler().setSnapshotFormat(Arrays.asList("transims"));
		
		AgentPositionWriter apw = new AgentPositionWriter(dir+"rDataPersonPosition_"+prefix+".txt", sc, eventsFile); 
		apw.run();
	}


	/**
	 * Constructor opens writer, creates transims file and stores person 2 mode from events file.
	 */
	public AgentPositionWriter(String outFile, Scenario scenario, String eventsFile)
	{
		writer = IOUtils.getBufferedWriter(outFile);
		try {
			writer.write("personId \t legMode \t positionOnLink \t time \t speed  \t cycleNumber \n");
		} catch (Exception e) {
			throw new RuntimeException("Data is not written to the file. Reason :"+e);
		}
		createTransimSnapshotFile(scenario, eventsFile);
		storePerson2Modes(eventsFile);
	}

	private void createTransimSnapshotFile(Scenario sc, String eventsFile)
	{
		Events2Snapshot e2s = new Events2Snapshot();
		File file = new File(eventsFile);
		e2s.run(file, sc.getConfig(), sc.getNetwork());
		this.transimFile = file.getParent() + "/" +"T.veh";
	}

	private void storePerson2Modes(String eventsFile)
	{
		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler(new PersonDepartureEventHandler() {
			@Override
			public void reset(int iteration) {
				person2mode.clear();
			}
			@Override
			public void handleEvent(PersonDepartureEvent event) {
				person2mode.put(event.getPersonId(), event.getLegMode());
			}
		});
		new MatsimEventsReader(manager).readFile(eventsFile);
	}

	private String transimFile;
	private BufferedWriter writer ;

	private Map<Id<Person>,String> person2mode = new HashMap<>();
	private Map<Id<Person>,Double> prevEasting = new HashMap<>();
	private Map<Id<Person>,Double> prevNorthing = new HashMap<>();
	private Map<Id<Person>,Double> prevPosition = new HashMap<>();
	private Map<Id<Person>,Integer> prevCycle = new HashMap<>();

	public void run()
	{
		TabularFileParserConfig config = new TabularFileParserConfig() ;
		config.setFileName( this.transimFile );
		config.setDelimiterTags( new String []{"\t"} );
		// ---
		TabularFileHandler handler = new TabularFileHandler(){
			List<String> labels = new ArrayList<>() ;
			@Override
			public void startRow(String[] row) {
				List<String> strs = Arrays.asList( row ) ;

				if ( row[0].substring(0, 1).matches("[A-Za-z]") ) {
					for ( String str : strs ) {
						labels.add( str ) ;
					}
				} else {
					double time = Double.parseDouble( strs.get( labels.indexOf( Labels.TIME.toString() ) ) ) ;
					double easting = Double.parseDouble( strs.get( labels.indexOf( Labels.EASTING.toString() ) ) ) ;
					double northing = Double.parseDouble( strs.get( labels.indexOf( Labels.NORTHING.toString() ) ) ) ;
//					double velocity = Double.parseDouble( strs.get( labels.indexOf( Labels.VELOCITY.toString() ) ) ) ;
					Id<Person> agentId = Id.createPersonId( strs.get( labels.indexOf( Labels.VEHICLE.toString() ) ) ) ;
					try {
						if( prevEasting.containsKey(agentId) ){
							double currentDist = Math.sqrt( (easting - prevEasting.get(agentId))*(easting - prevEasting.get(agentId)) 
									+ (northing- prevNorthing.get(agentId))*(northing- prevNorthing.get(agentId)) );
							
							double velocity = currentDist / (SANPSOHOT_PERIOD); // denominator should be equal to snapshot period.
							if(velocity > maxSpeed ) { // person arriving (vehicle leaving traffic) are falling in this category
								return;
							}else if (velocity < 0.0) throw new RuntimeException("Speed can not be negative. Aborting ...");
						
							double position = prevPosition.get(agentId) + currentDist ;
							if(position > trackLength) {
								position = position-trackLength;
								prevCycle.put(agentId, prevCycle.get(agentId)+1);
							}
							
							writer.write(agentId+"\t"+person2mode.get(agentId)+"\t"+position+"\t"+time+"\t"+velocity+"\t"+prevCycle.get(agentId)+"\n");
							prevPosition.put(agentId, position);
						}  else {
							writer.write(agentId+"\t"+person2mode.get(agentId)+"\t"+0.+"\t"+time+"\t"+maxSpeed+"\t"+"1"+"\n");
							prevPosition.put(agentId, 0.);
							prevCycle.put(agentId, 1);
						}
						prevEasting.put(agentId, easting);
						prevNorthing.put(agentId, northing);
					} catch (Exception e) {
						throw new RuntimeException("Data is not written to the file. Reason :"+e);
					}
				}
			}
		} ;
		TabularFileParser reader = new TabularFileParser() ;
		reader.parse(config, handler);
		try{
			writer.close();
		} catch (Exception e) {
			throw new RuntimeException("Data is not written to the file. Reason :"+e);
		}
	}
}