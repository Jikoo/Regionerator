package com.github.jikoo.regionerator;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;


/**
 * Storing time stamps for chunks made easy.
 * 
 * @author Jikoo
 */
public class ChunkFlagger {

	private final Regionerator plugin;
	private final File flagsFile;
	private final YamlConfiguration flags;
	private final AtomicBoolean dirty, saving;
	private final Set<String> pendingFlag, pendingUnflag;

	public ChunkFlagger(Regionerator plugin) {
		this.plugin = plugin;
		flagsFile = new File(plugin.getDataFolder(), "flags.yml");
		if (flagsFile.exists()) {
			flags = YamlConfiguration.loadConfiguration(flagsFile);
		} else {
			flags = new YamlConfiguration();
		}
		dirty = new AtomicBoolean(false);
		saving = new AtomicBoolean(false);
		pendingFlag = new HashSet<>();
		pendingUnflag = new HashSet<>();
	}

	public void flagChunk(String world, int chunkX, int chunkZ) {
		final int radius = plugin.getChunkFlagRadius();
		for (int dX = -radius; dX <= radius; dX++) {
			for (int dZ = -radius; dZ <= radius; dZ++) {
				flag(getChunkString(world, chunkX + dX, chunkZ + dZ), false);
			}
		}
	}

	private void flag(String chunkPath, boolean force) {
		if (force || !saving.get()) {
			flags.set(chunkPath, System.currentTimeMillis() + plugin.getFlagDuration());
			dirty.set(true);
		} else {
			pendingFlag.add(chunkPath);
		}
	}

	public void unflagRegion(String world, int regionX, int regionZ) {
		for (int chunkX = regionX; chunkX < regionX + 32; chunkX++) {
			for (int chunkZ = regionZ; chunkZ < regionZ + 32; chunkZ++) {
				unflagChunk(world, chunkX, chunkZ);
			}
		}
	}

	public void unflagChunk(String world, int chunkX, int chunkZ) {
		unflag(getChunkString(world, chunkX, chunkZ), false);
	}

	private void unflag(String chunkPath, boolean force) {
		if (force || !saving.get()) {
			flags.set(chunkPath, null);
			dirty.set(true);
		} else {
			pendingUnflag.add(chunkPath);
		}
	}

	public void scheduleSaving() {
		new BukkitRunnable() {
			@Override
			public void run() {
				saving.set(true);
				save();
				saving.set(false);
				new BukkitRunnable() {
					@Override
					public void run() {
						for (String path : pendingFlag) {
							flag(path, true);
						}
						pendingFlag.clear();
						for (String path : pendingUnflag) {
							unflag(path, true);
						}
						pendingUnflag.clear();
					}
				}.runTask(plugin);
			}
		}.runTaskTimerAsynchronously(plugin, plugin.getTicksPerFlagAutosave(), plugin.getTicksPerFlagAutosave());
	}

	public void save() {
		if (!dirty.get()) {
			return;
		}
		// Save is not being called async, plugin is probably disabling. Flush pending changes.
		if (!saving.get()) {
			for (String path : pendingFlag) {
				flag(path, true);
			}
			pendingFlag.clear();
			for (String path : pendingUnflag) {
				unflag(path, true);
			}
			pendingUnflag.clear();
		}
		try {
			flags.save(flagsFile);
		} catch (IOException e) {
			plugin.getLogger().severe("Could not save flags.yml!");
			e.printStackTrace();
		}
	}

	public boolean isChunkFlagged(String world, int chunkX, int chunkZ) {
		return flags.getLong(getChunkString(world, chunkX, chunkZ)) > System.currentTimeMillis();
	}

	private String getChunkString(String world, int chunkX, int chunkZ) {
		return new StringBuilder(world).append('.').append(chunkX).append('_').append(chunkZ).toString();
	}
}
