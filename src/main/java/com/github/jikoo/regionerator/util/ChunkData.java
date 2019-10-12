package com.github.jikoo.regionerator.util;

import com.github.jikoo.regionerator.VisitStatus;

public class ChunkData {

	private final int localChunkX;
	private final int localChunkZ;
	private final boolean orphaned;
	private final long lastModified;
	private long visitFlag;
	private VisitStatus visitStatus;

	public ChunkData(int localChunkX, int localChunkZ, boolean orphaned, long lastModified) {
		this.localChunkX = localChunkX;
		this.localChunkZ = localChunkZ;
		this.orphaned = orphaned;
		this.lastModified = lastModified;
		this.visitStatus = VisitStatus.UNKNOWN;
	}

	public int getLocalChunkX() {
		return localChunkX;
	}

	public int getLocalChunkZ() {
		return localChunkZ;
	}

	public boolean isOrphaned() {
		return orphaned;
	}

	public long getLastModified() {
		return lastModified;
	}

	public VisitStatus getVisitStatus() {
		return visitStatus;
	}

	public void setVisitStatus(VisitStatus status) {
		this.visitStatus = status;
	}

}
