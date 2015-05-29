package com.github.jikoo.regionerator;

import java.io.File;
import java.io.FilenameFilter;

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
	private int count = 0;
	private int regionsDeleted = 0;
	private long nextRun = Long.MAX_VALUE;

	public DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		regionFileFolder = new File(world.getWorldFolder(), "region");
		if (!regionFileFolder.exists()) {
			throw new RuntimeException("World " + world.getName() + " has no generated terrain!");
		}
		regions = regionFileFolder.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				// r.0.0.mca, r.-1.0.mca, etc.
				return name.matches("r\\.-?\\d+\\.-?\\d+\\.mca");
			}
		});
	}

	@Override
	public void run() {
		if (count >= regions.length) {
			plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
			nextRun = System.currentTimeMillis() + plugin.getMillisecondsBetweenDeletionCycles();
			this.cancel();
			return;
		}
		region: for (int i = 0; i < plugin.getRegionsPerCheck() && count < regions.length; i++, count++) {
			String regionFileName = regions[count];
			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug("Checking region file #" + count + ": " + regionFileName);
			}
			Pair<Integer, Integer> regionCoordinates = parseRegion(regionFileName);
			for (int chunkX = regionCoordinates.getLeft(); chunkX < regionCoordinates.getLeft() + 32; chunkX++) {
				for (int chunkZ = regionCoordinates.getRight(); chunkZ < regionCoordinates.getRight() + 32; chunkZ++) {
					if (world.isChunkLoaded(chunkX, chunkZ)) {
						if (plugin.debug(DebugLevel.HIGH)) {
							plugin.debug("Chunk at " + chunkX + "," + chunkZ + " is loaded.");
						}
						continue region;
					}
					if (plugin.getFlagger().isChunkFlagged(world.getName(), chunkX, chunkZ)) {
						if (plugin.debug(DebugLevel.HIGH)) {
							plugin.debug("Chunk at " + chunkX + "," + chunkZ + " is flagged.");
						}
						continue region;
					}
					for (Hook hook : plugin.getProtectionHooks()) {
						if (hook.isChunkProtected(world, chunkX, chunkZ)) {
							if (plugin.debug(DebugLevel.HIGH)) {
								plugin.debug("Chunk at " + chunkX + "," + chunkZ + " contains protections.");
							}
							continue region;
						}
					}
					// FUTURE potentially allow chunk deletion
				}
			}
			File regionFile = new File(regionFileFolder, regionFileName);
			if (regionFile.exists() && regionFile.delete()) {
				regionsDeleted++;
				if (plugin.debug(DebugLevel.MEDIUM) || plugin.debug(DebugLevel.LOW) && count % 20 == 0) {
					plugin.debug(regionFileName + " deleted from " + world.getName());
				}
				plugin.getFlagger().unflagRegion(world.getName(), regionCoordinates.getLeft(), regionCoordinates.getRight());
			}

			if (plugin.debug(DebugLevel.LOW) && count % 20 == 0) {
				plugin.debug(world.getName() + "- Checked: " + (count + 1) + "; Deleted: " + regionsDeleted);
			}
		}
	}

	public String getRunStats() {
		return new StringBuilder(world.getName()).append(" - Checked: ").append(count).append("; Deleted: ").append(regionsDeleted).toString();
	}

	public long getNextRun() {
		return nextRun;
	}

	private Pair<Integer, Integer> parseRegion(String regionFile) {
		String[] split = regionFile.split("\\.");
		return new ImmutablePair<Integer, Integer>(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
	}
}
