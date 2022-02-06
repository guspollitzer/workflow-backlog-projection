package design.strategymethod;

import design.strategymethod.Workflow.OutboundWallStage;

import fj.data.List;
import fj.data.TreeMap;

import java.time.Instant;

import static design.strategymethod.BacklogProjectionCalculator.*;

public interface WorkflowTrajectoryBuilderStrategies {

	static WorkflowTrajectoryStep dammedSourceFirstStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowBacklog startingBacklog,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider
	) {
		var wavingInitialPile = startingBacklog.getHeapAt(OutboundWallStage.wavingForWall);
		var wavingPower = staffingPlan.integrateThroughputOf(OutboundWallStage.wavingForWall, stepStartingDate, nextDeadline);

		var pickingInitialPile = startingBacklog.getHeapAt(OutboundWallStage.pickingForWall);
		var pickingPower = staffingPlan.integrateThroughputOf(OutboundWallStage.pickingForWall, stepStartingDate, nextDeadline);

		var wallingInitialPile = startingBacklog.getHeapAt(OutboundWallStage.walling);
		var wallingPower = staffingPlan.integrateThroughputOf(OutboundWallStage.walling, stepStartingDate, nextDeadline);

		var packingInitialPile = startingBacklog.getHeapAt(OutboundWallStage.packingWalled);
		var packingPower = staffingPlan.integrateThroughputOf(OutboundWallStage.packingWalled, stepStartingDate, nextDeadline);


		var readyToPickBounds = backlogBoundsDecider.getMinAndMax(OutboundWallStage.pickingForWall, stepStartingDate);
		var readyToPickDesiredBufferSizeInTime = (readyToPickBounds._1().plus(readyToPickBounds._2())).dividedBy(2);
		var readyToPickDesiredBufferSizeInQuantity = staffingPlan.integrateThroughputOf(OutboundWallStage.pickingForWall, stepStartingDate, stepStartingDate.plus(readyToPickDesiredBufferSizeInTime));
		var wavedTotalQuantity = (int)(pickingPower + readyToPickDesiredBufferSizeInQuantity) - pickingInitialPile.total();
		var wavedHeap = processedSlasDistributionDecider
				.decide(OutboundWallStage.wavingForWall, wavingInitialPile, wavedTotalQuantity, stepStartingDate, nextDeadline);
		var wavingStep = new StageTrajectoryStep(OutboundWallStage.wavingForWall, wavingInitialPile, wavedHeap);



		// TODO
		var wallingStep = new StageTrajectoryStep(
				OutboundWallStage.walling,
				startingBacklog.getHeapAt(OutboundWallStage.walling),
				staffingPlan.integrateThroughputOf(OutboundWallStage.walling, stepStartingDate, nextDeadline)
		);
		var packingStep = new StageTrajectoryStep(
				OutboundWallStage.packingWalled,
				packingInitialPile,
				processedSlasDistributionDecider.decide(OutboundWallStage.packingWalled, packingInitialPile, packingPower, stepStartingDate, nextDeadline)
		);
		return new WorkflowTrajectoryStep(startingDate, List.arrayList(wavingStep, ...));
	}

	static WorkflowTrajectoryStep dammedSourceNextStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowTrajectoryStep previousWorkflowStep,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider,
			UpstreamThroughputTrajectory upstreamThroughputTrajectory
	) {

		return null;
	}

	static WorkflowTrajectoryStep inboundFirstStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowBacklog startingBacklog,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider
	) {
		return null;
	}


	static WorkflowTrajectoryStep inboundNextStepBuilder(
			Instant stepStartingDate,
			Instant nextDeadline,
			TreeMap<Instant, List<Sla>> nextSlasByDeadline,
			WorkflowTrajectoryStep previousWorkflowStep,
			StaffingPlan staffingPlan,
			ProcessedSlasDistributionDecider processedSlasDistributionDecider,
			BacklogBoundsDecider backlogBoundsDecider,
			UpstreamThroughputTrajectory upstreamThroughputTrajectory
	) {
		return null;
	}


}
