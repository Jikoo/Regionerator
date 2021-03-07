package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.database.DatabaseAdapter;
import com.github.jikoo.regionerator.util.BatchExpirationLoadingCache;
import com.github.jikoo.regionerator.util.yaml.Config;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

		Config config = plugin.config();
		this.flagCache = new BatchExpirationLoadingCache.Builder<String, FlagData>()
				.setRetention(config.getCacheRetention())
				.setCacheMax(config.getCacheMaxSize())
				.setFrequency(config.getCacheExpirationFrequency())
				.setBatchMax(config.getCacheBatchMax())
				.setBatchDelay(config.getCacheBatchDelay())
				.build(this::loadFlag, this::expireBatch);

		convertOldFlagsFile();
		convertOldPerWorldFlagFiles();
		flagCache.lazyExpireAll();

		// Even if cache is stagnant, save every 10 minutes
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, flagCache::lazyExpireAll, 10 * 60 * 20, 10 * 60 * 20);
	}

	/**
	 * For use in cache. Don't call manually.
	 *
	 * @param key the key of the FlagData
	 * @return the FlagData
	 */
	private FlagData loadFlag(String key) {
		try {
			return new FlagData(key, adapter.get(key));
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Exception fetching chunk flags", e);
			return new FlagData(key, Config.FLAG_OH_NO);
		}
	}

	/**
	 * For use in cache. Don't call manually.
	 *
	 * @param expiredData the batch of expired FlagData
	 */
	private void expireBatch(Collection<FlagData> expiredData) {
		// Only attempt to save if dirty to minimize write time
		expiredData.removeIf(next -> !next.isDirty() || next.getLastVisit() == Config.FLAG_OH_NO);

		if (expiredData.isEmpty()) {
			return;
		}

		try {
			adapter.update(expiredData);

			// Flag as no longer dirty to reduce saves if data is still in use
			expiredData.forEach(FlagData::wash);
		} catch (Exception e) {
			plugin.getLogger().log(Level.SEVERE, "Exception updating chunk flags", e);
		}
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

				flagCache.get(this.getFlagDbId(world, chunkX, chunkZ))
						.thenAccept(flagData -> flagData.importOldValue(worldSection.getLong(chunkPath)));
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

					flagCache.get(this.getFlagDbId(worldName, chunkX, chunkZ))
							.thenAccept(flagData -> flagData.importOldValue((long) entry.getValue()));
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
		flagChunksInRadius(world, chunkX, chunkZ, this.plugin.config().getFlaggingRadius(), this.plugin.config().getFlagVisit(world));
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
			if (current == Config.FLAG_ETERNAL) {
				return;
			}
			flagData.setLastVisit(flagTil);
		} else {
			flagData = new FlagData(flagDbId, flagTil, true);
			flagCache.put(flagDbId, flagData);
		}
	}

	/**
	 * @deprecated Just un-flag each chunk, odds are on that you already have a collection of chunk data to iterate over
	 * Unflags an entire region.
	 *
	 * @param world the world name
	 * @param regionLowestChunkX the lowest chunk X coordinate in the region
	 * @param regionLowestChunkZ the lowest chunk Z coordinate in the region
	 */
	@Deprecated
	public void unflagRegionByLowestChunk(@NotNull String world, int regionLowestChunkX, int regionLowestChunkZ) {
		for (int chunkX = regionLowestChunkX; chunkX < regionLowestChunkX + 32; chunkX++) {
			for (int chunkZ = regionLowestChunkZ; chunkZ < regionLowestChunkZ + 32; chunkZ++) {
				unflagChunk(world, chunkX, chunkZ);
			}
		}
	}

	/**
	 * Removes flags from a chunk.
	 *
	 * @param world the world name
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 */
	public void unflagChunk(@NotNull String world, int chunkX, int chunkZ) {
		flagCache.computeIfAbsent(getFlagDbId(world, chunkX, chunkZ), key -> new FlagData(key, Config.FLAG_DEFAULT, true))
				.setLastVisit(Config.FLAG_DEFAULT);
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
		return this.flagCache.get(getFlagDbId(world.getName(), chunkX, chunkZ)).thenApply(flagData -> {
			// Ensure changing config value allows deleting fresh chunks.
			if (flagData.getLastVisit() == Long.MAX_VALUE && plugin.config().isDeleteFreshChunks(world)) {
				flagData.setLastVisit(Config.FLAG_DEFAULT);
			}
			return flagData;
		});
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
		private final AtomicLong lastVisit;
		private final AtomicBoolean dirty;

		private FlagData(@NotNull String chunkId, long lastVisit) {
			this(chunkId, lastVisit, false);
		}

		private FlagData(@NotNull String chunkId, long lastVisit, boolean dirty) {
			this.chunkId = chunkId;
			this.lastVisit = new AtomicLong(lastVisit);
			this.dirty = new AtomicBoolean(dirty);
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
		 * @return the chunk's last visit timestamp
		 */
		public long getLastVisit() {
			return lastVisit.get();
		}

		/**
		 * Sets the chunk's last visit timestamp.
		 *
		 * @param lastVisit the chunk's last visit timestamp
		 */
		private void setLastVisit(long lastVisit) {
			if (this.lastVisit.getAndSet(lastVisit) != lastVisit) {
				this.dirty.set(true);
			}
		}

		/**
		 * Imports an old value. For use in data conversion.
		 *
		 * @param oldValue the imported old value
		 */
		private void importOldValue(long oldValue) {
			// Ignore default values.
			if (oldValue == Config.FLAG_DEFAULT) {
				return;
			}

			if (oldValue == Long.MAX_VALUE) {
				// Only set fresh generated flag if current value is default.
				if (this.lastVisit.compareAndSet(Config.FLAG_DEFAULT, oldValue)) {
					this.dirty.set(true);
				}
				return;
			}

			if (this.lastVisit.updateAndGet(currentValue -> Math.max(currentValue, oldValue)) == oldValue) {
				this.dirty.set(true);
			}
		}

		/**
		 * Gets whether or not the chunk data needs to be saved to the database.
		 *
		 * @return true if the chunk data has not been saved
		 */
		private boolean isDirty() {
			return dirty.get();
		}

		/**
		 * Hello yes this is Joseph King how can I help you?
		 */
		private void wash() {
			dirty.set(false);
		}

		@Override
		public int hashCode() {
			return Objects.hash(chunkId, lastVisit, dirty);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			FlagData other = (FlagData) obj;
			return lastVisit.equals(other.lastVisit) && dirty.equals(other.dirty) && chunkId.equals(other.chunkId);
		}
	}

}

