package com.github.jikoo.regionerator.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A cache system designed to load values automatically and minimize write operations by expiring values in batches.
 * @param <K> the key type
 * @param <V> the value stored
 */
public class BatchExpirationLoadingCache<K, V> {

	private final Map<K, V> internal = new ConcurrentHashMap<>();
	private final Queue<K> expired = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean expirationQueued = new AtomicBoolean();
	private final ExpirationMap<K> expirationMap;
	private final Function<K, V> load;
	private final Consumer<Collection<V>> expirationConsumer;
	private final int maxBatchSize;
	private final long batchDelay;

	public BatchExpirationLoadingCache(final long retention, @NotNull final Function<K, V> load,
		@NotNull final Consumer<Collection<V>> expirationConsumer) {
		this(retention, load, expirationConsumer, 100, 5000);
	}

	public BatchExpirationLoadingCache(final long retention, @NotNull final Function<K, V> load,
			@NotNull final Consumer<Collection<V>> expirationConsumer, int maxBatchSize, long batchDelay) {
		expirationMap = new ExpirationMap<>(retention);

		// Wrap load function to update expiration when used
		this.load = key -> {
			V value = load.apply(key);
			if (value != null) {
				// Only update expiration when loading yields a result.
				expirationMap.add(key);
			}
			checkExpiration();
			return value;
		};

		this.expirationConsumer = expirationConsumer;
		this.maxBatchSize = maxBatchSize;
		this.batchDelay = batchDelay;
	}

	@NotNull
	public CompletableFuture<V> get(@NotNull K key) {
		if (internal.containsKey(key)) {
			return CompletableFuture.completedFuture(getIfPresent(key));
		}
		return CompletableFuture.supplyAsync(() -> load.apply(key));
	}

	@Nullable
	public V getIfPresent(@NotNull K key) {
		V value = internal.get(key);
		if (value != null) {
			expirationMap.add(key);
		}
		checkExpiration();
		return value;
	}

	/**
	 * Insert a value into the cache.
	 *
	 * @param key the key associated with the value
	 * @param value the value to be inserted
	 */
	public void put(@NotNull K key, @NotNull V value) {
		internal.put(key, value);
		expirationMap.add(key);
	}

	/**
	 * Remove an existing cached mapping.
	 *
	 * @param key the key whose mapping is to be removed
	 */
	public void remove(@NotNull K key) {
		if (internal.remove(key) != null) {
			expirationMap.remove(key);
			expired.remove(key);
		}
	}

	/**
	 * Invalidate all expired keys that are not considered in use.
	 */
	private void checkExpiration() {
		if (!expirationQueued.get()) {
			expired.addAll(expirationMap.doExpiration());
		}

		if (expired.isEmpty() || !expirationQueued.compareAndSet(false, true)) {
			return;
		}

		new Thread(
				() -> {
					// If not yet at maximum batch size, wait before
					if (expired.size() < maxBatchSize) {
						try {
							Thread.sleep(batchDelay);
							expired.addAll(expirationMap.doExpiration());
						} catch (InterruptedException e) {
							System.err.println("Encountered exception while attempting to await larger batch:");
							e.printStackTrace();
						}
					}

					ArrayList<V> expiredValues = new ArrayList<>();
					for (int i = 0; i < maxBatchSize && !expired.isEmpty(); ++i) {
						K expiredKey = expired.poll();
						// Batch expire even if re-added to expiration queue - writes vs reliability.
						V value = expirationMap.contains(expiredKey) ? internal.get(expiredKey) : internal.remove(expiredKey);
						if (value != null) {
							expiredValues.add(value);
						}
					}

					expirationConsumer.accept(expiredValues);
					expirationQueued.set(false);

					// Re-run expiration check to queue next batch if necessary
					checkExpiration();
				}, "BatchExpiration"
		).start();
	}

	public void lazyExpireAll() {
		expired.addAll(internal.keySet());
		checkExpiration();
	}

	public void expireAll() {
		expirationConsumer.accept(internal.values());
		internal.clear();
		expirationMap.clear();
	}

}
