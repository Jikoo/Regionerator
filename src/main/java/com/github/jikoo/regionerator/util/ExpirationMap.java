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
public class ExpirationMap<K> {

	private final ConcurrentSkipListMap<Long, Collection<K>> multiMap = new ConcurrentSkipListMap<>();
	private final ConcurrentHashMap<K, Long> inverse = new ConcurrentHashMap<>();
	private final long durationMillis;

	public ExpirationMap(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public Set<K> doExpiration() {

		Set<K> keys = new HashSet<>();

		ConcurrentNavigableMap<Long, Collection<K>> headMap = multiMap.headMap(System.currentTimeMillis(), true);

		for (Collection<K> collection : headMap.values()) {
			keys.addAll(collection);
		}

		headMap.clear();

		for (K key :  keys) {
			inverse.remove(key);
		}

		return keys;
	}

	public void add(K key) {
		Long timestamp = System.currentTimeMillis() + durationMillis;
		Long replacedTimestamp = inverse.replace(key, timestamp);

		if (replacedTimestamp != null) {
			// Key was already set to expire. Remove old expiration entry.
			multiMap.computeIfPresent(replacedTimestamp, (oldTimestamp, collection) -> {
				collection.remove(key);
				return collection.isEmpty() ? null : collection;
			});
		}

		// Add new expiration entry.
		multiMap.compute(timestamp, (newTimestamp, collection) -> {
			if (collection == null) {
				collection = new HashSet<>();
			}
			collection.add(key);
			return collection;
		});
	}

	public void remove(K key) {
		Long removedTimestamp = inverse.remove(key);
		if (removedTimestamp != null) {
			multiMap.computeIfPresent(removedTimestamp, (oldTimestamp, collection) -> {
				collection.remove(key);
				return collection.isEmpty() ? null : collection;
			});
		}
	}

	public boolean contains(K key) {
		return inverse.containsKey(key);
	}

	public void clear() {
		multiMap.clear();
		inverse.clear();
	}

}
