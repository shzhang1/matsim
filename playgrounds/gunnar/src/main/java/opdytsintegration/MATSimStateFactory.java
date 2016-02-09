package opdytsintegration;

import org.matsim.api.core.v01.population.Population;

import floetteroed.opdyts.DecisionVariable;
import floetteroed.utilities.math.Vector;

/**
 * A factory for MATSim simulation states.
 * 
 * @author Gunnar Flötteröd
 *
 * @see MATSimState
 * @see DecisionVariable
 */
public interface MATSimStateFactory<U extends DecisionVariable> {

	/**
	 * Creates a new object representation of the current MATSim simulation
	 * state.
	 * 
	 * @see MATSimState
	 * 
	 * @param population
	 *            the current MATSim population
	 * @param stateVector
	 *            a vector representation of the state to be created
	 * @param decisionVariable
	 *            the decision variable that has led to the state to be created
	 * @return the current MATSim simulation state
	 */
	public MATSimState newState(Population population, Vector stateVector,
			U decisionVariable);

}
