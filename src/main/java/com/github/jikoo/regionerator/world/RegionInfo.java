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
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * A representation of a Minecraft region.
 */
public abstract class RegionInfo {

	private static final String[] ACCEPTABLE_IO_EXCEPTIONS = { "Text file busy" };

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
	 * @return true if the RegionInfo was read successfully
	 * @throws IOException if there is an error reading the RegionInfo
	 */
	public abstract boolean read() throws IOException;

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
	 * Checks whether the Region has been saved before.
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
	public abstract @NotNull ChunkInfo getLocalChunk(int localChunkX, int localChunkZ);

	/**
	 * Gets a {@link Stream<ChunkInfo>} requesting every {@link ChunkInfo} within the region.
	 *
	 * @return a {@link Stream<ChunkInfo>}
	 */
	public abstract @NotNull Stream<ChunkInfo> getChunks();

	public abstract int getChunksPerRegion();

	/**
	 * Gets the instance of Regionerator loading the RegionInfo.
	 *
	 * @return the Regionerator instance
	 */
	protected @NotNull Regionerator getPlugin() {
		return getWorldInfo().getPlugin();
	}

	/**
	 * Helper method for returning false or re-throwing an exception. If the exception does not impede achieving long-term
	 * correctness, it can be ignored.
	 *
	 * @param e the IOException thrown
	 * @return false if the exception is known to cause no long-term issues
	 * @throws IOException if the exception is not known to cause no issues
	 */
	protected static boolean acceptOrRethrow(IOException e) throws IOException {
		for (String acceptable : ACCEPTABLE_IO_EXCEPTIONS) {
			if (acceptable.equals(e.getMessage())) {
				return false;
			}
		}
		throw e;
	}

}
