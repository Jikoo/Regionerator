package com.github.jikoo.regionerator;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.ListIterator;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.jikoo.regionerator.event.RegioneratorChunkDeleteEvent;

/**
 * BukkitRunnable for looping through all region files of a given World and deleting them if they are inactive.
 * 
 * @author Jikoo
 */
public class DeletionRunnable extends BukkitRunnable {

	private static final String STATS_FORMAT = "%s - Checked %s/%s, deleted %s regions and %s chunks";

	private final Regionerator plugin;
	private final World world;
	private final String[] regions;
	private final int chunksPerCheck;
	private int count = -1;
	private int regionChunkX;
	private int regionChunkZ;
	private int dX = 0;
	private int dZ = 0;
	private final ArrayList<Pair<Integer, Integer>> regionChunks = new ArrayList<>();
	private int regionsDeleted = 0;
	private long nextRun = Long.MAX_VALUE;

	final File regionFileFolder;
	int chunksDeleted = 0;

	public DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		File folder = new File(world.getWorldFolder(), "region");
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

		chunksPerCheck = plugin.getChunksPerDeletionCheck();

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
		for (int i = 0; i < chunksPerCheck && count < regions.length; i++) {
			/*
			 * Deletion is, by far, the worst offender for lag. It has to be done on the main thread
			 * or we may risk corruption, so to combat any issues we'll dedicate an entire run cycle
			 * to deletion unless the user has configured the plugin to check more than an entire
			 * region per run.
			 */
			if (chunksPerCheck <= 1024 && i > 0 && dZ >= 32) {
				// Deletion next check
				return;
			}
			checkNextChunk();
			if (chunksPerCheck <= 1024 && dX == 0 && dZ == 0) {
				// Deletion this check
				return;
			}
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
			if (plugin.isPaused() || chunksPerCheck <= 1024) {
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
		ListIterator<Pair<Integer, Integer>> iterator = regionChunks.listIterator();
		while (iterator.hasNext()) {
			Pair<Integer, Integer> chunkCoords = iterator.next();
			if (world.isChunkLoaded(chunkCoords.getLeft(), chunkCoords.getRight())) {
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
			} else if (plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(String.format("Unable to delete %s from %s",
						regionFileName, world.getName()));
			}
			while (iterator.hasPrevious()) {
				Pair<Integer, Integer> chunkCoords = iterator.previous();
				plugin.getServer().getPluginManager().callEvent(
						new RegioneratorChunkDeleteEvent(world, chunkCoords.getLeft(), chunkCoords.getRight()));
			}
		} else if (regionChunks.size() > 0) {
			/*
			 * For dealing with manually wiping individual chunks, heavily rewrite and adapt
			 * @Brettflan's code from WorldBorder - this is relatively complex, and I'd like to
			 * start off with a working solution.
			 */
			String regionFileName = regions[count];
			File regionFile = new File(regionFileFolder, regionFileName);

			if (!regionFile.canWrite() && !regionFile.setWritable(true) && !regionFile.canWrite()) {
				if (plugin.debug(DebugLevel.MEDIUM)) {
					plugin.debug(String.format("Unable to set %s in %s writable to delete %s chunks",
							regionFileName, world.getName(), regionChunks.size()));
				}
				return;
			}

			try (RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "rwd")) {
				int[] pointers = new int[1024];
				for (int i = 0; i < pointers.length; i++) {
					// Read all chunk pointers into an int array
					pointers[i] = regionRandomAccess.readInt();
				}

				int chunkCount = 0;

				while (iterator.hasPrevious()) {
					Pair<Integer, Integer> chunkCoords = iterator.previous();

					// Chunk is delete-eligible even if deletion fails, no need to wait to remove data
					plugin.getFlagger().unflagChunk(world.getName(), chunkCoords.getLeft(), chunkCoords.getRight());

					// Pointers for chunks are 4 byte integers stored at coordinates relative to the region file itself.
					int pointer = chunkCoords.getLeft() - regionChunkX + (chunkCoords.getRight() - regionChunkZ) * 32;

					// Chunk is already orphaned, continue on.
					if (pointers[pointer] == 0) {
						continue;
					}

					// Rather than loop through all our chunks again, call deletion event now
					plugin.getServer().getPluginManager().callEvent(
							new RegioneratorChunkDeleteEvent(world, chunkCoords.getLeft(), chunkCoords.getRight()));

					if (plugin.debug(DebugLevel.HIGH)) {
						plugin.debug(String.format("Wiping chunk %s, %s from %s in %s of %s",
								chunkCoords.getLeft(), chunkCoords.getRight(), pointer,
								regionFileName, world.getName()));
					}

					++chunkCount;
					pointers[pointer] = 0;
				}

				// Overwrite all chunk pointers - this is much faster than seeking.
				for (int i = 0; i < pointers.length; i++) {
					regionRandomAccess.writeInt(pointers[i]);
				}

				regionRandomAccess.close();

				if (plugin.debug(DebugLevel.MEDIUM)) {
					plugin.debug(String.format("%s chunks deleted from %s of %s", chunkCount, regionFileName, world.getName()));
				}

			} catch (IOException ex) {
				if (plugin.debug(DebugLevel.MEDIUM)) {
					plugin.debug(String.format("Caught an IOException writing %s in %s to delete %s chunks",
							regionFileName, world.getName(), regionChunks.size()));
				}
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
