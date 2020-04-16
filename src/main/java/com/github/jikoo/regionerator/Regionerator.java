package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.commands.CommandFlag;
import com.github.jikoo.regionerator.event.RegioneratorChunkDeleteEvent;
import com.github.jikoo.regionerator.event.RegioneratorDeleteEvent;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.hooks.PluginHook;
import com.github.jikoo.regionerator.listeners.FlaggingListener;
import com.github.jikoo.regionerator.listeners.HookListener;
import com.github.jikoo.regionerator.util.Config;
import com.github.jikoo.regionerator.util.SimpleListener;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin for deleting unused region files gradually.
 *
 * @author Jikoo
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue", "unused"})
public class Regionerator extends JavaPlugin {

	private final CommandFlag commandFlag = new CommandFlag(this);
	private HashMap<String, DeletionRunnable> deletionRunnables;
	private ChunkFlagger chunkFlagger;
	private List<Hook> protectionHooks;
	private Config config;
	private WorldManager worldManager;
	private boolean paused = false;

	@Override
	public void onEnable() {

		saveDefaultConfig();
		config = new Config();
		config.reload(this);

		deletionRunnables = new HashMap<>();
		chunkFlagger = new ChunkFlagger(this);
		protectionHooks = new ArrayList<>();
		worldManager = new WorldManager(this);

		boolean hasHooks = false;
		Set<String> hookNames = Objects.requireNonNull(Objects.requireNonNull(getConfig().getDefaults()).getConfigurationSection("hooks")).getKeys(false);
		ConfigurationSection hookSection = getConfig().getConfigurationSection("hooks");
		if (hookSection != null) {
			hookNames.addAll(hookSection.getKeys(false));
		}
		for (String hookName : hookNames) {
			// Default true - hooks should likely be enabled unless explicitly disabled
			if (!getConfig().getBoolean("hooks." + hookName, true)) {
				continue;
			}
			try {
				Class<?> clazz = Class.forName("com.github.jikoo.regionerator.hooks." + hookName + "Hook");
				if (!Hook.class.isAssignableFrom(clazz)) {
					// What.
					continue;
				}
				Hook hook = (Hook) clazz.getDeclaredConstructor().newInstance();
				if (!hook.areDependenciesPresent()) {
					debug(DebugLevel.LOW, () -> String.format("Dependencies not found for %s hook, skipping.", hookName));
					continue;
				}
				if (!hook.isReadyOnEnable()) {
					debug(DebugLevel.LOW, () -> String.format("Protection hook for %s is available but not yet ready.", hookName));
					hook.readyLater(this);
					continue;
				}
				if (hook.isHookUsable()) {
					protectionHooks.add(hook);
					hasHooks = true;
					debug(DebugLevel.LOW, () -> "Enabled protection hook for " + hookName);
				} else {
					getLogger().info("Protection hook for " + hookName + " failed usability check! Deletion is paused.");
					paused = true;
				}
			} catch (ClassNotFoundException e) {
				getLogger().severe("No hook found for " + hookName + "! Please request compatibility!");
			} catch (ReflectiveOperationException e) {
				getLogger().severe("Unable to enable hook for " + hookName + "! Deletion is paused.");
				paused = true;
				e.printStackTrace();
			} catch (NoClassDefFoundError e) {
				debug(DebugLevel.LOW, () -> String.format("Dependencies not found for %s hook, skipping.", hookName));
				debug(DebugLevel.MEDIUM, (Runnable) e::printStackTrace);
			}
		}

		// Don't register listeners if there are no worlds configured
		if (config.getWorlds().isEmpty()) {
			getLogger().severe("No worlds are enabled. There's nothing to do!");
			return;
		}

		// Only enable hook listener if there are actually any hooks enabled
		if (hasHooks) {
			getServer().getPluginManager().registerEvents(new HookListener(this), this);
		}

		if (config.getFlagDuration() > 0) {
			// Flag duration is set, start flagging

			getServer().getPluginManager().registerEvents(new FlaggingListener(this), this);

			new FlaggingRunnable(this).runTaskTimer(this, 0, config.getFlaggingInterval());
		} else {
			// Flagging runnable is not scheduled, schedule a task to start deletion
			new BukkitRunnable() {
				@Override
				public void run() {
					attemptDeletionActivation();
				}
			}.runTaskTimer(this, 0L, 1200L);

			// Additionally, since flagging will not be editing values, flagging untouched chunks is not an option
			getConfig().set("delete-new-unvisited-chunks", true);
		}

		RegioneratorDeleteEvent.getHandlerList().register(new SimpleListener<>(RegioneratorDeleteEvent.class, event -> {
			if (RegioneratorChunkDeleteEvent.getHandlerList().getRegisteredListeners().length == 0) {
				return;
			}
			PluginManager pluginManager = getServer().getPluginManager();
			for (ChunkInfo chunk : event.getChunks()) {
				pluginManager.callEvent(new RegioneratorChunkDeleteEvent(chunk.getWorld(), chunk.getChunkX(), chunk.getChunkZ()));
			}
		}, this));

		debug(DebugLevel.LOW, () -> onCommand(Bukkit.getConsoleSender(), Objects.requireNonNull(getCommand("regionerator")), "regionerator", new String[0]));
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		// TODO tab completion

		attemptDeletionActivation();

		if (args.length < 1) {
			if (config.getWorlds().isEmpty()) {
				sender.sendMessage("No worlds are configured. Edit your config and use /regionerator reload.");
				return true;
			}

			SimpleDateFormat format = new SimpleDateFormat("HH:mm 'on' d MMM");

			for (String worldName : config.getWorlds()) {
				long activeAt = getConfig().getLong("delete-this-to-reset-plugin." + worldName);
				if (activeAt > System.currentTimeMillis()) {
					// Not time yet.
					sender.sendMessage(worldName + ": Gathering data, deletion starts " + format.format(new Date(activeAt)));
					continue;
				}

				if (deletionRunnables.containsKey(worldName)) {
					DeletionRunnable runnable = deletionRunnables.get(worldName);
					sender.sendMessage(runnable.getRunStats());
					if (runnable.getNextRun() < Long.MAX_VALUE) {
						sender.sendMessage(" - Next run: " + format.format(runnable.getNextRun()));
					}
				} else {
					sender.sendMessage("Cycle for " + worldName + " is ready to start.");
				}
			}

			if (paused) {
				sender.sendMessage("Regionerator is paused. Use \"/regionerator resume\" to continue.");
			}
			return true;
		}

		args[0] = args[0].toLowerCase();
		if (args[0].equals("reload")) {
			reloadConfig();
			config.reload(this);
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

		if (args[0].equals("flag")) {
			commandFlag.handleFlags(sender, args, true);
			return true;
		}
		if (args[0].equals("unflag")) {
			commandFlag.handleFlags(sender, args, false);
			return true;
		}

		if (args[0].equals("cache")) {
			sender.sendMessage("Cached chunk values: " + getFlagger().getCached());
			sender.sendMessage("Queued saves: " + getFlagger().getQueued());
			return true;
		}

		if (sender instanceof Player && args[0].equals("check")) {
			Player player = (Player) sender;

			if (!getActiveWorlds().contains(player.getWorld().getName())) {
				player.sendMessage("World is not configured for deletion.");
			}

			Chunk chunk = player.getLocation().getChunk();

			for (Hook hook : protectionHooks) {
				player.sendMessage("Chunk is " + (hook.isChunkProtected(chunk.getWorld(), chunk.getX(), chunk.getZ()) ? "" : "not ") + "protected by " + hook.getProtectionName());
			}

			SimpleDateFormat format = new SimpleDateFormat("HH:mm 'on' d MMM yyyy");
			RegionInfo regionInfo = null;
			try {
				regionInfo = getWorldManager().getWorld(player.getWorld()).getRegion(Coords.chunkToRegion(chunk.getX()), Coords.chunkToRegion(chunk.getZ()));
			} catch (IOException e) {
				player.sendMessage("Caught IOException reading region data from disk. Please check console!");
				e.printStackTrace();
			}

			// Region not yet saved, cannot obtain chunk detail data
			if (regionInfo == null || !regionInfo.getRegionFile().exists()) {
				long visit = chunkFlagger.getChunkFlag(chunk.getWorld(), chunk.getX(), chunk.getZ()).join().getLastVisit();
				if (visit == Config.getFlagDefault()) {
					player.sendMessage("Chunk has not been visited.");
				} else if (visit == config.getFlagGenerated()) {
					player.sendMessage("Chunk has not been visited since generation.");
				} else if (visit == Config.getFlagEternal()) {
					player.sendMessage("Chunk is eternally flagged.");
				} else {
					player.sendMessage("Chunk visited until: " + format.format(new Date(visit)));
				}
				visit = chunkFlagger.getChunkFlagOnDelete(chunk.getWorld(), chunk.getX(), chunk.getZ()).join().getLastVisit();
				if (visit != Config.getFlagDefault()) {
					player.sendMessage("Visited (last delete): " + format.format(new Date(visit)));
				}
				player.sendMessage("Region not available from disk! Cannot check details.");
				return true;
			}

			ChunkInfo chunkInfo = regionInfo.getChunk(chunk.getX(), chunk.getZ());
			player.sendMessage("Chunk visited until: " + format.format(new Date(chunkInfo.getLastVisit())));
			player.sendMessage("Chunk last modified: " + format.format(new Date(chunkInfo.getLastModified())));
			player.sendMessage("Chunk VisitStatus: " + chunkInfo.getVisitStatus().name());
			long visit = chunkFlagger.getChunkFlagOnDelete(chunk.getWorld(), chunk.getX(), chunk.getZ()).join().getLastVisit();
			if (visit != Config.getFlagDefault()) {
				player.sendMessage("Visited (last delete): " + format.format(new Date(visit)));
			}
			if (chunkInfo.isOrphaned()) {
				player.sendMessage("Chunk is marked as orphaned. VisitStatus should be GENERATED or UNKNOWN.");
			}
			return true;
		}

		return false;
	}

	@Override
	public void onDisable() {
		if (chunkFlagger != null) {
			getLogger().info("Shutting down flagger - currently holds " + chunkFlagger.getCached() + " flags.");
			chunkFlagger.shutdown();
		}

		// Manually cancel deletion runnables - Bukkit does not do a good job of informing tasks they can't continue.
		deletionRunnables.values().forEach(BukkitRunnable::cancel);
	}

	public Config config() {
		return config;
	}

	public WorldManager getWorldManager() {
		return worldManager;
	}

	public void attemptDeletionActivation() {
		deletionRunnables.values().removeIf(value -> value.getNextRun() < System.currentTimeMillis());

		if (isPaused()) {
			return;
		}

		for (String worldName : config.getWorlds()) {
			if (getConfig().getLong("delete-this-to-reset-plugin." + worldName) > System.currentTimeMillis()) {
				// Not time yet.
				continue;
			}
			DeletionRunnable runnable = deletionRunnables.get(worldName);
			if (runnable != null) {
				// Deletion is ongoing for world.
				if (runnable.getNextRun() == Long.MAX_VALUE) {
					return;
				}
				// Deletion is complete for world.
				continue;
			}
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				// World is not loaded.
				continue;
			}
			try {
				runnable = new DeletionRunnable(this, world);
			} catch (RuntimeException e) {
				debug(DebugLevel.HIGH, e::getMessage);
				continue;
			}
			runnable.runTaskAsynchronously(this);
			deletionRunnables.put(worldName, runnable);
			debug(DebugLevel.LOW, () -> "Deletion run scheduled for " + world.getName());
			return;
		}
	}

	public List<String> getActiveWorlds() {
		return config.getWorlds();
	}

	public List<Hook> getProtectionHooks() {
		return Collections.unmodifiableList(this.protectionHooks);
	}

	public void addHook(PluginHook hook) {
		if (hook == null) {
			throw new IllegalArgumentException("Hook cannot be null");
		}

		for (Hook enabledHook : this.protectionHooks) {
			if (enabledHook.getClass().equals(hook.getClass())) {
				throw new IllegalStateException(String.format("Hook %s is already enabled", hook.getProtectionName()));
			}
		}

		if (!hook.isHookUsable()) {
			throw new IllegalStateException(String.format("Hook %s is not usable", hook.getProtectionName()));
		}

		this.protectionHooks.add(hook);
	}

	public boolean removeHook(Class<? extends Hook> hook) {
		Iterator<Hook> hookIterator = this.protectionHooks.iterator();
		while (hookIterator.hasNext()) {
			if (hookIterator.next().getClass().equals(hook)) {
				hookIterator.remove();
				return true;
			}
		}
		return false;
	}

	public boolean removeHook(Hook hook) {
		return this.protectionHooks.remove(hook);
	}

	public ChunkFlagger getFlagger() {
		return chunkFlagger;
	}

	public boolean isPaused() {
		return paused;
	}

	public boolean debug(DebugLevel level) {
		return config.getDebugLevel().ordinal() >= level.ordinal();
	}

	public void debug(DebugLevel level, Supplier<String> message) {
		if (Regionerator.this.debug(level)) {
			getLogger().info(message.get());
		}
	}

	public void debug(DebugLevel level, Runnable runnable) {
		if (debug(level)) {
			runnable.run();
		}
	}

}
