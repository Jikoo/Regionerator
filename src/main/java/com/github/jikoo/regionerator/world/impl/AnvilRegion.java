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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class AnvilRegion extends RegionInfo {

	private final @NotNull File regionFile;
	private byte[] header;

	AnvilRegion(@NotNull AnvilWorld world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) {
		super(world, lowestChunkX, lowestChunkZ);
		this.regionFile = regionFile;
	}

	public @NotNull File getRegionFile() {
		return regionFile;
	}

	@Override
	public void read() throws IOException {
		if (header == null) {
			header = new byte[8192];
		}

		if (!getRegionFile().exists()) {
			Arrays.fill(header, (byte) 0);
			return;
		}

		// Chunk pointers are the first 4096 bytes, last modification is the second set
		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(getRegionFile(), "r")) {
			regionRandomAccess.read(header);
		}
	}

	@Override
	public boolean write() throws IOException {
		if (!getRegionFile().exists()) {
			getPlugin().debug(DebugLevel.HIGH, () -> String.format("Skipped nonexistent region %s", getIdentifier()));
			return false;
		}

		if (!getRegionFile().canWrite() && !getRegionFile().setWritable(true) && !getRegionFile().canWrite()) {
			throw new IOException("Unable to set " + getRegionFile().getName() + " writable");
		}

		for (int i = 0; i < 4096; ++i) {
			if (header[i] != 0) {
				try (RandomAccessFile regionRandomAccess = new RandomAccessFile(getRegionFile(), "rwd")) {
					regionRandomAccess.write(header, 0, 4096);
				}
				if (getPlugin().debug(DebugLevel.HIGH)) {
					// Convert back from header index to chunk location.
					int nonZeroIndex = i / 4;
					int chunkX = getLowestChunkX() + nonZeroIndex % 32;// TODO faster bitwise op
					// Truncation eliminates X data, no need to subtract before division.
					int chunkZ = getLowestChunkZ() + nonZeroIndex / 32;

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

		// Header contains no content, delete region
		Files.deleteIfExists(getRegionFile().toPath());
		getPlugin().debug(DebugLevel.HIGH, () -> String.format("Deleted region %s with empty header", getIdentifier()));

		// Return true even if file already did not exist; end goal was still accomplished
		return true;
	}

	@Override
	public @NotNull AnvilWorld getWorldInfo() {
		return (AnvilWorld) super.getWorldInfo();
	}

	@Override
	public boolean exists() {
		return getRegionFile().exists();
	}

	@Override
	protected @NotNull ChunkInfo getChunkInternal(int localChunkX, int localChunkZ) {
		return new AnvilChunk(localChunkX, localChunkZ);
	}

	private class AnvilChunk extends ChunkInfo {

		public AnvilChunk(int localChunkX, int localChunkZ) {
			super(AnvilRegion.this, localChunkX, localChunkZ);
		}

		@Override
		public boolean isOrphaned() {
			boolean orphaned = true;
			int index = 4 * (getLocalChunkX() + getLocalChunkZ() * 32);
			for (int i = 0; i < 4; ++i) {
				// Chunk data location in file is defined by a 4 byte pointer. If no location, orphaned.
				if (header[index + i] != 0) {
					orphaned = false;
					break;
				}
			}
			return orphaned;
		}

		@Override
		public void setOrphaned() {
			int index = 4 * (getLocalChunkX() + getLocalChunkZ() * 32);
			for (int i = 0; i < 4; ++i) {
				header[index + i] = 0;
			}
		}

		@Override
		public long getLastModified() {
			int index = 4096 + 4 * (getLocalChunkX() + getLocalChunkZ() * 32);
			// Last modification is stored as a big endian integer. Last 3 bytes are unsigned.
			return 1000 * (long) (header[index] << 24 | (header[index + 1] & 0xFF) << 16 | (header[index + 2] & 0xFF) << 8 | (header[index + 3] & 0xFF));
		}

	}

}
