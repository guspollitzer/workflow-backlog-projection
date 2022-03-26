package design.global;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
		boolean isHumanPowered();
		int ordinal();
		Workflow workflow();
	}

	@Getter
	@RequiredArgsConstructor
	public enum InboundStage implements Stage {
		checkIn(true), putAway(true);
		final boolean humanPowered;
		public Workflow workflow() {
			return inbound;
		}
	}

	@Getter
	@RequiredArgsConstructor
	public enum OutboundDirectStage implements Stage {
		wavingDirect(false), pickingDirect(true), packingDirect(true);
		final boolean humanPowered;

		@Override
		public Workflow workflow() {
			return outboundDirect;
		}
	}

	@Getter
	@RequiredArgsConstructor
	public enum OutboundWallStage implements Stage {
		wavingForWall(false), pickingForWall(true), walling(true), packingWalled(true);
		final boolean humanPowered;

		@Override
		public Workflow workflow() {
			return outboundWall;
		}
	}

}
