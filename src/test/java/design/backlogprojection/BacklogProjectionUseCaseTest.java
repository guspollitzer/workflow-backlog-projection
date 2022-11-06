package design.backlogprojection;

import design.backlogprojection.BacklogTrajectoryEstimator.BacklogBoundsDecider;
import design.backlogprojection.BacklogTrajectoryEstimator.ProcessingOrderCriteria;
import design.backlogprojection.BacklogTrajectoryEstimator.Sla;
import design.backlogprojection.BacklogTrajectoryEstimator.StaffingPlan;
import design.backlogprojection.BacklogTrajectoryEstimator.UpstreamThroughputTrajectory;
import design.backlogprojection.BacklogTrajectoryEstimator.WorkflowBacklog;
import design.backlogprojection.BacklogTrajectoryEstimator.WorkflowTrajectoryStep;
import design.global.TrajectoryTest;
import design.global.Workflow;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.time.api.constraints.DateTimeRange;

import fj.data.List;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static design.backlogprojection.BacklogProjectionUseCase.SCOPE_IN_HOURS;

class BacklogProjectionUseCaseTest {

  @Property
  void execute(@ForAll("sampleDataProvider") SampleData sd) {
	BacklogProjectionUseCase bpuc = new BacklogProjectionUseCase(
		(from, to, stages) -> sd.staffingPlan,
		(w, i) -> sd.actualWorkflowBacklog,
		(w, i) -> sd.nextKnownSlas,
		(w, i) -> sd.processingOrderCriteria,
		(w, i) -> sd.backlogBoundsDecider,
		() -> sd.upstreamThroughputTrajectory
	);

	List<WorkflowTrajectoryStep> workflowTrajectory = bpuc.execute(sd.workflow, sd.viewDate);
	// TODO
  }

  record SampleData(
	  StaffingPlan staffingPlan,
	  WorkflowBacklog actualWorkflowBacklog,
	  Stream<Sla> nextKnownSlas,
	  ProcessingOrderCriteria processingOrderCriteria,
	  BacklogBoundsDecider backlogBoundsDecider,
	  UpstreamThroughputTrajectory upstreamThroughputTrajectory,
	  Workflow workflow,
	  Instant viewDate
  ) {}

  @Provide
  public Arbitrary<SampleData> sampleDataProvider(
	  @ForAll Workflow workflow, @ForAll @DateTimeRange(min = "2022-01-01T00:00:00Z", max = "2030-01-01T00:00:00Z") Instant viewDate
  ) {
	final Arbitrary<StaffingPlan> staffingPlanArbitrary =
		TrajectoryTest.buildTrajectoryArbitrary(SCOPE_IN_HOURS, 99, viewDate, viewDate.plusSeconds(SCOPE_IN_HOURS * 3600))
			.list().ofSize(workflow.stages.length)
			.map(trajectories -> (stage, from, to) -> trajectories.get(stage.ordinal()).integrate(from, to, TimeUnit.HOURS));

//	TODO
//	final Arbitrary<SlaQueue> slaQueueArbitrary =

//	final Arbitrary<WorkflowBacklog> workflowBacklogArbitrary =


	return Combinators.combine(staffingPlanArbitrary, Arbitraries.integers()).as((staffingPlan, x) ->
		new SampleData(
			staffingPlan,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		)
	);
  }

  <A, B> Arbitrary<List<B>> liftEach(List<A> as, Function<A, Arbitrary<B>> f) {
	return as.isEmpty()
		? Arbitraries.just(List.nil())
		: f.apply(as.head()).flatMap(b -> liftEach(as.tail(), f).map(bs -> List.cons(b, bs)));
  }

}