package design.strategymethod;

import design.strategymethod.Workflow.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import fj.data.List;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static design.strategymethod.BacklogProjectionCalculator.*;

@Service
@RequiredArgsConstructor
public class BacklogProjectionUseCase {

	private final RequestClock requestClock;
	private final BiFunction<Workflow, Instant, int[]> startingBacklogSupplier;
	private final BiFunction<Workflow, Instant, TreeSet<Instant>> slasSupplier;
	private final BiFunction<Workflow, Instant, Plan> staffingPlanSupplier;

	public List<double[]> execute(final Workflow workflow) {
		return calculate(
				requestClock.now(),
				startingBacklogSupplier.apply(workflow, requestClock.now()),
				slasSupplier.apply(workflow, requestClock.now()),
				staffingPlanSupplier.apply(workflow, requestClock.now()),
				WorkflowBehaviour.from(workflow)
		);
	}

	interface ThroughputStrategy {
		double calcCombinedThroughput(Plan plan, Stage[] stages, Instant startingPoint, Instant endingPoint);
	}

	@Getter
	enum WorkflowBehaviour implements Behaviour {
		inbound(ThroughputStrategies::backpressure, SlaDispersionStrategies::maximizeProductivity),
		outboundDirect(ThroughputStrategies::pessimistic, SlaDispersionStrategies::minimizeProcessingTime),
		outboundWall(ThroughputStrategies::average, SlaDispersionStrategies::maximizeProductivity);

		private final Workflow workflow;
		private final ThroughputStrategy throughputStrategy;
		private final SlaDispersionStrategy slaDispersionStrategy;

		WorkflowBehaviour(ThroughputStrategy throughputStrategy, SlaDispersionStrategy bufferStrategy) {
			this.workflow = Workflow.valueOf(this.name());
			this.throughputStrategy = throughputStrategy;
			this.slaDispersionStrategy = bufferStrategy;
		}

		@Override
		public Stage[] getStages() {
			return workflow.stages;
		}

		@Override
		public Optional<Stage> sourceOf(Stage stage) {
			return stage.ordinal() == 0 ? Optional.empty() : Optional.of(workflow.stages[stage.ordinal() - 1]);
		}

		@Override
		public double integrateFirstStageThroughput(Plan plan, Instant startingPoint, Instant endingPoint) {
			return throughputStrategy.calcCombinedThroughput(plan, workflow.stages, startingPoint, endingPoint);
		}

		static WorkflowBehaviour from(final Workflow workflow) {
			return WorkflowBehaviour.valueOf(workflow.name());
		}

		static {
			assert Arrays.stream(WorkflowBehaviour.values()).map(WorkflowBehaviour::name).collect(Collectors.toSet())
					.equals(Arrays.stream(Workflow.values()).map(Workflow::name).collect(Collectors.toSet()));
		}
	}
}
