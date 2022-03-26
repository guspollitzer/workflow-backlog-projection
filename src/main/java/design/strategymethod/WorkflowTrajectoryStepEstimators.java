package design.strategymethod;

import design.global.Workflow.Stage;
import lombok.RequiredArgsConstructor;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;

import static design.strategymethod.BacklogTrajectoryEstimator.*;

/** Contains pure functions that estimate the workflow backlog at an instant based on the backlog at a previous instant and the specified contextual parameters.
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
		final var wavingDesiredPower = Math.round(transcendentals.staffingPlan().integrateThroughputOf(
				firstProcessingStage, stepStartingDate, stepEndingDate.plus(firstProcessingStageDesiredBufferSize)
		)) - firstProcessingStageInitialHeapTotal;

		// calculate the waving achievable power
		final var wavingInitialHeap = stepStartingBacklog.getHeapAt(wavingStage);
		final var wavingAchievablePower = Math.max(0, Math.min(wavingInitialHeap.total(), wavingDesiredPower)); // TODO hay que sumar el forecast acá, o no? No lo sumé porque, a diferencia de las otras etapas, en waving la cantidad procesada depende del tamaño de la ola y dicho tamaña se calcula a lo que hay en ready-to-wave al comienzo del intervalo.
		final var wavingAchievableHeap = transcendentals.processedSlasDistributionDecider()
				.decide(wavingStage, wavingInitialHeap, wavingAchievablePower, stepStartingDate, stepEndingDate, nextSlasByDeadline);
		assert wavingAchievablePower == wavingAchievableHeap.total();

		// build the wavingSimulationStep
		final var upstreamHeap = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
		final var wavingTrajectoryStep = new StageTrajectoryStep(
				wavingStage,
				wavingInitialHeap,
				upstreamHeap,
				wavingAchievableHeap,
				wavingAchievablePower,
				Math.max(0, wavingDesiredPower - wavingAchievablePower)
		);

		// estimate the step of each processing stage
		final var processingStagesTrajectoryStep =
				estimateProcessingStagesStep(transcendentals.processingStages(), wavingAchievableHeap, wavingAchievablePower, List.nil());
		// return the estimated trajectory step
		return new WorkflowTrajectoryStep(
				stepStartingDate,
				stepEndingDate,
				List.cons(wavingTrajectoryStep, processingStagesTrajectoryStep.reverse()),
				nextSlasByDeadline
		);
	}

	WorkflowTrajectoryStep estimateWavelessStep() {
		var incomingHeap = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
		// calculate simulation of processing steps
		final var stagesTrajectoryStep =
				estimateProcessingStagesStep(transcendentals.processingStages(), incomingHeap, incomingHeap.total(), List.nil());
		return new WorkflowTrajectoryStep(stepStartingDate, stepEndingDate, stagesTrajectoryStep, nextSlasByDeadline);
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
			final var maxProcessedTotal = stageStepIncomingTotal + stageStepStartingHeap.total();
			final var processingPower = Math.round(transcendentals.staffingPlan().integrateThroughputOf(stage, stepStartingDate, stepEndingDate));
			final var heapShortage = processingPower - maxProcessedTotal;
			final var processedTotal = Math.min(maxProcessedTotal, processingPower);
			final var processedHeap = transcendentals.processedSlasDistributionDecider()
					.decide(stage, stageStepStartingHeap, processedTotal, stepStartingDate, stepEndingDate, nextSlasByDeadline);
			assert processedTotal == processedHeap.total();
			var stageTrajectoryStep = new StageTrajectoryStep(
					stage,
					stageStepStartingHeap,
					stageStepIncomingHeap,
					processedHeap,
					processedTotal,
					heapShortage
			);
			return estimateProcessingStagesStep(
					remainingStages.tail(),
					processedHeap,
					processedTotal,
					List.cons(stageTrajectoryStep, alreadyEstimatedStages)
			);
		}
	}
}
