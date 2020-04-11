package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.VisitStatus;
import org.bukkit.World;

public abstract class ChunkInfo {

	private final RegionInfo regionInfo;
	private final int localChunkX, localChunkZ;

	public ChunkInfo(RegionInfo regionInfo, int localChunkX, int localChunkZ) {
		this.regionInfo = regionInfo;
		this.localChunkX = localChunkX;
		this.localChunkZ = localChunkZ;
	}

	public World getWorld() {
		return regionInfo.getWorld();
	}

	public int getChunkX() {
		return regionInfo.getLowestChunkX() + localChunkX;
	}

	public int getChunkZ() {
		return regionInfo.getLowestChunkZ() + localChunkZ;
	}

	public int getLocalChunkX() {
		return localChunkX;
	}

	public int getLocalChunkZ() {
		return localChunkZ;
	}

	public abstract boolean isOrphaned();

	public abstract void setOrphaned();

	public abstract long getLastModified();

	public abstract long getLastVisit();

	public abstract VisitStatus getVisitStatus();

}
