package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.commands.RegioneratorExecutor;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.hooks.PluginHook;
import com.github.jikoo.regionerator.listeners.DebugListener;
import com.github.jikoo.regionerator.listeners.FlaggingListener;
import com.github.jikoo.regionerator.listeners.HookListener;
import com.github.jikoo.regionerator.util.yaml.Config;
import com.github.jikoo.regionerator.util.yaml.MiscData;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plugin for deleting unused region files gradually.
 *
 * @author Jikoo
 */
@SuppressWarnings({"WeakerAccess"})
public class Regionerator extends JavaPlugin {

	private final Map<String, DeletionRunnable> deletionRunnables = new ConcurrentHashMap<>();
	private final Set<Hook> protectionHooks = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final WorldManager worldManager = new WorldManager(this);
	private final AtomicBoolean paused = new AtomicBoolean();
	private ChunkFlagger chunkFlagger;
	private Config config;
	private MiscData miscData;
	private BukkitTask flagging;
	private DebugListener debugListener;

	@Override
	public void onEnable() {

		saveDefaultConfig();
		config = new Config(this);
		miscData = new MiscData(this, new File(getDataFolder(), "data.yml"));

		boolean migrated = false;
		for (String worldName : config.getWorlds()) {
			if (getConfig().isLong("delete-this-to-reset-plugin." + worldName)) {
				// Migrate existing settings
				miscData.setNextCycle(worldName, getConfig().getLong("delete-this-to-reset-plugin." + worldName));
				migrated = true;
			}
		}
		if (migrated) {
			getConfig().set("delete-this-to-reset-plugin", null);
			saveConfig();
		}

		miscData.checkWorldValidity();

		chunkFlagger = new ChunkFlagger(this);
		debugListener = new DebugListener(this);

		PluginCommand command = getCommand("regionerator");
		RegioneratorExecutor executor = new RegioneratorExecutor(this, deletionRunnables);
		if (command != null) {
			command.setExecutor(executor);
		}

		// Don't register listeners if there are no worlds configured
		if (config.getWorlds().isEmpty()) {
			getLogger().severe("No worlds are enabled. There's nothing to do!");
			return;
		}

		reloadFeatures();

		debug(DebugLevel.LOW, () -> executor.onCommand(Bukkit.getConsoleSender(), Objects.requireNonNull(command), "regionerator", new String[0]));
	}

	@Override
	public void onDisable() {
		// Manually cancel deletion runnables - Bukkit does not do a good job of informing tasks they can't continue.
		deletionRunnables.values().forEach(BukkitRunnable::cancel);
		deletionRunnables.clear();
		getServer().getScheduler().cancelTasks(this);

		if (chunkFlagger != null) {
			getLogger().info("Shutting down flagger - currently holds " + chunkFlagger.getCached() + " flags.");
			chunkFlagger.shutdown();
		}

		protectionHooks.clear();
	}

	@Override
	public void reloadConfig() {
		super.reloadConfig();
		if (this.config != null) {
			this.config.reload();
		}
		if (this.miscData != null) {
			this.miscData.reload();
		}
	}

	public void reloadFeatures() {
		// Remove all existing features
		HandlerList.unregisterAll(this);
		if (flagging != null) {
			flagging.cancel();
		}
		protectionHooks.clear();

		// Always enable hook listener in case someone else adds hooks.
		getServer().getPluginManager().registerEvents(new HookListener(this), this);

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
				if (hook.isHookUsable()) {
					protectionHooks.add(hook);
					debug(DebugLevel.LOW, () -> "Enabled protection hook for " + hookName);
				} else {
					getLogger().warning("Protection hook for " + hookName + " failed usability check! Deletion is paused.");
					setPaused(true);
				}
			} catch (ClassNotFoundException e) {
				getLogger().severe("No hook found for " + hookName + "! Please request compatibility!");
			} catch (ReflectiveOperationException e) {
				getLogger().log(Level.SEVERE, "Unable to enable hook for " + hookName + "! Deletion is paused.", e);
				setPaused(true);
			} catch (NoClassDefFoundError e) {
				debug(() -> String.format("Dependencies not found for %s hook, skipping.", hookName), e);
			}
		}

		if (config.getFlagDuration() > 0) {
			// Flag duration is set, start flagging

			getServer().getPluginManager().registerEvents(new FlaggingListener(this), this);

			flagging = new FlaggingRunnable(this).runTaskTimer(this, 0, config.getFlaggingInterval());
		} else {
			// Flagging runnable is not scheduled, schedule a task to start deletion
			flagging = new BukkitRunnable() {
				@Override
				public void run() {
					attemptDeletionActivation();
				}
			}.runTaskTimer(this, 0L, 1200L);
		}

		if (debug(DebugLevel.HIGH)) {
			getServer().getPluginManager().registerEvents(debugListener, this);
		}
	}

	public Config config() {
		return config;
	}

	public MiscData getMiscData() {
		return miscData;
	}

	public WorldManager getWorldManager() {
		return worldManager;
	}

	void finishCycle(DeletionRunnable runnable) {
		miscData.setNextCycle(runnable.getWorld(), runnable.getNextRun());
	}

	/**
	 * Attempts to activate {@link DeletionRunnable}s for any configured worlds.
	 */
	public void attemptDeletionActivation() {
		deletionRunnables.values().removeIf(value -> value.getNextRun() < System.currentTimeMillis());

		if (isPaused()) {
			return;
		}

		for (String worldName : config.getWorlds()) {
			if (miscData.getNextCycle(worldName) > System.currentTimeMillis()) {
				// Not time yet.
				continue;
			}
			DeletionRunnable runnable = deletionRunnables.get(worldName);
			if (runnable != null) {
				if (runnable.getNextRun() == Long.MAX_VALUE) {
					// Deletion is ongoing for world.
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

	public Set<Hook> getProtectionHooks() {
		return Collections.unmodifiableSet(this.protectionHooks);
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
		return this.chunkFlagger;
	}

	DebugListener getDebugListener() {
		return debugListener;
	}

	public boolean isPaused() {
		return this.paused.get();
	}

	public void setPaused(boolean paused) {
		boolean wasPaused = this.paused.getAndSet(paused);

		if (paused == wasPaused) {
			return;
		}

		Consumer<Phaser> phaserConsumer = paused ? Phaser::register : Phaser::arriveAndDeregister;
		deletionRunnables.values().stream().map(DeletionRunnable::getPhaser).forEach(phaserConsumer);
	}

	public boolean debug(DebugLevel level) {
		return config.getDebugLevel().ordinal() >= level.ordinal();
	}

	public void debug(DebugLevel level, Supplier<String> message) {
		if (debug(level)) {
			getLogger().info(message.get());
		}
	}

	public void debug(DebugLevel level, Runnable runnable) {
		if (debug(level)) {
			runnable.run();
		}
	}

	public void debug(Supplier<String> message, Throwable throwable) {
		if (debug(DebugLevel.MEDIUM)) {
			getLogger().log(Level.WARNING, message.get(), throwable);
		} else if (debug(DebugLevel.LOW)) {
			getLogger().log(Level.WARNING, message.get());
		}
	}

}
