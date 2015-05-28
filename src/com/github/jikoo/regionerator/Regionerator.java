package com.github.jikoo.regionerator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.collect.ImmutableList;

/**
 * Plugin for deleting unused region files gradually.
 * 
 * @author Jikoo
 */
public class Regionerator extends JavaPlugin {

	private long flagDuration;
	private long ticksPerFlag;
	private long ticksPerFlagAutosave;
	private List<String> worlds;
	private List<Hook> protectionHooks;
	private ChunkFlagger chunkFlagger;
	private HashMap<String, DeletionRunnable> deletionRunnables;
	private long millisBetweenCycles;

	@Override
	public void onEnable() {
		// TODO ensure soft-reload friendly - no final variables representing config values
		// TODO finish scheduling deletion
		// TODO pause via command
		// TODO reports via command

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
		// Immutable list, this should not be changed during run by myself or another plugin
		worlds = ImmutableList.copyOf(worlds);
		if (dirtyConfig) {
			getConfig().set("worlds", worlds);
		}

		if (getConfig().getInt("days-till-flag-expires") < 1) {
			getConfig().set("days-till-flag-expires", 1);
			dirtyConfig = true;
		}

		// 86,400,000 = 24 hours * 60 minutes * 60 seconds * 1000 milliseconds
		flagDuration = 86400000L * getConfig().getInt("days-till-flag-expires");

		for (String worldName : worlds) {
			if (getConfig().getLong("delete-this-to-reset-plugin." + worldName, 0) == 0) {
				// Set time to start actually deleting chunks to ensure that all existing areas are given a chance
				getConfig().set("delete-this-to-reset-plugin." + worldName, System.currentTimeMillis() + flagDuration);
				dirtyConfig = true;
			}
		}

		if (getConfig().getInt("chunk-flag-radius") < 0) {
			getConfig().set("chunk-flag-radius", 4);
			dirtyConfig = true;
		}

		if (getConfig().getInt("seconds-per-flag") < 1) {
			getConfig().set("seconds-per-flag", 10);
			dirtyConfig = true;
		}
		// 20 ticks per second
		ticksPerFlag = getConfig().getInt("seconds-per-flag") * 20L;

		if (getConfig().getInt("minutes-per-flag-autosave") < 1) {
			getConfig().set("minutes-per-flag-autosave", 5);
			dirtyConfig = true;
		}
		// 60 seconds per minute, 20 ticks per second
		ticksPerFlagAutosave = getConfig().getInt("seconds-per-flag") * 120L;

		if (getConfig().getLong("ticks-per-deletion") < 1) {
			getConfig().set("ticks-per-deletion", 20L);
			dirtyConfig = true;
		}

		if (getConfig().getInt("regions-per-deletion") < 1) {
			getConfig().set("regions-per-deletion", 1);
			dirtyConfig = true;
		}

		if (getConfig().getInt("hours-between-cycles") < 0) {
			getConfig().set("hours-between-cycles", 0);
		}
		// 60 minutes per hour, 60 seconds per minute, 1000 milliseconds per second
		millisBetweenCycles = getConfig().getInt("hours-between-cycles") * 360000L;

		protectionHooks = new ArrayList<>();
		for (String pluginName : getConfig().getConfigurationSection("hooks").getKeys(false)) {
			try {
				Class<?> clazz = Class.forName("com.github.jikoo.regionerator.hooks." + pluginName + "Hook");
				if (!clazz.isAssignableFrom(Hook.class)) {
					// What.
					continue;
				}
				Hook hook = (Hook) clazz.newInstance();
				if (hook.isHookUsable()) {
					protectionHooks.add(hook);
				}
			} catch (ClassNotFoundException e) {
				getLogger().severe("No hook found for " + pluginName + "! Please request compatibility!");
			} catch (InstantiationException | IllegalAccessException e) {
				getLogger().severe("Unable to enable hook for " + pluginName + "!");
				e.printStackTrace();
			}
		}

		if (dirtyConfig) {
			saveConfig();
		}

		chunkFlagger = new ChunkFlagger(this);

		deletionRunnables = new HashMap<>();
		attemptDeletionActivation();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		long activeAt = getConfig().getLong("delete-this-to-reset-plugin");
		if (System.currentTimeMillis() < activeAt) {
			sender.sendMessage("Gathering data. Regeneration will begin at " + new SimpleDateFormat("HH:mm 'on' dd/MM").format(new Date(activeAt)));
			return true;
		}
		// TODO
		return false;
	}

	@Override
	public void onDisable() {
		chunkFlagger.save();
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

	public long getTicksPerFlagAutosave() {
		return ticksPerFlagAutosave;
	}

	public int getRegionsPerCheck() {
		return getConfig().getInt("regions-per-deletion");
	}

	public long getTicksPerDeletionCheck() {
		return getConfig().getLong("ticks-per-deletion");
	}

	public long getMillisecondsBetweenDeletionCycles() {
		return millisBetweenCycles;
	}

	public void attemptDeletionActivation() {
		// TODO support Java 7?
		deletionRunnables.entrySet().removeIf(entry -> entry.getValue().getNextRun() < System.currentTimeMillis());

		for (String worldName : worlds) {
			if (getConfig().getLong("delete-this-to-reset-plugin." + worldName) > System.currentTimeMillis()) {
				// Not time yet.
				continue;
			}
			if (deletionRunnables.containsKey(worldName)) {
				// Already running/ran
				continue;
			}
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				// World is not loaded.
				continue;
			}
			DeletionRunnable runnable = new DeletionRunnable(this, world);
			runnable.runTaskTimer(this, 0, getTicksPerDeletionCheck());
			deletionRunnables.put(worldName, runnable);
		}
	}

	public List<String> getActiveWorlds() {
		return worlds;
	}

	public List<Hook> getProtectionHooks() {
		return protectionHooks;
	}

	public ChunkFlagger getFlagger() {
		return chunkFlagger;
	}
}
