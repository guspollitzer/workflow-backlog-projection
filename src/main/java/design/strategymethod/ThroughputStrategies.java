package design.strategymethod;

import design.strategymethod.BacklogProjectionCalculator.Plan;
import design.strategymethod.Workflow.Stage;

import java.time.Instant;
import java.util.Arrays;

public interface ThroughputStrategies {

	static double pessimistic(Plan plan, Stage[] stages, Instant startingPoint, Instant endingPoint) {
		return Arrays.stream(stages)
				.mapToDouble(stage -> plan.integrateThroughput(stage, startingPoint, endingPoint))
				.min().orElse(0d);
	}

	static double average(Plan plan, Stage[] stages, Instant startingPoint, Instant endingPoint) {
		return Arrays.stream(stages)
				.mapToDouble(stage -> plan.integrateThroughput(stage, startingPoint, endingPoint))
				.average().orElse(0d);
	}

	static double backpressure(Plan plan, Stage[] stages, Instant startingPoint, Instant endingPoint) {
		return plan.integrateThroughput(stages[1], startingPoint, endingPoint);
	}
}
