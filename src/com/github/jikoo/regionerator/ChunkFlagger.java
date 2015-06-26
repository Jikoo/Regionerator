package com.github.jikoo.regionerator;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.World;
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
		flagChunk(world, chunkX, chunkZ, plugin.getChunkFlagRadius(), plugin.getVisitFlag());
	}

	public void flagChunk(String world, int chunkX, int chunkZ, int radius, long flagTil) {
		for (int dX = -radius; dX <= radius; dX++) {
			for (int dZ = -radius; dZ <= radius; dZ++) {
				flag(getChunkString(world, chunkX + dX, chunkZ + dZ), flagTil, false);
			}
		}
	}

	private void flag(String chunkPath, boolean force) {
		flag(chunkPath, plugin.getVisitFlag(), force);
	}

	private void flag(String chunkPath, long flagTil, boolean force) {
		if (force || !saving.get()) {
			flags.set(chunkPath, flagTil);
			dirty.set(true);
		} else {
			pendingFlag.add(chunkPath);
		}
	}

	public void unflagRegion(String world, int regionX, int regionZ) {
		regionX = regionX << 5;
		regionZ = regionZ << 5;
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

	public VisitStatus getChunkVisitStatus(World world, int chunkX, int chunkZ) {
		String chunkString = getChunkString(world.getName(), chunkX, chunkZ);
		if (world.isChunkLoaded(chunkX, chunkZ)) {
			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug("Chunk " + chunkString + " is loaded.");
			}
			return VisitStatus.LOADED;
		}
		long visit = flags.getLong(chunkString, -1);
		if (visit != Long.MAX_VALUE && visit > System.currentTimeMillis()) {
			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug("Chunk " + chunkString + " is flagged.");
			}
			return VisitStatus.VISITED;
		}
		for (Hook hook : plugin.getProtectionHooks()) {
			if (hook.isChunkProtected(world, chunkX, chunkZ)) {
				if (plugin.debug(DebugLevel.HIGH)) {
					plugin.debug("Chunk " + chunkString + " contains protections by " + hook.getPluginName());
				}
				return VisitStatus.PROTECTED;
			}
		}
		if (visit == Long.MAX_VALUE) {
			if (plugin.debug(DebugLevel.HIGH)) {
				plugin.debug("Chunk " + chunkString + " has not been visited since it was generated.");
			}
			return VisitStatus.GENERATED;
		}
		return VisitStatus.UNKNOWN;
	}

	private String getChunkString(String world, int chunkX, int chunkZ) {
		return new StringBuilder(world).append('.').append(chunkX).append('_').append(chunkZ).toString();
	}
}
