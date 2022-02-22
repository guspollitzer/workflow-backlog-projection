package design.strategymethod;

import design.global.RequestClock;
import design.global.Workflow;
import design.global.Workflow.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import fj.data.List;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;

import static design.strategymethod.BacklogProjectionCalculator.*;

@Service
@RequiredArgsConstructor
public class BacklogProjectionUseCase {

	private final RequestClock requestClock;
	private final BiFunction<Workflow, Instant, int[]> startingBacklogSupplier;
	private final BiFunction<Workflow, Instant, TreeSet<Instant>> slasSupplier;
	private final BiFunction<Workflow, Instant, Forecast> forecastSupplier;
	private final BiFunction<Workflow, Instant, Plan> staffingPlanSupplier;

	public List<double[]> execute(final Workflow workflow) {
		return calculate(
				requestClock.now(),
				startingBacklogSupplier.apply(workflow, requestClock.now()),
				slasSupplier.apply(workflow, requestClock.now()),
				forecastSupplier.apply(workflow, requestClock.now()),
				staffingPlanSupplier.apply(workflow, requestClock.now()),
				WorkflowBehaviour.from(workflow)
		);
	}

	public interface Plan {
		double integrateThroughput(Stage stage, Instant from, Instant to);
	}

	interface ThroughputStrategy {
		double calcCombinedThroughput(Workflow workflow, Plan plan, Stage stage, Instant startingPoint, Instant endingPoint);
	}

	interface SlaDispersionStrategy {
		float[] calcProcessingProportions(Duration[] nextSlasDistances);
	}

	@Getter
	enum WorkflowBehaviour implements Behaviour<Plan> {
		inbound(ThroughputStrategies::self, SlaDispersionStrategies::maximizeProductivity),
		outboundDirect(ThroughputStrategies::pessimistic, SlaDispersionStrategies::minimizeProcessingTime),
		outboundWall(ThroughputStrategies::backpressure, SlaDispersionStrategies::maximizeProductivity);

		private final Workflow workflow;
		private final ThroughputStrategy firstStageThroughputStrategy;
		private final SlaDispersionStrategy slaDispersionStrategy;

		WorkflowBehaviour(ThroughputStrategy firstStageThroughputStrategy, SlaDispersionStrategy bufferStrategy) {
			this.workflow = Workflow.valueOf(this.name());
			this.firstStageThroughputStrategy = firstStageThroughputStrategy;
			this.slaDispersionStrategy = bufferStrategy;
		}

		@Override
		public Optional<Stage> sourceOf(Stage stage) {
			return stage.ordinal() == 0 ? Optional.empty() : Optional.of(workflow.stages[stage.ordinal() - 1]);
		}

		@Override
		public double integrateThroughput(Plan plan, Stage stage, Instant startingPoint, Instant endingPoint) {
			if (stage.ordinal() == 0) {
				return firstStageThroughputStrategy.calcCombinedThroughput(workflow, plan, stage, startingPoint, endingPoint);
			} else {
				return plan.integrateThroughput(stage, startingPoint, endingPoint);
			}
		}

		@Override
		public float[] calcProcessingProportions(Duration[] nextSlasDistances) {
			return slaDispersionStrategy.calcProcessingProportions(nextSlasDistances);
		}

		static WorkflowBehaviour from(final Workflow workflow) {
			return WorkflowBehaviour.valueOf(workflow.name());
		}
	}
}
