package design.strategymethod;

import design.strategymethod.Workflow.Stage;

import fj.data.List;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.ToDoubleFunction;

public interface BacklogProjectionCalculator {

	interface Plan {
		double integrateForecastedInput(Instant from, Instant to);

		double integrateThroughput(Stage stage, Instant from, Instant to);
	}

	interface SlaDispersionStrategy {
		float[] calcProcessingProportions(Duration[] nextSlasDistances);
	}

	interface Behaviour {
		Stage[] getStages();

		Optional<Stage> sourceOf(Stage stage);

		double integrateFirstStageThroughput(Plan plan, Instant startingPoint, Instant endingPoint);

		SlaDispersionStrategy getSlaDispersionStrategy();
	}

	static List<double[]> calculate(
			final Instant startingDate,
			final int[] startingBacklog,
			final SortedSet<Instant> inflectionPoints,
			final Plan plan,
			final Behaviour behaviour
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
					final var currentStepEndingPoint = nextStepsStartingPoints.head();
					final var currentStepEndingBacklog = Arrays.stream(behaviour.getStages())
							.mapToDouble(buildFinalBacklogCalcFunc(
									currentStepStartingPoint,
									currentStepEndingPoint,
									alreadyCalculatedSteps.head()
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

			private ToDoubleFunction<Stage> buildFinalBacklogCalcFunc(
					final Instant startingPoint,
					final Instant endingPoint,
					final double[] startingBacklog
			) {
				return stage -> {
					final double input;
					final double output;
					final var source = behaviour.sourceOf(stage);
					if (source.isEmpty()) {
						input = plan.integrateForecastedInput(startingPoint, endingPoint);
						output = behaviour.integrateFirstStageThroughput(plan, startingPoint, endingPoint);
					} else {
						input = plan.integrateThroughput(source.get(), startingPoint, endingPoint);
						output = plan.integrateThroughput(stage, startingPoint, endingPoint);
					}
					final var current = startingBacklog[stage.ordinal()];
					return Math.max(0, current + input - output);
				};
			}
		}

		final var initialBacklog = Arrays.stream(startingBacklog).mapToDouble(Math::floor).toArray();
		return new LMC().loop(startingDate, List.iterableList(inflectionPoints), List.cons(initialBacklog, List.nil()));
	}
}
