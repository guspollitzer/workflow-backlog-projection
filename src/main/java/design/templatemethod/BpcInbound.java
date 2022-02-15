package design.templatemethod;

import design.global.Workflow;
import design.global.Workflow.Stage;

import java.time.Duration;
import java.time.Instant;

class BpcInbound extends BacklogProjectionCalculator {
	@Override
	protected Workflow getWorkflow() {
		return Workflow.inbound;
	}

	@Override
	protected double calcCombinedThroughput(Plan plan, Stage stage, Instant startingPoint, Instant endingPoint) {
		return plan.integrateThroughput(stage, startingPoint, endingPoint);
	}

	@Override
	protected float[] calcProcessingProportions(Duration[] nextSlasDistances) {
		return new float[]{0.5f, 0.4f, 0.1f};
	}
}
