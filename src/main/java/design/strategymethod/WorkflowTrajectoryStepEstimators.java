package design.strategymethod;

import design.global.Workflow.Stage;
import lombok.RequiredArgsConstructor;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;

import static design.strategymethod.BacklogTrajectoryEstimator.*;

/** Contains pure functions that estimate the workflow backlog at an instant based on the backlog at a previous instant and a context specified with many parameters.
 * Each of the contained pure function is designed to work only for a specific workflow configuration.
 * <p>
 * IMPLEMENTATION NOTE: This pure functions' container was originally implemented with an uninstantiable class containing class methods.
 * But given all the contained methods share many parameters and also propagate them to local methods, the shared parameters were converted to
 * instance constants and the class methods to instance methods in order to avoid parameters boilerplate. */
@RequiredArgsConstructor
class WorkflowTrajectoryStepEstimators {
	private final Instant stepStartingDate;
	private final Instant stepEndingDate;
	private final WorkflowBacklog stepStartingBacklog;
	private final TreeMap<Instant, List<Sla>> nextSlasByDeadline;
	private final StepTranscendentalInvariants transcendentals;

	WorkflowTrajectoryStep estimateWavefullStep(final Stage wavingStage) {
		// calculate the waving desired power (integral on [startingInstant, endingInstant] of the waving throughput)
		final var firstProcessingStage = transcendentals.processingStages().head();
		final var firstProcessingStageInitialHeap = stepStartingBacklog.getHeapAt(firstProcessingStage);
		final var firstProcessingStageInitialHeapTotal = firstProcessingStageInitialHeap.total();
		final var firstProcessingStageDesiredBufferSize =
				transcendentals.backlogBoundsDecider().getDesiredBufferSize(firstProcessingStage, stepStartingDate, nextSlasByDeadline);
		final var wavingDesiredPower = transcendentals.staffingPlan().integrateThroughputOf(
				firstProcessingStage, stepStartingDate, stepEndingDate.plus(firstProcessingStageDesiredBufferSize)
		) - firstProcessingStageInitialHeapTotal;

		// calculate the waving achievable power
		final var wavingInitialHeap = stepStartingBacklog.getHeapAt(wavingStage);
		final var wavingAchievablePower = Math.max(0, Math.min(wavingInitialHeap.total(), Math.round(wavingDesiredPower)));
		final var wavingAchievableHeap = transcendentals.processedSlasDistributionDecider()
				.decide(wavingStage, wavingInitialHeap, wavingAchievablePower, stepStartingDate, stepEndingDate, nextSlasByDeadline);
		assert wavingAchievablePower == wavingAchievableHeap.total();

		// build the wavingSimulationStep
		final var upstreamHeap = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
		final var wavingTrajectoryStep = new StageTrajectoryStep(
				wavingStage, wavingInitialHeap, upstreamHeap, wavingAchievableHeap, wavingAchievablePower);

		// calculate simulation step of processing stages
		final var processingStagesTrajectoryStep =
				estimateProcessingStagesStep(transcendentals.processingStages(), wavingAchievableHeap, wavingAchievablePower, List.nil());
		// return the estimated trajectory step
		return new WorkflowTrajectoryStep(stepStartingDate, List.cons(wavingTrajectoryStep, processingStagesTrajectoryStep));
	}

	WorkflowTrajectoryStep estimateWavelessStep() {
		var incomingHeap = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
		// calculate simulation of processing steps
		final var stagesTrajectoryStep =
				estimateProcessingStagesStep(transcendentals.processingStages(), incomingHeap, incomingHeap.total(), List.nil());
		return new WorkflowTrajectoryStep(stepStartingDate, stagesTrajectoryStep);
	}


	/**
	 * Calculates the processing stages section of a simulation step.
	 */
	private List<StageTrajectoryStep> estimateProcessingStagesStep(
			final List<Stage> remainingStages,
			final Heap stageStepIncomingHeap,
			final long stageStepIncomingTotal,
			final List<StageTrajectoryStep> alreadyEstimatedStages
	) {
		if (remainingStages.isEmpty()) {
			return alreadyEstimatedStages;
		} else {
			final var stage = remainingStages.head();
			final var stageStepStartingHeap = stepStartingBacklog.getHeapAt(stage);
			final var processingPower = transcendentals.staffingPlan().integrateThroughputOf(stage, stepStartingDate, stepEndingDate);
			final var processedTotal = Math.min(stageStepIncomingTotal + stageStepStartingHeap.total(), Math.round(processingPower));
			final var processedHeap = transcendentals.processedSlasDistributionDecider()
					.decide(stage, stageStepStartingHeap, processedTotal, stepStartingDate, stepEndingDate, nextSlasByDeadline);
			assert processedTotal == processedHeap.total();
			var stageTrajectoryStep =
					new StageTrajectoryStep(stage, stageStepStartingHeap, stageStepIncomingHeap, processedHeap, processedTotal);
			return estimateProcessingStagesStep(
					remainingStages.tail(),
					processedHeap,
					processedTotal,
					List.cons(stageTrajectoryStep, alreadyEstimatedStages)
			);
		}
	}
}
