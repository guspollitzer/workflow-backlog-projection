package design.global;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class ImmutableEnumMap<K extends Enum<K>, V> implements ImmutableMap<K, V> {

  private final int size;
  private final V[] entries;

  private ImmutableEnumMap(final int size, final V[] entries) {
	this.size = size;
	this.entries = entries;
  }

  @Override
  public int size() {
	return this.size;
  }

  @Override
  public V get(final K k) {
	return k.ordinal() < entries.length ? entries[k.ordinal()] : null;
  }

  @Override
  public ImmutableEnumMap<K, V> put(K k, V v) {
	final int newSize;
	final int newLength;
	if (k.ordinal() >= this.entries.length) {
	  newSize = this.size + 1;
	  newLength = 1 + k.ordinal();
	} else {
	  newSize = this.entries[k.ordinal()] == null ? this.size + 1 : this.size;
	  newLength = this.entries.length;
	}
	var newEntries = Arrays.copyOf(this.entries, newLength);
	newEntries[k.ordinal()] = v;
	return new ImmutableEnumMap<>(newSize, newEntries);
  }

  public ImmutableEnumMap<K, V> putAll(ImmutableEnumMap<K, V> other) {
	if (this.size == 0) {
	  return other;
	} else if (other.size == 0) {
	  return this;
	} else {
	  final var newLength = Math.max(this.entries.length, other.entries.length);
	  final var newEntries = Arrays.copyOf(other.entries, newLength);
	  var newSize = other.size;
	  for (var i = 0; i < this.entries.length; ++i) {
		if (newEntries[i] == null && this.entries[i] != null) {
		  newEntries[i] = this.entries[i];
		  newSize += 1;
		}
	  }
	  return new ImmutableEnumMap<>(newSize, newEntries);
	}
  }

  @Override
  public <W> ImmutableEnumMap<K, W> mapValues(final Function<V, W> f) {
	final W[] newEntries = createEntries(this.entries.length);
	for (var i = 0; i < newEntries.length; ++i) {
	  if (this.entries[i] != null) {
		newEntries[i] = f.apply(this.entries[i]);
	  }
	}
	return new ImmutableEnumMap<>(this.size, newEntries);
  }

  public Stream<ImmutableMap.Entry<K, V>> toStream(final K[] enumValues) {
	var builder = Stream.<ImmutableMap.Entry<K, V>>builder();
	for (var i = 0; i < this.entries.length; ++i) {
	  builder.accept(new EntryImpl<>(enumValues[i], this.entries[i]));
	}
	return builder.build();
  }

  public static <K extends Enum<K>, V> ImmutableEnumMap<K, V> of() {
	return new ImmutableEnumMap<>(0, createEntries(0));
  }

  public static <K extends Enum<K>, V> ImmutableEnumMap<K, V> of(final K k1, final V v1) {
	V[] entries = createEntries(1 + k1.ordinal());
	entries[k1.ordinal()] = v1;
	return new ImmutableEnumMap<>(1, entries);
  }

  public static <K extends Enum<K>, V> ImmutableEnumMap<K, V> of(final K k1, final V v1, final K k2, final V v2) {
	var length = 1 + Math.max(k1.ordinal(), k2.ordinal());
	V[] entries = createEntries(length);
	entries[k1.ordinal()] = v1;
	entries[k2.ordinal()] = v2;
	return new ImmutableEnumMap<>(2, entries);
  }

  public static <K extends Enum<K>, V> ImmutableEnumMap<K, V> of(final K k1, final V v1, final K k2, final V v2, final K k3, final V v3) {
	var length = 1 + Math.max(k1.ordinal(), Math.max(k2.ordinal(), k3.ordinal()));
	V[] entries = createEntries(length);
	entries[k1.ordinal()] = v1;
	entries[k2.ordinal()] = v2;
	entries[k3.ordinal()] = v3;
	return new ImmutableEnumMap<>(3, entries);
  }

  public static <K extends Enum<K>, V> Builder<K, V> builder(final K[] enumValues) {
	return new Builder<>(enumValues);
  }

  private static <W> W[] createEntries(final int length) {
	return (W[]) new Object[length];
  }

  public static class Builder<K extends Enum<K>, V> {
	final K[] enumValues;
	final V[] entries;

	private Builder(final K[] enumValues) {
	  this.enumValues = enumValues;
	  this.entries = createEntries(enumValues.length);
	}

	public Builder<K, V> add(final K k, final V v) {
	  this.entries[k.ordinal()] = v;
	  return this;
	}

	public Optional<V> getSome(final K k) {
	  return Optional.ofNullable(this.entries[k.ordinal()]);
	}

	public ImmutableEnumMap<K, V> build() {
	  var size = 0;
	  for (V entry : entries) {
		if (entry != null) {
		  size += 1;
		}
	  }
	  return new ImmutableEnumMap<>(size, entries);
	}
  }

  public static <K extends Enum<K>, V> Collector<ImmutableMap.Entry<K, V>, Builder<K, V>, ImmutableEnumMap<K, V>> buildStreamBinaryCollector(
	  final K[] enumValues,
	  final BinaryOperator<V> binaryOperator
  ) {
	return new Collector<>() {
	  static final Set<Characteristics> CHARACTERISTICS = Set.of(Characteristics.UNORDERED);

	  @Override
	  public Supplier<Builder<K, V>> supplier() {
		return () -> builder(enumValues);
	  }

	  @Override
	  public BiConsumer<Builder<K, V>, ImmutableMap.Entry<K, V>> accumulator() {
		return (builder, entry) -> {
		  var newValue = builder.getSome(entry.key())
			  .map(previous -> binaryOperator.apply(previous, entry.value()))
			  .orElse(entry.value());
		  builder.add(entry.key(), newValue);
		};
	  }

	  @Override
	  public BinaryOperator<Builder<K, V>> combiner() {
		return (a, b) -> {
		  for (var i = 0; i < a.entries.length; ++i) {
			if ( a.entries[i] != null) {
			  if (b.entries[i] == null) {
				b.entries[i] = a.entries[i];
			  } else {
				b.entries[i] = binaryOperator.apply(a.entries[i], b.entries[i]);
			  }
			}
		  }
		  return b;
		};
	  }

	  @Override
	  public Function<Builder<K, V>, ImmutableEnumMap<K, V>> finisher() {
		return Builder::build;
	  }

	  @Override
	  public Set<Characteristics> characteristics() {
		return CHARACTERISTICS;
	  }
	};
  }
}
