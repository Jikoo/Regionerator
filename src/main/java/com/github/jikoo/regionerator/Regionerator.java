package com.github.jikoo.regionerator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.github.jikoo.regionerator.listeners.FlaggingListener;
import com.github.jikoo.regionerator.listeners.HookListener;

import com.google.common.collect.ImmutableList;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

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
	private DebugLevel debugLevel;
	private boolean paused;

	@Override
	public void onEnable() {

		saveDefaultConfig();

		paused = false;

		List<String> worldList = getConfig().getStringList("worlds");
		if (worldList.isEmpty()) {
			getLogger().severe("No worlds are enabled. Disabling!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		boolean dirtyConfig = false;

		try {
			debugLevel = DebugLevel.valueOf(getConfig().getString("debug-level", "OFF").toUpperCase());
		} catch (IllegalArgumentException e) {
			debugLevel = DebugLevel.OFF;
			getConfig().set("debug-level", "OFF");
			dirtyConfig = true;
		}
		if (debug(DebugLevel.LOW)) {
			debug("Debug level: " + debugLevel.name());
		}

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

		// 86,400,000 = 24 hours * 60 minutes * 60 seconds * 1000 milliseconds
		flagDuration = 86400000L * getConfig().getInt("days-till-flag-expires");

		for (String worldName : worlds) {
			if (!getConfig().isLong("delete-this-to-reset-plugin." + worldName)) {
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
		ticksPerFlagAutosave = getConfig().getInt("minutes-per-flag-autosave") * 1200L;

		if (getConfig().getLong("ticks-per-deletion") < 1) {
			getConfig().set("ticks-per-deletion", 20L);
			dirtyConfig = true;
		}

		if (getConfig().getInt("chunks-per-deletion") < 1) {
			getConfig().set("chunks-per-deletion", 20);
			dirtyConfig = true;
		}

		if (getConfig().getInt("hours-between-cycles") < 0) {
			getConfig().set("hours-between-cycles", 0);
			dirtyConfig = true;
		}
		// 60 minutes per hour, 60 seconds per minute, 1000 milliseconds per second
		millisBetweenCycles = getConfig().getInt("hours-between-cycles") * 3600000L;

		protectionHooks = new ArrayList<>();
		boolean hasHooks = false;
		for (String pluginName : getConfig().getDefaults().getConfigurationSection("hooks").getKeys(false)) {
			if (!getConfig().getBoolean("hooks." + pluginName)) {
				continue;
			}
			try {
				Class<?> clazz = Class.forName("com.github.jikoo.regionerator.hooks." + pluginName + "Hook");
				if (!Hook.class.isAssignableFrom(clazz)) {
					// What.
					continue;
				}
				Hook hook = (Hook) clazz.newInstance();
				if (hook.isHookUsable()) {
					protectionHooks.add(hook);
					hasHooks = true;
					if (debug(DebugLevel.LOW)) {
						debug("Enabled protection hook for " + pluginName);
					}
				}
			} catch (ClassNotFoundException e) {
				getLogger().severe("No hook found for " + pluginName + "! Please request compatibility!");
			} catch (InstantiationException | IllegalAccessException e) {
				getLogger().severe("Unable to enable hook for " + pluginName + "!");
				e.printStackTrace();
			}
		}
		// Only enable hook listener if there are actually any hooks enabled
		if (hasHooks) {
			getServer().getPluginManager().registerEvents(new HookListener(this), this);
		}

		if (dirtyConfig) {
			getConfig().options().copyHeader(true);
			saveConfig();
		}

		deletionRunnables = new HashMap<>();

		chunkFlagger = new ChunkFlagger(this);

		chunkFlagger.scheduleSaving();

		if (flagDuration > 0) {
			// Flag duration is set, start flagging

			getServer().getPluginManager().registerEvents(new FlaggingListener(this), this);
	
			new FlaggingRunnable(this).runTaskTimer(this, 0, getTicksPerFlag());
		} else {
			// Flagging runnable is not scheduled, schedule a task to start deletion
			new BukkitRunnable() {
				@Override
				public void run() {
					attemptDeletionActivation();
				}
			}.runTaskTimer(this, 0L, 1200L);
		}

		if (debug(DebugLevel.LOW)) {
			onCommand(Bukkit.getConsoleSender(), null, null, new String[0]);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

		attemptDeletionActivation();

		if (args.length > 0) {
			args[0] = args[0].toLowerCase();
			if (args[0].equals("reload")) {
				reloadConfig();
				onDisable();
				onEnable();
				sender.sendMessage("Regionerator configuration reloaded, all tasks restarted!");
				return true;
			}
			if (args[0].equals("pause") || args[0].equals("stop") ) {
				paused = true;
				sender.sendMessage("Paused Regionerator. Use /regionerator resume to resume.");
				return true;
			}
			if (args[0].equals("resume") || args[0].equals("unpause") || args[0].equals("start")) {
				paused = false;
				sender.sendMessage("Resumed Regionerator. Use /regionerator pause to pause.");
				return true;
			}
			if (sender instanceof Player && args[0].equals("check")) {
				Player player = (Player) sender;
				Chunk chunk = player.getLocation().getChunk();
				for (Hook hook : protectionHooks) {
					player.sendMessage("Chunk is " + (hook.isChunkProtected(chunk.getWorld(), chunk.getX(), chunk.getZ()) ? "" : "not ") + "protected by " + hook.getPluginName());
				}
				player.sendMessage("Chunk VisitStatus: " + chunkFlagger.getChunkVisitStatus(chunk.getWorld(), chunk.getX(), chunk.getZ()).name());
				return true;
			}
			return false;
		}

		if (worlds.isEmpty()) {
			sender.sendMessage("No worlds are configured. Edit your config and use /regionerator reload.");
			return true;
		}

		SimpleDateFormat format = new SimpleDateFormat("HH:mm 'on' d MMM");

		boolean running = false;
		for (String worldName : worlds) {
			long activeAt = getConfig().getLong("delete-this-to-reset-plugin." + worldName);
			if (activeAt > System.currentTimeMillis()) {
				// Not time yet.
				sender.sendMessage(worldName + ": Gathering data, regeneration starts " + format.format(new Date(activeAt)));
				continue;
			}

			if (deletionRunnables.containsKey(worldName)) {
				DeletionRunnable runnable = deletionRunnables.get(worldName);
				String message = runnable.getRunStats();
				if (runnable.getNextRun() < Long.MAX_VALUE) {
					message += " - next run: " + format.format(runnable.getNextRun());
				} else if (!getConfig().getBoolean("allow-concurrent-cycles")) {
					running = true;
				}
				sender.sendMessage(message);
				continue;
			}

			if (running && !getConfig().getBoolean("allow-concurrent-cycles")) {
				sender.sendMessage("Cycle for " + worldName + " is ready to start.");
				continue;
			}

			if (!running) {
				getLogger().severe("Deletion cycle failed to start for " + worldName + "! Please report this issue if you see any errors!");
			}
		}
		return true;
	}

	@Override
	public void onDisable() {
		getServer().getScheduler().cancelTasks(this);
		HandlerList.unregisterAll(this);
		if (chunkFlagger != null) {
			chunkFlagger.cancelSaving();
			chunkFlagger.save(true);
		}
	}

	public long getVisitFlag() {
		return flagDuration + System.currentTimeMillis();
	}

	public long getGenerateFlag() {
		return getConfig().getBoolean("delete-new-unvisited-chunks") ? getVisitFlag() : Long.MAX_VALUE;
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

	public int getChunksPerDeletionCheck() {
		return getConfig().getInt("chunks-per-deletion");
	}

	public long getTicksPerDeletionCheck() {
		return getConfig().getLong("ticks-per-deletion");
	}

	public long getMillisecondsBetweenDeletionCycles() {
		return millisBetweenCycles;
	}

	public void attemptDeletionActivation() {
		Iterator<Entry<String, DeletionRunnable>> iterator = deletionRunnables.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue().getNextRun() < System.currentTimeMillis()) {
				iterator.remove();
			}
		}

		if (isPaused()) {
			return;
		}

		for (String worldName : worlds) {
			if (getConfig().getLong("delete-this-to-reset-plugin." + worldName) > System.currentTimeMillis()) {
				// Not time yet.
				continue;
			}
			if (deletionRunnables.containsKey(worldName)) {
				// Already running/ran
				if (!getConfig().getBoolean("allow-concurrent-cycles")
						&& deletionRunnables.get(worldName).getNextRun() == Long.MAX_VALUE) {
					// Concurrent runs aren't allowed, we've got one going. Quit out.
					return;
				}
				continue;
			}
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				// World is not loaded.
				continue;
			}
			DeletionRunnable runnable;
			try {
				runnable = new DeletionRunnable(this, world);
			} catch (RuntimeException e) {
				if (debug(DebugLevel.HIGH)) {
					debug(e.getMessage());
				}
				continue;
			}
			runnable.runTaskTimer(this, 0, getTicksPerDeletionCheck());
			deletionRunnables.put(worldName, runnable);
			if (debug(DebugLevel.LOW)) {
				debug("Deletion run scheduled for " + world.getName());
			}
			if (!getConfig().getBoolean("allow-concurrent-cycles")) {
				return;
			}
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

	public boolean isPaused() {
		return paused;
	}

	public boolean debug(DebugLevel level) {
		return debugLevel.ordinal() >= level.ordinal();
	}

	public void debug(String message) {
		getLogger().info(message);
	}
}
