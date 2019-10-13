package com.github.jikoo.regionerator.util;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class Config {

	private long flagDuration;
	private long ticksPerFlag;
	private long ticksPerFlagAutosave;
	private List<String> worlds;
	private long millisBetweenCycles;
	private DebugLevel debugLevel;

	public void reload(Regionerator plugin) {

		List<String> worldList = plugin.getConfig().getStringList("worlds");

		boolean dirtyConfig = false;

		try {
			String debugConfigVal = plugin.getConfig().getString("debug-level", "OFF");
			debugLevel = debugConfigVal == null ? DebugLevel.OFF : DebugLevel.valueOf(debugConfigVal.toUpperCase());
		} catch (IllegalArgumentException e) {
			debugLevel = DebugLevel.OFF;
			plugin.getConfig().set("debug-level", "OFF");
			dirtyConfig = true;
		}

		plugin.debug(DebugLevel.LOW, () -> "Debug level: " + debugLevel.name());

		worlds = new ArrayList<>();
		for (World world : Bukkit.getWorlds()) {
			if (worldList.contains(world.getName())) {
				worlds.add(world.getName());
				continue;
			}
			for (String name : worldList) {
				if (world.getName().equalsIgnoreCase(name)) {
					worlds.add(world.getName());
					dirtyConfig = true;
					break;
				}
			}
		}
		// Immutable list, this should not be changed during run by myself or another plugin
		worlds = ImmutableList.copyOf(worlds);
		if (dirtyConfig) {
			plugin.getConfig().set("worlds", worlds);
		}

		// 86,400,000 = 24 hours * 60 minutes * 60 seconds * 1000 milliseconds
		flagDuration = 86400000L * plugin.getConfig().getInt("days-till-flag-expires");

		for (String worldName : worlds) {
			if (!plugin.getConfig().isLong("delete-this-to-reset-plugin." + worldName)) {
				// Set time to start actually deleting chunks to ensure that all existing areas are given a chance
				plugin.getConfig().set("delete-this-to-reset-plugin." + worldName, System.currentTimeMillis() + flagDuration);
				dirtyConfig = true;
			}
		}

		if (plugin.getConfig().getInt("chunk-flag-radius") < 0) {
			plugin.getConfig().set("chunk-flag-radius", 4);
			dirtyConfig = true;
		}

		if (plugin.getConfig().getInt("seconds-per-flag") < 1) {
			plugin.getConfig().set("seconds-per-flag", 10);
			dirtyConfig = true;
		}
		// 20 ticks per second
		ticksPerFlag = plugin.getConfig().getInt("seconds-per-flag") * 20L;

		if (plugin.getConfig().getInt("minutes-per-flag-autosave") < 1) {
			plugin.getConfig().set("minutes-per-flag-autosave", 5);
			dirtyConfig = true;
		}
		// 60 seconds per minute, 20 ticks per second
		ticksPerFlagAutosave = plugin.getConfig().getInt("minutes-per-flag-autosave") * 1200L;

		if (plugin.getConfig().getLong("ticks-per-deletion") < 1) {
			plugin.getConfig().set("ticks-per-deletion", 20L);
			dirtyConfig = true;
		}

		if (plugin.getConfig().getInt("chunks-per-deletion") < 1) {
			plugin.getConfig().set("chunks-per-deletion", 20);
			dirtyConfig = true;
		}

		if (plugin.getConfig().getInt("hours-between-cycles") < 0) {
			plugin.getConfig().set("hours-between-cycles", 0);
			dirtyConfig = true;
		}
		// 60 minutes per hour, 60 seconds per minute, 1000 milliseconds per second
		millisBetweenCycles = plugin.getConfig().getInt("hours-between-cycles") * 3600000L;

		if (dirtyConfig) {
			plugin.getConfig().options().copyHeader(true);
			plugin.saveConfig();
		}

	}

	public long getFlagDuration() {
		return flagDuration;
	}

	public long getFlagEternal() {
		return Long.MAX_VALUE - 1;
	}

	public long getFlagInterval() {
		return ticksPerFlag;
	}

	public long getFlagAutosaveInterval() {
		return ticksPerFlagAutosave;
	}

	public long getMillisBetweenCycles() {
		return millisBetweenCycles;
	}

	public DebugLevel getDebugLevel() {
		return debugLevel;
	}

	public List<String> getWorlds() {
		return worlds;
	}

}
