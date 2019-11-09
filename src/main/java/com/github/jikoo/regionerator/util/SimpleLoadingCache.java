package com.github.jikoo.regionerator.util;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleLoadingCache<K, V> {

	private final Map<K, V> internal;
	private final Multimap<Long, K> expiry;
	private final Function<K, V> load;
	private final Consumer<Collection<V>> postRemoval;

	/**
	 * Constructs a Cache with the specified retention duration, loading function, and post-removal call.
	 * @param retention duration after which keys are automatically invalidated if not in use
	 * @param load Function used to load values for keys
	 * @param postRemoval Consumer used to perform any operations required when a key is invalidated
	 */
	public SimpleLoadingCache(final long retention, Function<K, V> load,
			final Consumer<Collection<V>> postRemoval) {

		this.internal = new HashMap<>();
		this.expiry = TreeMultimap.create(Long::compareTo, (k1, k2) -> Objects.equals(k1, k2) ? 0 : 1);

		// Wrap load function to update expiration when used
		this.load = key -> {
			V value = load.apply(key);
			if (value != null) {
				// Only update expiration when loading yields a result.
				expiry.put(System.currentTimeMillis() + retention, key);
			}
			return value;
		};

		this.postRemoval = postRemoval;
	}

	/**
	 * Returns the value to which the specified key is mapped, or null if no value is mapped for the key.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if no value is mapped for the key
	 */
	public V getIfPresent(final K key) {
		// Run lazy check to clean cache
		this.lazyCheck();

		synchronized (this.internal) {
			return this.internal.get(key);
		}
	}

	/**
	 * Returns the value to which the specified key is mapped using the loading function if necessary.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped
	 */
	public V get(final K key) {
		// Run lazy check to clean cache
		this.lazyCheck();

		synchronized (this.internal) {
			return this.internal.computeIfAbsent(key, this.load);
		}
	}

	/**
	 * Returns true if the specified key is mapped to a value.
	 *
	 * @param key key to check if a mapping exists for
	 * @return true if a mapping exists for the specified key
	 */
	public boolean containsKey(final K key) {
		// Run lazy check to clean cache
		this.lazyCheck();

		synchronized (this.internal) {
			return this.internal.containsKey(key);
		}
	}

	/**
	 * Invalidate a key.
	 * <p>
	 * N.B. Respects modifications made by post-removal call.
	 *
	 * @param key key to invalidate
	 */
	public void invalidate(final K key) {
		// Run lazy check to clean cache
		this.lazyCheck();

		synchronized (this.internal) {

			// Remove stored object
			V value = this.internal.remove(key);

			if (value == null) {
				return;
			}

			postRemoval.accept(Collections.singleton(value));
		}
	}

	/**
	 * Invalidate all keys.
	 * <p>
	 * N.B. Ignores any modifications made by post-removal call.
	 */
	public void invalidateAll() {
		synchronized (this.internal) {
			postRemoval.accept(internal.values());
			this.expiry.clear();
			this.internal.clear();
		}
	}

	/**
	 * Invalidate all expired keys that are not considered in use.
	 */
	private void lazyCheck() {
		long now = System.currentTimeMillis();
		synchronized (this.internal) {
			List<V> expired = new ArrayList<>();
			for (Iterator<Map.Entry<Long, K>> iterator = this.expiry.entries().iterator(); iterator.hasNext();) {
				Map.Entry<Long, K> entry = iterator.next();

				if (entry.getKey() > now) {
					break;
				}

				iterator.remove();

				V value = this.internal.remove(entry.getValue());

				if (value == null) {
					continue;
				}

				expired.add(value);
			}

			if (this.postRemoval != null) {
				postRemoval.accept(expired);
			}
		}
	}

}
