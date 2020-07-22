package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.database.DatabaseAdapter;
import com.github.jikoo.regionerator.util.BatchExpirationLoadingCache;
import com.github.jikoo.regionerator.util.yaml.Config;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Utility for storing and loading chunk visit timestamps.
 *
 * @author Jikoo
 */
public class ChunkFlagger {

	private final Regionerator plugin;
	private final DatabaseAdapter adapter;
	private final BatchExpirationLoadingCache<String, FlagData> flagCache;

	ChunkFlagger(@NotNull Regionerator plugin) {
		this.plugin = plugin;

		try {
			// Set up database adapter
			adapter = DatabaseAdapter.getAdapter(plugin);
		} catch (Exception e) {
			throw new RuntimeException("An error occurred while setting up the database", e);
		}

		this.flagCache = new BatchExpirationLoadingCache<>(180000, key -> {
			try {
				return new FlagData(key, adapter.get(key));
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "Exception fetching chunk flags", e);
				return new FlagData(key, Config.getFlagEternal());
			}
		}, expiredData -> {
			// Only attempt to save if dirty to minimize write time
			expiredData.removeIf(next -> !next.isDirty());

			if (expiredData.isEmpty()) {
				return;
			}

			try {
				adapter.update(expiredData);

				// Flag as no longer dirty to reduce saves if data is still in use
				expiredData.forEach(flagData -> flagData.dirty = false);
			} catch (Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Exception updating chunk flags", e);
			}
		});

		convertOldFlagsFile();
		convertOldPerWorldFlagFiles();
		flagCache.lazyExpireAll();

		// Even if cache is stagnant, save every 3 minutes
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, flagCache::lazyExpireAll, 3600, 3600);
	}

	/**
	 * Converts old flags file.
	 */
	private void convertOldFlagsFile() {
		File oldFlagsFile = new File(this.plugin.getDataFolder(), "flags.yml");
		if (!oldFlagsFile.exists()) {
			return;
		}
		this.plugin.getLogger().info("Beginning converting flags.yml");
		YamlConfiguration oldFlags = YamlConfiguration.loadConfiguration(oldFlagsFile);
		for (String world : oldFlags.getKeys(false)) {
			if (!oldFlags.isConfigurationSection(world)) {
				continue;
			}

			ConfigurationSection worldSection = oldFlags.getConfigurationSection(world);
			if (worldSection == null) {
				continue;
			}

			for (String chunkPath : worldSection.getKeys(false)) {
				if (!worldSection.isLong(chunkPath)) {
					continue;
				}

				String[] chunk = chunkPath.split("_");
				if (chunk.length != 2) {
					// Invalid data, skip
					continue;
				}

				int chunkX;
				int chunkZ;

				try {
					chunkX = Integer.parseInt(chunk[0]);
					chunkZ = Integer.parseInt(chunk[1]);
				} catch (NumberFormatException e) {
					// Invalid data, skip
					continue;
				}

				FlagData flagData = new FlagData(this.getFlagDbId(world, chunkX, chunkZ), worldSection.getLong(chunkPath));
				flagData.dirty = true;
				flagCache.put(flagData.getChunkId(), flagData);
			}
		}
		// Rename old flag file
		if (oldFlagsFile.renameTo(new File(oldFlagsFile.getParentFile(), "flags.yml.bak"))) {
			this.plugin.getLogger().info("Finished converting flags.yml, renamed to flags.yml.bak. Delete at convenience if all appears well.");
		} else {
			this.plugin.getLogger().warning("Finished converting flags.yml but could not rename! Conversion will run again on startup.");
		}
	}

	/**
	 * Converts old per-region flag files.
	 */
	private void convertOldPerWorldFlagFiles() {
		File oldFlagsFolder = new File(this.plugin.getDataFolder(), "flags");
		if (!oldFlagsFolder.exists() || !oldFlagsFolder.isDirectory()) {
			return;
		}
		this.plugin.getLogger().info("Beginning converting flags folder.");

		Pattern chunkCoordsSplitter = Pattern.compile("_");

		File[] worldDirectories = oldFlagsFolder.listFiles();
		if (worldDirectories == null) {
			return;
		}

		for (File worldFlagsFolder : worldDirectories) {
			if (!worldFlagsFolder.isDirectory()) continue;

			String worldName = worldFlagsFolder.getName();

			File[] regionFlagsFiles = worldFlagsFolder.listFiles();
			if (regionFlagsFiles == null) {
				continue;
			}

			for (File regionFlagsFile : regionFlagsFiles) {
				YamlConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFlagsFile);

				Map<String, Object> values = regionConfig.getValues(false);

				for (Map.Entry<String, Object> entry : values.entrySet()) {
					String[] args = chunkCoordsSplitter.split(entry.getKey());

					int chunkX = Integer.parseInt(args[0]);
					int chunkZ = Integer.parseInt(args[1]);

					FlagData flagData = new FlagData(this.getFlagDbId(worldName, chunkX, chunkZ), (Long) entry.getValue());
					flagData.dirty = true;
					flagCache.put(flagData.getChunkId(), flagData);
				}
			}
		}
		// Rename old flag file
		if (oldFlagsFolder.renameTo(new File(oldFlagsFolder.getParentFile(), "flags.bak"))) {
			this.plugin.getLogger().info("Finished converting flags folder, renamed to flags.bak. Delete at convenience if all appears well.");
		} else {
			this.plugin.getLogger().warning("Finished converting flags folder but could not rename! Conversion will run again on startup.");
		}
	}

	/**
	 * Flags chunks in a radius around the specified chunk according to configured settings.
	 *
	 * @param world the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 */
	public void flagChunksInRadius(@NotNull String world, int chunkX, int chunkZ) {
		flagChunksInRadius(world, chunkX, chunkZ, this.plugin.config().getFlaggingRadius(), this.plugin.config().getFlagVisit());
	}

	public void flagChunksInRadius(@NotNull String world, int chunkX, int chunkZ, int radius, long flagTil) {
		for (int dX = -radius; dX <= radius; dX++) {
			for (int dZ = -radius; dZ <= radius; dZ++) {
				flagChunk(world, chunkX + dX, chunkZ + dZ, flagTil);
			}
		}
	}

	/**
	 * Flags a chunk until the specified time.
	 *
	 * @param world the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @param flagTil the flag timestamp
	 */
	public void flagChunk(@NotNull String world, int chunkX, int chunkZ, long flagTil) {
		String flagDbId = this.getFlagDbId(world, chunkX, chunkZ);
		FlagData flagData = this.flagCache.getIfPresent(flagDbId);
		if (flagData != null) {
			long current = flagData.getLastVisit();
			if (current == Config.getFlagEternal()) {
				return;
			}
			flagData.lastVisit = flagTil;
		} else {
			flagData = new FlagData(flagDbId, flagTil);
			flagCache.put(flagDbId, flagData);
		}
		flagData.dirty = true;
	}

	/**
	 * Unflags an entire region.
	 *
	 * @param world the world name
	 * @param regionLowestChunkX the lowest chunk X coordinate in the region
	 * @param regionLowestChunkZ the lowest chunk Z coordinate in the region
	 */
	public void unflagRegionByLowestChunk(@NotNull String world, int regionLowestChunkX, int regionLowestChunkZ) {
		for (int chunkX = regionLowestChunkX; chunkX < regionLowestChunkX + 32; chunkX++) {
			for (int chunkZ = regionLowestChunkZ; chunkZ < regionLowestChunkZ + 32; chunkZ++) {
				unflagChunk(world, chunkX, chunkZ);
			}
		}
	}

	/**
	 * Unflags a chunk.
	 *
	 * @param world the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 */
	public void unflagChunk(@NotNull String world, int chunkX, int chunkZ) {
		String flagDBId = getFlagDbId(world, chunkX, chunkZ);
		FlagData flagData = new FlagData(flagDBId, -1);
		flagData.dirty = true;
		flagCache.put(flagDBId, flagData);
	}

	/**
	 * Force a save of all flags and close the connection.
	 */
	void shutdown() {
		flagCache.expireAll();
		adapter.close();
	}

	/**
	 * Gets the number of entries loaded in the flag cache.
	 *
	 * @return the flag cache size
	 */
	public int getCached() {
		return flagCache.getCached();
	}

	/**
	 * Gets the number of entries queued to be removed from the flag cache.
	 *
	 * @return the flag cache deletion queue size
	 */
	public int getQueued() {
		return flagCache.getQueued();
	}

	/**
	 * Gets a {@link CompletableFuture} providing a chunk's {@link FlagData} from the database.
	 *
	 * @param world the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @return a CompletableFuture supplying a FlagData
	 */
	public CompletableFuture<FlagData> getChunkFlag(@NotNull World world, int chunkX, int chunkZ) {
		return this.flagCache.get(getFlagDbId(world.getName(), chunkX, chunkZ));
	}

	/**
	 * Gets a {@link CompletableFuture} providing a chunk's {@link FlagData} as of last delete from the database.
	 *
	 * @param world the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @return a {@link CompletableFuture<FlagData>}
	 */
	public CompletableFuture<FlagData> getChunkFlagOnDelete(@NotNull World world, int chunkX, int chunkZ) {
		return this.flagCache.get(getFlagDbId(world.getName(), chunkX, chunkZ) + "_old");
	}

	 /**
	 * Gets a unique identifier for a chunk for use in the database.
	 *
	 * @param worldName the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @return the ID of the chunk for database use
	 */
	@NotNull
	@Contract(pure = true)
	private String getFlagDbId(@NotNull String worldName, int chunkX, int chunkZ) {
		return worldName + "_" + chunkX + '_' + chunkZ;
	}

	/**
	 * A container for a chunk's visit time.
	 */
	public static class FlagData {

		private final String chunkId;
		private long lastVisit;
		private boolean dirty = false;

		FlagData(@NotNull String chunkId, long lastVisit) {
			this.chunkId = chunkId;
			this.lastVisit = lastVisit;
		}

		/**
		 * Gets the chunk's database identifier.
		 *
		 * @return the chunk's database identifier
		 */
		@NotNull
		public String getChunkId() {
			return chunkId;
		}

		/**
		 * Gets the chunk's last visit timestamp.
		 *
		 * @return the chunk's last visit timestamp.
		 */
		public long getLastVisit() {
			return lastVisit;
		}

		/**
		 * Gets whether or not the chunk data needs to be saved to the database.
		 *
		 * @return true if the chunk data has not been saved
		 */
		boolean isDirty() {
			return dirty;
		}

		@Override
		public int hashCode() {
			return chunkId.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj != null && getClass().equals(obj.getClass()) && ((FlagData) obj).chunkId.equals(chunkId);
		}
	}

}

