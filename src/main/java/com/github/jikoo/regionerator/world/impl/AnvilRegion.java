/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world.impl;

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.impl.anvil.RegionFile;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;

public class AnvilRegion extends RegionInfo {

	private static final int CHUNKS_PER_AXIS = 32;
	// Regions are a square of chunks.
	private static final int CHUNK_COUNT = CHUNKS_PER_AXIS * CHUNKS_PER_AXIS;
	private final boolean[] pointerWipes = new boolean[CHUNK_COUNT];
	static final String[] DATA_SUBDIRS = { "region", "entities", "poi" };

	private final @NotNull Path worldDataFolder;
	private final @NotNull String fileName;
	private final @NotNull RegionFile defaultRegionFile;

	AnvilRegion(
					@NotNull AnvilWorld world,
					@NotNull Path worldDataFolder,
					int regionX,
					int regionZ,
					@NotNull String fileFormat) {
		super(world, Coords.regionToChunk(regionX), Coords.regionToChunk(regionZ));
		this.worldDataFolder = worldDataFolder;
		this.fileName = String.format(fileFormat, regionX, regionZ);

		// TODO need to redefine reading header
		Path regionFilePath = worldDataFolder.resolve(Path.of(DATA_SUBDIRS[0], fileName));
		defaultRegionFile = new RegionFile(regionFilePath, false);
	}

	/**
	 * @deprecated Region data may be saved in multiple files.
	 * @return the chunk data Minecraft Region file
	 */
	@Deprecated(forRemoval = true, since = "2.5.0")
	public @NotNull File getRegionFile() {
		return worldDataFolder.resolve(Path.of(DATA_SUBDIRS[0], fileName)).toFile();
	}

	@Override
	public void read() throws IOException {
		try {
			defaultRegionFile.open(true);
			defaultRegionFile.readHeader();
			defaultRegionFile.close();
		} catch (DataFormatException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean write() throws IOException {
		// May just remove this and later change method signature - is either true or an exception is thrown
		boolean failed = false;

		for (String dir : DATA_SUBDIRS) {
			Path mcaFilePath = worldDataFolder.resolve(Path.of(dir, fileName));
			failed |= !write(mcaFilePath);
		}

		return !failed;
	}

	private boolean write(@NotNull Path mcaFilePath) throws IOException {
		// TODO identifiers for subdir in logging
		if (!Files.isRegularFile(mcaFilePath)) {
			getPlugin().debug(DebugLevel.HIGH, () -> String.format("Skipped nonexistent region %s", getIdentifier()));
			// Return true even if file already did not exist; end goal was still accomplished
			return true;
		}

		try (RegionFile regionFile = new RegionFile(mcaFilePath, true)) {
			regionFile.open(false);
			regionFile.readHeader();
			boolean headerEmpty = true;
			for (int i = 0; i < pointerWipes.length; ++i) {
				if (pointerWipes[i]) {
					regionFile.deleteChunk(i);
				} else if (headerEmpty && regionFile.isPresent(i)) {

					if (getPlugin().debug(DebugLevel.HIGH)) {
						int chunkX = getLowestChunkX() + getLocalX(i);
						int chunkZ = getLowestChunkZ() + getLocalZ(i);
						getPlugin().getLogger().info(
										String.format(
														"Rewriting header of region %s due to non-zero index of chunk %s_%s_%s",
														getIdentifier(),
														getWorld().getName(),
														chunkX,
														chunkZ));
					}
					headerEmpty = false;
				}
			}
			if (!headerEmpty) {
				regionFile.writeHeader();
				return true;
			}
		} catch (DataFormatException e) {
			throw new IOException(e);
		}

		// Header contains no content, delete data.
		Files.deleteIfExists(mcaFilePath);

		// Also delete oversized chunks belonging to this region.
		for (int dX = 0; dX < CHUNKS_PER_AXIS; ++dX) {
			for (int dZ = 0; dZ < CHUNKS_PER_AXIS; ++dZ) {
				String xlChunkData = String.format("c.%s.%s.mcc", getLowestChunkX() + dX, getLowestChunkZ() + dZ);
				for (String dir : DATA_SUBDIRS) {
					Files.deleteIfExists(worldDataFolder.resolve(Path.of(dir, xlChunkData)));
				}
			}
		}

		getPlugin().debug(DebugLevel.HIGH, () -> String.format("Deleted region %s with empty header", getIdentifier()));
		return true;
	}

	@Override
	public @NotNull AnvilWorld getWorldInfo() {
		return (AnvilWorld) super.getWorldInfo();
	}

	@Override
	public boolean exists() {
		for (String dir : DATA_SUBDIRS) {
			File mcaFile = worldDataFolder.resolve(Path.of(dir, fileName)).toFile();
			if (mcaFile.exists()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public @NotNull ChunkInfo getLocalChunk(int localChunkX, int localChunkZ) {
		Preconditions.checkArgument(localChunkX >= 0 && localChunkX < 32 && localChunkZ >= 0 && localChunkZ < 32,
				"Local chunk coords must be within range 0-31! Received values X: %s, Z: %s", localChunkX, localChunkZ);
		return new AnvilChunk(localChunkX, localChunkZ);
	}

	@Deprecated
	@Override
	protected @NotNull ChunkInfo getChunkInternal(int localChunkX, int localChunkZ) {
		return getLocalChunk(localChunkX, localChunkZ);
	}

	@Override
	public @NotNull Stream<ChunkInfo> getChunks() {
		AtomicInteger index = new AtomicInteger();
		return Stream.generate(() -> {
			int localIndex = index.getAndIncrement();
			int localChunkX = getLocalX(localIndex);
			int localChunkZ = getLocalZ(localIndex);
			return getLocalChunk(localChunkX, localChunkZ);
		}).limit(CHUNK_COUNT);
	}

	@Override
	public int getChunksPerRegion() {
		return CHUNK_COUNT;
	}

	private class AnvilChunk extends ChunkInfo {

		private AnvilChunk(int localChunkX, int localChunkZ) {
			super(AnvilRegion.this, localChunkX, localChunkZ);
			Preconditions.checkArgument(localChunkX >= 0 && localChunkX < 32, "localChunkX must be between 0 and 31");
			Preconditions.checkArgument(localChunkZ >= 0 && localChunkZ < 32, "localChunkZ must be between 0 and 31");
		}

		@Override
		public boolean isOrphaned() {
			int index = getIndex();

			// Is chunk slated to be orphaned on region write?
			if (pointerWipes[index]) {
				return true;
			}

			return defaultRegionFile.isPresent(index);
		}

		@Override
		public void setOrphaned() {
			pointerWipes[getIndex()] = true;
		}

		@Override
		public long getLastModified() {
			return defaultRegionFile.getLastModified(getIndex());
		}

		private int getIndex() {
			return AnvilRegion.getLocalIndex(getLocalChunkX(), getLocalChunkZ());
		}

	}

	/**
	 * Get a localized index - 10 bits, highest 5 are Z, lowest 5 are X.
	 *
	 * @param localX the local chunk X
	 * @param localZ the local chunk Z
	 * @return a combined index
	 */
	private static int getLocalIndex(int localX, int localZ) {
		return localX ^ (localZ << 5);
	}

	/**
	 * Get a local chunk X coordinate from an index.
	 *
	 * @param index the index
	 * @return the local chunk coordinate
	 */
	private static int getLocalX(int index) {
		return index & 0x1F;
	}

	/**
	 * Get a local chunk Z coordinate from an index.
	 *
	 * @param index the index
	 * @return the local chunk coordinate
	 */
	private static int getLocalZ(int index) {
		return index >> 5;
	}

}
