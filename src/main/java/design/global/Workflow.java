package design.global;

import fj.data.List;

public enum Workflow {
	inbound(false, InboundStage.values()),
	outboundDirect(true, OutboundDirectStage.values()),
	outboundWall(true, OutboundWallStage.values());

	public final boolean hasWaving;
	public final Stage[] stages;
	public List<Stage> processingStages;

	Workflow(final boolean hasWaving, final Stage[] stages) {
		this.hasWaving = hasWaving;
		this.stages = stages;
		var list = List.arrayList(this.stages);
		this.processingStages = this.hasWaving ? list.tail() : list;
	}

	public interface Stage {
		String name();
		int ordinal();
		Workflow workflow();
	}

	public enum InboundStage implements Stage {
		checkIn, putAway;
		public Workflow workflow() {
			return inbound;
		}
	}

	public enum OutboundDirectStage implements Stage {
		wavingDirect, pickingDirect, packingDirect;

		@Override
		public Workflow workflow() {
			return outboundDirect;
		}
	}

	public enum OutboundWallStage implements Stage {
		wavingForWall, pickingForWall, walling, packingWalled;

		@Override
		public Workflow workflow() {
			return outboundWall;
		}
	}

}
