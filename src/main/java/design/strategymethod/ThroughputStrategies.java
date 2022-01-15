package design.strategymethod;

import java.util.stream.DoubleStream;

public interface ThroughputStrategies {

	static double pessimistic(double[] throughputOfThisAndFollowingStages) {
		return DoubleStream.of(throughputOfThisAndFollowingStages).min().orElse(0.0);
	}

	static double optimistic(double[] throughputOfThisAndFollowingStages) {
		return DoubleStream.of(throughputOfThisAndFollowingStages).max().orElse(0.0);
	}
}
