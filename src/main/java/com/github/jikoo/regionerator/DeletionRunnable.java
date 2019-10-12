package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.util.AnvilRegion;
import com.github.jikoo.regionerator.util.Pair;
import com.github.jikoo.regionerator.util.Region;
import java.io.File;
import java.util.ArrayList;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runnable for checking and deleting chunks and regions.
 *
 * @author Jikoo
 */
public class DeletionRunnable extends BukkitRunnable {

	private static final String STATS_FORMAT = "%s: %s/%s, deleted %s regions, %s chunks";

	private final Regionerator plugin;
	private final World world;
	private final Region[] regions;
	private final ArrayList<Pair<Integer, Integer>> regionChunks = new ArrayList<>();
	private long nextRun = Long.MAX_VALUE;
	private int index = -1, chunksDeleted = 0, regionsDeleted = 0;

	DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		File regionDir = new File(world.getWorldFolder(), "region");
		File[] regionFiles = regionDir.listFiles((dir, name) -> AnvilRegion.ANVIL_REGION.matcher(name).matches());

		if (regionFiles != null) {
			regions = new Region[regionFiles.length];
			for (int i = 0; i < regionFiles.length; i++) {
				regions[i] = AnvilRegion.parseAnvilRegion(world, regionFiles[i]);
			}
		} else {
			regions = new Region[0];
		}
	}

	@Override
	public void run() {
		if (index >= regions.length) {
			plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
			nextRun = System.currentTimeMillis() + plugin.getMillisecondsBetweenDeletionCycles();
			this.cancel();
			return;
		}

		if (index == -1 || regions[index].hasDeletionRun()) {
			// Only pause before next region - who knows how long we'll be paused for?
			if (plugin.isPaused()) {
				return;
			}

			++index;
			plugin.debug(DebugLevel.HIGH, () -> String.format("Checking %s:%s (%s/%s)",
					world.getName(), regions[index].getRegionFile().getName(), index, regions.length));
		}

		if (!regions[index].isCompletelyChecked()) {
			regions[index].checkChunks(plugin);
			if (plugin.getChunksPerDeletionCheck() <= 1024) {
				/*
				 * Deletion is by far the worst offender for lag. It has to be done on the main thread
				 * or we may risk corruption, so to combat any issues we dedicate an entire run cycle
				 * to deletion unless the user has configured the plugin to check more than an entire
				 * region per run.
				 */
				return;
			}
		}

		if (regions[index].isCompletelyChecked()) {
			int cycleDeletedChunks = regions[index].deleteChunks(plugin);
			if (cycleDeletedChunks == 1024) {
				++regionsDeleted;
			} else {
				chunksDeleted += cycleDeletedChunks;
			}
		}

		if (index > 0 && index % 20 == 0) {
			plugin.debug(DebugLevel.LOW, this::getRunStats);
		}
	}

	String getRunStats() {
		return String.format(STATS_FORMAT, world.getName(), index, regions.length, regionsDeleted, chunksDeleted);
	}

	long getNextRun() {
		return nextRun;
	}

}
