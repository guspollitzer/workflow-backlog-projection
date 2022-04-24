package design.backlogprojection;

import design.global.Workflow.Stage;
import lombok.RequiredArgsConstructor;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;

import static design.backlogprojection.BacklogTrajectoryEstimator.*;

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
		final var firstProcessingStageInitialQueue = stepStartingBacklog.getQueueAt(firstProcessingStage);
		final var firstProcessingStageInitialQueueTotal = firstProcessingStageInitialQueue.total();
		final var firstProcessingStageDesiredBufferSize =
				transcendentals.backlogBoundsDecider().getDesiredBufferSize(firstProcessingStage, stepStartingDate, nextSlasByDeadline);
		final var wavingDesiredPower = Math.round(transcendentals.staffingPlan().integrateThroughputOf(
				firstProcessingStage, stepStartingDate, stepEndingDate.plus(firstProcessingStageDesiredBufferSize)
		)) - firstProcessingStageInitialQueueTotal;

		// calculate the waving achievable power
		final var wavingInitialQueue = stepStartingBacklog.getQueueAt(wavingStage);
		final var wavingAchievablePower = Math.max(0, Math.min(wavingInitialQueue.total(), wavingDesiredPower)); // TODO ¿hay que sumar el forecast acá, o no? No lo sumé porque, a diferencia de las otras etapas, en waving la cantidad procesada depende del tamaño de la ola y dicho tamaña se calcula en el comienzo del intervalo, antes de que entre en juego el pronóstico del resto del intervalo.
		final var afterWaveQueues = transcendentals.processingOrderCriteria()
				.decide(wavingStage, wavingInitialQueue, wavingAchievablePower, stepStartingDate, stepEndingDate, nextSlasByDeadline);
		assert wavingAchievablePower == afterWaveQueues.processed().total();

		// build the wavingSimulationStep
		final var upstreamQueue = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
		final var wavingTrajectoryStep = new StageTrajectoryStep(
				wavingStage,
				wavingInitialQueue,
				upstreamQueue,
				afterWaveQueues.processed(),
				afterWaveQueues.remaining(),
				wavingAchievablePower,
				Math.max(0, wavingDesiredPower - wavingAchievablePower)
		);

		// estimate the step of each processing stage
		final var processingStagesTrajectoryStep =
				estimateProcessingStagesStep(transcendentals.processingStages(), afterWaveQueues.processed(), wavingAchievablePower, List.nil());
		// return the estimated trajectory step
		return new WorkflowTrajectoryStep(
				stepStartingDate,
				stepEndingDate,
				List.cons(wavingTrajectoryStep, processingStagesTrajectoryStep.reverse()),
				nextSlasByDeadline
		);
	}

	WorkflowTrajectoryStep estimateWavelessStep() {
		var incomingQueue = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
		// calculate simulation of processing steps
		final var stagesTrajectoryStep =
				estimateProcessingStagesStep(transcendentals.processingStages(), incomingQueue, incomingQueue.total(), List.nil());
		return new WorkflowTrajectoryStep(stepStartingDate, stepEndingDate, stagesTrajectoryStep, nextSlasByDeadline);
	}


	/**
	 * Calculates the processing stages section of a simulation step.
	 */
	private List<StageTrajectoryStep> estimateProcessingStagesStep(
			final List<Stage> remainingStages,
			final Queue stageStepIncomingQueue,
			final long stageStepIncomingTotal,
			final List<StageTrajectoryStep> alreadyEstimatedStages
	) {
		if (remainingStages.isEmpty()) {
			return alreadyEstimatedStages;
		} else {
			final var stage = remainingStages.head();
			final var stageStepStartingQueue = stepStartingBacklog.getQueueAt(stage);
			final var maxProcessedTotal = stageStepIncomingTotal + stageStepStartingQueue.total();
			final var processingPower = Math.round(transcendentals.staffingPlan().integrateThroughputOf(stage, stepStartingDate, stepEndingDate));
			final var queueShortage = Math.max(0, processingPower - maxProcessedTotal);
			final var processedTotal = Math.min(maxProcessedTotal, processingPower);
			final var afterProcessQueues = transcendentals.processingOrderCriteria()
					.decide(stage, stageStepStartingQueue, processedTotal, stepStartingDate, stepEndingDate, nextSlasByDeadline);
			assert processedTotal == afterProcessQueues.processed().total();
			var stageTrajectoryStep = new StageTrajectoryStep(
					stage,
					stageStepStartingQueue,
					stageStepIncomingQueue,
					afterProcessQueues.processed(),
					afterProcessQueues.remaining(),
					processedTotal,
					queueShortage
			);
			return estimateProcessingStagesStep(
					remainingStages.tail(),
					afterProcessQueues.processed(),
					processedTotal,
					List.cons(stageTrajectoryStep, alreadyEstimatedStages)
			);
		}
	}
}
