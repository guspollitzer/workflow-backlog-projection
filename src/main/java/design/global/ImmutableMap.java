package design.global;

import java.util.Optional;
import java.util.function.Function;

public interface ImmutableMap<K, V> {
  int size();

  V get(K k);

  ImmutableMap<K, V> put(K k, V v);

  <W> ImmutableMap<K, W> mapValues(final Function<V, W> f);

  default Optional<V> getSome(final K k) {
	return Optional.ofNullable(get(k));
  }

  default V getOrElse(final K k, final V def) {
	var got = get(k);
	return got == null ? def : got;
  }

  default boolean containsKey(final K k) {
	return get(k) != null;
  }

  interface Entry<K, V> {
	K key();
	V value();
  }

  record EntryImpl<K extends Enum<K>, V>(K key, V value) implements ImmutableMap.Entry<K, V> {}
}
