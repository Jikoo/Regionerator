package com.github.jikoo.regionerator.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Wrapper for mapping keys to timestamps for expiration.
 *
 * @author Jikoo
 */
public class ExpirationMap<V> {

	private final ConcurrentSkipListMap<Long, Collection<V>> multiMap = new ConcurrentSkipListMap<>();
	private final ConcurrentHashMap<V, Long> inverse = new ConcurrentHashMap<>();
	private final long durationMillis;

	public ExpirationMap(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	/**
	 * Check and collect all values which have expired.
	 *
	 * @return a {@link Set} of expired values
	 */
	public Set<V> doExpiration() {

		Set<V> values = new HashSet<>();

		ConcurrentNavigableMap<Long, Collection<V>> headMap = multiMap.headMap(System.currentTimeMillis(), true);

		for (Collection<V> collection : headMap.values()) {
			values.addAll(collection);
		}

		headMap.clear();

		for (V key :  values) {
			inverse.remove(key);
		}

		return values;
	}

	/**
	 * Adds or updates a value's expiration.
	 *
	 * @param value the value to add
	 */
	public void add(V value) {
		Long timestamp = System.currentTimeMillis() + durationMillis;
		Long replacedTimestamp = inverse.replace(value, timestamp);

		if (replacedTimestamp != null) {
			// Key was already set to expire. Remove old expiration entry.
			multiMap.computeIfPresent(replacedTimestamp, (oldTimestamp, collection) -> {
				collection.remove(value);
				return collection.isEmpty() ? null : collection;
			});
		}

		// Add new expiration entry.
		multiMap.compute(timestamp, (newTimestamp, collection) -> {
			if (collection == null) {
				collection = new HashSet<>();
			}
			collection.add(value);
			return collection;
		});
	}

	/**
	 * Removes a mapped value.
	 *
	 * @param value the value to remove
	 */
	public void remove(V value) {
		Long removedTimestamp = inverse.remove(value);
		if (removedTimestamp != null) {
			multiMap.computeIfPresent(removedTimestamp, (oldTimestamp, collection) -> {
				collection.remove(value);
				return collection.isEmpty() ? null : collection;
			});
		}
	}

	/**
	 * Checks if the specified value is currently mapped.
	 *
	 * @param value the value to check for
	 * @return true if the value is present
	 */
	public boolean contains(V value) {
		return inverse.containsKey(value);
	}

	/**
	 * Clears all mapped values.
	 */
	public void clear() {
		multiMap.clear();
		inverse.clear();
	}

}
