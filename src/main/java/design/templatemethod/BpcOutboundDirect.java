package design.templatemethod;

import design.global.Workflow;
import design.global.Workflow.Stage;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

class BpcOutboundDirect extends BacklogProjectionCalculator {
	@Override
	protected Workflow getWorkflow() {
		return Workflow.outboundDirect;
	}

	@Override
	protected double calcCombinedThroughput(Plan plan, Stage dummy, Instant startingPoint, Instant endingPoint) {
		return Arrays.stream(getWorkflow().stages, 1, getWorkflow().stages.length)
				.mapToDouble(stage -> plan.integrateThroughput(stage, startingPoint, endingPoint))
				.min().orElse(0d);
	}

	@Override
	protected float[] calcProcessingProportions(Duration[] nextSlasDistances) {
		return new float[]{1.0f};
	}
}
