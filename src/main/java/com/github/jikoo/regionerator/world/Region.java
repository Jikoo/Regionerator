package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.VisitStatus;
import com.github.jikoo.regionerator.event.RegioneratorChunkDeleteEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public abstract class Region {

	private final World world;
	private final File regionFile;
	private final int lowestChunkX, lowestChunkZ;
	protected final List<ChunkData> chunks;
	private int chunkIndex = 0;
	protected boolean populated = false;
	private boolean deleted = false;

	protected Region(@NotNull World world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) {
		this.world = world;
		this.regionFile = regionFile;
		this.lowestChunkX = lowestChunkX;
		this.lowestChunkZ = lowestChunkZ;

		// Regions are composed of 32x32 chunks, construct list with required capacity
		chunks = new ArrayList<>(1024);
	}

	public boolean isPopulated() {
		return populated;
	}

	public abstract void populate(Regionerator plugin) throws IOException;

	public File getRegionFile() {
		return regionFile;
	}

	public World getWorld() {
		return world;
	}

	public int getLowestChunkX() {
		return lowestChunkX;
	}

	public int getLowestChunkZ() {
		return lowestChunkZ;
	}

	public boolean isCompletelyChecked() {
		return this.chunkIndex >= chunks.size();
	}

	public void checkChunks(Regionerator plugin) {
		if (!isPopulated()) {
			throw new IllegalStateException("Region cannot be checked before being populated!");
		}
		for (int i = 0; i < plugin.getChunksPerDeletionCheck() && chunkIndex < chunks.size(); ++i, ++chunkIndex) {
			if (!checkChunk(plugin, chunkIndex)) {
				--i;
			}
		}
	}

	private boolean checkChunk(Regionerator plugin, int i) {
		ChunkData chunkData = chunks.get(i);

		if (chunkData.isOrphaned()) {
			chunkData.setVisitStatus(VisitStatus.ORPHANED);
			return false;
		}

		int chunkX = lowestChunkX + chunkData.getLocalChunkX();
		int chunkZ = lowestChunkZ + chunkData.getLocalChunkZ();
		VisitStatus chunkFlagStatus = plugin.getFlagger().getChunkVisitStatus(world, chunkX, chunkZ).join();
		chunkData.setVisitStatus(chunkFlagStatus);
		return true;
	}

	public boolean hasDeletionRun() {
		return deleted;
	}

	public final int deleteChunks(Regionerator plugin) {
		if (!this.isCompletelyChecked()) {
			throw new IllegalStateException("Cannot delete until region is completely checked!");
		}
		if (deleted) {
			return 0;
		}
		deleted = true;

		if (!chunks.removeIf(chunkData -> chunkData.getVisitStatus().ordinal() >= VisitStatus.VISITED.ordinal())) {

			if (!deleteRegion(plugin)) {
				plugin.debug(DebugLevel.MEDIUM, () -> String.format("Unable to delete %s from %s", getRegionFile().getName(), world.getName()));
				return 0;
			} else {
				plugin.debug(DebugLevel.MEDIUM, () -> getRegionFile().getName() + " deleted from " + world.getName());
			}

			// Fire a RegioneratorChunkDeleteEvent for each chunk in region
			for (int dX = 0; dX < 32; ++dX) {
				for (int dZ = 0; dZ < 32; ++dZ) {
					plugin.getServer().getPluginManager().callEvent(
							new RegioneratorChunkDeleteEvent(world, lowestChunkX + dX, lowestChunkZ + dZ));
				}
			}

			// Unflag all chunks in the region, no need to inflate flag file
			plugin.getFlagger().unflagRegionByLowestChunk(world.getName(), lowestChunkX, lowestChunkZ);
			return 1024;
		} else {
			if (plugin.getGenerateFlag() != Long.MAX_VALUE) {
				chunks.removeIf(chunkData -> chunkData.getVisitStatus() == VisitStatus.GENERATED);
			}
			for (ChunkData chunk : chunks) {
				plugin.getServer().getPluginManager().callEvent(new RegioneratorChunkDeleteEvent(world,
						lowestChunkX + chunk.getLocalChunkX(), lowestChunkZ + chunk.getLocalChunkZ()));
			}
			try {
				return deleteChunks(plugin, chunks);
			} catch (Exception e) {
				plugin.debug(DebugLevel.LOW, () -> String.format("Caught an exception writing %s in %s to delete %s chunks",
						getRegionFile().getName(), getWorld().getName(), chunks.size()));
				e.printStackTrace();
				return 0;
			}
		}
	}

	protected boolean deleteRegion(Regionerator plugin) {
		return regionFile.exists() && getRegionFile().delete();
	}

	protected abstract int deleteChunks(Regionerator plugin, Collection<ChunkData> chunks) throws Exception;

}
