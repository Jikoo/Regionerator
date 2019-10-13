package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.Pair;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Storing time stamps for chunks made easy.
 * 
 * @author Jikoo
 */
public class ChunkFlagger {

	private final Regionerator plugin;
	private final LoadingCache<Pair<String, String>, Pair<YamlConfiguration, Boolean>> flagFileCache;
	private final HashMap<Pair<String, String>, YamlConfiguration> saveQueue;
	private BukkitTask saveTask = null;

	ChunkFlagger(Regionerator plugin) {
		this.plugin = plugin;
		this.saveQueue = new HashMap<>();
		this.flagFileCache = CacheBuilder.newBuilder()
				// Minimum 1 minute cache duration even if flags are saved more often than every 30s
				.expireAfterAccess(Math.max(60, plugin.getTicksPerFlagAutosave() * 10), TimeUnit.SECONDS)
				.removalListener(new RemovalListener<Pair<String, String>, Pair<YamlConfiguration, Boolean>>() {
					@Override
					public void onRemoval(@NotNull RemovalNotification<Pair<String, String>, Pair<YamlConfiguration, Boolean>> notification) {
						// Only attempt to save if dirty to minimize writes
						if (notification.getValue().getRight()) {
							saveQueue.put(notification.getKey(), notification.getValue().getLeft());
							startSaveQueue();
						}
					}
				}).build(new CacheLoader<Pair<String, String>, Pair<YamlConfiguration, Boolean>>() {
					@Override
					public Pair<YamlConfiguration, Boolean> load(@NotNull Pair<String, String> key) throws Exception {
						if (saveQueue.containsKey(key)) {
							return new Pair<>(saveQueue.get(key), false);
						}
						File flagFile = getFlagFile(key);
						if (flagFile.exists()) {
							return new Pair<>(YamlConfiguration.loadConfiguration(flagFile), false);
						}
						return new Pair<>(new YamlConfiguration(), false);
					}
				});

		convertOldFlagsFile();
	}

	private void startSaveQueue() {

		// If plugin is disabling, save all files.
		if (!plugin.isEnabled()) {
			if (saveTask != null) {
				saveTask.cancel();
				saveTask = null;
			}
			saveQueue.entrySet().removeIf(entry -> {
				try {
					entry.getValue().save(getFlagFile(entry.getKey()));
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			});
		}

		if (saveTask != null && !saveTask.isCancelled()) {
			return;
		}

		// Save 1 file per tick - it's inconceivable that ordinary play would exceed this in load.
		saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (saveQueue.isEmpty()) {
				if (saveTask != null) {
					saveTask.cancel();
				}
				saveTask = null;
				return;
			}

			Iterator<Map.Entry<Pair<String, String>, YamlConfiguration>> iterator = saveQueue.entrySet().iterator();
			Map.Entry<Pair<String, String>, YamlConfiguration> entry = iterator.next();
			try {
				entry.getValue().save(getFlagFile(entry.getKey()));
			} catch (IOException e) {
				e.printStackTrace();
			}
			iterator.remove();

			if (saveQueue.isEmpty() && saveTask != null) {
				saveTask.cancel();
				saveTask = null;
			}
		}, 1L, 1L);
	}

	private void convertOldFlagsFile() {
		File oldFlagsFile = new File(this.plugin.getDataFolder(), "flags.yml");
		if (!oldFlagsFile.exists()) {
			return;
		}
		this.plugin.getLogger().info("Beginning splitting flags.yml into smaller per-world files.");
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

				Pair<YamlConfiguration, Boolean> flagData = this.flagFileCache.getUnchecked(this.getFlagFileIdentifier(world, chunkX, chunkZ));
				flagData.getLeft().set(chunkPath, worldSection.getLong(chunkPath));
				flagData.setRight(true);
			}
		}
		// Force save
		this.flagFileCache.invalidateAll();
		// Rename old flag file
		oldFlagsFile.renameTo(new File(oldFlagsFile.getParentFile(), "flags.yml.bak"));
		this.plugin.getLogger().info("Finished converting flags.yml, renamed to flags.yml.bak. Delete at convenience if all appears well.");
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
		Pair<YamlConfiguration, Boolean> flagData = this.flagFileCache.getUnchecked(this.getFlagFileIdentifier(world, chunkX, chunkZ));
		String chunkPath = this.getChunkPath(chunkX, chunkZ);
		long current = flagData.getLeft().getLong(chunkPath, 0);
		if (current == this.plugin.getEternalFlag()) {
			return;
		}
		flagData.getLeft().set(chunkPath, flagTil);
		flagData.setRight(true);
	}

	public void unflagRegion(String world, int regionX, int regionZ) {
		unflagRegionByLowestChunk(world, CoordinateConversions.regionToChunk(regionX), CoordinateConversions.regionToChunk(regionZ));
	}

	public void unflagRegionByLowestChunk(String world, int regionLowestChunkX, int regionLowestChunkZ) {
		Pair<String, String> flagFileIdentifier = getFlagFileIdentifier(world, regionLowestChunkX, regionLowestChunkZ);
		Pair<YamlConfiguration, Boolean> flagData = this.flagFileCache.getUnchecked(flagFileIdentifier);
		flagData.setRight(false);
		this.flagFileCache.invalidate(flagData);
		getFlagFile(flagFileIdentifier).delete();
	}

	public void unflagChunk(String world, int chunkX, int chunkZ) {
		Pair<YamlConfiguration, Boolean> flagData = this.flagFileCache.getUnchecked(getFlagFileIdentifier(world, chunkX, chunkZ));
		String chunkPath = this.getChunkPath(chunkX, chunkZ);
		flagData.getLeft().set(chunkPath, null);
		flagData.setRight(true);
	}

	/**
	 * Save all flag files.
	 */
	public void save() {
		this.flagFileCache.invalidateAll();
	}

	public VisitStatus getChunkVisitStatus(World world, int chunkX, int chunkZ) {
		Pair<YamlConfiguration, Boolean> flagData = this.flagFileCache.getUnchecked(getFlagFileIdentifier(world.getName(), chunkX, chunkZ));
		String chunkPath = this.getChunkPath(chunkX, chunkZ);
		long visit = flagData.getLeft().getLong(chunkPath, -1);
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

	private String getChunkPath(int chunkX, int chunkZ) {
		return String.valueOf(chunkX) + '_' + chunkZ;
	}

}

