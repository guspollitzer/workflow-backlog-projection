package design.backlogprojection;

import design.global.Workflow;
import design.global.Workflow.Stage;

import fj.data.List;

import java.time.Instant;

public class RequiredStaffEstimator {

	/**
	 * Specifies what the {@link RequiredStaffEstimator} needs to know about the trajectory of the downstream throughput.
	 */
	public interface DownstreamThroughputTrajectory {
		/**
		 * Calculates the definite integral of this scalar trajectory on the specified interval.
		 */
		long integral(Instant from, Instant to);
	}

	record RequiredStaffEstimation(List<TimeSlot> slots) {}

	record TimeSlot(Instant startingDate, Instant endingDate, List<StageSlot> stagesSlots) {}

	record StageSlot(Stage stage, int planned, int minimum, int idle) {}

	RequiredStaffEstimation estimate(
			Workflow workflow,
			DownstreamThroughputTrajectory downstreamThroughputTrajectory
	) {

		class LMC {
			RequiredStaffEstimation calc() {
				return null;
			}
		}

		// TODO

		return null;
	}

}
