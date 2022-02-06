package design.strategymethod;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Workflow {
	inbound(InboundStage.values()),
	outboundDirect(OutboundDirectStage.values()),
	outboundWall(OutboundWallStage.values());

	public final Stage[] stages;

	public interface Stage {
		String name();
		int ordinal();
		int workflowIndex();
//		default int compareTo(Stage o) {
//			return ordinal() - o.ordinal();
//		}
	}

	 enum InboundStage implements Stage {
		checkIn, putAway;
		public int workflowIndex() {
			return inbound.ordinal();
		}
	}

	enum OutboundDirectStage implements Stage {
		wavingDirect, pickingDirect, packingDirect;

		@Override
		public int workflowIndex() {
			return outboundDirect.ordinal();
		}
	}

	enum OutboundWallStage implements Stage {
		wavingForWall, pickingForWall, walling, packingWalled;

		@Override
		public int workflowIndex() {
			return outboundWall.ordinal();
		}
	}

}
