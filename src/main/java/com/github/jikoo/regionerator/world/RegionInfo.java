/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world;

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.Regionerator;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A representation of a Minecraft region.
 */
public abstract class RegionInfo {

	private final @NotNull WorldInfo world;
	private final int lowestChunkX, lowestChunkZ;

	/**
	 * Constructs a new RegionInfo.
	 *
	 * @param world the {@link WorldInfo} containing the RegionInfo
	 * @param lowestChunkX the lowest chunk X coordinate contained within the region
	 * @param lowestChunkZ the lowest chunk Z coordinate contained within the region
	 */
	protected RegionInfo(@NotNull WorldInfo world, int lowestChunkX, int lowestChunkZ) {
		this.world = world;
		this.lowestChunkX = lowestChunkX;
		this.lowestChunkZ = lowestChunkZ;
	}

	/**
	 * Reads the RegionInfo into memory.
	 *
	 * @throws IOException if there is an error reading the RegionInfo
	 */
	public abstract void read() throws IOException;

	/**
	 * Saves changes to the RegionInfo.
	 *
	 * @return true if the RegionInfo is saved successfully
	 * @throws IOException if there is an error writing the RegionInfo
	 */
	public abstract boolean write() throws IOException;

	/**
	 * Gets the {@link WorldInfo} containing the RegionInfo.
	 *
	 * @return the {@link WorldInfo}
	 */
	public @NotNull WorldInfo getWorldInfo() {
		return world;
	}

	/**
	 * Gets the {@link World} containing the region represented.
	 *
	 * @return the {@link World}
	 */
	public @NotNull World getWorld() {
		return world.getWorld();
	}

	/**
	 * Checks whether or not the Region has been saved before.
	 *
	 * @return true if the Region has been written in the past
	 */
	public abstract boolean exists();

	/**
	 * Gets the lowest chunk X coordinate contained within the region.
	 *
	 * @return the lowest chunk X coordinate
	 */
	public int getLowestChunkX() {
		return lowestChunkX;
	}

	/**
	 * Gets the lowest chunk Z coordinate contained within the region.
	 *
	 * @return the lowest chunk Z coordinate
	 */
	public int getLowestChunkZ() {
		return lowestChunkZ;
	}

	/**
	 * Gets a readable identifier for the RegionInfo.
	 *
	 * @return the identifier
	 */
	public @NotNull String getIdentifier() {
		return Coords.chunkToRegion(getLowestChunkX()) + "_" + Coords.chunkToRegion(getLowestChunkZ());
	}

	/**
	 * Gets a {@link ChunkInfo} within the region based on chunk coordinates.
	 *
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @return the {@link ChunkInfo}
	 */
	public @NotNull ChunkInfo getChunk(int chunkX, int chunkZ) {
		return getLocalChunk(chunkX - lowestChunkX, chunkZ - lowestChunkZ);
	}

	/**
	 * Gets a {@link ChunkInfo} within the region based on chunk coordinates relative to the region's lowest coordinates.
	 *
	 * @param localChunkX the chunk X coordinate within the region
	 * @param localChunkZ the chunk Z coordinate within the region
	 * @return the {@link ChunkInfo}
	 */
	public @NotNull ChunkInfo getLocalChunk(int localChunkX, int localChunkZ) {
		Preconditions.checkArgument(localChunkX >= 0 && localChunkX < 32 && localChunkZ >= 0 && localChunkZ < 32,
				"Local chunk coords must be within range 0-31! Received values X: %s, Z: %s", localChunkX, localChunkZ);
		return getChunkInternal(localChunkX, localChunkZ);
	}

	/**
	 * Implementation of obtaining a ChunkInfo.
	 *
	 * @param localChunkX the chunk X coordinate within the region
	 * @param localChunkZ the chunk Z coordinate within the region
	 * @return the {@link ChunkInfo} implementation
	 */
	protected @NotNull abstract ChunkInfo getChunkInternal(int localChunkX, int localChunkZ);

	/**
	 * Gets a {@link Stream<ChunkInfo>} requesting every {@link ChunkInfo} within the region.
	 *
	 * @return a {@link Stream<ChunkInfo>}
	 */
	public @NotNull Stream<ChunkInfo> getChunks() {
		AtomicInteger index = new AtomicInteger();
		return Stream.generate(() -> {
			int localIndex = index.getAndIncrement();
			int localChunkX = localIndex % 32;
			int localChunkZ = localIndex / 32;
			return getLocalChunk(localChunkX, localChunkZ);
		}).limit(1024);
	}

	/**
	 * Gets the instance of Regionerator loading the RegionInfo.
	 *
	 * @return the Regionerator instance
	 */
	protected @NotNull Regionerator getPlugin() {
		return getWorldInfo().getPlugin();
	}

}
