package design.global;

import design.global.Workflow.Stage;
import design.templatemethod.BacklogProjectionCalculator;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class StaffingPlanSupplier implements BiFunction<Workflow, Instant, StaffingPlanSupplier.Plan> {

	@Override
	public Plan apply(Workflow workflow, Instant instant) {
		throw new AssertionError("not implemented");
	}

	public record Plan(Map<Stage, Trajectory> unitsProcessedPerHourTrajectoriesByStage) implements design.strategymethod.BacklogProjectionUseCase.Plan, BacklogProjectionCalculator.Plan {

		@Override
		public double integrateThroughput(Stage stage, Instant from, Instant to) {
			return unitsProcessedPerHourTrajectoriesByStage.get(stage).integrate(from, to, TimeUnit.HOURS);
		}
	}
}
