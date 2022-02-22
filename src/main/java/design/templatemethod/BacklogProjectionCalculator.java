package design.templatemethod;

import design.global.Workflow;
import design.global.Workflow.Stage;

import fj.data.List;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.ToDoubleFunction;

public abstract class BacklogProjectionCalculator {

	protected abstract Workflow getWorkflow();

	protected abstract double calcCombinedThroughput(Plan plan, Stage stage, Instant startingPoint, Instant endingPoint);

	protected abstract float[] calcProcessingProportions(Duration[] nextSlasDistances);

	public interface Forecast {
		double integrateForecastedInput(Instant from, Instant to);
	}

	public interface Plan {
		double integrateThroughput(Stage stage, Instant from, Instant to);
	}

	List<double[]> calculate(
			final Instant startingDate,
			final int[] startingBacklog,
			final SortedSet<Instant> inflectionPoints,
			final Forecast forecast,
			final Plan plan
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
					final var currentStepEndingBacklog = Arrays.stream(getWorkflow().stages)
							.mapToDouble(buildEndingBacklogCalcFunc(
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

			private ToDoubleFunction<Stage> buildEndingBacklogCalcFunc(
					final Instant startingPoint,
					final Instant endingPoint,
					final double[] startingBacklog
			) {
				return stage -> {
					final double input = sourceOf(stage)
							.map(source ->  integrateThroughput(plan, source, startingPoint, endingPoint))
							.orElse(forecast.integrateForecastedInput(startingPoint, endingPoint));
					final double output = integrateThroughput(plan, stage, startingPoint, endingPoint);
					final var current = startingBacklog[stage.ordinal()];
					return Math.max(0, current + input - output);
				};
			}
		}

		final var initialBacklog = Arrays.stream(startingBacklog).mapToDouble(Math::floor).toArray();
		return new LMC().loop(startingDate, List.iterableList(inflectionPoints), List.cons(initialBacklog, List.nil()));
	}

	private Optional<Stage> sourceOf(Stage stage) {
		return stage.ordinal() == 0 ? Optional.empty() : Optional.of(getWorkflow().stages[stage.ordinal() - 1]);
	}

	private double integrateThroughput(Plan plan, Stage stage, Instant startingPoint, Instant endingPoint) {
		if (stage.ordinal() == 0) {
			return calcCombinedThroughput(plan, stage, startingPoint, endingPoint);
		} else {
			return plan.integrateThroughput(stage, startingPoint, endingPoint);
		}
	}
}
