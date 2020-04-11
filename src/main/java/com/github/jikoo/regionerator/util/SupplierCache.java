package com.github.jikoo.regionerator.util;

import java.lang.ref.SoftReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A Supplier wrapper that temporarily caches values. Designed for values that are costly to calculate.
 *
 * @param <T>
 */
public class SupplierCache<T> {

	final Supplier<T> supplier;
	final long cacheDuration;
	SoftReference<T> value;
	long lastUpdate;

	public SupplierCache(Supplier<T> supplier, long duration, TimeUnit timeUnit) {
		this.supplier = supplier;
		cacheDuration = TimeUnit.MILLISECONDS.convert(duration, timeUnit);
	}

	public T get() {
		if (value == null || value.isEnqueued() || lastUpdate <= System.currentTimeMillis() - cacheDuration) {
			T obj = supplier.get();
			value = new SoftReference<>(obj);
			lastUpdate = System.currentTimeMillis();
			return obj;
		}
		return value.get();
	}

}
