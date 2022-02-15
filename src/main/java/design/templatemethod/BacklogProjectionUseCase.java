package design.templatemethod;

import design.global.RequestClock;
import design.global.Workflow;
import design.templatemethod.BacklogProjectionCalculator.Forecast;
import design.templatemethod.BacklogProjectionCalculator.Plan;
import lombok.RequiredArgsConstructor;

import fj.data.List;

import java.time.Instant;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Supplier;


@RequiredArgsConstructor
public class BacklogProjectionUseCase {

	private final RequestClock requestClock;
	private final BiFunction<Workflow, Instant, int[]> startingBacklogSupplier;
	private final BiFunction<Workflow, Instant, TreeSet<Instant>> slasSupplier;
	private final BiFunction<Workflow, Instant, Forecast> forecastSupplier;
	private final BiFunction<Workflow, Instant, Plan> staffingPlanSupplier;


	public List<double[]> execute(final Workflow workflow) {
		var calculator = WorkflowBehaviour.from(workflow).behaviourSupplier.get();
		return calculator.calculate(
				requestClock.now(),
				startingBacklogSupplier.apply(workflow, requestClock.now()),
				slasSupplier.apply(workflow, requestClock.now()),
				forecastSupplier.apply(workflow, requestClock.now()),
				staffingPlanSupplier.apply(workflow, requestClock.now())
		);
	}


	@RequiredArgsConstructor
	private enum WorkflowBehaviour {
		inbound(BpcInbound::new),
		outboundDirect(BpcOutboundDirect::new),
		outboundWall(BpcOutboundWall::new);

		final Supplier<BacklogProjectionCalculator> behaviourSupplier;

		static WorkflowBehaviour from(final Workflow workflow) {
			return WorkflowBehaviour.valueOf(workflow.name());
		}
	}

}
