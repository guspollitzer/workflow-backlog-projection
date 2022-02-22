package design.global;

import design.global.ForecastSupplier.Forecast;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class ForecastSupplier implements BiFunction<Workflow, Instant, Forecast> {

	@Override
	public Forecast apply(Workflow workflow, Instant startingDate) {
		throw new AssertionError("not implemented");
	}

	public record Forecast(Trajectory sellsPerHourTrajectory) implements design.strategymethod.BacklogProjectionCalculator.Forecast, design.templatemethod.BacklogProjectionCalculator.Forecast {
		@Override
		public double integrateForecastedInput(Instant from, Instant to) {
			return sellsPerHourTrajectory.integrate(from, to, TimeUnit.HOURS);
		}
	}
}
