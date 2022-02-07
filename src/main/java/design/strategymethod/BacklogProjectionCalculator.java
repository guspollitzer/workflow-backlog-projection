package design.strategymethod;

import design.strategymethod.Workflow.Stage;

import fj.Ord;
import fj.P;
import fj.P2;
import fj.data.List;
import fj.data.TreeMap;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface BacklogProjectionCalculator {

	interface Sla {
		/**
		 * Date before which all the units corresponding to this {@link Sla} should be already processed by the last stage of the workflow.
		 */
		Instant getDeadline();
	}

	record Heap(Map<Sla, Integer> quantityBySla) {
		int total() {
			return quantityBySla.values().stream().mapToInt(Integer::intValue).sum();
		}

		Heap added(final Heap other) {
			var mergedPiles = Stream.concat(
							quantityBySla.entrySet().stream(),
							other.quantityBySla.entrySet().stream()
					)
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue, Integer::sum));
			return new Heap(mergedPiles);
		}
	}

	interface WorkflowBacklog {
		Collection<Stage> getStages();

		Heap getHeapAt(Stage stage);

		default Stream<Sla> getSlas() {
			return getStages().stream()
					.flatMap(stage -> getHeapAt(stage).quantityBySla.keySet().stream());
		}
	}

	/**
	 * Knows the staffing plan of a workflow.
	 */
	interface StaffingPlan {
		Instant getEndingDate();

		int getThroughput(Stage stage, Instant at);

		/**
		 * Calculates the integral of the throughput trajectory corresponding to the specified {@link Stage} on the specified interval.
		 */
		double integrateThroughputOf(Stage stage, Instant from, Instant to);

//		Duration reverseThroughputIntegral(Stage stage, Instant from, double targetValue, ChronoUnit targetUnit);
	}

	interface UpstreamThroughputTrajectory {
		/**
		 * Calculates the definite integral of this scalar trajectory on the specified interval.
		 */
		double integral(Instant from, Instant to);
	}

	interface ProcessedSlasDistributionDecider {
		/** The implementation should return a {@link Heap} {@code result} such that:
		 * <p>(1) {@code result.total() = processedQuantity}
		 * <p>(2) {@code result.get(sla) <= initialHeap.get(sla)} for all slas.
		 * <p>The number of units that are processed for each SLA is an implementation decision.
		 * The caller compromise is that the interval {@code (start,end)} should not contain any of the inflection points returned by {@link #getInflectionPointsBetween(Instant, Instant)}. */
		Heap decide(Stage stage, Heap initialHeap, long processedQuantity, Instant start, Instant end);

		Stream<Instant> getInflectionPointsBetween(Instant from, Instant to);
	}

	interface BacklogBoundsDecider {
		P2<Duration, Duration> getMinAndMax(Stage stage, Instant when);

		Stream<Instant> getInflectionPointsBetween(Instant from, Instant to);
	}

//	interface WavingStrategy {
//		Heap wave(WorkflowBacklog backlog, double processingPower);
//	}

	/**
	 * Contains all the information, about a step of the backlog trajectory of a single {@link Stage}, needed to calculate the next step.
	 * @param stage the {@link Stage} this step corresponds to.
	 * @param initialBacklog the actual backlog at the {@link Stage} when the step started.
	 * @param processed the amount of units processed during this step.
	 */
	record StageTrajectoryStep(Stage stage, Heap initialBacklog, Heap processed, long processedTotal) {}

	/**
	 * Contains all the information, about a step of the backlog trajectory of a whole workflow, needed to calculate the next step.
	 */
	record WorkflowTrajectoryStep(Instant startingDate, List<StageTrajectoryStep> stagesStep) {}

	@FunctionalInterface
	interface WorkflowTrajectoryFirstStepBuilder {
		WorkflowTrajectoryStep build(
				Instant stepStartingDate,
				Instant nextInflectionPoint,
				TreeMap<Instant, List<Sla>> nextSlasByDeadline,
				WorkflowBacklog startingBacklog,
				StaffingPlan staffingPlan,
				ProcessedSlasDistributionDecider processedSlasDistributionDecider,
				BacklogBoundsDecider backlogBoundsDecider
		);
	}

	@FunctionalInterface
	interface WorkflowTrajectoryNextStepBuilder {
		WorkflowTrajectoryStep build(
				Instant stepStartingDate,
				Instant nextInflectionPoint,
				TreeMap<Instant, List<Sla>> nextSlasByDeadline,
				WorkflowTrajectoryStep previousWorkflowStep,
				StaffingPlan staffingPlan,
				ProcessedSlasDistributionDecider processedSlasDistributionDecider,
				BacklogBoundsDecider backlogBoundsDecider,
				UpstreamThroughputTrajectory upstreamThroughputTrajectory
		);
	}

	static List<WorkflowTrajectoryStep> buildWorkflowTrajectory(
			final Instant startingDate,
			final WorkflowBacklog startingBacklog,
			final Stream<Sla> nextKnownSlas,
			final WorkflowTrajectoryFirstStepBuilder firstStepBuilder,
			final WorkflowTrajectoryNextStepBuilder nextStepBuilder,
			final StaffingPlan staffingPlan,
			final ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			final BacklogBoundsDecider backlogBoundsDecider,
			final UpstreamThroughputTrajectory upstreamThroughputTrajectory
	) {
		final var allSlas = Stream
				.concat(nextKnownSlas, startingBacklog.getSlas())
				.collect(Collectors.toSet());

		final var nextSlasByDeadline = groupSlasByDeadline(allSlas).splitLookup(startingDate)._3();

		// The java language does not support local functions so a local class with a single method is used instead.
		class WorkflowTrajectoryBuilder {
			/**
			 * Calculates the following steps of the received workflow trajectory based on its last step and the inputs: staffing plan and upstreamForecast.
			 * @param workflowTrajectory steps of the workflow trajectory that have already been calculated.
			 * @param remainingInflectionPoints the remaining inflection points for which the workflow trajectory should be calculated. It is assumed that inputs don't change during the time intervals between inflection points. Inflection
			 * points that correspond to deadlines are associated to the SLAs corresponding to said deadline.
			 */
			final List<WorkflowTrajectoryStep> calculateFollowingSteps(
					final List<WorkflowTrajectoryStep> workflowTrajectory,
					final Instant stepStartingInstant,
					final List<Instant> remainingInflectionPoints
			) {
				if (remainingInflectionPoints.isEmpty()) {
					return workflowTrajectory;
				} else {
					final var nextWorkflowStep = nextStepBuilder.build(
							stepStartingInstant,
							remainingInflectionPoints.head(),
							nextSlasByDeadline.splitLookup(stepStartingInstant)._3(),
							workflowTrajectory.head(),
							staffingPlan,
							processedSlasDistributionDecider,
							backlogBoundsDecider,
							upstreamThroughputTrajectory
					);
					return calculateFollowingSteps(
							List.cons(nextWorkflowStep, workflowTrajectory),
							remainingInflectionPoints.head(),
							remainingInflectionPoints.tail()
					);
				}
			}
		}

		if (nextSlasByDeadline.isEmpty()) {
			return List.nil();
		} else {
			final var processInflectionPoints = processedSlasDistributionDecider
					.getInflectionPointsBetween(startingDate, nextSlasByDeadline.maxKey().some())
					.map(i -> P.p(i, List.<Sla>nil()))
					.collect(Collectors.toList());
			// TODO add the BacklogBoundsDecider inflection points
			var inflectionPoints = nextSlasByDeadline.union(processInflectionPoints).keys();
			final var firstWorkflowStep = firstStepBuilder.build(
					startingDate,
					inflectionPoints.head(),
					nextSlasByDeadline,
					startingBacklog,
					staffingPlan,
					processedSlasDistributionDecider,
					backlogBoundsDecider
			);
			return new WorkflowTrajectoryBuilder().calculateFollowingSteps(
					List.cons(firstWorkflowStep, List.nil()),
					inflectionPoints.head(),
					inflectionPoints.tail()
			);
		}
	}

	private static TreeMap<Instant, List<Sla>> groupSlasByDeadline(Iterable<Sla> slas) {
		return List.iterableList(slas)
				.groupBy(Sla::getDeadline, Ord.comparableOrd());
	}
}
