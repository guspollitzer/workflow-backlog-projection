package design.backlogprojection;

import design.global.ImmutableEnumMap;
import design.global.Workflow.Stage;
import lombok.RequiredArgsConstructor;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;

import static design.backlogprojection.BacklogTrajectoryEstimator.*;

/**
 * Contains pure functions that estimate the workflow backlog at an instant based on the backlog at a previous instant and the specified
 * contextual parameters. Each of the contained pure function is designed to work only for a specific workflow configuration.
 * <p>
 * IMPLEMENTATION NOTE: This pure functions' container was originally implemented with an uninstantiable class containing class methods. But
 * given all the contained methods share many parameters and also propagate them to local methods, the shared parameters were converted to
 * instance constants and the class methods to instance methods in order to avoid parameters boilerplate.
 */
@RequiredArgsConstructor
class WorkflowTrajectoryStepEstimators {
  private final Instant stepStartingDate;
  private final Instant stepEndingDate;
  private final WorkflowBacklog stepStartingBacklog;
  private final TreeMap<Instant, List<Sla>> nextSlasByDeadline;
  private final StepTranscendentalInvariants transcendentals;

  WorkflowTrajectoryStep estimateWavefullStep(final Stage wavingStage) {
	assert !wavingStage.isHumanPowered();
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
	final var wavingAchievablePower = Math.max(
		0,
		Math.min(wavingInitialQueue.total(), wavingDesiredPower)
	); // TODO ¿hay que sumar el forecast acá, o no? No lo sumé porque, a diferencia de las otras etapas, en waving la cantidad procesada
	// depende del tamaño de la ola y dicho tamaña se calcula en el comienzo del intervalo, antes de que entre en juego el pronóstico del
	// resto del intervalo.
	final var afterWaveQueues = transcendentals.processingOrderCriteria()
		.decide(wavingStage, wavingInitialQueue, wavingAchievablePower, stepStartingDate, stepEndingDate, nextSlasByDeadline);
	assert wavingAchievablePower == afterWaveQueues.processed().toStream(transcendentals.allStages())
		.mapToLong(x -> x.value().total())
		.sum();

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

	// estimate the simultaneous step for each processing stage
	var stagesTrajectoryStep = estimateParallelStagesChains(
		afterWaveQueues.processed(),
		ImmutableEnumMap.of(wavingStage, wavingTrajectoryStep)
	);
	// return the estimated trajectory step
	return new WorkflowTrajectoryStep(
		stepStartingDate,
		stepEndingDate,
		stagesTrajectoryStep,
		nextSlasByDeadline
	);
  }

  WorkflowTrajectoryStep estimateWavelessStep() {
	var incomingQueue = transcendentals.upstreamThroughputTrajectory().integral(stepStartingDate, stepEndingDate);
	// calculate simulation of processing steps
	final var stagesTrajectoryStep =
		estimateAStepForAStageAndThoseThatFollowIt(transcendentals.processingStages().head(), incomingQueue, ImmutableEnumMap.of());
	return new WorkflowTrajectoryStep(stepStartingDate, stepEndingDate, stagesTrajectoryStep, nextSlasByDeadline);
  }


  /**
   * Estimates a step for the specified stage and all the stages it feeds transitively.
   */
  private ImmutableEnumMap<Stage, StageTrajectoryStep> estimateAStepForAStageAndThoseThatFollowIt(
	  final Stage stage,
	  final Queue stageStepIncomingQueue,
	  final ImmutableEnumMap<Stage, StageTrajectoryStep> alreadyEstimatedStages
  ) {
	if (stage == null) {
	  return alreadyEstimatedStages;
	} else {
	  final var stageStepStartingQueue = stepStartingBacklog.getQueueAt(stage);
	  final var maxProcessedTotal = stageStepIncomingQueue.total() + stageStepStartingQueue.total();
	  final var processingPower = Math.round(transcendentals.staffingPlan().integrateThroughputOf(stage, stepStartingDate,
		  stepEndingDate
	  ));
	  final var queueShortage = Math.max(0, processingPower - maxProcessedTotal);
	  final var processedTotal = Math.min(maxProcessedTotal, processingPower);
	  final var afterProcessQueues = transcendentals.processingOrderCriteria()
		  .decide(stage, stageStepStartingQueue, processedTotal, stepStartingDate, stepEndingDate, nextSlasByDeadline);
	  assert processedTotal == afterProcessQueues.processed().toStream(transcendentals.allStages()).mapToLong(x -> x.value().total()).sum();

	  var stageTrajectoryStep = new StageTrajectoryStep(
		  stage,
		  stageStepStartingQueue,
		  stageStepIncomingQueue,
		  afterProcessQueues.processed(),
		  afterProcessQueues.remaining(),
		  processedTotal,
		  queueShortage
	  );

	  return estimateParallelStagesChains(afterProcessQueues.processed(), alreadyEstimatedStages.put(stage, stageTrajectoryStep));
	}
  }

  private ImmutableEnumMap<Stage, StageTrajectoryStep> estimateParallelStagesChains(
	  final ImmutableEnumMap<Stage, BacklogTrajectoryEstimator.Queue> incomingQueuesByStage,
	  final ImmutableEnumMap<Stage, StageTrajectoryStep> alreadyEstimatedStages
  ) {
	return incomingQueuesByStage.toStream(transcendentals.allStages())
		.reduce(
			alreadyEstimatedStages,
			(previouslyEstimatedStages, processedQueueAndItsDestinationStage) -> previouslyEstimatedStages.putAll(
				estimateAStepForAStageAndThoseThatFollowIt(
					processedQueueAndItsDestinationStage.key(),
					processedQueueAndItsDestinationStage.value(),
					ImmutableEnumMap.of()
				)
			),
			ImmutableEnumMap::putAll
		);
  }
}
