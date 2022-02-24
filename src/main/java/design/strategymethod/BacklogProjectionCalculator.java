package design.strategymethod;

import design.global.Workflow;
import design.global.Workflow.Stage;

import fj.data.List;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.ToDoubleFunction;

public interface BacklogProjectionCalculator {

	interface Forecast {
		double integrateForecastedInput(Instant from, Instant to);
	}

	/** A strategy that parameterizes the behaviour of the {@link #calculate(Instant, int[], SortedSet, Forecast, Object, Behaviour)} method. */
	interface Behaviour<Plan> {
		Workflow getWorkflow();

		Optional<Stage> sourceOf(Stage stage);

		double integrateThroughput(Plan plan, Stage stage, Instant startingPoint, Instant endingPoint);

		float[] calcProcessingProportions(Duration[] nextSlasDistances);
	}

	/** Estimates which will be the backlog of each stage at each `inflectionPoint`
	 * @return the workflow's backlog at each inflection point ordered by the corresponding inflection point.
	 * 		The workflow backlog is represented with an array where each element is the backlog of a {@link Stage} indexed by its ordinal.
	 */
	static <Plan> List<double[]> calculate(
			final Instant startingDate,
			final int[] startingBacklog,
			final SortedSet<Instant> inflectionPoints,
			final Forecast forecast,
			final Plan plan,
			final Behaviour<Plan> behaviour
	) {
		class LMC {
			List<double[]> loop(
					final Instant currentStepStartingPoint,
					final List<Instant> nextStepsStartingPoints,
					final List<double[]> alreadyCalculatedSteps
			) {
				if (nextStepsStartingPoints.isEmpty()) {
					return alreadyCalculatedSteps;
				} else {
					final var currentStepStartingBacklog = alreadyCalculatedSteps.head();
					final var currentStepEndingPoint = nextStepsStartingPoints.head();
					final var currentStepEndingBacklog = Arrays.stream(behaviour.getWorkflow().stages)
							.mapToDouble(buildEndingBacklogCalcFunc(
									currentStepStartingPoint,
									currentStepEndingPoint,
									currentStepStartingBacklog
							))
							.toArray();
					return loop(
							currentStepEndingPoint,
							nextStepsStartingPoints.tail(),
							List.cons(currentStepEndingBacklog,
									alreadyCalculatedSteps
							));
				}
			}

			private ToDoubleFunction<Stage> buildEndingBacklogCalcFunc(
					final Instant startingPoint,
					final Instant endingPoint,
					final double[] startingBacklog
			) {
				return stage -> {
					final double input = behaviour.sourceOf(stage)
							.map(source ->  behaviour.integrateThroughput(plan, source, startingPoint, endingPoint))
							.orElse(forecast.integrateForecastedInput(startingPoint, endingPoint));
					final double output = behaviour.integrateThroughput(plan, stage, startingPoint, endingPoint);
					final var current = startingBacklog[stage.ordinal()];
					return Math.max(0, current + input - output);
				};
			}
		}

		final var initialBacklog = Arrays.stream(startingBacklog).mapToDouble(Math::floor).toArray();
		return new LMC().loop(startingDate, List.iterableList(inflectionPoints), List.cons(initialBacklog, List.nil()));
	}
}
