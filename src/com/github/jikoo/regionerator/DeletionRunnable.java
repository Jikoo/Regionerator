package com.github.jikoo.regionerator;

import java.io.File;
import java.io.FilenameFilter;
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
	private final int chunksDeleted = 0;
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
		for (int i = 0; i < plugin.getChunksPerCheck() && count < regions.length; i++) {

			checkNextChunk();
		}
	}

	private void checkNextChunk() {
		if (count >= regions.length) {
			return;
		}
		if (dX >= 32) {
			dX = 0;
			dZ++;
		}
		if (dZ >= 32) {
			handleRegionCompletion();
		}
		int chunkX = regionChunkX + dX;
		int chunkZ = regionChunkZ + dZ;
		VisitStatus status = plugin.getFlagger().getChunkVisitStatus(world, chunkX, chunkZ);
		if (status.ordinal() <= VisitStatus.UNVISITED.ordinal()) {
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
			// Delete individual chunks
			// TODO implement
		}
		count++;
		if (plugin.debug(DebugLevel.LOW) && count % 20 == 0 && count > 0) {
			plugin.debug(world.getName() + " - Checked " + count + ", deleted " + regionsDeleted + " regions and " + chunksDeleted + " chunks");
			// TODO count/total
		}
		if (count < regions.length) {
			dX = 0;
			dZ = 0;
			regionChunks.clear();
			Pair<Integer, Integer> regionChunkCoordinates = parseRegion(regions[0]);
			regionChunkX = regionChunkCoordinates.getLeft();
			regionChunkZ = regionChunkCoordinates.getRight();

			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug("Checking region file " + count + ": " + regions[count]);
				// TODO count/total
			}
		} else {
			return;
		}
	}

	public String getRunStats() {
		return new StringBuilder(world.getName()).append(" - Checked ").append(count).append(", deleted ").append(regionsDeleted).toString();
	}

	public long getNextRun() {
		return nextRun;
	}

	private Pair<Integer, Integer> parseRegion(String regionFile) {
		String[] split = regionFile.split("\\.");
		return new ImmutablePair<Integer, Integer>(Integer.parseInt(split[1]) << 5, Integer.parseInt(split[2]) << 5);
	}
}
