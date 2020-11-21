package com.github.jikoo.regionerator.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class DistributedTask<T> {

	private final Set<T> allContent = new HashSet<>();
	private final Set<T>[] distributedContent;
	private final Consumer<Collection<T>> consumer;
	private int taskId = -1;
	private int currentIndex = 0;

	public DistributedTask(long period, @NotNull TimeUnit periodUnit, @NotNull Consumer<Collection<T>> consumer) {
		int totalTicks = (int) (TimeUnit.MILLISECONDS.convert(period, periodUnit) / 50);
		if (totalTicks < 2) throw new IllegalArgumentException("Useless DistributedTask");

		//noinspection unchecked
		distributedContent = new Set[totalTicks];
		for (int index = 0; index < distributedContent.length; ++index) {
			distributedContent[index] = new HashSet<>();
		}

		this.consumer = consumer;
	}

	public void add(@NotNull T content) {
		if (!allContent.add(content)) {
			return;
		}

		int lowestSize = Integer.MAX_VALUE, lowestIndex = 0;
		for (int index = 0; index < distributedContent.length; ++index) {
			int size = distributedContent[index].size();
			if (size < lowestSize) {
				lowestSize = size;
				lowestIndex = index;
			}
		}

		distributedContent[lowestIndex].add(content);
	}

	public void remove(@NotNull T content) {
		if (!allContent.remove(content)) {
			return;
		}

		for (Set<T> contentPartition : distributedContent) {
			contentPartition.remove(content);
		}
	}

	private void run() {
		consumer.accept(Collections.unmodifiableSet(distributedContent[currentIndex]));
		++currentIndex;
		if (currentIndex >= distributedContent.length) {
			currentIndex = 0;
		}
	}

	public DistributedTask<T> schedule(Plugin plugin) {
		taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::run, 1, 1);
		return this;
	}

	public void cancel(Plugin plugin) {
		if (taskId != -1) {
			plugin.getServer().getScheduler().cancelTask(taskId);
			taskId = -1;
		}
	}

}
