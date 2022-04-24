package design.global;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public record Trajectory(TreeMap<Instant, Long> map) {
  public double integrate(final Instant from, final Instant to, final TimeUnit timeUnit) {
	if (from.isAfter(to)) {
	  return -integrate(to, from, timeUnit);
	} else {
	  final var tail = map.tailMap(from, false);
	  final var higherEntry = tail.firstEntry();
	  final var floorEntry = map.floorEntry(from);
	  var value = floorEntry != null ? floorEntry.getValue() : higherEntry != null ? higherEntry.getValue() : 0;
	  long accum = 0;
	  var start = from;
	  for (var entry : tail.entrySet()) {
		if (to.isAfter(entry.getKey())) {
		  accum += value * ChronoUnit.MICROS.between(start, entry.getKey());
		  start = entry.getKey();
		  value = entry.getValue();
		} else {
		  break;
		}
	  }
	  accum += value * ChronoUnit.MICROS.between(start, to);
	  return accum / (double) timeUnit.toMicros(1);
	}
  }
}
