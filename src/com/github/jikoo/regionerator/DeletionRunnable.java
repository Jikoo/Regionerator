package com.github.jikoo.regionerator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.atomic.AtomicInteger;

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

	private final World world;
	private final File regionFileFolder;
	private final String[] regions;
	private final AtomicInteger count = new AtomicInteger(0);
	private final AtomicInteger regionsDeleted = new AtomicInteger(0);

	public DeletionRunnable(World world) {
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
		if (count.get() >= regions.length) {
			// TODO report finalized stats
			// TODO schedule next run (config option)
			this.cancel();
			return;
		}
		region: for (int i = 0; i < Regionerator.getInstance().getRegionsPerCheck() && count.get() < regions.length; i++, count.incrementAndGet()) {
			String regionFileName = regions[count.get()];
			Pair<Integer, Integer> regionCoordinates = parseRegion(regionFileName);
			for (int chunkX = regionCoordinates.getLeft(); chunkX < regionCoordinates.getLeft() + 32; chunkX++) {
				for (int chunkZ = regionCoordinates.getRight(); chunkZ < regionCoordinates.getRight() + 32; chunkZ++) {
					if (world.isChunkLoaded(chunkX, chunkZ)) {
						continue region;
					}
					if (Regionerator.getInstance().getFlagger().isChunkFlagged(world.getName(), chunkX, chunkZ)) {
						continue region;
					}
					for (Hook hook : Regionerator.getInstance().getProtectionHooks()) {
						if (hook.isChunkProtected(world, chunkX, chunkZ)) {
							continue region;
						}
					}
					// FUTURE potentially allow chunk deletion
				}
			}
			File regionFile = new File(regionFileFolder, regionFileName);
			if (regionFile.exists() && regionFile.delete()) {
				regionsDeleted.incrementAndGet();
			}
		}
	}

	public String getRunStats() {
		return new StringBuilder(world.getName()).append(" - Checked: ").append(count.get()).append("; Deleted: ").append(regionsDeleted.get()).toString();
	}

	private Pair<Integer, Integer> parseRegion(String regionFile) {
		String[] split = regionFile.split("\\.");
		// TODO check if it's possible for a region file to be numbered high enough to warrant a long (probably not)
		return new ImmutablePair<Integer, Integer>(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
	}
}
