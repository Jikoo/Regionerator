package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.util.BatchExpirationLoadingCache;
import com.github.jikoo.regionerator.util.Config;
import java.io.File;
import java.nio.file.AccessDeniedException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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
	private final Connection database;
	private final BatchExpirationLoadingCache<String, FlagData> flagCache;
	private final Logger logger;

	ChunkFlagger(Regionerator plugin) {
		this.plugin = plugin;

		try {
			// Set up logger
			this.logger = Logger.getLogger("Regionerator-DB");

			File errors = new File(plugin.getDataFolder(), "logs");
			if (!errors.mkdirs()) {
				throw new AccessDeniedException(errors.getPath());
			}
			Handler handler = new FileHandler(plugin.getDataFolder().getPath() + File.separatorChar + "logs" + File.separatorChar + "error-log-%g-%u.log",
					10 * 1024 * 1024, 10, false);
			handler.setLevel(Level.WARNING);
			handler.setFilter(record -> record.getThrown() != null);
			handler.setFormatter(new Formatter() {
				final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("'['yyyy-MM-dd HH:MM:ss'] '");
				@Override
				public String format(LogRecord record) {
					StringBuilder builder = new StringBuilder(simpleDateFormat.format(new Date(record.getMillis())));
					builder.append(record.getThrown().getMessage());
					for (StackTraceElement element : record.getThrown().getStackTrace()) {
						builder.append("\n\t").append(element.toString());
					}
					if (record.getThrown().getCause() != null) {
						builder.append("\nCaused by: ").append(record.getThrown().getCause().getMessage());
						for (StackTraceElement element : record.getThrown().getCause().getStackTrace()) {
							builder.append("\n\t").append(element.toString());
						}
					}
					return builder.append('\n').toString();
				}
			});
			logger.addHandler(handler);

			// Set up SQLite database
			Class.forName("org.sqlite.JDBC");
			this.database = DriverManager.getConnection("jdbc:sqlite://" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
			try (Statement st = database.createStatement()) {
				st.executeUpdate("CREATE TABLE IF NOT EXISTS `chunkdata`(`chunk_id` TEXT NOT NULL UNIQUE, `time` BIGINT NOT NULL)");
				st.executeUpdate(
						"CREATE TRIGGER IF NOT EXISTS chunkdataold\n" +
						"AFTER DELETE ON chunkdata\n" +
						"WHEN OLD.time NOT NULL AND OLD.chunk_id NOT LIKE '%_old'"+
						"BEGIN\n" +
						"INSERT INTO chunkdata (chunk_id,time) VALUES (OLD.chunk_id || '_old',OLD.time) ON CONFLICT(chunk_id) DO UPDATE SET `time`=OLD.time;\n" +
						"END");
			}
			this.database.setAutoCommit(false);
		} catch (Exception e) {
			throw new RuntimeException("An error occurred while setting up the chunk flagger", e);
		}

		this.flagCache = new BatchExpirationLoadingCache<>(Math.max(300000, plugin.config().getMillisBetweenFlagSave()),
				key -> {
					try (PreparedStatement st = database.prepareStatement("SELECT time FROM chunkdata WHERE chunk_id=?")) {
						st.setString(1, key);
						try (ResultSet rs = st.executeQuery()) {
							if (rs.next()) {
								return new FlagData(key, rs.getLong(1));
							} else {
								return new FlagData(key, Config.getFlagDefault());
							}
						}
					} catch (SQLException e) {
						e.printStackTrace();
						return null;
					}
				},
				expiredData -> {
					// Only attempt to save if dirty to minimize write time
					expiredData.removeIf(next -> !next.isDirty());

					if (expiredData.isEmpty()) {
						return;
					}

					try (PreparedStatement upsert = database.prepareStatement("INSERT INTO chunkdata(chunk_id,time) VALUES (?,?) ON CONFLICT(chunk_id) DO UPDATE SET time=?");
							PreparedStatement delete = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
						for (FlagData data : expiredData) {
							if (data.getLastVisit() == Config.getFlagDefault()) {
								delete.setString(1, data.getChunkId());
								delete.addBatch();
							} else {
								upsert.setString(1, data.getChunkId());
								upsert.setLong(2, data.getLastVisit());
								upsert.setLong(3, data.getLastVisit());
								upsert.addBatch();
							}
						}
						delete.executeBatch();
						upsert.executeBatch();
						this.database.commit();

						// Flag as no longer dirty to reduce saves if data is still in use
						expiredData.forEach(flagData -> flagData.dirty = false);
					} catch (SQLException e) {
						logger.log(Level.WARNING, "Exception updating chunk flags", e);
					}
				});

		convertOldFlagsFile();
		convertOldPerWorldFlagFiles();
		flagCache.lazyExpireAll();

		// Even if cache is stagnant, save every 3 minutes
		Bukkit.getScheduler().runTaskTimer(plugin, flagCache::lazyExpireAll, 20 * 60 * 3, 20 * 60 * 3);
	}

	/**
	 * Converts old flags file to SQLite.
	 */
	private void convertOldFlagsFile() {
		File oldFlagsFile = new File(this.plugin.getDataFolder(), "flags.yml");
		if (!oldFlagsFile.exists()) {
			return;
		}
		this.plugin.getLogger().info("Beginning converting flags.yml into SQLite.");
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
	 * Converts old per-region flag files to SQLite.
	 */
	private void convertOldPerWorldFlagFiles() {
		File oldFlagsFolder = new File(this.plugin.getDataFolder(), "flags");
		if (!oldFlagsFolder.exists() || !oldFlagsFolder.isDirectory()) {
			return;
		}
		this.plugin.getLogger().info("Beginning converting flags folder into SQLite.");

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
		FlagData flagData = this.flagCache.getIfPresent(flagDbId); //TODO need to change call to not wipe eternal flag in this case
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
		try {
			database.commit();
			database.close();
		} catch (SQLException e) {
			logger.log(Level.WARNING, "Exception committing to and closing DB connection", e);
		}
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

		private String chunkId;
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

