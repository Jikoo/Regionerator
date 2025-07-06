/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Runnable for checking and deleting chunks and regions.
 */
public class DeletionRunnable extends BukkitRunnable {

	private static final String STATS_FORMAT = "%s: checked %s, deleted %s regions & %s chunks";

	private final @NotNull Regionerator plugin;
	private final @NotNull Phaser phaser;
	private final WorldInfo world;
	private final AtomicLong nextRun = new AtomicLong(Long.MAX_VALUE);
	private final AtomicInteger regionCount = new AtomicInteger();
	private final AtomicInteger heavyChecks = new AtomicInteger();
	private final AtomicInteger regionsDeleted = new AtomicInteger();
	private final AtomicInteger chunksDeleted = new AtomicInteger();
	private long nextLogSecond = Instant.now().getEpochSecond() + 5;
	private int nextLogCount = 20;

	DeletionRunnable(@NotNull Regionerator plugin, @NotNull World world) {
		this.plugin = plugin;
		this.phaser = new Phaser(1);
		this.world = plugin.getWorldManager().getWorld(world);
	}

	@Override
	public void run() {
		world.getRegions().forEach(this::handleRegion);
		plugin.getLogger().info("Deletion cycle complete for " + getRunStats());
		nextRun.set(System.currentTimeMillis() + plugin.config().getCycleDelayMillis());
		if (plugin.config().isRememberCycleDelay()) {
			try {
				plugin.getServer().getScheduler().runTask(plugin, () -> plugin.finishCycle(this));
			} catch (IllegalPluginAccessException e) {
				// Plugin disabling, odds are on that we were mid-cycle. Don't update finish time.
			}
		}
		phaser.arriveAndDeregister();
	}

	@Override
	public synchronized boolean isCancelled() throws IllegalStateException {
		return super.isCancelled() || !plugin.isEnabled();
	}

	private void handleRegion(@NotNull RegionInfo region) {
		if (isCancelled()) {
			return;
		}

		phaser.arriveAndAwaitAdvance();

		regionCount.incrementAndGet();
		plugin.debug(DebugLevel.HIGH, () -> String.format("Checking %s: %s (%s)",
				world.getWorld().getName(), region.getIdentifier(), regionCount.get()));

		try {
			if (!region.read()) {
				plugin.debug(DebugLevel.HIGH, () -> "Skipping region - in use by server.");
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to read region!", e);
			return;
		}

		// Collect potentially eligible chunks
		List<ChunkInfo> chunks = region.getChunks().filter(this::isDeleteEligible).collect(Collectors.toList());

		if (chunks.size() != region.getChunksPerRegion()) {
			plugin.debug(DebugLevel.HIGH, () ->
					String.format("Not all chunks are delete-eligible (%s) - removing unnecessary chunks", chunks.size()));
			// If entire region is not being deleted, filter out chunks that are already orphaned or freshly generated
			chunks.removeIf(chunk -> {
				if (isCancelled()) {
					return true;
				}
				VisitStatus visitStatus;
				try {
					// The status should be cached here, but if enough time has elapsed
					visitStatus = chunk.getVisitStatus();
				} catch (RuntimeException e) {
					return true;
				}

				return visitStatus == VisitStatus.ORPHANED || !plugin.config().isDeleteFreshChunks(world.getWorld()) && visitStatus == VisitStatus.GENERATED;
			});
		} else if (!plugin.config().isDeleteFreshChunks(world.getWorld())
				&& chunks.stream().noneMatch(chunk -> chunk.getVisitStatus() == VisitStatus.UNVISITED)) {
			// If we're configured to not delete fresh chunks and the whole region is likely fresh, do nothing.
			plugin.debug(DebugLevel.HIGH, () -> "Skipping region - chunks are freshly generated.");
			return;
		}

		if (chunks.isEmpty()) {
			// If no chunks are modified, do nothing.
			plugin.debug(DebugLevel.HIGH, () -> "Skipping region - no chunks are delete-eligible.");
			recover();
			return;
		}

		// Orphan chunks. N.B. Changes do not take effect until RegionInfo#write is called.
		chunks.forEach(ChunkInfo::setOrphaned);

		try {
			if (!region.write()) {
				plugin.debug(DebugLevel.HIGH, () -> "Skipping region - in use by server.");
			}
			chunks.forEach(chunk -> plugin.getFlagger().unflagChunk(chunk.getWorld().getName(), chunk.getChunkX(), chunk.getChunkZ()));
			if (chunks.size() == region.getChunksPerRegion()) {
				regionsDeleted.incrementAndGet();
			} else {
				chunksDeleted.addAndGet(chunks.size());
			}
		} catch (IOException e) {
			plugin.debug(() -> String.format(
					"Caught an IOException attempting to populate chunk data: %s", e.getMessage()), e);
		}

		// If 5 seconds have elapsed since last log and 20 or more regions have been checked, log run stats.
		long now = Instant.now().getEpochSecond();
		int regionsChecked = regionCount.get();
		if (nextLogSecond <= now && regionsChecked >= nextLogCount) {
			nextLogSecond = now + 5;
			nextLogCount = regionsChecked + 20;
			plugin.debug(DebugLevel.LOW, this::getRunStats);
		}

		recover();
	}

	private void recover() {
		if (isCancelled()) {
			return;
		}

		long recoveryTime = plugin.config().getDeletionRecoveryMillis();
		if (recoveryTime > 0) {
			try {
				// Allow server to recover for configured time.
				Thread.sleep(recoveryTime);
			} catch (InterruptedException ignored) {
			}
		}

		// Reset chunk count after sleep.
		heavyChecks.set(0);
	}

	private boolean isDeleteEligible(@NotNull ChunkInfo chunkInfo) {
		if (isCancelled()) {
			// If task is cancelled, report all chunks ineligible for deletion
			plugin.debug(DebugLevel.HIGH, () -> "Deletion task is cancelled, chunks are ineligible for delete.");
			return false;
		}

		if (chunkInfo.isOrphaned()) {
			// Chunk already deleted
			plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s_%s_%s is already orphaned.",
					chunkInfo.getWorld().getName(), chunkInfo.getChunkX(), chunkInfo.getChunkZ()));
			return true;
		}

		long now = System.currentTimeMillis();
		long lastVisit = chunkInfo.getLastVisit();
		boolean isFresh = !plugin.config().isDeleteFreshChunks(world.getWorld()) && lastVisit == plugin.config().getFlagGenerated(world.getWorld());

		if (!isFresh && now <= lastVisit) {
			// Chunk is visited
			plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s_%s_%s is visited until %s",
					chunkInfo.getWorld().getName(), chunkInfo.getChunkX(), chunkInfo.getChunkZ(), lastVisit));
			return false;
		}

		if (!isFresh && now - plugin.config().getFlagDuration(chunkInfo.getWorld()) <= chunkInfo.getLastModified()) {
			plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s_%s_%s is modified until %s",
					chunkInfo.getWorld().getName(), chunkInfo.getChunkX(), chunkInfo.getChunkZ(), chunkInfo.getLastModified()));
			return false;
		}

		// Do recovery for heavy checks as required.
		if (heavyChecks.incrementAndGet() >= plugin.config().getDeletionChunkCount()) {
			recover();
		}

		if (plugin.debug(DebugLevel.HIGH)) {
			plugin.getDebugListener().monitorChunk(chunkInfo.getChunkX(), chunkInfo.getChunkZ());
		}

		VisitStatus visitStatus;
		try {
			// Calculate VisitStatus including protection hooks.
			visitStatus = chunkInfo.getVisitStatus();
		} catch (RuntimeException e) {
			// Interruption is not due to plugin shutdown, log.
			if (!this.isCancelled() && plugin.isEnabled()) {
				plugin.debug(() -> String.format("Caught an exception getting VisitStatus: %s", e.getMessage()), e);
			}

			// If an exception occurred, do not delete chunk.
			visitStatus = VisitStatus.UNKNOWN;
		}

		if (plugin.debug(DebugLevel.HIGH)) {
			plugin.getDebugListener().ignoreChunk(chunkInfo.getChunkX(), chunkInfo.getChunkZ());
		}

		return visitStatus.ordinal() < VisitStatus.VISITED.ordinal();

	}

	public String getRunStats() {
		return String.format(STATS_FORMAT, world.getWorld().getName(), regionCount, regionsDeleted, chunksDeleted);
	}

	public @NotNull String getWorld() {
		return world.getWorld().getName();
	}

	public long getNextRun() {
		return nextRun.get();
	}

	@NotNull Phaser getPhaser() {
		return phaser;
	}

}
