/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper for mapping keys to timestamps for expiration.
 */
public class ExpirationMap<V> {

	private final ConcurrentSkipListMap<Long, Collection<V>> multiMap = new ConcurrentSkipListMap<>();
	private final ConcurrentHashMap<V, Long> inverse = new ConcurrentHashMap<>();
	private final long durationMillis;
	private final int maxSize;
	private final long expirationFrequency;
	private final AtomicLong lastExpiration = new AtomicLong();

	public ExpirationMap(long durationMillis) {
		this(durationMillis, -1, 10_000L);
	}

	public ExpirationMap(long durationMillis, int maxSize, long expirationFrequency) {
		this.durationMillis = durationMillis;
		this.maxSize = maxSize;
		this.expirationFrequency = expirationFrequency;
	}

	/**
	 * Check and collect all values which have expired.
	 *
	 * @return a {@link Set} of expired values
	 */
	public @NotNull Set<V> doExpiration() {
		long now = System.currentTimeMillis();

		if (lastExpiration.get() >= now - expirationFrequency) {
			return Collections.emptySet();
		}

		lastExpiration.set(now);

		Iterator<Map.Entry<Long, Collection<V>>> iterator = multiMap.descendingMap().entrySet().iterator();
		int count = 0;
		Set<V> values = new HashSet<>();

		while (iterator.hasNext()) {
			Map.Entry<Long, Collection<V>> next = iterator.next();

			if (next.getKey() > now && (count += next.getValue().size()) <= maxSize) {
				continue;
			}

			values.addAll(next.getValue());

			for (V key : next.getValue()) {
				inverse.remove(key);
			}

			iterator.remove();
		}

		return values;
	}

	/**
	 * Adds or updates a value's expiration.
	 *
	 * @param value the value to add
	 */
	public void add(V value) {
		// Potential (slight) optimization: Bucket to nearest expiration frequency
		// Would need a max frequency cap.
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
