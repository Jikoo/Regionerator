package com.github.jikoo.regionerator;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin for deleting unused region files gradually.
 * 
 * @author Jikoo
 */
public class Regionerator extends JavaPlugin {

	private static Regionerator instance;

	private long flagDuration;
	private long ticksPerFlag;
	private ArrayList<String> worlds;
	private ArrayList<Hook> protectionHooks;

	@Override
	public void onEnable() {
		// TODO ensure soft-reload friendly - no final variables representing config values
		// TODO flagging implementation
		// TODO deletion implementation
		// TODO hook implementation - this is per-plugin, prioritizing GP and WG as they're what I use.
		// TODO finalize config
		instance = this;

		saveDefaultConfig();

		List<String> worldList = getConfig().getStringList("worlds");
		if (worldList.isEmpty()) {
			getLogger().severe("No worlds are enabled. Disabling!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		boolean dirtyConfig = false;

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
		if (dirtyConfig) {
			getConfig().set("worlds", worlds);
		}

		if (getConfig().getInt("days-till-flag-expires") < 1) {
			getConfig().set("days-till-flag-expires", 1);
			dirtyConfig = true;
		}

		// 86,400,000 = 24 hours * 60 minutes * 60 seconds * 1000 milliseconds
		flagDuration = 86400000L * getConfig().getInt("days-till-flag-expires");

		if (getConfig().getLong("do-not-touch", 0) == 0) {
			// Set time to start actually deleting chunks to ensure that all existing areas are given a chance
			getConfig().set("do-not-touch", System.currentTimeMillis() + flagDuration);
			dirtyConfig = true;
		}

		if (getConfig().getInt("chunk-flag-radius") < 0) {
			getConfig().set("chunk-flag-radius", 4);
			dirtyConfig = true;
		}

		if (getConfig().getInt("seconds-per-flag") < 1) {
			getConfig().set("seconds-per-flag", 10);
			dirtyConfig = true;
		}
		ticksPerFlag = getConfig().getInt("seconds-per-flag") * 20L;

		if (getConfig().getLong("ticks-per-deletion") < 1) {
			getConfig().set("ticks-per-deletion", 20L);
			dirtyConfig = true;
		}

		if (getConfig().getInt("regions-per-deletion") < 1) {
			getConfig().set("regions-per-deletion", 1);
			dirtyConfig = true;
		}

		if (dirtyConfig) {
			saveConfig();
		}
	}

	@Override
	public void onDisable() {
		instance = null;
	}

	public long getFlagDuration() {
		return flagDuration;
	}

	public int getChunkFlagRadius() {
		return getConfig().getInt("chunk-flag-radius");
	}

	public long getTicksPerFlag() {
		return ticksPerFlag;
	}

	public int getRegionsPerCheck() {
		return getConfig().getInt("regions-per-deletion");
	}

	public long getDeletionCheckInterval() {
		return getConfig().getLong("ticks-per-deletion");
	}

	public boolean activateRegen() {
		return getConfig().getLong("do-not-touch") <= System.currentTimeMillis(); // && regenTask == null
	}

	public List<Hook> getProtectionHooks() {
		return this.protectionHooks;
	}

	public static Regionerator getInstance() {
		return instance;
	}
}
