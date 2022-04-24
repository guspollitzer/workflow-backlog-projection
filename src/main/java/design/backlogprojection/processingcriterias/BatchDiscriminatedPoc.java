package design.backlogprojection.processingcriterias;

import design.backlogprojection.BacklogTrajectoryEstimator.ProcessingOrderCriteria;
import design.backlogprojection.BacklogTrajectoryEstimator.Queue;
import design.backlogprojection.BacklogTrajectoryEstimator.Sla;
import design.backlogprojection.processingcriterias.SlaDiscriminatedPoc.SlaQueue;
import design.global.Workflow.QueueType;
import design.global.Workflow.Stage;

import fj.P;
import fj.P2;
import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class BatchDiscriminatedPoc implements ProcessingOrderCriteria {

  @Override
  public SplitQueue decide(
	  Stage stage, Queue queue, long toProcessQuantity, Instant start, Instant end, TreeMap<Instant, List<Sla>> nextSlasByDeadline
  ) {
	if (stage.inQueueType() == QueueType.FIFO && queue instanceof BatchQueue initialQueue) {
	  return consumeProportionally(initialQueue.total, initialQueue.heaps, toProcessQuantity, 0, List.nil());
	} else if (stage.inQueueType() == QueueType.FEFO && queue instanceof SlaQueue initialQueue) {
	  return consumeNearDeadlineSlasFirst(initialQueue.quantityBySla(), toProcessQuantity, nextSlasByDeadline);
	} else {
	  throw new IllegalArgumentException(String.format("Invalid queue type: %s", queue.getClass()));
	}
  }

  @Override
  public Stream<Instant> getInflectionPointsBetween(Instant from, Instant to) {
	return Stream.empty();
  }

  private SplitQueue consumeProportionally(
	  long waitingQuantity, List<Heap> waitingHeaps, long toProcessQuantity, long processedQuantity, List<Heap> processedHeaps
  ) {
	if (toProcessQuantity == 0) {
	  return new SplitQueue(new BatchQueue(waitingQuantity, waitingHeaps), new BatchQueue(processedQuantity, processedHeaps));
	} else {
	  assert toProcessQuantity > 0;
	  var nextHeap = waitingHeaps.head();
	  if (nextHeap.total <= toProcessQuantity) {
		return consumeProportionally(
			waitingQuantity - nextHeap.total, waitingHeaps.tail(), toProcessQuantity - nextHeap.total, processedQuantity + nextHeap.total,
			List.cons(nextHeap, processedHeaps)
		);
	  } else {
		var split = nextHeap.split(toProcessQuantity);
		return new SplitQueue(
			new BatchQueue(waitingQuantity - toProcessQuantity, List.cons(split._1(), waitingHeaps.tail())),
			new BatchQueue(processedQuantity + toProcessQuantity, List.cons(split._2(), processedHeaps))
		);
	  }
	}
  }

  private SplitQueue consumeNearDeadlineSlasFirst(
	  final Map<Sla, Long> waitingQuantityBySla,
	  long toProcessQuantity,
	  final TreeMap<Instant, List<Sla>> nextSlasByDeadline
  ) {
	if (toProcessQuantity == 0) {
	  return new SplitQueue(new SlaQueue(waitingQuantityBySla), BatchQueue.EMPTY);
	} else {
	  assert toProcessQuantity > 0;

	  final var newWaitingQuantityBySla = new HashMap<>(waitingQuantityBySla);
	  List<Pile> processedQuantityBySla = List.nil();
	  for (P2<Instant, List<Sla>> nextSlasEntry : nextSlasByDeadline) {
		final var nextSlas = nextSlasEntry._2();
		for (Sla nextSla : nextSlas) {
		  final var nextSlaWaitingQuantity = newWaitingQuantityBySla.get(nextSla);
		  if (nextSlaWaitingQuantity != null) {
			if (nextSlaWaitingQuantity <= toProcessQuantity) {
			  newWaitingQuantityBySla.remove(nextSla);
			  toProcessQuantity -= nextSlaWaitingQuantity;
			  processedQuantityBySla = List.cons(new Pile(nextSla, nextSlaWaitingQuantity), processedQuantityBySla);
			} else {
			  newWaitingQuantityBySla.put(nextSla, nextSlaWaitingQuantity - toProcessQuantity);
			  toProcessQuantity = 0;
			  processedQuantityBySla = List.cons(new Pile(nextSla, toProcessQuantity), processedQuantityBySla);
			}
		  }
		}
	  }
	  return new SplitQueue(
		  new SlaQueue(newWaitingQuantityBySla),
		  new BatchQueue(
			  toProcessQuantity,
			  List.single(new Heap(toProcessQuantity, processedQuantityBySla))
		  )
	  );
	}
  }


  /**
   * A {@link Queue} where the units are discriminated by batch and SLA.
   */
  private static class BatchQueue implements Queue {
	static final BatchQueue EMPTY = new BatchQueue(0, List.nil());

	private final long total;
	private final List<Heap> heaps;

	BatchQueue(long total, final List<Heap> heaps) {
	  assert heaps.forall(heap -> heap.total > 0);
	  assert heaps.foldLeft((accum, heap) -> accum + heap.total, 0L) == total;
	  this.total = total;
	  this.heaps = heaps;
	}

	public long total() {
	  return total;
	}

	@Override
	public Queue append(final Queue otherQueue) {
	  var other = (BatchQueue) otherQueue;
	  return new BatchQueue(this.total + other.total, this.heaps.append(other.heaps));
	}
  }

  private record Heap(long total, List<Pile> quantityBySla) {
	static final Heap EMPTY = new Heap(0, List.nil());

	P2<Heap, Heap> split(long rightTotal) {
	  if (rightTotal == 0) {
		return P.p(this, EMPTY);
	  } else if (rightTotal == total) {
		return P.p(EMPTY, this);
	  } else if (rightTotal > total) {
		throw new IllegalArgumentException(String.format("quantity=%d, total=%d", rightTotal, total));
	  } else {
		var accumInitial = 0;
		var accumRight = 0;
		var accumLeft = 0;
		List<Pile> leftPiles = List.nil();
		List<Pile> rightPiles = List.nil();
		List<Pile> remaining = this.quantityBySla;
		while (!remaining.isEmpty()) {
		  var initialPile = remaining.head();
		  accumInitial += initialPile.quantity;

		  var expectedAccumRight = (accumInitial * rightTotal) / total;
		  var rightQuantity = expectedAccumRight - accumRight;
		  accumRight += rightQuantity;
		  if (rightQuantity > 0) {
			rightPiles = List.cons(new Pile(initialPile.sla, rightQuantity), rightPiles);
		  }

		  var leftQuantity = initialPile.quantity - rightQuantity;
		  if (leftQuantity > 0) {
			leftPiles = List.cons(new Pile(initialPile.sla, leftQuantity), leftPiles);
		  }
		  accumLeft += leftQuantity;

		  remaining = remaining.tail();
		}

		assert accumRight == rightTotal;
		assert accumRight + accumLeft == total;
		return P.p(new Heap(accumLeft, leftPiles), new Heap(accumRight, rightPiles));
	  }
	}
  }

  private record Pile(Sla sla, long quantity) {}
}
