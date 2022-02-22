package design.strategymethod;

import design.global.ForecastSupplier;
import design.global.StaffingPlanSupplier;
import design.global.Trajectory;
import design.global.Workflow;
import design.strategymethod.BacklogProjectionCalculator.Behaviour;
import design.strategymethod.BacklogProjectionCalculator.Forecast;
import design.strategymethod.BacklogProjectionUseCase.Plan;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.statistics.Histogram;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.api.statistics.StatisticsReport;
import net.jqwik.time.api.DateTimes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static design.global.TrajectoryTest.buildPartitionArbitrary;
import static design.global.TrajectoryTest.buildTrajectoryArbitrary;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BacklogProjectionCalculatorTest {
	private static final boolean HISTOGRAM_MODE = false;

//	@Property(shrinking = ShrinkingMode.OFF, afterFailure = AfterFailureMode.SAMPLE_ONLY)
	@Property(shrinking = ShrinkingMode.BOUNDED, afterFailure = AfterFailureMode.PREVIOUS_SEED)
	@StatisticsReport(format = Histogram.class)
	void theProjectedBacklogShouldBeIndependentOfTheSetOfInflectionPointsAsLongAsTheInvolvedFunctionsBehaveLinearly(@ForAll("testDataSupplier") TestData td) {
		var workflowBacklogTrajectoryReversed = BacklogProjectionCalculator.calculate(td.startingDate, td.startingBacklog, td.inflectionPoints, td.forecast, td.plan, td.behaviour);
		var workflowFinalBacklog1 = workflowBacklogTrajectoryReversed.head();
		var workflowBacklogTrajectoryInit = workflowBacklogTrajectoryReversed.tail().reverse();
		Assume.that(
						td.behaviour.getWorkflow() != Workflow.outboundDirect // because the `outboundDirect` workflow uses the `ThroughputStrategies::pessimistic` strategy which is non-lineal.
						&& workflowBacklogTrajectoryInit.find(backlog -> Arrays.stream(backlog).anyMatch(p -> p == 0d)).isNone()
		);
		var lastInflectionPoint = td.inflectionPoints.last();
		var inflectionPointsInit = td.inflectionPoints.headSet(lastInflectionPoint);
		var inflectionPointsInitSubsetsArbitrary = Arbitraries.subsetOf(inflectionPointsInit.toArray(Instant[]::new)).ofMinSize(1);
		Arbitraries.lazy(() -> inflectionPointsInitSubsetsArbitrary).forEachValue(subset -> {
			var ip = new TreeSet<>(subset);
			ip.add(lastInflectionPoint);
			var workflowBacklogIncompleteTrajectoryReversed = BacklogProjectionCalculator.calculate(td.startingDate, td.startingBacklog, ip, td.forecast, td.plan, td.behaviour);
			var workflowFinalBacklog2 = workflowBacklogIncompleteTrajectoryReversed.head();

			if (HISTOGRAM_MODE) {
				var pass = IntStream.range(0, td.behaviour.getWorkflow().stages.length)
						.allMatch(so -> workflowFinalBacklog1[so] - workflowFinalBacklog2[so] <= Math.max(workflowFinalBacklog1[so], workflowFinalBacklog2[so]) / 10000);
				Statistics.collect(td.behaviour.getWorkflow(), workflowBacklogTrajectoryInit.isEmpty(), pass ? "success" : "fail");
			} else {
				for (var stage = 0; stage < td.behaviour.getWorkflow().stages.length; ++stage) {
					assertEquals(workflowFinalBacklog1[stage], workflowFinalBacklog2[stage], Math.max(workflowFinalBacklog1[stage], workflowFinalBacklog2[stage]) / 1e3);
				}
			}
		});
	}

	record TestData(
			Instant startingDate,
			int[] startingBacklog,
			SortedSet<Instant> inflectionPoints,
			Forecast forecast,
			Plan plan,
			Behaviour<Plan> behaviour,
			List<Instant> partition
	) {
		public String toString() {
			return "TestData(startingDate=" + this.startingDate + ", startingBacklog=" + Arrays.toString(this.startingBacklog) + ", inflectionPoints=" + this.inflectionPoints + ", forecast="
					+ this.forecast + ", plan=" + this.plan + ", behaviour=" + this.behaviour + ", partition=" + this.partition + ")";
		}
	}

	@Provide
	Arbitrary<TestData> testDataSupplier(@ForAll Instant startingDate, @ForAll Workflow workflow) {
		return buildTestDataArbitrary(startingDate, workflow, 2, 100);
	}

	Arbitrary<TestData> buildTestDataArbitrary(Instant startingDate, Workflow workflow, int minInflectionPoints, int minInitialBacklog) {
		var endingDateArbitrary = Arbitraries.integers().between(1, 60 * 92).map(minutes -> startingDate.plus(minutes, ChronoUnit.MINUTES));
		var startingBacklogArbitrary = Arbitraries.integers().between(minInitialBacklog, 199).array(int[].class).ofSize(workflow.stages.length);
		return Combinators.combine(endingDateArbitrary, startingBacklogArbitrary).flatAs((endingDate, startingBacklog) -> {
			var inflectionPointsArbitrary = DateTimes.instants().between(startingDate, endingDate).set().ofMinSize(minInflectionPoints).ofMaxSize(5).map(TreeSet::new);
			var forecastArbitrary = buildTrajectoryArbitrary(99, startingDate, endingDate).map(ForecastSupplier.Forecast::new);
			var planArbitrary = buildPlanArbitrary(workflow, 99, startingDate, endingDate);
			var partitionArbitrary = buildPartitionArbitrary(startingDate, endingDate);
			return Combinators.combine(inflectionPointsArbitrary, forecastArbitrary, planArbitrary, partitionArbitrary)
					.as((inflectionPoints, forecast, plan, partition) -> new TestData(
							startingDate,
							startingBacklog,
							inflectionPoints,
							forecast,
							plan,
							BacklogProjectionUseCase.WorkflowBehaviour.from(workflow),
							partition
					));
		});
	}

	private Arbitrary<StaffingPlanSupplier.Plan> buildPlanArbitrary(Workflow workflow, int maxThroughputPerHour, Instant startingDate, Instant endingDate) {
		return buildTrajectoryArbitrary(maxThroughputPerHour, startingDate, endingDate)
				.array(Trajectory[].class)
				.ofSize(workflow.stages.length)
				.map(trajectories -> IntStream.range(0, trajectories.length)
						.boxed()
						.collect(Collectors.toMap(
								i -> workflow.stages[i],
								i -> trajectories[i]
						))
				)
				.map(StaffingPlanSupplier.Plan::new);
	}
}