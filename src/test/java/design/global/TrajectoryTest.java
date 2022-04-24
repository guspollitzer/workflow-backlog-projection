package design.global;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.time.api.DateTimes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class TrajectoryTest {

  @Example
  boolean theIntegralOfAConstantUnitTrajectoryShouldBeEqualToTheLengthOfTheInterval() {
	var start = Instant.now();
	var end = start.plus(1, ChronoUnit.HOURS);
	var unitTrajectory = new Trajectory(new TreeMap<>(Map.of(start, 1L)));
	return Math.abs(unitTrajectory.integrate(start, end, TimeUnit.MINUTES) - 60d) <= 1e-9;
  }

  /**
   * Sea T una trayectoria, y sean A, B, C tres instantes; entonces la integral de T en el intervalo [A,C] debe ser igual a la suma de
   * las
   * integrales de T en los intervalos [A,B] y [B,C].
   */
  @Property
  boolean theIntegralOnACShouldBeEqualToTheIntegralOnABPlusTheIntegralOnBC(@ForAll("testDataProvider") TestData tap) {
	var integralOnAC = tap.trajectory.integrate(tap.a, tap.c, TimeUnit.HOURS);
	var integralOnAB = tap.trajectory.integrate(tap.a, tap.b, TimeUnit.HOURS);
	var integralOnBC = tap.trajectory.integrate(tap.b, tap.c, TimeUnit.HOURS);
	return Math.abs(integralOnAC - integralOnAB - integralOnBC) <= Math.max(Math.abs(integralOnAC), Math.abs(integralOnAB + integralOnBC))
		/ 1e9;
  }

  record TestData(Trajectory trajectory, Instant a, Instant b, Instant c) {}

  @Provide
  Arbitrary<TestData> testDataProvider() {
	return DateTimes.instants().between(
			Instant.parse("2020-01-01T00:00:00Z"),
			Instant.parse("2030-01-01T00:00:00Z")
		).list().ofSize(3)
		.flatMap(instants -> buildTrajectoryArbitrary(
			9,
			99,
			instants.stream().min(Instant::compareTo).get(),
			instants.stream().max(Instant::compareTo).get()
		).map(trajectory -> new TestData(trajectory, instants.get(0), instants.get(1), instants.get(2))));
  }

  public static Arbitrary<Trajectory> buildTrajectoryArbitrary(int maxSize, int maxValue, Instant startingDate, Instant endingDate) {
	return Arbitraries.maps(
		DateTimes.instants().between(startingDate, endingDate),
		Arbitraries.longs().between(0, maxValue)
	).ofMaxSize(maxSize).map(TreeMap::new).map(Trajectory::new);
  }

}