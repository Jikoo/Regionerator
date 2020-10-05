package com.github.jikoo.regionerator.world.impl;

import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class AnvilRegion extends RegionInfo {

	private final File regionFile;
	private byte[] header;

	AnvilRegion(@NotNull AnvilWorld world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) throws IOException {
		super(world, lowestChunkX, lowestChunkZ);
		this.regionFile = regionFile;

		read();
	}

	public File getRegionFile() {
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
				return true;
			}
		}

		// Header contains no content, delete region
		Files.deleteIfExists(getRegionFile().toPath());

		// Return true even if file already did not exist; end goal was still accomplished
		return true;
	}

	@NotNull
	@Override
	public AnvilWorld getWorldInfo() {
		return (AnvilWorld) super.getWorldInfo();
	}

	@Override
	public boolean exists() {
		return getRegionFile().exists();
	}

	@NotNull
	@Override
	protected ChunkInfo getChunkInternal(int localChunkX, int localChunkZ) {
		return new AnvilChunkInfo(localChunkX, localChunkZ);
	}

	private class AnvilChunkInfo extends ChunkInfo {

		public AnvilChunkInfo(int localChunkX, int localChunkZ) {
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
