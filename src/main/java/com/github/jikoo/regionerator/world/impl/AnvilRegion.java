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
import com.github.jikoo.regionerator.world.impl.anvil.AccessMode;
import com.github.jikoo.regionerator.world.impl.anvil.RegionFile;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
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

	private final ByteBuffer volatileRegionHeader;
	private final IntBuffer volatileChunkTimes;
	private final ByteBuffer storedRegionHeader;
	private final IntBuffer storedChunkUsage;
	private final IntBuffer storedChunkTimes;
	private final ByteBuffer chunkHeader;
	private final @NotNull Path worldDataFolder;
	private final @NotNull String fileName;

	AnvilRegion(
					@NotNull AnvilWorld world,
					@NotNull Path worldDataFolder,
					int regionX,
					int regionZ,
					@NotNull String fileFormat) {
		super(world, Coords.regionToChunk(regionX), Coords.regionToChunk(regionZ));
		this.worldDataFolder = worldDataFolder;
		this.fileName = String.format(fileFormat, regionX, regionZ);

		volatileRegionHeader = ByteBuffer.allocateDirect(RegionFile.REGION_HEADER_LENGTH);
		volatileChunkTimes = volatileRegionHeader.slice(RegionFile.SECTOR_BYTES, RegionFile.SECTOR_BYTES).asIntBuffer();
		storedRegionHeader = ByteBuffer.allocateDirect(volatileRegionHeader.capacity());
		storedChunkUsage = storedRegionHeader.slice(0, RegionFile.SECTOR_BYTES).asIntBuffer();
		storedChunkTimes = storedRegionHeader.slice(RegionFile.SECTOR_BYTES, RegionFile.SECTOR_BYTES).asIntBuffer();
		chunkHeader = ByteBuffer.allocateDirect(RegionFile.CHUNK_HEADER_LENGTH);
	}

	@Contract("_ -> new")
	private @NotNull RegionFile createRegionFile(@NotNull String dir) {
		Path regionFilePath = worldDataFolder.resolve(Path.of(dir, fileName));
		return new RegionFile(regionFilePath,
						volatileRegionHeader,
						chunkHeader,
						"I am John RegionFile; I understand that providing my own buffer may be unsafe.");
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
		// We don't read POI data because it generally only updates with a world or entity change.
		// It's effectively a redundant disk operation.
		try (RegionFile regionFileBlockData = createRegionFile(SUBDIR_BLOCK_DATA);
				RegionFile regionFileEntityData = createRegionFile(SUBDIR_ENTITY_DATA)) {
			// Read world data.
			regionFileBlockData.open(AccessMode.READ);
			regionFileBlockData.readHeader();
			regionFileBlockData.close();

			// Clobber all our existing data with the new world data.
			storeCurrentHeader();

			// Read entity data.
			regionFileEntityData.open(AccessMode.READ);
			regionFileEntityData.readHeader();
			regionFileEntityData.close();

			// Use the more recent of block data and entity data modification times.
			storeMoreRecentTimes();
		} catch (DataFormatException e) {
			throw new IOException(e);
		}
	}

	private void storeCurrentHeader() {
		storedRegionHeader.rewind();
		volatileRegionHeader.rewind();
		storedRegionHeader.put(volatileRegionHeader);
	}

	private void storeMoreRecentTimes() {
		for (int i = 0; i < CHUNK_COUNT; ++i) {
			int blockDataTime = storedChunkTimes.get(i);
			int entityDataTime = volatileChunkTimes.get(i);
			if (entityDataTime > blockDataTime) {
				storedChunkTimes.put(i, entityDataTime);
			}
		}
	}

	@Override
	public boolean write() throws IOException {
		// May just remove this and later change method signature - is either true or an exception is thrown
		boolean failed = false;

		for (String dir : DATA_SUBDIRS) {
			failed |= !write(dir);
		}
		Arrays.fill(pointerWipes, false);

		return !failed;
	}

	private boolean write(@NotNull String subdirectory) throws IOException {
		Path mcaFilePath = worldDataFolder.resolve(Path.of(subdirectory, fileName));
		if (!Files.isRegularFile(mcaFilePath)) {
			getPlugin().debug(DebugLevel.HIGH, () -> String.format("Skipped nonexistent region %s/%s", subdirectory, getIdentifier()));
			// Return true even if file already did not exist; end goal was still accomplished
			return true;
		}

		try (RegionFile regionFile = createRegionFile(subdirectory)) {
			regionFile.open(AccessMode.WRITE_DSYNC);
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
				regionFile.close();

				// Since the region still has content, update our stored data with the current data.
				if (subdirectory.equals(SUBDIR_BLOCK_DATA)) {
					storeCurrentHeader();
				} else if (subdirectory.equals(SUBDIR_ENTITY_DATA)) {
					storeMoreRecentTimes();
				}

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
		return IntStream.range(0, CHUNK_COUNT).mapToObj(index -> {
			int localChunkX = RegionFile.unpackLocalX(index);
			int localChunkZ = RegionFile.unpackLocalZ(index);
			return getLocalChunk(localChunkX, localChunkZ);
		});
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
			// Return true if chunk is slated to be orphaned on region write or already orphaned.
			return pointerWipes[index] || storedChunkUsage.get(index) != 0;
		}

		@Override
		public void setOrphaned() {
			pointerWipes[RegionFile.packIndex(getLocalChunkX(), getLocalChunkZ())] = true;
		}

		@Override
		public long getLastModified() {
			return TimeUnit.MILLISECONDS.convert(storedChunkTimes.get(RegionFile.packIndex(getLocalChunkX(), getLocalChunkZ())), TimeUnit.SECONDS);
		}
	}
}
