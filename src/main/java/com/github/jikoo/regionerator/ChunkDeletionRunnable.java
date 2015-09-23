package com.github.jikoo.regionerator;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.lang3.tuple.Pair;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Helper BukkitRunnable for more slowly wiping chunks to prevent server lag.
 * 
 * @author Jikoo
 */
public class ChunkDeletionRunnable extends BukkitRunnable {

	private final Regionerator plugin;
	private final DeletionRunnable parent;
	private final World world;
	private final String regionFileName;
	private final ArrayList<Pair<Integer, Integer>> regionChunks;
	private final int regionChunkX, regionChunkZ;
	private int chunkCount = 0;

	public ChunkDeletionRunnable(Regionerator plugin, DeletionRunnable parent, World world,
			String regionFileName, int regionChunkX, int regionChunkZ,
			ArrayList<Pair<Integer, Integer>> regionChunks) {
		// TODO make this constructor less terrible
		this.plugin = plugin;
		this.parent = parent;
		this.world = world;
		this.regionFileName = regionFileName;
		this.regionChunkX = regionChunkX;
		this.regionChunkZ = regionChunkZ;
		this.regionChunks = regionChunks;
	}

	@Override
	public void run() {
		File regionFile = new File(parent.regionFileFolder, regionFileName);

		if (!regionFile.canWrite() && !regionFile.setWritable(true) && !regionFile.canWrite()) {
			if (plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(String.format("Unable to set %s in %s writable to delete %s chunks",
						regionFileName, world.getName(), regionChunks.size()));
			};
		}
		Iterator<Pair<Integer, Integer>> iterator = regionChunks.iterator();
		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "rwd")) {
			for (int i = 0; i < plugin.getChunksPerWipeCycle() && iterator.hasNext(); i++) {

				Pair<Integer, Integer> chunkCoords = iterator.next();
				// Cycles run regularly, risking skipping chunks due to IO errors is good.
				// Risking building up failing BukkitRunnables is bad. Remove fast.
				iterator.remove();

				// Chunk is delete-eligible even if deletion fails, no need to wait to remove data
				plugin.getFlagger().unflagChunk(world.getName(), chunkCoords.getLeft(), chunkCoords.getRight());

				// Pointers for chunks are 4 byte integers stored at coordinates relative to the region file itself.
				long chunkPointer = 4 * (chunkCoords.getLeft() - regionChunkX + (chunkCoords.getRight() - regionChunkZ) * 32);

				regionRandomAccess.seek(chunkPointer);

				// Read the chunk pointer before writing - if it's 0, the chunk is already orphaned, don't add it
				if (regionRandomAccess.readInt() == 0) {
					continue;
				}

				// Seek back to the right position - reading the int moved us forward 4 bytes
				regionRandomAccess.seek(chunkPointer);

				if (plugin.debug(DebugLevel.HIGH)) {
					plugin.debug(String.format("Wiping chunk %s, %s from %s in %s of %s",
							chunkCoords.getLeft(), chunkCoords.getRight(), chunkPointer,
							regionFileName, world.getName()));
				}

				/*
				 * Orphan the chunk data. This does not free up disk space, but Minecraft will
				 * overwrite the data when another chunk is created in the region.
				 */
				regionRandomAccess.writeInt(0);
				chunkCount++;
			}

			regionRandomAccess.close();

		} catch (IOException ex) {
			if (plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(String.format("Caught an IOException writing %s in %s to delete %s chunks",
						regionFileName, world.getName(), regionChunks.size()));
			}
		}

		if (iterator.hasNext()) {
			return;
		} else {
			this.cancel();
		}

		if (chunkCount == 0) {
			return;
		}

		parent.chunksDeleted += chunkCount;

		if (plugin.debug(DebugLevel.MEDIUM)) {
			plugin.debug(String.format("%s chunks deleted from %s of %s", chunkCount, regionFileName, world.getName()));
		}
	}

}
