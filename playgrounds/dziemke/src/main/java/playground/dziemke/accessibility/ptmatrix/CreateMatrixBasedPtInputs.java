package playground.dziemke.accessibility.ptmatrix;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.gis.GridUtils;
import org.matsim.contrib.matrixbasedptrouter.MatrixBasedPtRouterConfigGroup;
import org.matsim.contrib.matrixbasedptrouter.utils.BoundingBox;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import playground.dziemke.utils.LogToOutputSaver;

/**
 * @author dziemke
 */
public class CreateMatrixBasedPtInputs {
	private static final Logger log = Logger.getLogger(CreateMatrixBasedPtInputs.class);

	public static void main(String[] args) {
		// number of arguments needs to be 0 or 8
		if (args.length != 0 && args.length != 8) {
			throw new RuntimeException("Number of arguments is wrong.");
		}
		
		
		// ... for local use
		// Input and Output
		String networkFile = "../../../../Workspace/data/accessibility/nmb/scenario/NMBM_PT_V1.xml.gz";
//		String networkFile = "../../../../Workspace/shared-svn/projects/bvg_3_bln_inputdata/rev554B-bvg00-0.1sample/network/network.final.xml.gz";
//		String networkFile = "../../../../Workspace/runs-svn/nmbm_minibuses/nmbm/output/jtlu14i/jtlu14i.output_network.xml.gz";
//		String networkFile = "../../../../Workspace/runs-svn/nmbm_minibuses/nmbm/output/jtlu14d/jtlu14d.output_network.xml.gz";
//		String networkFile = "../../../../Workspace/shared-svn/projects/accessibility_berlin/network/2015-05-26/network.xml";
		
		String transitScheduleFile = "../../../../Workspace/data/accessibility/nmb/scenario/Transitschedule_PT_V1_WithVehicles.xml.gz";
//		String transitScheduleFile = "../../../../Workspace/shared-svn/projects/bvg_3_bln_inputdata/rev554B-bvg00-0.1sample/network/transitSchedule.xml.gz";
//		String transitScheduleFile = "../../../../Workspace/runs-svn/nmbm_minibuses/nmbm/output/jtlu14i/ITERS/it.300/jtlu14i.300.transitScheduleScored.xml.gz";
//		String transitScheduleFile = "../../../../Workspace/runs-svn/nmbm_minibuses/nmbm/output/jtlu14d/ITERS/it.300/jtlu14d.300.transitScheduleScored.xml.gz";
//		String transitScheduleFile = "../../../../Workspace/shared-svn/projects/accessibility_berlin/gtfs/2015-07-03/transitschedule.xml";
		
		String outputRoot = "../../../../Workspace/data/accessibility/nmb/matrix/06/";
//		String outputRoot = "../../../../Workspace/shared-svn/projects/accessibility_berlin/travel_matrix/2016-01-05/";
		
//		String outputFileRoot = "../../data/accessibility/be_002/";
		LogToOutputSaver.setOutputDirectory(outputRoot);
		
		// Parameters
		Boolean measuringPointsAsPTStops = false;
		Double cellSize = 1000.; // only relevant if "meauringPointsAsPTStops = true"
		Double departureTime = 8. * 60 * 60;
		Integer numberOfThreads = 1;
//		Integer numberOfThreads = 20;
//		String bounds = "4550000,5790000,4630000,5850000";
		String bounds = "network"; // set bounds = "network" to compute bounds automatically based on the spatial extent of the network
		
		
		// ... for server use
		if (args.length == 8) {
			networkFile = args[0];
			transitScheduleFile = args[1];
			outputRoot = args[2];
			measuringPointsAsPTStops = Boolean.parseBoolean(args[3]);
			cellSize = Double.parseDouble(args[4]);
			departureTime = Double.parseDouble(args[5]);
			numberOfThreads = Integer.parseInt(args[6]);
			bounds = args[7];
		}
		
		
		// Logging
		log.info("networkFile = " + networkFile);
		log.info("transitScheduleFile = " + transitScheduleFile);
		log.info("outputFileRoot = " + outputRoot);
		log.info("measuringPointsAsPTStops = " + measuringPointsAsPTStops);
		log.info("cellSize = " + cellSize);
		log.info("departureTime = "	+ departureTime);
		log.info("numberOfThreads = " + numberOfThreads);
		log.info("bounds = " + bounds);


		// Infrastructure
		Config config = ConfigUtils.createConfig(new AccessibilityConfigGroup(), new MatrixBasedPtRouterConfigGroup());
		config.network().setInputFile(networkFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.getConfig().transit().setUseTransit(true);


		// Read in public transport schedule
		TransitScheduleReader reader = new TransitScheduleReader(scenario);
		reader.readFile(transitScheduleFile);

		
		// A LinkedHashMap is a hash table and linked list implementation of the Map interface, with predictable iteration order.
		// It differs from HashMap in that it maintains a doubly-linked list running through all of its entries. This linked list
		// defines the iteration ordering, which is normally the order in which keys were inserted into the map insertion order.
		Map<Id<Coord>, Coord> ptMatrixLocationsMap = new LinkedHashMap<Id<Coord>, Coord>();
		
		if (measuringPointsAsPTStops == true) { // i.e. use regular, user-defined locations instead of stops from schedule
			
			BoundingBox boundingBox;
			if (bounds == "network") {
				boundingBox = BoundingBox.createBoundingBox(scenario.getNetwork());
			} else {
				String boundsArray[] = bounds.split(",");				
				boundingBox = BoundingBox.createBoundingBox(Double.parseDouble(boundsArray[0]),
						Double.parseDouble(boundsArray[1]), Double.parseDouble(boundsArray[2]), Double.parseDouble(boundsArray[3]));
			}
			
			ActivityFacilitiesImpl measuringPoints;
			measuringPoints = GridUtils.createGridLayerByGridSizeByBoundingBoxV2(
					boundingBox.getXMin(), boundingBox.getYMin(), boundingBox.getXMax(), boundingBox.getYMax(), cellSize);

			// Conversion from ActivityFacilities=measuringPoints to coordinates
			for (ActivityFacility activityFacility : measuringPoints.getFacilities().values()) {
				Id<Coord> id = Id.create(activityFacility.getId(), Coord.class);
				Coord coord = activityFacility.getCoord();
				ptMatrixLocationsMap.put(id, coord);
			}

			// Create pt stops file
			MatrixBasedPtInputUtils.createStopsFile(ptMatrixLocationsMap, outputRoot + "ptStops.csv", ",");
			
		} else { // i.e. measuringPointsAsPTStops == false, i.e. use actual pt stops from schedule
			
			// Conversion from TransitFacilities to coordinates
			for (TransitStopFacility transitStopFacility : scenario.getTransitSchedule().getFacilities().values()) {		
				Id<Coord> id = Id.create(transitStopFacility.getId(), Coord.class);
				Coord coord = transitStopFacility.getCoord();
				ptMatrixLocationsMap.put(id, coord);
			}

			// Create pt stops file
			MatrixBasedPtInputUtils.createStopsFile(ptMatrixLocationsMap, outputRoot + "ptStops.csv", ",");
		}


		// Split up map of locations into (approximately) equal parts
		int numberOfMeasuringPoints = ptMatrixLocationsMap.size();
		log.info("numberOfMeasuringPoints = " + numberOfMeasuringPoints);
		int arrayNumber = 0;
		int locationsAdded = 0;

		Map<Integer, Map<Id<Coord>, Coord>> mapOfLocationFacilitiesMaps = new HashMap<Integer, Map<Id<Coord>, Coord>>();
		for (int i = 0; i < numberOfThreads; i++) {
			Map<Id<Coord>, Coord> locationFacilitiesPartialMap = new LinkedHashMap<Id<Coord>, Coord>();
			mapOfLocationFacilitiesMaps.put(i, locationFacilitiesPartialMap);
		}


		// Now iterate over all locations and copy them into separate maps. Once the number of copied locations reaches a certain
		// share of all measuringPoints, they are copied to the next map. This separation is necessary to be able to do the travel
		// matrix computation multi-threaded.
		for (Id<Coord> locationFacilityId : ptMatrixLocationsMap.keySet()) {
			mapOfLocationFacilitiesMaps.get(arrayNumber).put(locationFacilityId, ptMatrixLocationsMap.get(locationFacilityId));
			locationsAdded++;
			if (locationsAdded == (numberOfMeasuringPoints / numberOfThreads) + 1) {
				arrayNumber++;
				locationsAdded = 0;
			}
		}


		for (int currentThreadNumber = 0; currentThreadNumber < numberOfThreads; currentThreadNumber++) {
			new ThreadedMatrixCreator(scenario, mapOfLocationFacilitiesMaps.get(currentThreadNumber), 
					ptMatrixLocationsMap, departureTime, outputRoot, " ", currentThreadNumber);
		}
	}
}