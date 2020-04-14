package com.github.jikoo.regionerator.util;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A Supplier wrapper that temporarily caches values. Designed for values that are costly to calculate.
 *
 * @param <T>
 */
public class SupplierCache<T> {

	private final Supplier<T> supplier;
	private final long cacheDuration;
	private T value;
	private long lastUpdate;

	public SupplierCache(Supplier<T> supplier, long duration, TimeUnit timeUnit) {
		this.supplier = supplier;
		cacheDuration = TimeUnit.MILLISECONDS.convert(duration, timeUnit);
	}

	public T get() {
		if (value == null || lastUpdate <= System.currentTimeMillis() - cacheDuration) {
			value = supplier.get();
			lastUpdate = System.currentTimeMillis();
		}
		return value;
	}

}
