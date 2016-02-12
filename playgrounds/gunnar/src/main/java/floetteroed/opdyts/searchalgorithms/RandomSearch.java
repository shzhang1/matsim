/*
 * Opdyts - Optimization of dynamic traffic simulations
 *
 * Copyright 2015 Gunnar Flötteröd
 * 
 *
 * This file is part of Opdyts.
 *
 * Opdyts is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Opdyts is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Opdyts.  If not, see <http://www.gnu.org/licenses/>.
 *
 * contact: gunnar.floetteroed@abe.kth.se
 *
 */
package floetteroed.opdyts.searchalgorithms;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;

import floetteroed.opdyts.DecisionVariable;
import floetteroed.opdyts.DecisionVariableRandomizer;
import floetteroed.opdyts.ObjectiveFunction;
import floetteroed.opdyts.SimulatorState;
import floetteroed.opdyts.convergencecriteria.ConvergenceCriterion;
import floetteroed.opdyts.convergencecriteria.ConvergenceCriterionResult;
import floetteroed.opdyts.trajectorysampling.ParallelTrajectorySampler;
import floetteroed.opdyts.trajectorysampling.SamplingStage;
import floetteroed.opdyts.trajectorysampling.SingleTrajectorySampler;
import floetteroed.utilities.statisticslogging.Statistic;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
public class RandomSearch<U extends DecisionVariable> {

	// -------------------- CONSTANTS --------------------

	public static final String TIMESTAMP = "Timestamp";

	public static final String RANDOM_SEARCH_ITERATION = "Random Search Iteration";

	private final Simulator<U> simulator;

	private final DecisionVariableRandomizer<U> randomizer;

	private final U initialDecisionVariable;

	private final ConvergenceCriterion convergenceCriterion;

	private final int maxIterations;

	private final int maxTransitions;

	private final int populationSize;

	private final Random rnd;

	private final boolean interpolate;

	private final ObjectiveFunction objectBasedObjectiveFunction;

	private final int maxMemoryLength;

	private final boolean includeCurrentBest;

	// -------------------- MEMBERS --------------------

	private List<DecisionVariable> bestDecisionVariables = new ArrayList<DecisionVariable>();

	private List<Double> bestObjectiveFunctionValues = new ArrayList<Double>();

	private List<Integer> transitionEvaluations = new ArrayList<Integer>();

	private List<Double> interpolatedObjectiveFunctionValueWeights = new ArrayList<Double>();

	private List<Double> equilibriumGapWeights = new ArrayList<Double>();

	private List<Double> uniformityGapWeights = new ArrayList<Double>();

	private List<Double> offsets = new ArrayList<Double>();

	private String logFileName = null;

	// -------------------- CONSTRUCTION --------------------

	public RandomSearch(final Simulator<U> simulator,
			final DecisionVariableRandomizer<U> randomizer,
			final U initialDecisionVariable,
			final ConvergenceCriterion convergenceCriterion,
			final int maxIterations, final int maxTransitions,
			final int populationSize, final Random rnd,
			final boolean interpolate,
			final ObjectiveFunction objectBasedObjectiveFunction,
			final int maxMemoryLength, final boolean includeCurrentBest) {
		this.simulator = simulator;
		this.randomizer = randomizer;
		this.initialDecisionVariable = initialDecisionVariable;
		this.convergenceCriterion = convergenceCriterion;
		this.maxIterations = maxIterations;
		this.maxTransitions = maxTransitions;
		this.populationSize = populationSize;
		this.rnd = rnd;
		this.interpolate = interpolate;
		this.objectBasedObjectiveFunction = objectBasedObjectiveFunction;
		this.maxMemoryLength = maxMemoryLength;
		this.includeCurrentBest = includeCurrentBest;
	}

	// -------------------- SETTERS AND GETTERS --------------------

	public void setLogFileName(final String logFileName) {
		this.logFileName = logFileName;
	}

	// -------------------- IMPLEMENTATION --------------------

	private int transitions = 0;

	public void run() {
		this.run(0.0, 0.0, true);
	}

	public void run(double equilibriumGapWeight, double uniformityGapWeight,
			final boolean adjustWeights) {

		if (this.logFileName != null) {
			final File logFile = new File(this.logFileName);
			if (logFile.exists()) {
				logFile.delete();
			}
		}

		final WeightOptimizer weightOptimizer;
		if (adjustWeights) {
			weightOptimizer = new WeightOptimizer(1.0);
		} else {
			weightOptimizer = null;
		}

		U bestDecisionVariable = this.initialDecisionVariable;
		Double bestObjectiveFunctionValue = null;
		SimulatorState newInitialState = null;

		for (int it = 0; it < this.maxIterations
				&& this.transitions < this.maxTransitions; it++) {

			Logger.getLogger(this.getClass().getName()).info(
					"Iteration " + (it + 1) + " of " + this.maxIterations
							+ ", transitions " + this.transitions + " of "
							+ this.maxTransitions + " ====================");

			this.interpolatedObjectiveFunctionValueWeights.add(1.0);
			this.equilibriumGapWeights.add(equilibriumGapWeight);
			this.uniformityGapWeights.add(uniformityGapWeight);
			this.offsets.add(Double.NaN);

			final Set<U> candidates = new LinkedHashSet<U>();
			if (this.includeCurrentBest) {
				candidates.add(bestDecisionVariable);
			}
			while (candidates.size() < this.populationSize) {
				candidates.addAll(this.randomizer
						.newRandomVariations(bestDecisionVariable));
			}

			int transitionsPerIteration = 0;
			U newBestDecisionVariable;
			double newBestObjectiveFunctionValue;
			if (this.interpolate) {

				final ParallelTrajectorySampler<U> sampler;
				sampler = new ParallelTrajectorySampler<>(candidates,
						this.objectBasedObjectiveFunction,
						this.convergenceCriterion, this.rnd,
						equilibriumGapWeight, uniformityGapWeight, (it > 0));
				sampler.setMaxMemoryLength(this.maxMemoryLength);

				if (this.logFileName != null) {
					sampler.addStatistic(this.logFileName,
							new Statistic<SamplingStage<U>>() {
								@Override
								public String label() {
									return TIMESTAMP;
								}

								@Override
								public String value(final SamplingStage<U> data) {
									return (new SimpleDateFormat(
											"yyyy-MM-dd HH:mm:ss"))
											.format(new Date(System
													.currentTimeMillis()));
								}
							});
					final int currentIt = it; // inner class requires final
					sampler.addStatistic(this.logFileName,
							new Statistic<SamplingStage<U>>() {
								@Override
								public String label() {
									return RANDOM_SEARCH_ITERATION;
								}

								@Override
								public String value(final SamplingStage<U> data) {
									return Integer.toString(currentIt);
								}
							});
					final Double currentBestObjectiveFunctionValue = bestObjectiveFunctionValue;
					sampler.addStatistic(this.logFileName,
							new Statistic<SamplingStage<U>>() {
								@Override
								public String label() {
									return "Best Overall Solution";
								}

								@Override
								public String value(final SamplingStage<U> data) {
									if (currentBestObjectiveFunctionValue == null) {
										return "";
									} else {
										return Double
												.toString(currentBestObjectiveFunctionValue);
									}
								}
							});
					sampler.setStandardLogFileName(this.logFileName);
				}

				newInitialState = this.simulator.run(sampler, newInitialState);
				newBestDecisionVariable = sampler
						.getDecisionVariable2convergenceResultView().keySet()
						.iterator().next();
				newBestObjectiveFunctionValue = sampler
						.getDecisionVariable2convergenceResultView().get(
								newBestDecisionVariable).finalObjectiveFunctionValue;
				transitionsPerIteration = sampler.getTotalTransitionCnt();

				if (weightOptimizer != null) {
					if ((bestObjectiveFunctionValue == null)
							|| (newBestObjectiveFunctionValue < bestObjectiveFunctionValue)) {
						final ConvergenceCriterionResult convergenceResult = sampler
								.getDecisionVariable2convergenceResultView()
								.get(newBestDecisionVariable);
						final double[] newWeights = weightOptimizer
								.updateWeights(
										convergenceResult.finalEquilibiriumGap,
										convergenceResult.finalUniformityGap,
										convergenceResult.finalObjectiveFunctionValueStddev);
						equilibriumGapWeight = newWeights[0];
						uniformityGapWeight = newWeights[1];
					}
				}

			} else {

				if (bestObjectiveFunctionValue != null) {
					try {
						final PrintWriter logWriter = new PrintWriter(
								new BufferedWriter(new FileWriter(
										this.logFileName, true)));
						logWriter.print((new SimpleDateFormat(
								"yyyy-MM-dd HH:mm:ss")).format(new Date(System
								.currentTimeMillis()))
								+ "\t");
						logWriter.print(it + "\t");
						logWriter.print(this.transitions + "\t");
						logWriter.print(bestObjectiveFunctionValue + "\t");
						logWriter.println(bestDecisionVariable);
						logWriter.flush();
						logWriter.close();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}

				final SimulatorState thisRoundsInitialState = newInitialState;

				newBestDecisionVariable = null;
				newBestObjectiveFunctionValue = Double.POSITIVE_INFINITY;

				for (U candidate : candidates) {
					final SingleTrajectorySampler<U> singleSampler;
					singleSampler = new SingleTrajectorySampler<>(candidate,
							this.objectBasedObjectiveFunction,
							this.convergenceCriterion);
					final SimulatorState candidateInitialState = this.simulator
							.run(singleSampler, thisRoundsInitialState);
					final double candidateObjectiveFunctionValue = singleSampler
							.getDecisionVariable2convergenceResultView().get(
									candidate).finalObjectiveFunctionValue;
					if (candidateObjectiveFunctionValue < newBestObjectiveFunctionValue) {
						newBestDecisionVariable = candidate;
						newBestObjectiveFunctionValue = candidateObjectiveFunctionValue;
						newInitialState = candidateInitialState;
					}
					transitionsPerIteration += singleSampler
							.getTotalTransitionCnt();
				}
			}

			if (bestObjectiveFunctionValue == null
					|| newBestObjectiveFunctionValue < bestObjectiveFunctionValue) {
				bestDecisionVariable = newBestDecisionVariable;
				bestObjectiveFunctionValue = newBestObjectiveFunctionValue;
			}

			this.bestDecisionVariables.add(bestDecisionVariable);
			this.bestObjectiveFunctionValues.add(bestObjectiveFunctionValue);
			this.transitionEvaluations.add(transitionsPerIteration);
			this.transitions += transitionsPerIteration;
		}
	}

	// -------------------- RESULT ACCESS --------------------

	public List<DecisionVariable> getBestDecisionVariablesView() {
		return Collections.unmodifiableList(this.bestDecisionVariables);
	}

	public List<Double> getBestObjectiveFunctionValuesView() {
		return Collections.unmodifiableList(this.bestObjectiveFunctionValues);
	}

	public List<Integer> getTransitionEvalautionsView() {
		return Collections.unmodifiableList(this.transitionEvaluations);
	}

	public List<Double> getInterpolatedObjectiveFunctionValueWeightsView() {
		return Collections
				.unmodifiableList(this.interpolatedObjectiveFunctionValueWeights);
	}

	public List<Double> getEquilibriumGapWeightsView() {
		return Collections.unmodifiableList(this.equilibriumGapWeights);
	}

	public List<Double> getUniformityWeightsView() {
		return Collections.unmodifiableList(this.uniformityGapWeights);
	}

	public List<Double> getOffsetsView() {
		return Collections.unmodifiableList(this.offsets);
	}
}
