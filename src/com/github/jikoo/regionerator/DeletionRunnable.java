package com.github.jikoo.regionerator;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * BukkitRunnable for looping through all region files of a given World and deleting them if they are inactive.
 * 
 * @author Jikoo
 */
public class DeletionRunnable extends BukkitRunnable {

	private static final String STATS_FORMAT = "%s - Checked %s/%s, deleted %s regions and %s chunks";

	private final Regionerator plugin;
	private final World world;
	private final File regionFileFolder;
	private final String[] regions;
	private int count = -1;
	private int regionChunkX;
	private int regionChunkZ;
	private int dX = 0;
	private int dZ = 0;
	private final ArrayList<Pair<Integer, Integer>> regionChunks = new ArrayList<>();
	private int regionsDeleted = 0;
	private int chunksDeleted = 0;
	private long nextRun = Long.MAX_VALUE;

	public DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		File folder = new File(world.getWorldFolder(), "region");
		// TODO perhaps a less rigid scan?
		if (!folder.exists()) {
			folder = new File(world.getWorldFolder(), "DIM-1/region");
			if (!folder.exists()) {
				folder = new File(world.getWorldFolder(), "DIM1/region");
				if (!folder.exists()) {
					throw new RuntimeException("World " + world.getName() + " has no generated terrain!");
				}
			}
		}
		regionFileFolder = folder;
		regions = regionFileFolder.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				// r.0.0.mca, r.-1.0.mca, etc.
				return name.matches("r\\.-?\\d+\\.-?\\d+\\.mca");
			}
		});

		handleRegionCompletion();
	}

	@Override
	public void run() {
		if (count >= regions.length) {
			plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
			nextRun = System.currentTimeMillis() + plugin.getMillisecondsBetweenDeletionCycles();
			this.cancel();
			return;
		}
		if (plugin.isPaused() && dX == 0 && dZ == 0) {
			return;
		}
		for (int i = 0; i < plugin.getChunksPerCheck() && count < regions.length; i++) {
			checkNextChunk();
		}
	}

	private void checkNextChunk() {
		if (plugin.isPaused() && dX == 0 && dZ == 0) {
			return;
		}
		if (count >= regions.length) {
			return;
		}
		if (dX >= 32) {
			dX = 0;
			dZ++;
		}
		if (dZ >= 32) {
			handleRegionCompletion();
			if (plugin.isPaused()) {
				return;
			}
		}
		int chunkX = regionChunkX + dX;
		int chunkZ = regionChunkZ + dZ;
		VisitStatus status = plugin.getFlagger().getChunkVisitStatus(world, chunkX, chunkZ);
		if (status.ordinal() < VisitStatus.GENERATED.ordinal()
				|| (plugin.getGenerateFlag() != Long.MAX_VALUE && status == VisitStatus.GENERATED)) {
			// Add generated flagged chunks
			// Deleting an entire region is much faster (and more space-efficient) than deleting chunks
			regionChunks.add(new ImmutablePair<Integer, Integer>(chunkX, chunkZ));
		}
		dX++;
	}

	private void handleRegionCompletion() {
		Iterator<Pair<Integer, Integer>> iterator = regionChunks.iterator();
		while (iterator.hasNext()) {
			Pair<Integer, Integer> chunk = iterator.next();
			if (world.isChunkLoaded(chunk.getLeft(), chunk.getRight())) {
				iterator.remove();
			}
		}
		if (regionChunks.size() == 1024) {
			// Delete entire region
			String regionFileName = regions[count];
			File regionFile = new File(regionFileFolder, regionFileName);
			if (regionFile.exists() && regionFile.delete()) {
				regionsDeleted++;
				if (plugin.debug(DebugLevel.MEDIUM)) {
					plugin.debug(regionFileName + " deleted from " + world.getName());
				}
				plugin.getFlagger().unflagRegion(world.getName(), regionChunkX, regionChunkZ);
			}
		} else if (regionChunks.size() > 0) {
			/*
			 * For dealing with manually wiping individual chunks, heavily rewrite and adapt
			 * @Brettflan's code from WorldBorder - this is relatively complex, and I'd like to
			 * start off with a working solution.
			 */
			String regionFileName = regions[count];
			File regionFile = new File(regionFileFolder, regionFileName);

			if (regionFile.canWrite() || regionFile.setWritable(true) && regionFile.canWrite()) {
				try (RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "rwd")) {
					int chunkCount = 0;
					for (Pair<Integer, Integer> chunkCoords : regionChunks) {
						// Chunk is delete-eligible even if deletion fails, no need to wait to remove data
						plugin.getFlagger().unflagChunk(world.getName(), chunkCoords.getLeft(), chunkCoords.getRight());

						// Pointers for chunks are 4 byte integers stored at coordinates relative to the region file itself.
						long chunkPointer = 4 * (chunkCoords.getLeft() - regionChunkX + (chunkCoords.getRight() - regionChunkZ) * 32);

						regionRandomAccess.seek(chunkPointer);

						/*
						 * Here, WorldBorder opens up a new RandomAccessFile and reads all of the
						 * data for each chunk, then caches it. That seems like a lot of useless
						 * overhead, so rather than directly copy, we just read the individual
						 * chunk's pointer.
						 */
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
						 * Note from @Brettflan: This method isn't perfect since the actual chunk
						 * data is left orphaned, but Minecraft will overwrite the orphaned data
						 * sector if/when another chunk is created in the region, so it's not so bad
						 */
						regionRandomAccess.writeInt(0);
						chunkCount++;
					}

					regionRandomAccess.close();
					chunksDeleted += chunkCount;

					if (plugin.debug(DebugLevel.MEDIUM) && chunksDeleted > 0) {
						plugin.debug(String.format("%s chunks deleted from %s of %s", chunkCount, regionFileName, world.getName()));
					}

				} catch (IOException ex) {}
			}
		}

		// Reset and increment
		regionChunks.clear();
		count++;
		if (plugin.debug(DebugLevel.LOW) && count % 20 == 0 && count > 0) {
			plugin.debug(getRunStats());
		}
		if (count < regions.length) {
			dX = 0;
			dZ = 0;
			regionChunks.clear();
			Pair<Integer, Integer> regionChunkCoordinates = parseRegion(regions[count]);
			regionChunkX = regionChunkCoordinates.getLeft();
			regionChunkZ = regionChunkCoordinates.getRight();

			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug(String.format("Checking %s:%s (%s/%s)", world.getName(),
						regions[count], count, regions.length));
			}
		} else {
			return;
		}
	}

	public String getRunStats() {
		return String.format(STATS_FORMAT, world.getName(), count, regions.length, regionsDeleted, chunksDeleted);
	}

	public long getNextRun() {
		return nextRun;
	}

	private Pair<Integer, Integer> parseRegion(String regionFile) {
		String[] split = regionFile.split("\\.");
		return new ImmutablePair<Integer, Integer>(Integer.parseInt(split[1]) << 5, Integer.parseInt(split[2]) << 5);
	}
}
