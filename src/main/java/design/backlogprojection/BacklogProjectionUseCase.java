package design.backlogprojection;

import design.global.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import fj.data.List;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static design.backlogprojection.BacklogTrajectoryEstimator.*;
import static design.global.Workflow.Stage;

@Service
@RequiredArgsConstructor
public class BacklogProjectionUseCase {

  static final int SCOPE_IN_HOURS = 72;

  private final StaffingPlanGetter staffingPlanGetter;
  private final BiFunction<Workflow, Instant, WorkflowBacklog> actualBacklogSupplier;
  private final BiFunction<Workflow, Instant, Stream<Sla>> nextKnownSlasSupplier;
  private final BiFunction<Workflow, Instant, ProcessingOrderCriteria> processingStrategySupplier;
  private final BiFunction<Workflow, Instant, BacklogBoundsDecider> backlogBoundsDeciderSupplier;
  private final Supplier<UpstreamThroughputTrajectory> upstreamThroughputTrajectorySupplier;

  public List<WorkflowTrajectoryStep> execute(final Workflow workflow, final Instant viewDate) {
	var actualBacklog = actualBacklogSupplier.apply(workflow, viewDate);
	var nextKnownSlas = nextKnownSlasSupplier.apply(workflow, viewDate);

	final StrategyByWorkflow strategy = StrategyByWorkflow.from(workflow);
	var stepTranscendentalsInvariants = new StepTranscendentalInvariants(
		workflow.stages,
		workflow.processingStages,
		upstreamThroughputTrajectorySupplier.get(),
		staffingPlanGetter.get(
			viewDate,
			viewDate.plus(SCOPE_IN_HOURS, ChronoUnit.HOURS),
			workflow.processingStages
		),
		processingStrategySupplier.apply(workflow, viewDate),
		backlogBoundsDeciderSupplier.apply(workflow, viewDate)
	);
	return estimateWorkflowTrajectory(
		viewDate,
		actualBacklog,
		nextKnownSlas,
		strategy.stepEstimator,
		stepTranscendentalsInvariants
	);
  }

  interface StaffingPlanGetter {
	BacklogTrajectoryEstimator.StaffingPlan get(Instant from, Instant to, List<Stage> stages);
  }

  @RequiredArgsConstructor
  private enum StrategyByWorkflow {
	inbound(WorkflowTrajectoryStepEstimators::estimateWavelessStep),
	outbound(estimators -> estimators.estimateWavefullStep(Stage.waving));

	private final Function<WorkflowTrajectoryStepEstimators, WorkflowTrajectoryStep> stepEstimator;

	static StrategyByWorkflow from(final Workflow workflow) {
	  return StrategyByWorkflow.valueOf(workflow.name());
	}

	static {
	  assert Arrays.stream(StrategyByWorkflow.values()).map(StrategyByWorkflow::name).collect(Collectors.toSet())
		  .equals(Arrays.stream(Workflow.values()).map(Workflow::name).collect(Collectors.toSet()));
	}
  }
}
