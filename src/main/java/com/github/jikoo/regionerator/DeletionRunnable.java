package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runnable for checking and deleting chunks and regions.
 *
 * @author Jikoo
 */
public class DeletionRunnable extends BukkitRunnable {

	private static final String STATS_FORMAT = "%s: checked %s, deleted %s regions & %s chunks";

	private final Regionerator plugin;
	private final Phaser phaser;
	private final WorldInfo world;
	private final AtomicLong nextRun = new AtomicLong(Long.MAX_VALUE);
	private final AtomicInteger regionCount = new AtomicInteger(), chunkCount = new AtomicInteger(),
			regionsDeleted = new AtomicInteger(), chunksDeleted = new AtomicInteger();

	DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.phaser = new Phaser();
		this.world = plugin.getWorldManager().getWorld(world);
	}

	@Override
	public void run() {
		world.getRegions().filter(Objects::nonNull).forEach(this::handleRegion);
		plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
		nextRun.set(System.currentTimeMillis() + plugin.config().getMillisBetweenCycles());
		if (plugin.config().isRememberCycleDelay()) {
			plugin.getServer().getScheduler().runTask(plugin, () -> plugin.finishCycle(this));
		}
		// Arrive on completion in case safe reload was attempted during completion of final region
		phaser.arrive();
	}

	private void handleRegion(RegionInfo region) {
		if (isCancelled()) {
			return;
		}

		if (phaser.getRegisteredParties() != 0) {
			// Arrive so registering party is alerted that we're done cycling
			phaser.arrive();
			// Await registering party
			phaser.awaitAdvance(phaser.getPhase() + 1);
		}

		regionCount.incrementAndGet();
		plugin.debug(DebugLevel.HIGH, () -> String.format("Checking %s:%s (%s)",
				world.getWorld().getName(), region.getIdentifier(), regionCount.get()));

		// Collect potentially eligible chunks
		List<ChunkInfo> chunks = region.getChunks().filter(this::isDeleteEligible).collect(Collectors.toList());

		if (chunks.size() != 1024) {
			// If entire region is not being deleted, filter out chunks that are already orphaned or freshly generated
			chunks.removeIf(chunk -> {
				if (isCancelled()) {
					return true;
				}
				VisitStatus visitStatus = chunk.getVisitStatus();
				return visitStatus == VisitStatus.ORPHANED || !plugin.config().isDeleteFreshChunks() && visitStatus == VisitStatus.GENERATED;
			});

			// Orphan chunks - N.B. this is called here and not outside of the block because AnvilRegion deletes regions on AnvilRegion#write
			chunks.forEach(ChunkInfo::setOrphaned);
		}

		try {
			region.write();
			if (chunks.size() == 1024) {
				plugin.getFlagger().unflagRegionByLowestChunk(world.getWorld().getName(), region.getLowestChunkX(), region.getLowestChunkZ());
				regionsDeleted.incrementAndGet();
			} else {
				chunks.forEach(chunk -> plugin.getFlagger().unflagChunk(chunk.getWorld().getName(), chunk.getChunkX(), chunk.getChunkZ()));
				chunksDeleted.addAndGet(chunks.size());
			}
		} catch (IOException e) {
			plugin.debug(DebugLevel.LOW, () -> String.format(
					"Caught an IOException attempting to populate chunk data: %s", e.getMessage()));
			plugin.debug(DebugLevel.MEDIUM, (Runnable) e::printStackTrace);
		}

		if (regionCount.get() % 20 == 0) {
			plugin.debug(DebugLevel.LOW, this::getRunStats);
		}

		try {
			Thread.sleep(plugin.config().getDeletionRecoveryMillis());
			// Reset chunk count after sleep
			chunkCount.set(0);
		} catch (InterruptedException ignored) {}
	}

	private boolean isDeleteEligible(ChunkInfo chunkInfo) {
		if (isCancelled()) {
			// If task is cancelled, report all chunks ineligible for deletion
			return false;
		}

		if (chunkInfo.isOrphaned()) {
			// Chunk already deleted
			return true;
		}

		long now = System.currentTimeMillis();
		if (now - plugin.config().getFlagDuration() <= chunkInfo.getLastModified() || now <= chunkInfo.getLastVisit()) {
			// Chunk is visited
			return false;
		}

		// Only count heavy checks towards total chunk count for recovery
		if (chunkCount.incrementAndGet() % plugin.config().getDeletionChunkCount() == 0) {
			try {
				Thread.sleep(plugin.config().getDeletionRecoveryMillis());
			} catch (InterruptedException ignored) {}
		}

		try {
			return chunkInfo.getVisitStatus().ordinal() < VisitStatus.VISITED.ordinal();
		} catch (RuntimeException e) {
			if (!this.isCancelled() && plugin.isEnabled()) {
				// Interruption is not due to plugin shutdown, log.
				plugin.debug(DebugLevel.LOW, () -> String.format("Caught an exception getting VisitStatus: %s", e.getMessage()));
				plugin.debug(DebugLevel.MEDIUM, (Runnable) e::printStackTrace);
			}
			// If an exception occurred, do not delete chunk.
			return false;
		}
	}

	public String getRunStats() {
		return String.format(STATS_FORMAT, world.getWorld().getName(), regionCount.get(), regionsDeleted, chunksDeleted);
	}

	public String getWorld() {
		return world.getWorld().getName();
	}

	public long getNextRun() {
		return nextRun.get();
	}

	Phaser getPhaser() {
		return phaser;
	}

}
