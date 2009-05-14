package playground.jhackney.controler;

import org.apache.log4j.Logger;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.world.algorithms.WorldConnectLocations;

import playground.jhackney.algorithms.InitializeKnowledge;

public class StandardControlerListener implements StartupListener {

	private Controler controler = null;
	private final Logger log = Logger.getLogger(StandardControlerListener.class);
	
	public void notifyStartup(final StartupEvent event) {
		this.controler = event.getControler();

		// Complete the world to make sure that the layers all have relevant mapping rules
//		new WorldConnectLocations().run(Gbl.getWorld());

		this.log.info(" Initializing agent knowledge about geography ...");
		initializeKnowledge();
		this.log.info("... done");

	}
	protected void initializeKnowledge() {
		new InitializeKnowledge(this.controler.getPopulation(), this.controler.getFacilities());
	}
}
