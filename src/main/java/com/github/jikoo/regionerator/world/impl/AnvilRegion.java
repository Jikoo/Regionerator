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

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AnvilRegion extends RegionInfo {

	// Regions consist of 32 * 32 chunks.
	private static final int CHUNK_COUNT = 32 * 32;
	// Header stores location in a 4 byte pointer.
	private static final int POINTER_LENGTH = 4;
	// Full header chunk pointer length is chunks * pointer length.
	private static final int HEADER_POINTER_LENGTH = POINTER_LENGTH * CHUNK_COUNT;
	private static final int LAST_MODIFIED_LENGTH = 4;
	// Full header last modification length is chunks * 4 bytes.
	private static final int HEADER_LAST_MODIFIED_LENGTH = LAST_MODIFIED_LENGTH * CHUNK_COUNT;

	private final @NotNull File regionFile;
	private final @NotNull File entitiesFile;
	private final @NotNull File poiFile;
	// Full header consists of chunk pointers, then chunk last modification.
	private final byte[] header = new byte[HEADER_POINTER_LENGTH + HEADER_LAST_MODIFIED_LENGTH];
	private final boolean[] pointerWipes = new boolean[CHUNK_COUNT];

	AnvilRegion(@NotNull AnvilWorld world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) {
		super(world, lowestChunkX, lowestChunkZ);
		this.regionFile = regionFile;
		Path parent = regionFile.toPath().normalize().getParent().getParent();
		String fileName = regionFile.getName();
		this.entitiesFile = parent.resolve(Path.of("entities", fileName)).toFile();
		this.poiFile = parent.resolve(Path.of("poi", fileName)).toFile();
		Arrays.fill(header, (byte) 1);
		Arrays.fill(pointerWipes, false);
	}

	public @NotNull File getRegionFile() {
		return regionFile;
	}

	@Override
	public void read() throws IOException {
		if (!regionFile.exists()) {
			Arrays.fill(header, (byte) 0);
			return;
		}

		// Chunk pointers are the first 4096 bytes, last modification is the second set
		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "r")) {
			regionRandomAccess.read(header);
		}
	}

	@Override
	public boolean write() throws IOException {
		// May just remove this and later change method signature - is either true or an exception is thrown
		boolean failed = false;

		for (File mcaFile : new File[] { regionFile, entitiesFile, poiFile }) {
			failed |= !write(mcaFile);
		}

		return !failed;
	}

	private boolean write(@NotNull File mcaFile) throws IOException {
		if (!mcaFile.exists()) {
			getPlugin().debug(DebugLevel.HIGH, () -> String.format("Skipped nonexistent region %s", getIdentifier()));
			// Return true even if file already did not exist; end goal was still accomplished
			return true;
		}

		if (!mcaFile.canWrite() && !mcaFile.setWritable(true) && !mcaFile.canWrite()) {
			throw new IOException("Unable to set " + mcaFile.getPath() + " writable");
		}

		try (RandomAccessFile randomAccess = new RandomAccessFile(mcaFile, "rwd")) {
			// Always re-read header to prevent writing incorrect chunk locations.
			// This prevents issues with servers with slow cycles combined with speedier modern chunk unloads.
			randomAccess.read(header);

			// Wipe specified pointers.
			for (int index = 0; index < pointerWipes.length; ++index) {
				if (pointerWipes[index]) {
					int pointerIndex = index * POINTER_LENGTH;
					int pointerEnd = pointerIndex + POINTER_LENGTH;
					for (; pointerIndex < pointerEnd; ++pointerIndex) {
						header[pointerIndex] = 0;
					}
				}
			}

			// Check header.
			for (int i = 0; i < HEADER_POINTER_LENGTH; ++i) {
				if (header[i] != 0) {
					// Header is not empty, region still contains chunks and must be rewritten.
					randomAccess.seek(0);
					randomAccess.write(header, 0, HEADER_POINTER_LENGTH);

					if (getPlugin().debug(DebugLevel.HIGH)) {
						// Convert back from header index to chunk coordinates for readable logs.
						int nonZeroIndex = i / POINTER_LENGTH;
						int chunkX = getLowestChunkX() + getLocalX(nonZeroIndex);
						int chunkZ = getLowestChunkZ() + getLocalZ(nonZeroIndex);

						getPlugin().getLogger().info(
										String.format(
														"Rewrote header of region %s due to non-zero index of chunk %s_%s_%s",
														getIdentifier(),
														getWorld().getName(),
														chunkX,
														chunkZ));
					}
					return true;
				}
			}
		}

		// Header contains no content, delete data.
		Files.deleteIfExists(mcaFile.toPath());
		getPlugin().debug(DebugLevel.HIGH, () -> String.format("Deleted region %s with empty header", getIdentifier()));
		return true;
	}

	@Override
	public @NotNull AnvilWorld getWorldInfo() {
		return (AnvilWorld) super.getWorldInfo();
	}

	@Override
	public boolean exists() {
		return regionFile.exists();
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

		public AnvilChunk(int localChunkX, int localChunkZ) {
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

			// Header stores location in a 4 byte pointer using the same index format.
			int headerIndex = POINTER_LENGTH * index;
			for (int i = 0; i < POINTER_LENGTH; ++i) {
				// Is location specified? Any non-zero value means not orphaned.
				if (header[headerIndex + i] != 0) {
					return false;
				}
			}
			return true;
		}

		@Override
		public void setOrphaned() {
			pointerWipes[getIndex()] = true;
		}

		@Override
		public long getLastModified() {
			int index = HEADER_POINTER_LENGTH + LAST_MODIFIED_LENGTH * getIndex();
			// Last modification is stored as a big endian integer. Last 3 bytes are unsigned.
			return 1000 * (long) (header[index] << 24 | (header[index + 1] & 0xFF) << 16 | (header[index + 2] & 0xFF) << 8 | (header[index + 3] & 0xFF));
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
