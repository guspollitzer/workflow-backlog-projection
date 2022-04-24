package design.global;

import lombok.RequiredArgsConstructor;

import fj.data.List;

import java.util.Arrays;

import static design.global.Workflow.QueueType.FEFO;
import static design.global.Workflow.QueueType.FIFO;

public enum Workflow {
  inbound,
  outboundDirect,
  outboundWall;

  public final Stage[] stages;
  public List<Stage> processingStages;

  Workflow() {
	this.stages = Arrays.stream(Stage.values()).filter(stage -> stage.workflow == this).toArray(Stage[]::new);
	this.processingStages = List.arrayList(this.stages).filter(stage -> stage.isHumanPowered);
  }

  public enum QueueType {
	FIFO, FEFO
  }

  @RequiredArgsConstructor
  public enum Stage {
	checkIn(inbound, true, FIFO),
	putAway(inbound, true, FIFO),
	wavingDirect(outboundDirect,false, FEFO),
	pickingDirect(outboundDirect,true, FIFO),
	packingDirect(outboundDirect,true, FIFO),
	wavingForWall(outboundWall, false, FEFO),
	pickingForWall(outboundWall, true, FIFO),
	walling(outboundWall, true, FIFO),
	packingWalled(outboundWall, true, FIFO);

	private final Workflow workflow;
	private final boolean isHumanPowered;
	private final QueueType inQueueType;

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
  }
}
