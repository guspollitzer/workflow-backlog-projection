package design.strategymethod;

import design.strategymethod.Workflow.OutboundWallStage;
import design.strategymethod.Workflow.Stage;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;

import static design.strategymethod.BacklogProjectionCalculator.*;

public interface WorkflowTrajectoryBuilderStrategies {

	static WorkflowTrajectoryStep dammedSourceFirstStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowBacklog startingBacklog,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider
	) {
		// ----- waving step
		final var wavingInitialHeap = startingBacklog.getHeapAt(OutboundWallStage.wavingForWall);

		final var pickingInitialHeap = startingBacklog.getHeapAt(OutboundWallStage.pickingForWall);
		final var pickingInitialHeapTotal = pickingInitialHeap.total();
		final var pickingPower = staffingPlan.integrateThroughputOf(OutboundWallStage.pickingForWall, stepStartingDate, nextDeadline);

		final var readyToPickBufferBounds = backlogBoundsDecider.getMinAndMax(OutboundWallStage.pickingForWall, stepStartingDate);
		final var readyToPickMinBufferSize = staffingPlan.integrateThroughputOf(OutboundWallStage.pickingForWall, stepStartingDate, stepStartingDate.plus(readyToPickBufferBounds._1()));
		final var readyToPickMaxBufferSize = staffingPlan.integrateThroughputOf(OutboundWallStage.pickingForWall, stepStartingDate, stepStartingDate.plus(readyToPickBufferBounds._2()));

		final var wavedLimitlessQuantity = pickingPower + (pickingInitialHeapTotal < readyToPickMinBufferSize
				? readyToPickMinBufferSize - pickingInitialHeapTotal
				: readyToPickMaxBufferSize < pickingInitialHeapTotal
					? readyToPickMaxBufferSize - pickingInitialHeapTotal
					: 0D
		);
		final var wavedTotal = Math.max(0, Math.min(wavingInitialHeap.total(), Math.round(wavedLimitlessQuantity)));
		final var wavedHeap = processedSlasDistributionDecider
				.decide(
						OutboundWallStage.wavingForWall,
						wavingInitialHeap,
						wavedTotal,
						stepStartingDate,
						nextDeadline
				);
		assert wavedTotal == wavedHeap.total();
		final var wavingStep = new StageTrajectoryStep(OutboundWallStage.wavingForWall, wavingInitialHeap, wavedHeap, wavedTotal);

		// all the other steps
		class LocalFuncContainer {
			StageTrajectoryStep calcStageStep(final Stage stage, final long processedByPreviousStage) {
				final var initialHeap = startingBacklog.getHeapAt(stage);
				final var initialHeapTotal = initialHeap.total();
				final var processingPower = staffingPlan.integrateThroughputOf(stage, stepStartingDate, nextDeadline);
				final var processedTotal = Math.min(processedByPreviousStage + initialHeapTotal, Math.round(processingPower));
				final var processedHeap = processedSlasDistributionDecider
						.decide(
								stage,
								initialHeap,
								processedTotal,
								stepStartingDate,
								nextDeadline
						);
				assert processedTotal == processedHeap.total();
				return new StageTrajectoryStep(OutboundWallStage.wavingForWall, initialHeap, processedHeap, processedTotal);
			}
		}
		final var localFuncContainer = new LocalFuncContainer();

		final var pickingStep = localFuncContainer.calcStageStep(OutboundWallStage.pickingForWall, wavingStep.processedTotal());
		final var wallingStep = localFuncContainer.calcStageStep(OutboundWallStage.walling, pickingStep.processedTotal());
		final var packingStep = localFuncContainer.calcStageStep(OutboundWallStage.packingWalled, wallingStep.processedTotal());
		return new WorkflowTrajectoryStep(stepStartingDate, List.arrayList(wavingStep, pickingStep, wallingStep, packingStep));
	}

	static WorkflowTrajectoryStep dammedSourceNextStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowTrajectoryStep previousWorkflowStep,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider,
			UpstreamThroughputTrajectory upstreamThroughputTrajectory
	) {

		return null;
	}

	static WorkflowTrajectoryStep inboundFirstStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowBacklog startingBacklog,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider
	) {
		return null;
	}


	static WorkflowTrajectoryStep inboundNextStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowTrajectoryStep previousWorkflowStep,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider,
			UpstreamThroughputTrajectory upstreamThroughputTrajectory
	) {
		return null;
	}


}
