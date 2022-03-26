package design.strategymethod;

import design.global.Workflow.Stage;
import design.strategymethod.BacklogTrajectoryEstimator.Sla;
import design.strategymethod.BacklogTrajectoryEstimator.StageTrajectoryStep;
import design.strategymethod.BacklogTrajectoryEstimator.WorkflowTrajectoryStep;
import lombok.RequiredArgsConstructor;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Duration;
import java.time.Instant;

/** Analyzes, for a given downstream throughput, how appropriate is the {@link StaffingPlan} on which a backlog trajectory estimation was
 * based. Specifically, calculates the minimum headcount necessary to maintain the desired buffer. */
@RequiredArgsConstructor
class BacklogTrajectoryOverseer {
	private final StaffingPlan staffingPlan;
	private final BacklogBoundsDecider backlogBoundsDecider;

	/**
	 * Specifies what the {@link BacklogTrajectoryOverseer} needs to know about the downstream throughput trajectory.
	 */
	public interface DownstreamThroughputTrajectory {
		/**
		 * Calculates the definite integral of this scalar trajectory on the specified interval.
		 */
		long integral(Instant from, Instant to);
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

	record WorkflowTrajectoryOversawStep(Instant startingDate, Instant endingDate, List<StageTrajectoryOversawStep> stagesStep) {}

	record StageTrajectoryOversawStep(StageTrajectoryStep rawStep, long optimumHeadcount) {}

	/**
	 * Given a {@link DownstreamThroughputTrajectory} and an estimated backlog trajectory (calculated by the {@link BacklogTrajectoryEstimator}),
	 * analyzes how appropriate is the {@link StaffingPlan} on which the received estimation was based.
	 * The result is the received backlog trajectory estimation with its steps enriched with the minimum headcount necessary to maintain the
	 * desired buffer size.
	 */
	List<WorkflowTrajectoryOversawStep> oversee(
			List<WorkflowTrajectoryStep> workflowTrajectorySteps,
			DownstreamThroughputTrajectory downstreamThroughputTrajectory
	) {
		return workflowTrajectorySteps.map(workflowTrajectoryStep -> new WorkflowTrajectoryOversawStep(
				workflowTrajectoryStep.startingDate(),
				workflowTrajectoryStep.endingDate(),
				new StepOverseer(
						workflowTrajectoryStep.startingDate(),
						workflowTrajectoryStep.endingDate(),
						workflowTrajectoryStep.nextSlasByDeadline()
				).overseeStep(
						workflowTrajectoryStep.stagesStep().reverse(),
						downstreamThroughputTrajectory.integral(
								workflowTrajectoryStep.startingDate(), workflowTrajectoryStep.endingDate()),
						List.nil()
				)
		));
	}

	@RequiredArgsConstructor
	private class StepOverseer {
		private final Instant stepStartingDate;
		private final Instant stepEndingDate;
		private final TreeMap<Instant, List<Sla>> nextSlasByDeadline;

		private List<StageTrajectoryOversawStep> overseeStep(
				final List<StageTrajectoryStep> remainingStagesReversed,
				final long desiredPower,
				final List<StageTrajectoryOversawStep> alreadyOversawStages
		) {
			if (remainingStagesReversed.isEmpty()) {
				return alreadyOversawStages;
			} else {
				final var rawStageStep = remainingStagesReversed.head();
				if (!rawStageStep.stage().isHumanPowered()) {
					return alreadyOversawStages;
				} else {
					final var averageProductivity = staffingPlan.getAverageProductivity(rawStageStep.stage(), stepStartingDate, stepEndingDate);
					if (averageProductivity <= 0) {
						return overseeStep(remainingStagesReversed.tail(), 0, List.cons(
								new StageTrajectoryOversawStep(rawStageStep, 0),
								alreadyOversawStages
						));
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
						) - rawStageStep.initialHeap().total();
						final var desiredUpstreamPower = Math.max(0, Math.round(unboundedDesiredUpstreamPower));
						final var oversawStep = new StageTrajectoryOversawStep(rawStageStep, desiredHeadcount);
						return overseeStep(
								remainingStagesReversed.tail(),
								desiredUpstreamPower,
								List.cons(oversawStep, alreadyOversawStages)
						);
					}
				}
			}
		}
	}
}
