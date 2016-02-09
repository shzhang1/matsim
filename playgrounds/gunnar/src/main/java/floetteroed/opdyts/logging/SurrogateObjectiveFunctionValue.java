package floetteroed.opdyts.logging;

import floetteroed.opdyts.DecisionVariable;
import floetteroed.opdyts.trajectorysampling.SamplingStage;
import floetteroed.utilities.statisticslogging.Statistic;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
public class SurrogateObjectiveFunctionValue<U extends DecisionVariable>
		implements Statistic<SamplingStage<U>> {

	public static final String LABEL = "Surrogate Objective Function Value";

	@Override
	public String label() {
		return LABEL;
	}

	@Override
	public String value(final SamplingStage<U> samplingStage) {
		return Double.toString(samplingStage
				.getSurrogateObjectiveFunctionValue());
	}

}
