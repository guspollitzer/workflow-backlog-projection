package design.strategymethod;

import design.global.Workflow;
import design.global.Workflow.Stage;
import design.strategymethod.BacklogProjectionUseCase.Plan;

import java.time.Instant;
import java.util.Arrays;

interface ThroughputStrategies {

	static double pessimistic(Workflow workflow, Plan plan, Stage dummy, Instant startingPoint, Instant endingPoint) {
		return Arrays.stream(workflow.stages, 1, workflow.stages.length)
				.mapToDouble(stage -> plan.integrateThroughput(stage, startingPoint, endingPoint))
				.min().orElse(0d);
	}

	static double backpressure(Workflow workflow, Plan plan, Stage stage, Instant startingPoint, Instant endingPoint) {
		return plan.integrateThroughput(workflow.stages[stage.ordinal() + 1], startingPoint, endingPoint);
	}

	static double self(Workflow dummy, Plan plan, Stage stage, Instant startingPoint, Instant endingPoint) {
		return plan.integrateThroughput(stage, startingPoint, endingPoint);
	}

}
