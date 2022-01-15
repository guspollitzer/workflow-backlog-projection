package design.strategymethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static design.strategymethod.BacklogProjectionCalculator.SlaDispersionStrategy;
import static design.strategymethod.BacklogProjectionCalculator.Sla;
import static design.strategymethod.BacklogProjectionCalculator.ThroughputStrategy;
import static design.strategymethod.BacklogProjectionCalculator.calculate;
import static design.strategymethod.Workflow.Stage;

@Service
@RequiredArgsConstructor
public class BacklogProjectionUseCase {

	private final Supplier<SortedMap<Sla, Integer>> actualBacklogSupplier;
	private final RequestClock requestClock;
	private final PlannedThroughputGetter plannedThroughputGetter;

	public SortedMap<Sla, Integer> execute(final Workflow workflow) {
		final double[][] throughputByStageOrdinalByHourIndex = plannedThroughputGetter.get(
				requestClock.now(),
				requestClock.now().plus(72, ChronoUnit.HOURS),
				workflow.stages
		);
		final StrategiesByWorkflow strategies = StrategiesByWorkflow.from(workflow);
		return calculate(
				actualBacklogSupplier.get(),
				strategies.throughputStrategy,
				strategies.bufferStrategy,
				throughputByStageOrdinalByHourIndex
		);
	}

	interface PlannedThroughputGetter {
		/** @return the throughput by stage index by hour index  */
		double[][] get(Instant from, Instant to, Stage[] stages);
	}

	private enum StrategiesByWorkflow {
		inbound(ThroughputStrategies::optimistic, SlaDispersionStrategies::maximizeProductivity),
		outboundDirect(ThroughputStrategies::pessimistic, SlaDispersionStrategies::minimizeProcessingTime),
		outboundWall(ThroughputStrategies::pessimistic, SlaDispersionStrategies::maximizeProductivity);

		private final ThroughputStrategy throughputStrategy;
		private final SlaDispersionStrategy bufferStrategy;

		StrategiesByWorkflow(
				ThroughputStrategy tphStrategy,
				SlaDispersionStrategy bufferStrategy
		) {
			this.throughputStrategy = tphStrategy;
			this.bufferStrategy = bufferStrategy;
		}

		static StrategiesByWorkflow from(final Workflow workflow) {
			return StrategiesByWorkflow.valueOf(workflow.name());
		}

		static {
			assert Arrays.stream(StrategiesByWorkflow.values()).map(StrategiesByWorkflow::name).collect(Collectors.toSet())
					.equals(Arrays.stream(Workflow.values()).map(Workflow::name).collect(Collectors.toSet()));
		}
	}
}
