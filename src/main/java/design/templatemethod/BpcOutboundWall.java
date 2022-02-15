package design.templatemethod;

import design.global.Workflow;
import design.global.Workflow.Stage;

import java.time.Duration;
import java.time.Instant;

class BpcOutboundWall extends BacklogProjectionCalculator {
	@Override
	protected Workflow getWorkflow() {
		return Workflow.outboundWall;
	}

	@Override
	protected double calcCombinedThroughput(Plan plan, Stage stage, Instant startingPoint, Instant endingPoint) {
		return plan.integrateThroughput(getWorkflow().stages[stage.ordinal() + 1], startingPoint, endingPoint);
	}

	@Override
	protected float[] calcProcessingProportions(Duration[] nextSlasDistances) {
		return new float[]{0.5f, 0.4f, 0.1f};
	}
}
