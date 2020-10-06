package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.VisitStatus;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.SupplierCache;
import com.github.jikoo.regionerator.util.VisitStatusCache;
import com.google.common.base.Preconditions;
import org.bukkit.World;

/**
 * A representation of a Minecraft chunk.
 *
 * @author Jikoo
 */
public abstract class ChunkInfo {

	private final RegionInfo regionInfo;
	private final int localChunkX, localChunkZ;
	private final SupplierCache<VisitStatus> visitStatusSupplier;

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
		this.visitStatusSupplier = new VisitStatusCache(getPlugin(), this);
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
	 * Gets the instance of Regionerator loading the ChunkInfo.
	 *
	 * @return the Regionerator instance
	 */
	private Regionerator getPlugin() {
		return getRegionInfo().getWorldInfo().getPlugin();
	}

}
