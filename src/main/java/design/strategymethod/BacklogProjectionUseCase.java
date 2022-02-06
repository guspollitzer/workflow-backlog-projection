package design.strategymethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import fj.data.List;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static design.strategymethod.BacklogProjectionCalculator.*;
import static design.strategymethod.Workflow.Stage;

@Service
@RequiredArgsConstructor
public class BacklogProjectionUseCase {

	private final RequestClock requestClock;
	private final StaffingPlanGetter staffingPlanGetter;
	private final Supplier<WorkflowBacklog> actualBacklogSupplier;
	private final Supplier<Stream<Sla>> nextKnownSlasSupplier;
	private final Supplier<ProcessedSlasDistributionDecider> processingStrategySupplier;
	private final Supplier<BacklogBoundsDecider> backlogBoundsDeciderSupplier;
	private final Supplier<UpstreamThroughputTrajectory> upstreamThroughputTrajectorySupplier;

	public List<WorkflowTrajectoryStep> execute(final Workflow workflow) {
		final StrategiesByWorkflow strategies = StrategiesByWorkflow.from(workflow);

		var actualBacklog = actualBacklogSupplier.get();
		var nextKnownSlas = nextKnownSlasSupplier.get();

		var workflowTrajectory = buildWorkflowTrajectory(
				requestClock.now(),
				actualBacklog,
				nextKnownSlas,
				strategies.firstStepBuilder,
				strategies.nextStepBuilder,
				staffingPlanGetter.get(
						requestClock.now(),
						requestClock.now().plus(72, ChronoUnit.HOURS),
						workflow.stages
				),
				processingStrategySupplier.get(),
				backlogBoundsDeciderSupplier.get(),
				upstreamThroughputTrajectorySupplier.get()
		);
		return workflowTrajectory;
	}

	interface StaffingPlanGetter {
		BacklogProjectionCalculator.StaffingPlan get(Instant from, Instant to, Stage[] stages);
	}

	private enum StrategiesByWorkflow {
		inbound(WorkflowTrajectoryBuilderStrategies::inboundFirstStepBuilder, WorkflowTrajectoryBuilderStrategies::inboundNextStepBuilder),
		outboundDirect(WorkflowTrajectoryBuilderStrategies::dammedSourceFirstStepBuilder, WorkflowTrajectoryBuilderStrategies::dammedSourceNextStepBuilder),
		outboundWall(WorkflowTrajectoryBuilderStrategies::dammedSourceFirstStepBuilder, WorkflowTrajectoryBuilderStrategies::dammedSourceNextStepBuilder);

		private final WorkflowTrajectoryFirstStepBuilder firstStepBuilder;
		private final WorkflowTrajectoryNextStepBuilder nextStepBuilder;

		StrategiesByWorkflow(
				WorkflowTrajectoryFirstStepBuilder firstStepBuilder,
				WorkflowTrajectoryNextStepBuilder nextStepBuilder
		) {
			this.firstStepBuilder = firstStepBuilder;
			this.nextStepBuilder = nextStepBuilder;
		}

		static StrategiesByWorkflow from(final Workflow workflow) {
			return StrategiesByWorkflow.valueOf(workflow.name());
		}

		static {
			assert Arrays.stream(StrategiesByWorkflow.values()).map(StrategiesByWorkflow::name).collect(Collectors.toSet())
					.equals(Arrays.stream(Workflow.values()).map(Workflow::name).collect(Collectors.toSet()));
		}
	}
}
