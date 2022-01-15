package design.strategymethod;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Workflow {
	inbound(InboundStages.values()),
	outboundDirect(OutboundDirectStages.values()),
	outboundWall(OutboundWallStages.values());

	public final Stage[] stages;

	public interface Stage {
		String name();
		int ordinal();
	}

	enum InboundStages implements Stage {
		checkIn, putAway
	}

	enum OutboundDirectStages implements Stage {
		pickingDirect, packingDirect
	}

	enum OutboundWallStages implements Stage {
		pickingForWall, walling, packingWalled
	}

}
