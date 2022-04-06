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
	public QueueImpl emptyQueue() {
		return new QueueImpl(Map.of());
	}

	@Override
	public QueueImpl decide(
			Stage stage, Queue initialQueue,
			long processedQuantity,
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
	private record QueueImpl(Map<Sla, Integer> quantityBySla) implements Queue {

		@Override
		public int total() {
			return quantityBySla.values().stream().mapToInt(Integer::intValue).sum();
		}

		@Override
		public QueueImpl append(final Queue otherQueue) {
			var other = (QueueImpl) otherQueue;
			var mergedPiles = Stream.concat(
							quantityBySla.entrySet().stream(),
							other.quantityBySla.entrySet().stream()
					)
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue, Integer::sum));
			return new QueueImpl(mergedPiles);
		}

		@Override
		public Queue consume(Queue other) {
			return this.append(((QueueImpl)other).negated());
		}

		QueueImpl negated() {
			return new QueueImpl(
					this.quantityBySla.entrySet().stream().collect(Collectors.toMap(
							Map.Entry::getKey,
							e -> -e.getValue()
					))
			);
		}
	}
}
