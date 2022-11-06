package design.global;

import fj.data.List;

import java.util.Arrays;

import static design.global.Workflow.QueueType.FEFO;
import static design.global.Workflow.QueueType.FIFO;

public enum Workflow {
  inbound,
  outbound;

  public final Stage[] stages;
  public final List<Stage> processingStages;
  public final List<Stage> finalStages;

  Workflow() {
	this.stages = Arrays.stream(Stage.values())
		.filter(stage -> stage.workflow == this)
		.toArray(Stage[]::new);
	final var stagesList = List.arrayList(this.stages);
	this.processingStages = stagesList
		.filter(stage -> stage.isHumanPowered);
	this.finalStages = stagesList
		.filter(candidate -> Arrays.stream(stages).noneMatch(stage -> stage.previousStage == stage));
  }

  public enum QueueType {
	FIFO, FEFO
  }

  public enum Stage {
	checkIn(inbound, true, FIFO, null),
	putAway(inbound, true, FIFO, checkIn),
	waving(outbound,false, FEFO, null),
	picking(outbound,true, FIFO, waving),
	packingDirect(outbound, true, FIFO, picking),
	walling(outbound, true, FIFO, picking),
	packingWalled(outbound,  true, FIFO, walling);

	private final Workflow workflow;
	private final boolean isHumanPowered;
	private final QueueType inQueueType;
	private final Stage previousStage;

	Stage(Workflow workflow, boolean isHumanPowered, QueueType inQueueType, Stage previousStage) {
	  this.workflow = workflow;
	  this.isHumanPowered = isHumanPowered;
	  this.inQueueType = inQueueType;
	  this.previousStage = previousStage;
	}

	public Workflow workflow() {
	  return this.workflow;
	}
	public boolean isHumanPowered() {
	  return this.isHumanPowered;
	}
	public QueueType inQueueType() {
	  return this.inQueueType;
	}
	public QueueType outQueueType() {
	  return FIFO;
	}
	public Stage previousStage() {
	  return this.previousStage;
	}
	public Stage[] nextStages() {
	  return Arrays.stream(Stage.values()).filter(stage -> stage.previousStage == this).toArray(Stage[]::new);
	}
	public Stage firstStageOfBranch() {
	  if (
		  previousStage == null
		  || Arrays.stream(Stage.values()).anyMatch(stage -> stage != this && stage.previousStage == this.previousStage)
	  ) {
		return this;
	  } else {
		return this.previousStage.firstStageOfBranch();
	  }
	}
  }
}
