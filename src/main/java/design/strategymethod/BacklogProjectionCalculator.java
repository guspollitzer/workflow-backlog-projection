package design.strategymethod;

import java.time.Duration;
import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;

public interface BacklogProjectionCalculator {

	interface Sla extends Comparable<Sla> {
		Instant getDateOut();
		Duration getCycleTime();
	}

	interface ThroughputStrategy {
		double calcCombinedThroughput(double[] throughputOfThisAndFollowingStages);
	}

	interface SlaDispersionStrategy {
		float[] calcProcessingProportions(Duration[] nextSlasDistances);
	}


	/** This is the strategy method */
	static SortedMap<Sla, Integer> calculate(
			final SortedMap<Sla, Integer> actualBacklog,
			final ThroughputStrategy throughputStrategy,
			final SlaDispersionStrategy slaDispersionStrategy,
			final double[][] throughputByStageOrdinalByHourIndex
	) {
		// TODO
		return new TreeMap<>();
	}
}
