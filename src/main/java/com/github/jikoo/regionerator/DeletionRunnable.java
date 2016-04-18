package com.github.jikoo.regionerator;

import java.io.File;
import java.io.FileFilter;
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
 * 
 * 
 * @author Jikoo
 */
public class DeletionRunnable extends BukkitRunnable {

	private static final String STATS_FORMAT = "%s - Checked %s/%s, deleted %s regions and %s chunks";

	private final Regionerator plugin;
	private final World world;
	private final File regionDir;
	private final String[] regionFileNames;
	private final int chunksPerCheck;
	private final ArrayList<Pair<Integer, Integer>> regionChunks = new ArrayList<>();

	private int count = 0, regionChunkX, regionChunkZ, localChunkX = 0, localChunkZ = 0, regionsDeleted = 0, chunksDeleted = 0;
	private long nextRun = Long.MAX_VALUE;

	public DeletionRunnable(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		this.regionDir = this.findRegionFolder();
		this.regionFileNames = this.regionDir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				// r.0.0.mca, r.-1.0.mca, etc.
				return name.matches("r\\.-?\\d+\\.-?\\d+\\.mca");
			}
		});
		this.chunksPerCheck = plugin.getChunksPerDeletionCheck();

		if (regionFileNames.length > 0) {
			Pair<Integer, Integer> regionLowestChunk = CoordinateConversions.getRegionChunkCoords(regionFileNames[0]);
			regionChunkX = regionLowestChunk.getLeft();
			regionChunkZ = regionLowestChunk.getRight();
		}
	}

	/**
	 * Gets the region directory containing all region files for the World.
	 * 
	 * @return the region directory
	 * 
	 * @throws IllegalStateException if the directory cannot be found
	 */
	private File findRegionFolder() {
		File worldDir = this.world.getWorldFolder();

		// Select files matching either the region file itself or a dimension name
		File[] subdir = worldDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory() && (file.getName().startsWith("DIM") || file.getName().equals("region"));
			}
		});

		// Try to match the region folder first
		for (File file : subdir) {
			if (file.getName().equals("region")) {
				return file;
			}
		}

		// No luck, search all subdirectories (DIMX) for a region folder
		// TODO better Cauldron/other mod support - this will select the first available folder, not world ID
		for (File file : subdir) {
			File[] subsubdir = file.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isDirectory() && file.getName().equals("region");
				}
			});
			if (subsubdir.length > 0) {
				return subsubdir[0];
			}
		}

		throw new IllegalStateException("Unable to find region folder for world " + world.getName());
	}

	@Override
	public void run() {
		if (count >= regionFileNames.length) {
			plugin.getLogger().info("Regeneration cycle complete for " + getRunStats());
			nextRun = System.currentTimeMillis() + plugin.getMillisecondsBetweenDeletionCycles();
			this.cancel();
			return;
		}

		// Only pause once the region is fully checked - who knows how long we'll be paused for?
		if (plugin.isPaused() && localChunkX == 0 && localChunkZ == 0) {
			return;
		}


		for (int i = 0; i < chunksPerCheck && count < regionFileNames.length; ++i) {
			/*
			 * Deletion is, by far, the worst offender for lag. It has to be done on the main thread
			 * or we may risk corruption, so to combat any issues we'll dedicate an entire run cycle
			 * to deletion unless the user has configured the plugin to check more than an entire
			 * region per run.
			 */
			if (chunksPerCheck <= 1024 && i > 0 && localChunkZ >= 32) {
				// Deletion next check
				return;
			}
			checkNextChunk();
			if (chunksPerCheck <= 1024 && localChunkX == 0 && localChunkZ == 0) {
				// Deletion this check
				return;
			}
		}
	}

	/**
	 * Check the next chunk in the current region. If all chunks have been checked, region
	 * completion will be started instead.
	 */
	private void checkNextChunk() {
		// If we're paused and done checking a region, it's time to stop.
		if (plugin.isPaused() && localChunkX == 0 && localChunkZ == 0) {
			return;
		}

		// Have all regions been checked?
		if (count >= regionFileNames.length) {
			return;
		}

		// If 32 chunks are checked in the X direction, increment Z and reset X to 0
		if (localChunkX >= 32) {
			localChunkX = 0;
			localChunkZ++;
		}

		// If 32x32 chunks are checked in X and Z, handle deletion and reset
		if (localChunkZ >= 32) {
			handleRegionCompletion();
			// If deleting up to 1 entire region per check, devote an entire run cycle to deletion
			if (plugin.isPaused() || chunksPerCheck <= 1024) {
				return;
			}
		}

		int chunkX = regionChunkX + localChunkX;
		int chunkZ = regionChunkZ + localChunkZ;
		VisitStatus status = plugin.getFlagger().getChunkVisitStatus(world, chunkX, chunkZ);
		// TODO eliminate enum ordinal comparison for safety in case someone decompiles Regionerator?
		// Seems like a minimal risk, it's open source, but could lead to disaster for them
		if (status.ordinal() < VisitStatus.GENERATED.ordinal()
				|| (plugin.getGenerateFlag() != Long.MAX_VALUE && status == VisitStatus.GENERATED)) {
			// Add chunks if unvisited and unprotected or (configurably) freshly generated
			regionChunks.add(new ImmutablePair<Integer, Integer>(localChunkX, localChunkZ));
		}
		localChunkX++;
	}

	/**
	 * Handle any deletion for the completed region, then increment to the next region file and reset.
	 */
	private void handleRegionCompletion() {
		ListIterator<Pair<Integer, Integer>> iterator = regionChunks.listIterator();

		// Remove all loaded chunks - there's no sense trying to delete them; they're guaranteed to be rewritten.
		while (iterator.hasNext()) {
			Pair<Integer, Integer> chunkCoords = iterator.next();
			if (world.isChunkLoaded(regionChunkX + chunkCoords.getLeft(), regionChunkZ + chunkCoords.getRight())) {
				iterator.remove();
			}
		}

		if (regionChunks.size() == 1024) {
			// Delete entire region
			deleteRegion(iterator);
		} else if (regionChunks.size() > 0) {
			// Delete individual chunks
			deleteChunks(iterator);
		}

		regionChunks.clear();
		count++;
		if (plugin.debug(DebugLevel.LOW) && count % 20 == 0 && count > 0) {
			plugin.debug(getRunStats());
		}
		if (count < regionFileNames.length) {
			localChunkX = 0;
			localChunkZ = 0;
			Pair<Integer, Integer> regionLowestChunk = CoordinateConversions.getRegionChunkCoords(regionFileNames[0]);
			regionChunkX = regionLowestChunk.getLeft();
			regionChunkZ = regionLowestChunk.getRight();

			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug(String.format("Checking %s:%s (%s/%s)", world.getName(),
						regionFileNames[count], count, regionFileNames.length));
			}
		}
	}

	/**
	 * Delete the file for the current region and fire the relevant RegioneratorChunkDeleteEvents.
	 * 
	 * @param iterator the ListIterator of local chunk coordinates
	 */
	private void deleteRegion(ListIterator<Pair<Integer, Integer>> iterator) {
		String regionFileName = regionFileNames[count];
		File regionFile = new File(regionDir, regionFileName);
		if (regionFile.exists() && regionFile.delete()) {
			regionsDeleted++;
			if (plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(regionFileName + " deleted from " + world.getName());
			}

			// Unflag all chunks in the region, no need to inflate flags.yml
			plugin.getFlagger().unflagRegionByLowestChunk(world.getName(), regionChunkX, regionChunkZ);
		} else if (plugin.debug(DebugLevel.MEDIUM)) {
			plugin.debug(String.format("Unable to delete %s from %s",
					regionFileName, world.getName()));
		}
		// 
		while (iterator.hasPrevious()) {
			Pair<Integer, Integer> chunkCoords = iterator.previous();
			plugin.getServer().getPluginManager().callEvent(
					new RegioneratorChunkDeleteEvent(world, chunkCoords.getLeft(), chunkCoords.getRight()));
		}
	}

	/**
	 * Delete individual chunks in the current region and fire the relevant
	 * RegioneratorChunkDeleteEvents.
	 * 
	 * @param iterator the ListIterator of local chunk coordinates
	 */
	private void deleteChunks(ListIterator<Pair<Integer, Integer>> iterator) {
		String regionFileName = regionFileNames[count];
		File regionFile = new File(regionDir, regionFileName);

		if (!regionFile.canWrite() && !regionFile.setWritable(true) && !regionFile.canWrite()) {
			if (plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(String.format("Unable to set %s in %s writable to delete %s chunks",
						regionFileName, world.getName(), regionChunks.size()));
			}
			return;
		}

		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "rwd")) {
			byte[] pointers = new byte[4096];
			// Read all chunk pointers into the byte array
			regionRandomAccess.read(pointers);

			int chunkCount = 0;

			while (iterator.hasPrevious()) {
				Pair<Integer, Integer> localChunkCoords = iterator.previous();
				int chunkX = regionChunkX + localChunkCoords.getLeft();
				int chunkZ = regionChunkZ + localChunkCoords.getRight();

				// Chunk is delete-eligible even if deletion fails, no need to wait to remove data
				plugin.getFlagger().unflagChunk(world.getName(), chunkX, chunkZ);

				// Pointers for chunks are 4 byte integers stored at coordinates relative to the region file itself.
				int pointer = 4 * (localChunkCoords.getRight() * 32 + localChunkCoords.getLeft());

				boolean orphaned = true;
				for (int i = pointer; i < pointer + 4; ++i) {
					if (pointers[i] != 0) {
						orphaned = false;
						pointers[i] = 0;
					}
				}

				// Chunk is already orphaned, continue on.
				if (orphaned) {
					continue;
				}

				// Rather than loop through all our chunks again, call deletion event now
				plugin.getServer().getPluginManager().callEvent(
						new RegioneratorChunkDeleteEvent(world, chunkX, chunkZ));

				if (plugin.debug(DebugLevel.HIGH)) {
					plugin.debug(String.format("Wiping chunk %s, %s from %s in %s of %s",
							chunkX, chunkZ, pointer, regionFileName, world.getName()));
				}

				++chunkCount;
			}

			// Overwrite all chunk pointers - this is much faster than seeking.
			regionRandomAccess.write(pointers, 0, 4096);

			regionRandomAccess.close();

			chunksDeleted += chunkCount;

			if (chunkCount > 0 && plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(String.format("%s chunks deleted from %s of %s", chunkCount, regionFileName, world.getName()));
			}

		} catch (IOException ex) {
			if (plugin.debug(DebugLevel.MEDIUM)) {
				plugin.debug(String.format("Caught an IOException writing %s in %s to delete %s chunks",
						regionFileName, world.getName(), regionChunks.size()));
			}
		}
	}

	public String getRunStats() {
		return String.format(STATS_FORMAT, world.getName(), count, regionFileNames.length, regionsDeleted, chunksDeleted);
	}

	public long getNextRun() {
		return nextRun;
	}

}
