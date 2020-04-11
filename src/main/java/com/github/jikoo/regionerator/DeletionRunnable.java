package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.event.RegioneratorDeleteEvent;
import com.github.jikoo.regionerator.world.ChunkInfo;
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
	AtomicInteger regionCount = new AtomicInteger(), regionsDeleted = new AtomicInteger(), chunksDeleted = new AtomicInteger();

	DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = plugin.getWorldManager().getWorld(world);
	}

	@Override
	public void run() {
		world.getRegions().filter(Objects::nonNull).forEach(region -> {
			regionCount.incrementAndGet();
			plugin.debug(DebugLevel.HIGH, () -> String.format("Checking %s:%s (%s)",
					world.getWorld().getName(), region.getRegionFile().getName(), regionCount.get()));

			List<ChunkInfo> chunks = region.getChunks().filter(chunkInfo -> {
				if (chunkInfo.isOrphaned()) {
					return true;
				}

				long now = System.currentTimeMillis();
				if (now - plugin.config().getFlagDuration() <= chunkInfo.getLastModified() || now <= chunkInfo.getLastVisit()) {
					return false;
				}

				return chunkInfo.getVisitStatus().ordinal() < VisitStatus.VISITED.ordinal();

			}).collect(Collectors.toList());

			if (chunks.size() != 1024) {
				chunks.removeIf(chunk -> {
					VisitStatus visitStatus = chunk.getVisitStatus();
					return visitStatus == VisitStatus.ORPHANED || !plugin.config().isDeleteFreshChunks() && visitStatus == VisitStatus.GENERATED;
				});


				chunks.forEach(ChunkInfo::setOrphaned);
			}

			try {
				region.write();
				new RegioneratorDeleteEvent(world.getWorld(), chunks);
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

		});
		plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
		nextRun = System.currentTimeMillis() + plugin.config().getMillisBetweenCycles();
	}

	String getRunStats() {
		return String.format(STATS_FORMAT, world.getWorld().getName(), regionCount.get(), regionsDeleted, chunksDeleted);
	}

	long getNextRun() {
		return nextRun;
	}

}
