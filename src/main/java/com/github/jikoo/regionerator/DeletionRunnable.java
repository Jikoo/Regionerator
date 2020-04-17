package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.event.RegioneratorDeleteEvent;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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
	private final WorldInfo world;
	private long nextRun = Long.MAX_VALUE;
	AtomicInteger regionCount = new AtomicInteger(), chunkCount = new AtomicInteger(),
			regionsDeleted = new AtomicInteger(), chunksDeleted = new AtomicInteger();

	DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = plugin.getWorldManager().getWorld(world);
	}

	@Override
	public void run() {
		world.getRegions().filter(Objects::nonNull).forEach(this::handleRegion);
		plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
		nextRun = System.currentTimeMillis() + plugin.config().getMillisBetweenCycles();
	}

	private void handleRegion(RegionInfo region) {
		regionCount.incrementAndGet();
		plugin.debug(DebugLevel.HIGH, () -> String.format("Checking %s:%s (%s)",
				world.getWorld().getName(), region.getIdentifier(), regionCount.get()));

		// Collect potentially eligible chunks
		List<ChunkInfo> chunks = region.getChunks().filter(this::filterChunk).collect(Collectors.toList());

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
			plugin.getServer().getPluginManager().callEvent(new RegioneratorDeleteEvent(world.getWorld(), chunks));
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

	private boolean filterChunk(ChunkInfo chunkInfo) {
		if (chunkInfo.isOrphaned()) {
			return true;
		}

		long now = System.currentTimeMillis();
		if (now - plugin.config().getFlagDuration() <= chunkInfo.getLastModified() || now <= chunkInfo.getLastVisit()) {
			return false;
		}

		// Only count heavy checks towards total chunk count for recovery
		if (chunkCount.incrementAndGet() % plugin.config().getDeletionChunkCount() == 0) {
			try {
				Thread.sleep(plugin.config().getDeletionRecoveryMillis());
			} catch (InterruptedException ignored) {}
		}

		if (isCancelled()) {
			return true;
		}

		try {
			return chunkInfo.getVisitStatus().ordinal() < VisitStatus.VISITED.ordinal();
		} catch (RuntimeException e) {
			if (this.isCancelled() || !plugin.isEnabled()) {
				// Interruption is due to task cancellation, likely for shutdown.
				return true;
			}
			plugin.debug(DebugLevel.LOW, () -> String.format("Caught an exception getting VisitStatus: %s", e.getMessage()));
			plugin.debug(DebugLevel.MEDIUM, (Runnable) e::printStackTrace);
			return true;
		}
	}

	String getRunStats() {
		return String.format(STATS_FORMAT, world.getWorld().getName(), regionCount.get(), regionsDeleted, chunksDeleted);
	}

	long getNextRun() {
		return nextRun;
	}

}
