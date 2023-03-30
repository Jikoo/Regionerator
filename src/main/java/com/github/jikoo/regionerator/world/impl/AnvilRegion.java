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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;

public class AnvilRegion extends RegionInfo {

	private static final int CHUNKS_PER_AXIS = 32;
	// Regions are a square of chunks.
	private static final int CHUNK_COUNT = CHUNKS_PER_AXIS * CHUNKS_PER_AXIS;
	private final boolean[] pointerWipes = new boolean[CHUNK_COUNT];
	private static final String SUBDIR_BLOCK_DATA = "region";
	private static final String SUBDIR_ENTITY_DATA = "entities";
	static final String[] DATA_SUBDIRS = { SUBDIR_BLOCK_DATA, SUBDIR_ENTITY_DATA, "poi" };

	private final @NotNull Path worldDataFolder;
	private final @NotNull String fileName;
	private final @NotNull RegionFile regionFileBlockData;
	private final @NotNull RegionFile regionFileEntityData;

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
		regionFileBlockData = createRegionFile(SUBDIR_BLOCK_DATA);
		regionFileEntityData = createRegionFile(SUBDIR_ENTITY_DATA);
	}

	@Contract("_ -> new")
	private @NotNull RegionFile createRegionFile(@NotNull String dir) {
		Path regionFilePath = worldDataFolder.resolve(Path.of(dir, fileName));
		return new RegionFile(regionFilePath, true);
	}

	/**
	 * @deprecated Region data may be saved in multiple files.
	 * @return the chunk data Minecraft Region file
	 */
	@Deprecated(forRemoval = true, since = "2.5.0")
	public @NotNull File getRegionFile() {
		return worldDataFolder.resolve(Path.of(SUBDIR_BLOCK_DATA, fileName)).toFile();
	}

	@Override
	public void read() throws IOException {
		try {
			// Read world data.
			regionFileBlockData.open(true);
			regionFileBlockData.readHeader();
			regionFileBlockData.close();
			// Read entity data.
			regionFileEntityData.open(true);
			regionFileEntityData.readHeader();
			regionFileEntityData.close();
			// We don't read POI data because it generally only updates with a world or entity change.
			// It's effectively a redundant disk operation.
		} catch (DataFormatException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean write() throws IOException {
		// May just remove this and later change method signature - is either true or an exception is thrown
		boolean failed = false;

		for (String dir : DATA_SUBDIRS) {
			failed |= !write(dir);
		}

		return !failed;
	}

	private boolean write(@NotNull String subdirectory) throws IOException {
		Path mcaFilePath = worldDataFolder.resolve(Path.of(subdirectory, fileName));
		if (!Files.isRegularFile(mcaFilePath)) {
			getPlugin().debug(DebugLevel.HIGH, () -> String.format("Skipped nonexistent region %s/%s", subdirectory, getIdentifier()));
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
					headerEmpty = false;
					if (getPlugin().debug(DebugLevel.HIGH)) {
						int chunkX = getLowestChunkX() + RegionFile.unpackLocalX(i);
						int chunkZ = getLowestChunkZ() + RegionFile.unpackLocalZ(i);
						getPlugin().getLogger().info(() ->
										String.format(
														"Rewriting header of region %s/%s due to non-zero index of chunk %s_%s_%s",
														subdirectory,
														getIdentifier(),
														getWorld().getName(),
														chunkX,
														chunkZ));
					}
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
				Files.deleteIfExists(worldDataFolder.resolve(Path.of(subdirectory, xlChunkData)));
			}
		}

		getPlugin().debug(DebugLevel.HIGH, () -> String.format("Deleted region %s/%s with empty header", subdirectory, getIdentifier()));
		return true;
	}

	@Override
	public @NotNull AnvilWorld getWorldInfo() {
		return (AnvilWorld) super.getWorldInfo();
	}

	@Override
	public boolean exists() {
		for (String dir : DATA_SUBDIRS) {
			if (Files.exists(worldDataFolder.resolve(Path.of(dir, fileName)))) {
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
			int index = RegionFile.packIndex(getLocalChunkX(), getLocalChunkZ());

			// Is chunk slated to be orphaned on region write?
			if (pointerWipes[index]) {
				return true;
			}

			return !regionFileBlockData.isPresent(index);
		}

		@Override
		public void setOrphaned() {
			pointerWipes[RegionFile.packIndex(getLocalChunkX(), getLocalChunkZ())] = true;
		}

		@Override
		public long getLastModified() {
			int index = RegionFile.packIndex(getLocalChunkX(), getLocalChunkZ());
			return Math.max(regionFileBlockData.getLastModified(index), regionFileEntityData.getLastModified(index));
		}
	}
}
