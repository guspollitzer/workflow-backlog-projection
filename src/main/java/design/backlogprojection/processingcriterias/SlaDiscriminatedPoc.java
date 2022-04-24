package design.backlogprojection.processingcriterias;

import design.backlogprojection.BacklogTrajectoryEstimator.Queue;
import design.backlogprojection.BacklogTrajectoryEstimator.ProcessingOrderCriteria;
import design.backlogprojection.BacklogTrajectoryEstimator.Sla;
import design.global.Workflow.Stage;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SlaDiscriminatedPoc implements ProcessingOrderCriteria {

	@Override
	public SlaQueue emptyQueue() {
		return new SlaQueue(Map.of());
	}

	@Override
	public SplitQueue decide(
			Stage stage, Queue initialQueue,
			long toProcessQuantity,
			Instant start,
			Instant end,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline
	) {
		// TODO no tiene mucho sentido discriminar por SLA sin discriminar por lote
		return null;
	}

	@Override
	public Stream<Instant> getInflectionPointsBetween(Instant from, Instant to) {
		return Stream.empty();
	}

	/** A {@link Queue} where the units are discriminated by SLA. */
	public record SlaQueue(Map<Sla, Long> quantityBySla) implements Queue {

		@Override
		public long total() {
			return quantityBySla.values().stream().mapToLong(Long::longValue).sum();
		}

		@Override
		public SlaQueue append(final Queue otherQueue) {
			var other = (SlaQueue) otherQueue;
			var mergedPiles = Stream.concat(
							quantityBySla.entrySet().stream(),
							other.quantityBySla.entrySet().stream()
					)
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue, Long::sum));
			return new SlaQueue(mergedPiles);
		}
	}
}
