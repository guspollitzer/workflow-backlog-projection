package design.backlogprojection.processingcriterias;

import design.backlogprojection.BacklogTrajectoryEstimator.ProcessingOrderCriteria;
import design.backlogprojection.BacklogTrajectoryEstimator.Queue;
import design.backlogprojection.BacklogTrajectoryEstimator.Sla;
import design.global.Workflow.Stage;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;
import java.util.stream.Stream;

public class BatchDiscriminatedPoc implements ProcessingOrderCriteria {
  @Override
  public Queue emptyQueue() {
	return new BatchQueue(List.nil());
  }

  @Override
  public Queue decide(
	  Stage stage, Queue initialQueue, long processedQuantity, Instant start, Instant end,
	  TreeMap<Instant, List<Sla>> nextSlasByDeadline
  ) {
	// TODO
	return null;
  }

  @Override
  public Stream<Instant> getInflectionPointsBetween(Instant from, Instant to) {
	return Stream.empty();
  }

  /**
   * A {@link Queue} where the units are discriminated by batch and SLA.
   */
  private static class BatchQueue implements Queue {
	static final BatchQueue EMPTY = new BatchQueue(List.nil());

	private final List<TreeMap<Sla, Integer>> lotsOfQuantityBySla;

	BatchQueue(final List<TreeMap<Sla, Integer>> lotsOfQuantityBySla){
	  assert lotsOfQuantityBySla.forall((map -> !map.isEmpty() && map.toStream().forall(e -> e._2() > 0)));
	  this.lotsOfQuantityBySla = lotsOfQuantityBySla;
	}

	public int total() {
	  return lotsOfQuantityBySla.foldLeft(
		  (sum, lot) -> lot.values().foldLeft(
			  Integer::sum,
			  0
		  ),
		  0
	  );
	}

	@Override
	public Queue append(final Queue otherQueue) {
	  var other = (BatchQueue) otherQueue;
	  return new BatchQueue(this.lotsOfQuantityBySla.append(other.lotsOfQuantityBySla));
	}

	@Override
	public Queue consume(final Queue other) {
	  return new BatchQueue(dropLotsWhileEqualTo(
		  this.lotsOfQuantityBySla.reverse(),
		  ((BatchQueue)other).lotsOfQuantityBySla.reverse()
	  ).reverse());
	}

	private static List<TreeMap<Sla, Integer>> dropLotsWhileEqualTo(
		List<TreeMap<Sla, Integer>> original,
		List<TreeMap<Sla, Integer>> elemsToDrop
	) {
	  if (elemsToDrop.isEmpty()) {
		return original;
	  } else if (original.head().equals(elemsToDrop.head())) {
		return dropLotsWhileEqualTo(original.tail(), elemsToDrop.tail());
	  } else {
		assert elemsToDrop.tail().isEmpty() : "The consumed lots should be the firsts of the queue.";
		return List.cons(minus(original.head(), elemsToDrop.head()), original.tail());
	  }
	}

	private static TreeMap<Sla, Integer> minus(TreeMap<Sla, Integer> whole, final TreeMap<Sla, Integer> part) {
	  for (var partEntry : part) {
		final var originalQuantity = whole.get(partEntry._1()).orSome(0);
		assert originalQuantity > 0 : "The part has an element that the whole lacks.";
		final var finalQuantity = originalQuantity - partEntry._2();
		if (finalQuantity > 0) {
		  whole = whole.update(partEntry._1(), x -> finalQuantity)._2();
		} else {
		  assert finalQuantity == 0 : "The part has an element whose quantity is greater than the corresponding in the whole.";
		  whole = whole.delete(partEntry._1());
		}
	  }
	  return whole;
	}
  }
}
