package design.strategymethod;

import java.time.Duration;

interface SlaDispersionStrategies {

	static float[] maximizeProductivity(Duration[] nextSlasDistances) {
		return new float[]{0.5f, 0.4f, 0.1f};
	}

	static float[] minimizeProcessingTime(Duration[] nextSlasDistances) {
		return new float[]{1.0f};
	}
}
