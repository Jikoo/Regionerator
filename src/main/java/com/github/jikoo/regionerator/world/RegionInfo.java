package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Coords;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public abstract class RegionInfo {

	private final WorldInfo world;
	private final File regionFile;
	private final int lowestChunkX, lowestChunkZ;

	protected RegionInfo(@NotNull WorldInfo world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) throws IOException {
		this.world = world;
		this.regionFile = regionFile;
		this.lowestChunkX = lowestChunkX;
		this.lowestChunkZ = lowestChunkZ;

		read();
	}

	public abstract void read() throws IOException;

	public abstract boolean write() throws IOException;

	@NotNull
	public WorldInfo getWorldInfo() {
		return world;
	}

	@NotNull
	public World getWorld() {
		return world.getWorld();
	}

	public File getRegionFile() {
		return regionFile;
	}

	public int getLowestChunkX() {
		return lowestChunkX;
	}

	public int getLowestChunkZ() {
		return lowestChunkZ;
	}

	@NotNull
	public String getIdentifier() {
		return Coords.chunkToRegion(getLowestChunkX()) + "_" + Coords.chunkToRegion(getLowestChunkZ());
	}

	@NotNull
	public ChunkInfo getChunk(int chunkX, int chunkZ) {
		return getLocalChunk(chunkX - lowestChunkX, chunkZ - lowestChunkZ);
	}

	@NotNull
	public ChunkInfo getLocalChunk(int localChunkX, int localChunkZ) {
		Preconditions.checkArgument(localChunkX >= 0 && localChunkX < 32 && localChunkZ >= 0 && localChunkZ < 32,
				"Local chunk coords must be within range 0-31! Received values X: %s, Z: %s", localChunkX, localChunkZ);
		return getChunkInternal(localChunkX, localChunkZ);
	}

	@NotNull
	protected abstract ChunkInfo getChunkInternal(int localChunkX, int localChunkZ);

	public Stream<ChunkInfo> getChunks() {
		AtomicInteger index = new AtomicInteger();
		return Stream.generate(() -> {
			int localIndex = index.getAndIncrement();
			int localChunkX = localIndex % 32;
			int localChunkZ = localIndex / 32;
			return getLocalChunk(localChunkX, localChunkZ);
		}).limit(1024);
	}

}
