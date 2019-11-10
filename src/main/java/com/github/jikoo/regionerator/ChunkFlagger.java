package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.SimpleLoadingCache;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Storing time stamps for chunks made easy.
 *
 * @author Jikoo
 */
public class ChunkFlagger {

	private final Regionerator plugin;
	private final SimpleLoadingCache<String, FlagData> flagCache;
	private final Connection database;
	private final BukkitTask dbCommitTask;

	ChunkFlagger(Regionerator plugin) {
		this.plugin = plugin;
		try {
			Class.forName("org.sqlite.JDBC");
			this.database = DriverManager.getConnection("jdbc:sqlite://" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
			try (Statement st = database.createStatement()) {
				st.executeUpdate("CREATE TABLE IF NOT EXISTS `chunkdata`(`chunk_id` TEXT NOT NULL UNIQUE, `time` BIGINT NOT NULL)");
			}
			this.database.setAutoCommit(false);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("An error occurred while connecting SQLite");
		}
		this.flagCache = new SimpleLoadingCache<>(Math.max(60, plugin.getTicksPerFlagAutosave() * 10) * 1000,
				key -> {
					try (PreparedStatement st = database.prepareStatement("SELECT time FROM chunkdata WHERE chunk_id=?")) {
						st.setString(1, key);
						try (ResultSet rs = st.executeQuery()) {
							if (rs.next()) {
								return new FlagData(key, rs.getLong(1));
							} else {
								return new FlagData(key, -1);
							}
						}
					} catch (SQLException e) {
						e.printStackTrace();
						return null;
					}
				},
				expiredData -> {
					if (expiredData.stream().noneMatch(FlagData::isDirty)) {
						// Only attempt to save if dirty to minimize writes
						return;
					}

					try (PreparedStatement st = database.prepareStatement("INSERT OR REPLACE INTO chunkdata(chunk_id,time) VALUES (?,?)")) {
						for (FlagData data : expiredData) {
							if (!data.dirty) {
								continue;
							}
							st.setString(1, data.chunkId);
							st.setLong(2, data.time);
							st.addBatch();
						}
						st.executeBatch();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				});

		convertOldFlagsFile();
		convertOldPerWorldFlagFiles();

		// Saving changes to the database every 3 minutes
		this.dbCommitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			try {
				database.commit();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}, 20 * 60 * 3, 20 * 60 * 3);
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

				FlagData flagData = this.flagCache.get(this.getFlagDbId(world, chunkX, chunkZ));
				flagData.time = worldSection.getLong(chunkPath);
				flagData.dirty = true;
			}
		}
		// Force save
		save();
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

					FlagData flagData = flagCache.get(getFlagDbId(worldName, chunkX, chunkZ));
					flagData.time = (Long) entry.getValue();
					flagData.dirty = true;
				}
			}
		}
		// Force save
		save();
		// Rename old flag file
		if (oldFlagsFolder.renameTo(new File(oldFlagsFolder.getParentFile(), "flags.bak"))) {
			this.plugin.getLogger().info("Finished converting flags folder, renamed to flags.bak. Delete at convenience if all appears well.");
		} else {
			this.plugin.getLogger().warning("Finished converting flags folder but could not rename! Conversion will run again on startup.");
		}
	}

	public void flagChunksInRadius(String world, int chunkX, int chunkZ) {
		flagChunk(world, chunkX, chunkZ, this.plugin.getChunkFlagRadius(), this.plugin.getVisitFlag());
	}

	public void flagChunk(String world, int chunkX, int chunkZ, int radius, long flagTil) {
		for (int dX = -radius; dX <= radius; dX++) {
			for (int dZ = -radius; dZ <= radius; dZ++) {
				flag(world, chunkX + dX, chunkZ + dZ, flagTil);
			}
		}
	}

	private void flag(String world, int chunkX, int chunkZ, long flagTil) {
		FlagData flagData = this.flagCache.get(this.getFlagDbId(world, chunkX, chunkZ));
		long current = flagData.time;
		if (current == this.plugin.getEternalFlag()) {
			return;
		}
		flagData.time = flagTil;
		flagData.dirty = true;
	}

	public void unflagRegion(String world, int regionX, int regionZ) {
		unflagRegionByLowestChunk(world, CoordinateConversions.regionToChunk(regionX), CoordinateConversions.regionToChunk(regionZ));
	}

	public void unflagRegionByLowestChunk(String world, int regionLowestChunkX, int regionLowestChunkZ) {
		try (PreparedStatement st = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
			for (int chunkX = regionLowestChunkX; chunkX < regionLowestChunkX + 32; chunkX++) {
				for (int chunkZ = regionLowestChunkZ; chunkZ < regionLowestChunkZ + 32; chunkZ++) {
					String flagDbId = getFlagDbId(world, chunkX, chunkZ);

					st.setString(1, flagDbId);
					st.addBatch();

					FlagData flagData = this.flagCache.getIfPresent(flagDbId);
					if (flagData == null) {
						continue;
					}

					flagData.time = -1;
					flagData.dirty = false;
				}
			}
			st.executeBatch();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void unflagChunk(String world, int chunkX, int chunkZ) {
		FlagData flagData = this.flagCache.get(getFlagDbId(world, chunkX, chunkZ));
		flagData.time = -1;
		flagData.dirty = false;
		try (PreparedStatement st = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
			st.setString(1, getFlagDbId(world, chunkX, chunkZ));
			st.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Save all flag files.
	 */
	public void save() {
		this.flagCache.invalidateAll();
		try {
			this.database.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public VisitStatus getChunkVisitStatus(World world, int chunkX, int chunkZ) {
		FlagData flagData = this.flagCache.get(getFlagDbId(world.getName(), chunkX, chunkZ));
		long visit = flagData.time;
		if (visit != Long.MAX_VALUE && visit > System.currentTimeMillis()) {
			this.plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.chunkId + " is flagged.");

			if (visit == this.plugin.getEternalFlag()) {
				return VisitStatus.PERMANENTLY_FLAGGED;
			}

			return VisitStatus.VISITED;
		}

		for (Hook hook : this.plugin.getProtectionHooks()) {
			if (hook.isChunkProtected(world, chunkX, chunkZ)) {
				this.plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.chunkId + " contains protections by " + hook.getProtectionName());
				return VisitStatus.PROTECTED;
			}
		}

		if (visit == Long.MAX_VALUE) {
			this.plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.chunkId + " has not been visited since it was generated.");
			return VisitStatus.GENERATED;
		}

		return VisitStatus.UNVISITED;
	}

	private String getFlagDbId(String worldName, int chunkX, int chunkZ) {
		return worldName + "_" + chunkX + '_' + chunkZ;
	}

	private static class FlagData {
		private String chunkId;
		private long time;
		private boolean dirty = false;

		FlagData(String chunkId, long time) {
			this.chunkId = chunkId;
			this.time = time;
		}

		boolean isDirty() {
			return dirty;
		}

	}

}

