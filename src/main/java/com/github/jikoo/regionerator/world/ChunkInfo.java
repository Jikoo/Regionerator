package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.ChunkFlagger;
import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.VisitStatus;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.SupplierCache;
import com.github.jikoo.regionerator.util.yaml.Config;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * A representation of a Minecraft chunk.
 *
 * @author Jikoo
 */
public abstract class ChunkInfo {

	private final RegionInfo regionInfo;
	private final int localChunkX, localChunkZ;
	private final SupplierCache<VisitStatus> visitStatusSupplier = new SupplierCache<>(() -> {
		if (isOrphaned()) {
			return VisitStatus.ORPHANED;
		}

		long now = System.currentTimeMillis();
		Regionerator plugin = getPlugin();
		if (now - plugin.config().getFlagDuration() <= getLastModified()) {
			return VisitStatus.VISITED;
		}

		ChunkFlagger.FlagData flagData = plugin.getFlagger().getChunkFlag(getWorld(), getChunkX(), getChunkZ()).join();

		long lastVisit = flagData.getLastVisit();

		if (lastVisit != Long.MAX_VALUE && lastVisit > now) {
			plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " is flagged.");

			if (lastVisit == Config.FLAG_ETERNAL) {
				return VisitStatus.PERMANENTLY_FLAGGED;
			}
			if (lastVisit == Config.FLAG_OH_NO) {
				return VisitStatus.UNKNOWN;
			}

			return VisitStatus.VISITED;
		}

		Collection<Hook> syncHooks = Bukkit.isPrimaryThread() ? null : new ArrayList<>();
		WorldInfo world = getRegionInfo().getWorldInfo();
		int chunkX = getChunkX();
		int chunkZ = getChunkZ();

		for (Hook hook : plugin.getProtectionHooks()) {
			if (syncHooks != null && !hook.isAsyncCapable()) {
				syncHooks.add(hook);
				continue;
			}
			if (hook.isChunkProtected(world.getWorld(), chunkX, chunkZ)) {
				plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " contains protections by " + hook.getProtectionName());
				return VisitStatus.PROTECTED;
			}
		}

		if (syncHooks != null) {

			if (!plugin.isEnabled()) {
				// Cannot return to main thread to check hooks requiring synchronization
				return VisitStatus.UNKNOWN;
			}

			try {
				VisitStatus visitStatus = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
					for (Hook hook : syncHooks) {
						if (hook.isChunkProtected(world.getWorld(), chunkX, chunkZ)) {
							plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " contains protections by " + hook.getProtectionName());
							return VisitStatus.PROTECTED;
						}
					}
					return VisitStatus.UNKNOWN;
				}).get();
				if (visitStatus == VisitStatus.PROTECTED) {
					return VisitStatus.PROTECTED;
				}
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}

		if (lastVisit == Long.MAX_VALUE) {
			plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " has not been visited since it was generated.");
			return VisitStatus.GENERATED;
		}

		return VisitStatus.UNVISITED;
	}, calcCacheDuration(), TimeUnit.MINUTES);

	/**
	 * Constructs a new ChunkInfo instance.
	 *
	 * @param regionInfo the {@link RegionInfo} of the region this chunk is contained by
	 * @param localChunkX the chunk X coordinate within the region
	 * @param localChunkZ the chunk Z coordinate within the region
	 */
	public ChunkInfo(RegionInfo regionInfo, int localChunkX, int localChunkZ) {
		Preconditions.checkArgument(localChunkX >= 0 && localChunkX < 32, "localChunkX must be between 0 and 31");
		Preconditions.checkArgument(localChunkZ >= 0 && localChunkZ < 32, "localChunkZ must be between 0 and 31");
		this.regionInfo = regionInfo;
		this.localChunkX = localChunkX;
		this.localChunkZ = localChunkZ;
	}

	/**
	 * Gets the {@link World} the ChunkInfo is in.
	 *
	 * @return the World
	 */
	public World getWorld() {
		return regionInfo.getWorld();
	}

	/**
	 * Gets the {@link RegionInfo} containing this Chunk.
	 *
	 * @return the RegionInfo
	 */
	public RegionInfo getRegionInfo() {
		return this.regionInfo;
	}

	/**
	 * Gets the X coordinate of the chunk.
	 *
	 * @return the chunk X coordinate
	 */
	public int getChunkX() {
		return regionInfo.getLowestChunkX() + localChunkX;
	}

	/**
	 * Gets the Z coordinate of the chunk.
	 *
	 * @return the chunk Z coordinate
	 */
	public int getChunkZ() {
		return regionInfo.getLowestChunkZ() + localChunkZ;
	}

	/**
	 * Gets the X coordinate of the chunk within the region.
	 *
	 * @return the chunk X coordinate
	 */
	public int getLocalChunkX() {
		return localChunkX;
	}

	/**
	 * Gets the Z coordinate of the chunk within the region.
	 *
	 * @return the chunk Z coordinate
	 */
	public int getLocalChunkZ() {
		return localChunkZ;
	}

	/**
	 * Gets whether or not the chunk is orphaned, or deleted.
	 *
	 * @return true if the chunk is orphaned
	 */
	public abstract boolean isOrphaned();

	/**
	 * Sets a chunk orphaned.
	 * <p>
	 * To better support batch operations, this method does not immediately orphan the chunk.
	 * To write changes, call {@link RegionInfo#write()} on the ChunkInfo's owning region.
	 */
	public abstract void setOrphaned();

	/**
	 * Gets the timestamp of the last modification of the chunk.
	 *
	 * @return the last modification date of the chunk
	 */
	public abstract long getLastModified();

	/**
	 * Gets the timestamp of the last visit of the chunk.
	 *
	 * @return the last visit date of the chunk
	 */
	public long getLastVisit() {
		return getPlugin().getFlagger().getChunkFlag(getWorld(), getChunkX(), getChunkZ()).join().getLastVisit();
	}

	/**
	 * Gets the {@link VisitStatus} of the chunk.
	 * <p>
	 * N.B. This method caches its value for a short duration based on chunks per deletion attempt
	 * and recovery time. However, querying {@link Hook Hooks} will always result in a heavier first
	 * operation. Use with caution.
	 * @return the VisitStatus
	 */
	public VisitStatus getVisitStatus() {
		return  visitStatusSupplier.get();
	}

	/**
	 * Calculates the duration to cache VisitStatus values to prevent excess load.
	 *
	 * @return the value calculated
	 */
	private int calcCacheDuration() {
		Config config = getPlugin().config();
		return (int) Math.ceil(1024D / config.getDeletionChunkCount() * config.getDeletionRecoveryMillis() / 60000);
	}

	/**
	 * Gets the instance of Regionerator loading the ChunkInfo.
	 *
	 * @return the Regionerator instance
	 */
	private Regionerator getPlugin() {
		return getRegionInfo().getWorldInfo().getPlugin();
	}

}
