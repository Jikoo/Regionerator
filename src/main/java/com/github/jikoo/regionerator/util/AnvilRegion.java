package com.github.jikoo.regionerator.util;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.Regionerator;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class AnvilRegion extends Region {

	// r.0.0.mca, r.-1.0.mca, etc.
	public static final Pattern ANVIL_REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	public static Region parseAnvilRegion(@NotNull World world, @NotNull File regionFile) {
		Matcher matcher = ANVIL_REGION.matcher(regionFile.getName());
		if (!matcher.matches()) {
			throw new IllegalArgumentException("File " + regionFile.getPath() + " does not match Anvil naming convention!");
		}
		return new AnvilRegion(world, regionFile, CoordinateConversions.regionToChunk(Integer.parseInt(matcher.group(1))),
				CoordinateConversions.regionToChunk(Integer.parseInt(matcher.group(2))));
	}

	private AnvilRegion(@NotNull World world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) {
		super(world, regionFile, lowestChunkX, lowestChunkZ);
	}

	public void populate(Regionerator plugin) throws IOException {

		if (populated) {
			return;
		}

		if (!getRegionFile().canWrite() && !getRegionFile().setWritable(true) && !getRegionFile().canWrite()) {
			throw new IOException("Unable to write file " + getRegionFile().getPath());
		}

		// Chunk pointers are the first 4096 bytes, last modification is the second set
		byte[] header = new byte[8192];
		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(getRegionFile(), "r")) {
			regionRandomAccess.read(header);
		}

		for (int x = 0; x < 32; ++x) {
			for (int z = 0; z < 32; ++z) {

				int index = 4 * (x + z * 32);

				boolean orphaned = true;
				long lastModified = 0;

				for (int i = 0; i < 4; ++i) {
					// Chunk data location in file is defined by a 4 byte pointer. If no location, orphaned.
					if (orphaned && header[index + i] != 0) {
						orphaned = false;
					}
					// Last modification is stored as a big endian integer
					lastModified += header[4096 + index + i] << 24 - i * 8;
				}
				lastModified *= 1000;

				ChunkData chunkData = new ChunkData(x, z, orphaned, lastModified);
				chunks.add(chunkData);
			}
		}

		populated = true;

	}

	@Override
	protected int deleteChunks(Collection<ChunkData> chunks) throws IOException {
		if (!getRegionFile().canWrite() && !getRegionFile().setWritable(true) && !getRegionFile().canWrite()) {
			throw new IOException("Unable to set " + getRegionFile().getName() + " writable");
		}

		int chunkCount = 0;

		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(getRegionFile(), "rwd")) {
			byte[] pointers = new byte[4096];
			regionRandomAccess.read(pointers);

			Iterator<ChunkData> chunkIterator = chunks.iterator();
			while (chunkIterator.hasNext()) {
				ChunkData chunk = chunkIterator.next();

				if (chunk.isOrphaned()) {
					// Chunk was already marked as orphaned, skip.
					chunkIterator.remove();
					continue;
				}

				// Pointers for chunks are 4 byte integers stored at coordinates relative to the region file itself.
				int pointer = 4 * (chunk.getLocalChunkX() + chunk.getLocalChunkZ() * 32);

				for (int i = pointer; i < pointer + 4; ++i) {
					pointers[i] = 0;
				}

				++chunkCount;
			}

			if (chunkCount > 0) {
				// Overwrite all chunk pointers - this is much faster than seeking.
				regionRandomAccess.write(pointers, 0, 4096);
			}

		} catch (IOException e) {
			IOException exception = new IOException(String.format("Caught an IOException writing %s in %s to delete %s chunks",
					getRegionFile().getName(), getWorld().getName(), chunks.size()));
			exception.addSuppressed(e);
			throw exception;
		}

		return chunkCount;
	}

}
