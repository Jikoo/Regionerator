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

	private final World world;
	private final String[] regions;
	private int count = 0;

	public DeletionRunnable(World world) {
		this.world = world;
		File regionFolder = new File(world.getWorldFolder(), "region");
		if (!regionFolder.exists()) {
			throw new RuntimeException("World " + world.getName() + " has no generated terrain!");
		}
		regions = regionFolder.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				// r.0.0.mca, r.-1.0.mca, etc.
				return name.matches("r\\.-?\\d+\\.-?\\d+\\.mca");
			}
		});
	}

	@Override
	public void run() {
		if (count > regions.length) {
			// TODO report finalized stats
			// TODO schedule next run (config option)
			this.cancel();
			return;
		}
		region: for (int i = 0; i < Regionerator.getInstance().getRegionsPerCheck(); i++) {
			Pair<Integer, Integer> regionCoordinates = parseRegion(regions[count]);
			count++;
			for (int chunkX = regionCoordinates.getLeft(); chunkX < regionCoordinates.getLeft() + 32; chunkX++) {
				for (int chunkZ = regionCoordinates.getRight(); chunkZ < regionCoordinates.getRight() + 32; chunkZ++) {
					if (world.isChunkLoaded(chunkX, chunkZ)) {
						continue region;
					}
					// TODO check chunk for flag
					for (Hook hook : Regionerator.getInstance().getProtectionHooks()) {
						if (hook.isChunkProtected(chunkX, chunkZ)) {
							continue region;
						}
					}
					// TODO delete region file
				}
			}
		}
	}

	private Pair<Integer, Integer> parseRegion(String regionFile) {
		String[] split = regionFile.split("\\.");
		// TODO check if it's possible for a region file to be numbered high enough to warrant a long (probably not)
		return new ImmutablePair<Integer, Integer>(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
	}
}
