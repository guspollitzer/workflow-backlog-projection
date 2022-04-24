package design.backlogprojection;

import design.global.Workflow.Stage;

import fj.Ord;
import fj.P2;
import fj.data.List;
import fj.data.TreeMap;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains a pure function that estimates the backlog trajectory of a workflow and the contract specification.
 */
public class BacklogTrajectoryEstimator {
	private BacklogTrajectoryEstimator() {}

	/**
	 * Specifies what the {@link BacklogTrajectoryEstimator#estimateWorkflowTrajectory} needs to know about an SLA.
	 */
	public interface Sla {
		/**
		 * Date before which all the units corresponding to this {@link Sla} should be already processed by the last stage of the workflow.
		 */
		Instant getDeadline();
	}

	/**
	 * Specifies how the {@link BacklogTrajectoryEstimator} represents a queue of otherwise indistinguishable units that are consecutively
	 * processed, and the operations that it needs to apply on them.
	 * The instances must be immutable.
	 */
	public interface Queue {
		/** The number of units contained in this queue. */
		long total();

		/** Should return a {@link Queue} that consists of the units in this queue followed by the units in the received queue, such that:
		 * {@code queueA.plus(queueB).total() == queueA.total() + queueB.total();}. */
		Queue append(final Queue other);
	}

	/**
	 * Specifies what the {@link BacklogTrajectoryEstimator} needs to know about a workflow's backlog.
	 */
	public interface WorkflowBacklog {
		Queue getQueueAt(Stage stage);
	}

	/**
	 * Specifies what the {@link BacklogTrajectoryEstimator} needs to know about the staffing plan of a workflow.
	 */
	public interface StaffingPlan {
		/**
		 * Calculates the integral of the throughput trajectory corresponding to the specified {@link Stage} on the specified interval.
		 */
		double integrateThroughputOf(Stage stage, Instant from, Instant to);
	}

	/**
	 * Specifies what the {@link BacklogTrajectoryEstimator} needs to know about the trajectory of the upstream throughput.
	 */
	public interface UpstreamThroughputTrajectory {
		/**
		 * Calculates the definite integral of this vectorial trajectory on the specified interval.
		 */
		Queue integral(Instant from, Instant to);
	}

  /**
   * Specifies what the {@link BacklogTrajectoryEstimator} needs to know about the processing order criteria of each stage's backlog.
   * <p>Note that the backlog of every stage should implement the {@link Queue} interface.
   */
	public interface ProcessingOrderCriteria {

		/**
		 * Should return an empty {@link Queue}, such that {@code aQueue.plus(emptyQueue()).equals(aQueue)}.
		 */
		Queue emptyQueue();

		/**
		 * The implementation should return a {@link Queue} {@code result} such that {@code result.total() = processedQuantity}.
		 * <p>The number of units that are processed for each SLA is an implementation decision.
		 * The caller compromise is that the interval {@code (start,end)} should not contain any of the inflection points returned by
		 * {@link
		 * #getInflectionPointsBetween(Instant, Instant)}.
		 */
		SplitQueue decide(
				Stage stage, Queue initialQueue,
				long toProcessQuantity,
				Instant start,
				Instant end,
				TreeMap<Instant, List<Sla>> nextSlasByDeadline
		);

		Stream<Instant> getInflectionPointsBetween(Instant from, Instant to);

		record SplitQueue(Queue remaining, Queue processed) {}
	}

	public interface BacklogBoundsDecider {
		// TODO Only the desired buffer size of the waving stage is needed here. So, perhaps, it would be clearer to remove the `stage`
		//  parameter.
		Duration getDesiredBufferSize(Stage stage, Instant when, TreeMap<Instant, List<Sla>> nextSlasByDeadline);

		Stream<Instant> getInflectionPointsBetween(Instant from, Instant to);
	}

	/**
	 * Knows relevant information about a step of an estimated trajectory of a stage's backlog.
	 * @param stage the {@link Stage} this step corresponds to.
	 * @param initialQueue the actual backlog at the {@link Stage} when the step started.
	 * @param processedQueue the amount of units processed during this step.
	 */
	public record StageTrajectoryStep(
			Stage stage,
			Queue initialQueue,
			Queue incomingQueue,
			Queue processedQueue,
			Queue finalQueue,
			long processedTotal,
			long queueShortage
	) {}

	/**
	 * Knows relevant information about a step of an estimated trajectory of a workflow's backlog.
	 */
	public record WorkflowTrajectoryStep(
			Instant startingDate,
			Instant endingDate,
			List<StageTrajectoryStep> stagesStep,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline
	) {}

	/**
	 * Parameters, whose invariability transcend the estimation steps, of a workflow-backlog's trajectory-simulator.
	 */
	record StepTranscendentalInvariants(
			List<Stage> processingStages,
			UpstreamThroughputTrajectory upstreamThroughputTrajectory,
			StaffingPlan staffingPlan,
			ProcessingOrderCriteria processingOrderCriteria,
			BacklogBoundsDecider backlogBoundsDecider
	) {}

	/**
	 * Creates a trajectory of a workflow's backlog based on the specified context.
	 */
	static List<WorkflowTrajectoryStep> estimateWorkflowTrajectory(
			final Instant startingDate,
			final WorkflowBacklog startingBacklog,
			final Stream<Sla> nextKnownSlas,
			final Function<WorkflowTrajectoryStepEstimators, WorkflowTrajectoryStep> stepEstimator,
			final StepTranscendentalInvariants transcendentals
	) {
		final var nextSlas = nextKnownSlas.collect(Collectors.toSet());
		final var nextSlasByDeadline = groupSlasByDeadline(nextSlas).splitLookup(startingDate)._3();

		// The java language does not support local functions so a local class with a single method is used instead.
		class WorkflowTrajectoryEstimator {
			/**
			 * Calculates the following steps of the received workflow trajectory based on its last step and the inputs: staffing plan and
			 * upstreamForecast.
			 * @param alreadyCalculatedSteps steps of the workflow trajectory that have already been calculated.
			 * @param remainingInflectionPoints the remaining inflection points for which the workflow trajectory should be calculated. It
			 * is assumed that inputs don't change during the time intervals between inflection points. Inflection
			 * points that correspond to deadlines are associated to the SLAs corresponding to said deadline.
			 */
			final List<WorkflowTrajectoryStep> estimateFollowingSteps(
					final List<WorkflowTrajectoryStep> alreadyCalculatedSteps,
					final Instant stepStartingInstant,
					final List<Instant> remainingInflectionPoints
			) {
				if (remainingInflectionPoints.isEmpty()) {
					return alreadyCalculatedSteps;
				} else {
					final WorkflowBacklog stepStartingBacklog = stage -> alreadyCalculatedSteps.head()
							.stagesStep.find(s -> s.stage == stage)
							.map(StageTrajectoryStep::finalQueue)
							.orSome(transcendentals.processingOrderCriteria.emptyQueue());

					final var nextWorkflowStep = stepEstimator.apply(new WorkflowTrajectoryStepEstimators(
							stepStartingInstant,
							remainingInflectionPoints.head(),
							stepStartingBacklog,
							nextSlasByDeadline.splitLookup(stepStartingInstant)._3(),
							transcendentals
					));
					return estimateFollowingSteps(
							List.cons(nextWorkflowStep, alreadyCalculatedSteps),
							remainingInflectionPoints.head(),
							remainingInflectionPoints.tail()
					);
				}
			}
		}

		if (nextSlasByDeadline.isEmpty()) {
			return List.nil();
		} else {
			final var lastDeadline = nextSlasByDeadline.maxKey().some();
			var inflectionPoints = List.arrayList(
					Stream.concat(
							transcendentals.processingOrderCriteria.getInflectionPointsBetween(startingDate, lastDeadline),
							Stream.concat(
									nextSlasByDeadline.keys().toCollection().stream(),
									transcendentals.backlogBoundsDecider.getInflectionPointsBetween(startingDate, lastDeadline)
							)
					).sorted().distinct().toArray(Instant[]::new)
			);

			final var firstWorkflowStep = stepEstimator.apply(new WorkflowTrajectoryStepEstimators(
					startingDate,
					inflectionPoints.head(),
					startingBacklog,
					nextSlasByDeadline,
					transcendentals
			));
			return new WorkflowTrajectoryEstimator().estimateFollowingSteps(
					List.cons(firstWorkflowStep, List.nil()),
					inflectionPoints.head(),
					inflectionPoints.tail()
			).reverse();
		}
	}

	private static TreeMap<Instant, List<Sla>> groupSlasByDeadline(Iterable<Sla> slas) {
		return List.iterableList(slas)
				.groupBy(Sla::getDeadline, Ord.comparableOrd());
	}
}
