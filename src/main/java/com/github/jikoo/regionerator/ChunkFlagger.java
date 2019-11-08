package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.Pair;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Storing time stamps for chunks made easy.
 *
 * @author Jikoo
 */
public class ChunkFlagger {

	private final Regionerator plugin;
	private final LoadingCache<String, FlagData> flagFileCache;
	private final Connection database;

	ChunkFlagger(Regionerator plugin) {
		this.plugin = plugin;
		try {
			Class.forName("org.sqlite.JDBC").newInstance();
			this.database = DriverManager.getConnection("jdbc:sqlite://" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
			try (Statement st = database.createStatement()) {
				st.executeUpdate("CREATE TABLE IF NOT EXISTS \"chunkdata\"(\"chunk_id\" TEXT NOT NULL UNIQUE, \"time\" INTEGER NOT NULL)");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("An error occurred while connecting SQLite");
		}
		this.flagFileCache = CacheBuilder.newBuilder()
				// Minimum 1 minute cache duration even if flags are saved more often than every 30s
				.expireAfterAccess(Math.max(60, plugin.getTicksPerFlagAutosave() * 10), TimeUnit.SECONDS)
				.removalListener((RemovalListener<String, FlagData>) notification -> {
					// Only attempt to save if dirty to minimize writes
					if (notification.getValue().dirty) {
						try (Statement st = database.createStatement()) {
							st.executeUpdate("INSERT OR REPLACE INTO \"chunkdata\"(\"chunk_id\", \"time\") VALUES ('" + notification.getValue().chunkId + "', '" + notification.getValue().time + "')");
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).build(new CacheLoader<String, FlagData>() {
					@Override
					public FlagData load(@NotNull String key) throws Exception {
						try (Statement st = database.createStatement(); ResultSet rs = st.executeQuery("SELECT \"time\" FROM \"chunkdata\" WHERE \"chunk_id\" = '" + key + "'")) {
							if (rs.next()) {
								return new FlagData(key, rs.getLong(1));
							} else {
								return new FlagData(key, -1);
							}
						}
					}
				});

		convertOldFlagsFile();
		convertOldPerWorldFlagFiles();
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

				FlagData flagData = this.flagFileCache.getUnchecked(this.getFlagDbId(world, chunkX, chunkZ));
				flagData.time = worldSection.getLong(chunkPath);
				flagData.dirty = true;
			}
		}
		// Force save
		this.flagFileCache.invalidateAll();
		// Rename old flag file
		oldFlagsFile.renameTo(new File(oldFlagsFile.getParentFile(), "flags.yml.bak"));
		this.plugin.getLogger().info("Finished converting flags.yml, renamed to flags.yml.bak. Delete at convenience if all appears well.");
	}

	private void convertOldPerWorldFlagFiles() {
		File oldFlagsFolder = new File(this.plugin.getDataFolder(), "flags");
		if (!oldFlagsFolder.exists() || !oldFlagsFolder.isDirectory()) {
			return;
		}
		this.plugin.getLogger().info("Beginning converting flags folder into SQLite.");

		Pattern chunkCoordsSplitter = Pattern.compile("_");

		for (File worldFlagsFolder : oldFlagsFolder.listFiles()) {
			if (!worldFlagsFolder.isDirectory()) continue;

			String worldName = worldFlagsFolder.getName();

			for (File regionFlagsFile : worldFlagsFolder.listFiles()) {
				YamlConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFlagsFile);

				Map<String, Object> values = regionConfig.getValues(false);

				for (Map.Entry<String, Object> entry : values.entrySet()) {
					String[] args = chunkCoordsSplitter.split(entry.getKey());

					int chunkX = Integer.parseInt(args[0]);
					int chunkZ = Integer.parseInt(args[1]);

					FlagData flagData = flagFileCache.getUnchecked(getFlagDbId(worldName, chunkX, chunkZ));
					flagData.time = (Long) entry.getValue();
					flagData.dirty = true;
				}
			}
		}
		// Force save
		this.flagFileCache.invalidateAll();
		// Rename old flag file
		oldFlagsFolder.renameTo(new File(oldFlagsFolder.getParentFile(), "flags.bak"));
		this.plugin.getLogger().info("Finished converting flags folder, renamed to flags.bak. Delete at convenience if all appears well.");
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
		FlagData flagData = this.flagFileCache.getUnchecked(this.getFlagDbId(world, chunkX, chunkZ));
		String chunkPath = this.getChunkPath(chunkX, chunkZ);
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
		for (int chunkX = regionLowestChunkX; chunkX < regionLowestChunkX + 32; chunkX++) {
			for (int chunkZ = regionLowestChunkZ; chunkZ < regionLowestChunkZ + 32; chunkZ++) {
				this.unflagChunk(world, chunkX, chunkZ);
			}
		}
	}

	public void unflagChunk(String world, int chunkX, int chunkZ) {
		FlagData flagData = this.flagFileCache.getUnchecked(getFlagDbId(world, chunkX, chunkZ));
		flagData.time = -1;
		flagData.dirty = false;
		try (Statement st = database.createStatement()) {
			st.executeUpdate("DELETE FROM \"chunkdata\" WHERE \"chunk_id\" = '" + getFlagDbId(world, chunkX, chunkZ) + "'");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Save all flag files.
	 */
	public void save() {
		this.flagFileCache.invalidateAll();
	}

	public VisitStatus getChunkVisitStatus(World world, int chunkX, int chunkZ) {
		FlagData flagData = this.flagFileCache.getUnchecked(getFlagDbId(world.getName(), chunkX, chunkZ));
		String chunkPath = this.getChunkPath(chunkX, chunkZ);
		long visit = flagData.time;
		if (visit != Long.MAX_VALUE && visit > System.currentTimeMillis()) {
			this.plugin.debug(DebugLevel.HIGH, () -> "Chunk " + chunkPath + " is flagged.");

			if (visit == this.plugin.getEternalFlag()) {
				return VisitStatus.PERMANENTLY_FLAGGED;
			}

			return VisitStatus.VISITED;
		}

		for (Hook hook : this.plugin.getProtectionHooks()) {
			if (hook.isChunkProtected(world, chunkX, chunkZ)) {
				this.plugin.debug(DebugLevel.HIGH, () -> "Chunk " + chunkPath + " contains protections by " + hook.getProtectionName());
				return VisitStatus.PROTECTED;
			}
		}

		if (visit == Long.MAX_VALUE) {
			this.plugin.debug(DebugLevel.HIGH, () -> "Chunk " + chunkPath + " has not been visited since it was generated.");
			return VisitStatus.GENERATED;
		}

		return VisitStatus.UNVISITED;
	}

	private File getFlagFile(Pair<String, String> data) {
		File worldFolder = new File(new File(this.plugin.getDataFolder(), "flags"), data.getLeft());
		if (!worldFolder.exists()) {
			worldFolder.mkdirs();
		}
		return new File(worldFolder, data.getRight());
	}

	private Pair<String, String> getFlagFileIdentifier(String world, int chunkX, int chunkZ) {
		return new Pair<>(world,
				String.valueOf(CoordinateConversions.chunkToRegion(chunkX) >> 9) +
						'_' + (CoordinateConversions.chunkToRegion(chunkZ) >> 9) +
						".yml");
	}

	private String getFlagDbId(String worldName, int chunkX, int chunkZ) {
		return worldName + "_" + chunkX + '_' + chunkZ;
	}

	private String getChunkPath(int chunkX, int chunkZ) {
		return String.valueOf(chunkX) + '_' + chunkZ;
	}

	private static class FlagData {
		private String chunkId;
		private long time;
		private boolean dirty = false;

		FlagData(String chunkId, long time) {
			this.chunkId = chunkId;
			this.time = time;
		}
	}

}

