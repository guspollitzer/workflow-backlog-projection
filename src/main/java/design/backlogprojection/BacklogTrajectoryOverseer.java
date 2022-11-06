package design.backlogprojection;

import design.backlogprojection.BacklogTrajectoryEstimator.Sla;
import design.backlogprojection.BacklogTrajectoryEstimator.StageTrajectoryStep;
import design.backlogprojection.BacklogTrajectoryEstimator.WorkflowTrajectoryStep;
import design.backlogprojection.processingcriterias.SlaDiscriminatedPoc.SlaQueue;
import design.global.ImmutableEnumMap;
import design.global.Workflow;
import design.global.Workflow.Stage;
import lombok.RequiredArgsConstructor;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Analyzes, for a given downstream throughput, how appropriate is the {@link StaffingPlan} on which a backlog trajectory estimation was
 * based. Specifically, calculates the minimum headcount necessary to maintain the desired buffer.
 */
@RequiredArgsConstructor
class BacklogTrajectoryOverseer {
  private final Workflow workflow;
  private final StaffingPlan staffingPlan;
  private final BacklogBoundsDecider backlogBoundsDecider;

  /**
   * Specifies what the {@link BacklogTrajectoryOverseer} needs to know about the downstream throughput trajectory.
   */
  public interface DownstreamThroughputTrajectory {
	/**
	 * Calculates the definite integral of this vectorial trajectory on the specified interval.
	 */
	SlaQueue integral(Stage finalStage, Instant from, Instant to);
  }

  /**
   * Specifies what the {@link BacklogTrajectoryOverseer} needs to know about the staffing plan of a workflow.
   */
  public interface StaffingPlan {
	/**
	 * Calculates the integral of the throughput trajectory corresponding to the specified {@link Stage} on the specified interval.
	 */
	double integrateThroughputOf(Stage stage, Instant from, Instant to);

	double getAverageProductivity(Stage stage, Instant from, Instant to);
  }

  public interface BacklogBoundsDecider {
	Duration getDesiredBufferSize(Stage stage, Instant when, TreeMap<Instant, List<Sla>> nextSlasByDeadline);
  }

  record WorkflowTrajectoryOversawStep(
	  Instant startingDate,
	  Instant endingDate,
	  ImmutableEnumMap<Stage, StageTrajectoryOversawStep> stagesStep
  ) {}

  record StageTrajectoryOversawStep(StageTrajectoryStep rawStep, long optimumHeadcount) {}

  /**
   * Given a {@link DownstreamThroughputTrajectory} and an estimated backlog trajectory (calculated by the {@link
   * BacklogTrajectoryEstimator}), analyzes how appropriate is the {@link StaffingPlan} on which the received estimation was based. The
   * result is the received backlog trajectory estimation with its steps enriched with the minimum headcount necessary to maintain the
   * desired buffer size.
   */
  List<WorkflowTrajectoryOversawStep> oversee(
	  final List<WorkflowTrajectoryStep> workflowTrajectorySteps,
	  final DownstreamThroughputTrajectory downstreamThroughputTrajectory,
	  final SlaQueue initialDownstreamQueue
  ) {
	final var finalStages = workflow.finalStages;

	return workflowTrajectorySteps.map(workflowTrajectoryStep -> {

	  var oversawStepDecomposedByFinalStage = finalStages.map(finalStage -> {
		var downStreamDemandBySla =
			downstreamThroughputTrajectory.integral(finalStage, workflowTrajectoryStep.startingDate(), workflowTrajectoryStep.endingDate());

		return new WorkflowTrajectoryOversawStep(
			workflowTrajectoryStep.startingDate(),
			workflowTrajectoryStep.endingDate(),
			new StepOverseer(
				workflowTrajectoryStep.startingDate(),
				workflowTrajectoryStep.endingDate(),
				workflowTrajectoryStep.stagesStep(),
				workflowTrajectoryStep.nextSlasByDeadline()
			).overseeStep(
				finalStage,
				downStreamDemandBySla.total(),
				ImmutableEnumMap.of()
			)
		);
	  });
	  // superpose all the oversaw step decompositions to get the totaled oversaw step.
	  return oversawStepDecomposedByFinalStage.tail().foldLeft(
		  (a, b) -> new WorkflowTrajectoryOversawStep(a.startingDate(), a.endingDate(), merge(a.stagesStep, b.stagesStep)),
		  oversawStepDecomposedByFinalStage.head()
	  );

	});
  }

  private ImmutableEnumMap<Stage, StageTrajectoryOversawStep> merge(
	  final ImmutableEnumMap<Stage, StageTrajectoryOversawStep> a,
	  final ImmutableEnumMap<Stage, StageTrajectoryOversawStep> b
  ) {
	return Stream.concat(a.toStream(workflow.stages), b.toStream(workflow.stages))
		.collect(ImmutableEnumMap.buildStreamBinaryCollector(workflow.stages, (x, y) -> {
		  assert x.rawStep == y.rawStep;
		  return new StageTrajectoryOversawStep(x.rawStep, x.optimumHeadcount + y.optimumHeadcount);
		}));
  }

  /**
   * A recursive function (implemented with the "invariant parameters as constant fields" pattern) that, based on a step of the stages state
   * trajectory, calculates the optimum headcount of the step at the specified stage and all the preceding ones.
   *
   * Implementation note: this class defines a recursive function whose invariant parameters (those whose value does not depend on the depth
   * of the recursion) are received by the class constructor and the variant ones by the single method this class has.
   */
  @RequiredArgsConstructor
  private class StepOverseer {
	private final Instant stepStartingDate;
	private final Instant stepEndingDate;
	private final ImmutableEnumMap<Stage, StageTrajectoryStep> stagesStep;
	private final TreeMap<Instant, List<Sla>> nextSlasByDeadline;

	private ImmutableEnumMap<Stage, StageTrajectoryOversawStep> overseeStep(
		final Stage stage,
		final long desiredPower,
		final ImmutableEnumMap<Stage, StageTrajectoryOversawStep> alreadyOversawStages
	) {
	  if (stage == null) {
		return alreadyOversawStages;
	  } else {
		final var rawStageStep = stagesStep.get(stage);
		if (!rawStageStep.stage().isHumanPowered()) {
		  return alreadyOversawStages;
		} else {
		  final var averageProductivity = staffingPlan.getAverageProductivity(rawStageStep.stage(), stepStartingDate, stepEndingDate);
		  if (averageProductivity <= 0) {
			return overseeStep(
				stage.previousStage(),
				0,
				alreadyOversawStages.put(stage, new StageTrajectoryOversawStep(rawStageStep, 0))
			);
		  } else {
			final var desiredHeadcount = Math.max(0, Math.round(Math.ceil(desiredPower / averageProductivity)));
			final var desiredBufferSize = backlogBoundsDecider.getDesiredBufferSize(
				rawStageStep.stage(),
				stepEndingDate,
				nextSlasByDeadline
			);
			final var unboundedDesiredUpstreamPower = staffingPlan.integrateThroughputOf(
				rawStageStep.stage(),
				stepStartingDate,
				stepEndingDate.plus(desiredBufferSize)
			) - rawStageStep.initialQueue().total();
			final var desiredUpstreamPower = Math.max(0, Math.round(unboundedDesiredUpstreamPower));
			final var oversawStep = new StageTrajectoryOversawStep(rawStageStep, desiredHeadcount);
			return overseeStep(
				stage.previousStage(),
				desiredUpstreamPower,
				alreadyOversawStages.put(stage, oversawStep)
			);
		  }
		}
	  }
	}
  }
}
