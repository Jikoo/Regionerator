package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.util.BatchExpirationLoadingCache;
import com.github.jikoo.regionerator.util.Config;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Storing time stamps for chunks made easy.
 *
 * @author Jikoo
 */
public class ChunkFlagger {

	private final Regionerator plugin;
	private final Connection database;
	private final BatchExpirationLoadingCache<String, FlagData> flagCache;

	ChunkFlagger(Regionerator plugin) {
		this.plugin = plugin;
		try {
			Class.forName("org.sqlite.JDBC");
			this.database = DriverManager.getConnection("jdbc:sqlite://" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
			try (Statement st = database.createStatement()) {
				st.executeUpdate("CREATE TABLE IF NOT EXISTS `chunkdata`(`chunk_id` TEXT NOT NULL UNIQUE, `time` BIGINT NOT NULL)");
				// TODO: Create trigger to copy old data to ID+"_old" - need to do more research & testing
				// N.B. REPLACE call actually internally deletes + re-inserts - must be replaced with upserts
				// (ON CONFLICT UPDATE) or trigger will update with replace values instead of just deletes.
				/*st.executeUpdate("CREATE TRIGGER IF NOT EXISTS chunkdataold AFTER DELETE ON chunkdata\n" +
						"BEGIN\n" +
						"DECLARE @chunk_id_old TEXT;\n" +
						"DECLARE @time BIGINT;\n" +
						"SET @chunk_id_old = (SELECT chunk_id FROM deleted);\n" +
						"SET @time = (SELECT time FROM deleted);\n" +
						"IF NOT @chunk_id_old LIKE %old AND @time NOT NULL INSERT INTO chunkdata (chunk_id,time) VALUES (CONCAT(@chunk_id_old, '_old'),@time) ON CONFLICT(chunk_id) UPDATE (time=@time);\n" +
						"END");*/
			}
			this.database.setAutoCommit(false);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("An error occurred while connecting SQLite");
		}
		this.flagCache = new BatchExpirationLoadingCache<>(Math.max(60, plugin.config().getFlagSaveInterval() / 20) * 1000,
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


					try (PreparedStatement st = database.prepareStatement("INSERT INTO chunkdata(chunk_id,time) VALUES (?,?) ON CONFLICT(chunk_id) DO UPDATE SET time=?")) {
						// TODO prepare 2 statements - one drop, one upsert. If lastVisit == -1, drop instead. Allows using queue to drop as well as update
						for (FlagData data : expiredData) {
							st.setString(1, data.chunkId);
							st.setLong(2, data.getLastVisit());
							st.setLong(3, data.getLastVisit());
							st.addBatch();
						}
						st.executeBatch();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				});

		convertOldFlagsFile();
		convertOldPerWorldFlagFiles();

		// Even if cache is stagnant, save every 3 minutes
		Bukkit.getScheduler().runTaskTimer(plugin, flagCache::lazyExpireAll, 20 * 60 * 3, 20 * 60 * 3);
	}

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

	public void flagChunksInRadius(@NotNull String world, int chunkX, int chunkZ) {
		flagChunk(world, chunkX, chunkZ, this.plugin.config().getFlaggingRadius(), this.plugin.config().getFlagVisit());
	}

	public void flagChunk(@NotNull String world, int chunkX, int chunkZ, int radius, long flagTil) {
		for (int dX = -radius; dX <= radius; dX++) {
			for (int dZ = -radius; dZ <= radius; dZ++) {
				flag(world, chunkX + dX, chunkZ + dZ, flagTil);
			}
		}
	}

	private void flag(@NotNull String world, int chunkX, int chunkZ, long flagTil) {
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

	public void unflagRegion(@NotNull String world, int regionX, int regionZ) {
		unflagRegionByLowestChunk(world, Coords.regionToChunk(regionX), Coords.regionToChunk(regionZ));
	}

	public void unflagRegionByLowestChunk(@NotNull String world, int regionLowestChunkX, int regionLowestChunkZ) {
		try (PreparedStatement st = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
			for (int chunkX = regionLowestChunkX; chunkX < regionLowestChunkX + 32; chunkX++) {
				for (int chunkZ = regionLowestChunkZ; chunkZ < regionLowestChunkZ + 32; chunkZ++) {
					String flagDbId = getFlagDbId(world, chunkX, chunkZ);
					this.flagCache.remove(flagDbId);

					st.setString(1, flagDbId);
					st.addBatch();
				}
			}
			st.executeBatch();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void unflagChunk(@NotNull String world, int chunkX, int chunkZ) {
		String flagDBId = getFlagDbId(world, chunkX, chunkZ);
		this.flagCache.remove(flagDBId);
		try (PreparedStatement st = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
			st.setString(1, flagDBId);
			st.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Save all flag files.
	 */
	public void save() {
		flagCache.expireAll();
		try {
			this.database.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public CompletableFuture<FlagData> getChunkFlag(@NotNull World world, int chunkX, int chunkZ) {
		return this.flagCache.get(getFlagDbId(world.getName(), chunkX, chunkZ));
	}

	@NotNull
	@Contract(pure = true)
	private String getFlagDbId(@NotNull String worldName, int chunkX, int chunkZ) {
		return worldName + "_" + chunkX + '_' + chunkZ;
	}

	public static class FlagData {

		private String chunkId;
		private long lastVisit;
		private boolean dirty = false;

		FlagData(@NotNull String chunkId, long lastVisit) {
			this.chunkId = chunkId;
			this.lastVisit = lastVisit;
		}

		@NotNull
		public String getChunkId() {
			return chunkId;
		}

		public long getLastVisit() {
			return lastVisit;
		}

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

