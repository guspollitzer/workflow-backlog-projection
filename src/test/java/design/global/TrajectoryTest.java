package design.global;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.time.api.DateTimes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TrajectoryTest {

	@Example
	boolean theIntegralOfAConstantUnitTrajectoryShouldBeEqualToTheLengthOfTheInterval() {
		var start = Instant.now();
		var end = start.plus(1, ChronoUnit.HOURS);
		var unitTrajectory = new Trajectory(new TreeMap<>(Map.of(start, 1L)));
		return Math.abs(unitTrajectory.integrate(start, end, TimeUnit.MINUTES) - 60d) <= 1e-9;
	}

	@Example
	boolean falla() {
		var partition = Stream.of("2409-10-20T04:37:30Z", "2409-10-20T04:37:31Z", "2409-10-20T04:37:49Z").map(Instant::parse).toList();
		var trajectory = new Trajectory(new TreeMap<>(Map.of(Instant.parse("2409-10-20T04:37:31Z"), 1L)));

		var allTogether = trajectory.integrate(partition.get(0), partition.get(partition.size()-1), TimeUnit.HOURS);
		var inParts = IntStream.range(1, partition.size()).mapToDouble(endIndex -> {
			var start = partition.get(endIndex - 1);
			var end = partition.get(endIndex);
			return trajectory.integrate(start, end, TimeUnit.HOURS);
		}).sum();
		return Math.abs(allTogether - inParts) <= Math.max(allTogether, inParts) * 1E-9;
	}

	/** Sea T una trajectoria, AD un intervalo cerrado, y {AB, BC, CD} todos los subintervalos de AD;
	 * entonces la integral de T en AD debe ser igual a la suma de las integrales de T en AB, BC, y CD. */
	@Property(tries=99)
	boolean theIntegralOnAnIntervalShouldBeEqualToTheSumOfTheIntegralsOnAllPartsOfSaidInterval(@ForAll("trajectoryAndPartitionProvider") TrajectoryAndPartition tap) {
		var integralOnARange = tap.trajectory.integrate(tap.partition.get(0), tap.partition.get(tap.partition.size()-1), TimeUnit.HOURS);
		var sumOfTheIntegralOnAllPartsOfAPartitionOfTheSameRange = IntStream.range(1, tap.partition.size()).mapToDouble(endIndex -> {
			var start = tap.partition.get(endIndex - 1);
			var end = tap.partition.get(endIndex);
			return tap.trajectory.integrate(start, end, TimeUnit.HOURS);
		}).sum();
		return Math.abs(integralOnARange - sumOfTheIntegralOnAllPartsOfAPartitionOfTheSameRange) <= Math.max(integralOnARange, sumOfTheIntegralOnAllPartsOfAPartitionOfTheSameRange) * 1E-9;
	}

	record TrajectoryAndPartition(Trajectory trajectory, List<Instant> partition) {}

	@Provide
	Arbitrary<TrajectoryAndPartition> trajectoryAndPartitionProvider(@ForAll Instant firstPointOfTrajectory) {
		var lastPointOfTrajectory_Arbitrary = DateTimes.instants().atTheEarliest(firstPointOfTrajectory);
		return lastPointOfTrajectory_Arbitrary.flatMap(lastPointOfTrajectory -> {
			var trajectoryArbitrary = buildTrajectoryArbitrary(99, firstPointOfTrajectory, lastPointOfTrajectory);

			var partitionArbitrary = buildPartitionArbitrary(firstPointOfTrajectory, lastPointOfTrajectory);

			return Combinators.combine(trajectoryArbitrary, partitionArbitrary)
					.as((trajectory, partition) -> {
						partition.sort(Instant::compareTo);
						return new TrajectoryAndPartition(trajectory, partition);
					});
		});
	}

	public static Arbitrary<Trajectory> buildTrajectoryArbitrary(int maxValue, Instant startingDate, Instant endingDate) {
		return Arbitraries.maps(
				DateTimes.instants().between(startingDate, endingDate),
				Arbitraries.longs().between(0, maxValue)
		).ofMaxSize(9).map(TreeMap::new).map(Trajectory::new);
	}

	public static Arbitrary<List<Instant>> buildPartitionArbitrary(Instant firstPointOfTrajectory, Instant lastPointOfTrajectory) {
		return Arbitraries.frequencyOf(
				Tuple.of(4, DateTimes.instants().between(firstPointOfTrajectory, lastPointOfTrajectory)),
				Tuple.of(1, DateTimes.instants().atTheLatest(firstPointOfTrajectory)),
				Tuple.of(1, DateTimes.instants().atTheEarliest(lastPointOfTrajectory))
		).list().ofMinSize(3).ofMaxSize(7);
	}
}