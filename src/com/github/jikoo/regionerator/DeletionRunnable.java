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
			if (plugin.debug(DebugLevel.LOW) && count % 20 == 0 && count > 0) {
				plugin.debug(world.getName() + " - Checked " + count + ", deleted " + regionsDeleted);
			}

			String regionFileName = regions[count];
			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug("Checking region file #" + count + ": " + regionFileName);
			}
			Pair<Integer, Integer> chunkCoordinates = parseRegion(regionFileName);
			VisitStatus visitStatus = VisitStatus.PROTECTED;
			for (int chunkX = chunkCoordinates.getLeft(); chunkX < chunkCoordinates.getLeft() + 32; chunkX++) {
				for (int chunkZ = chunkCoordinates.getRight(); chunkZ < chunkCoordinates.getRight() + 32; chunkZ++) {
					VisitStatus status = plugin.getFlagger().getChunkVisitStatus(world, chunkX, chunkZ);
					if (status.ordinal() > VisitStatus.GENERATED.ordinal()) {
						continue region;
					}
					if (status.ordinal() < visitStatus.ordinal()) {
						visitStatus = status;
					}
					// FUTURE potentially allow chunk deletion
				}
			}
			if (visitStatus == VisitStatus.GENERATED && plugin.getGenerateFlag() == Long.MAX_VALUE) {
				continue;
			}
			File regionFile = new File(regionFileFolder, regionFileName);
			if (regionFile.exists() && regionFile.delete()) {
				regionsDeleted++;
				if (plugin.debug(DebugLevel.MEDIUM) || plugin.debug(DebugLevel.LOW) && count % 20 == 0) {
					plugin.debug(regionFileName + " deleted from " + world.getName());
				}
				plugin.getFlagger().unflagRegion(world.getName(), chunkCoordinates.getLeft(), chunkCoordinates.getRight());
			}
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
