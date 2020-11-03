package com.github.jikoo.regionerator.util.yaml;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"unused", "SameParameterValue"})
public abstract class YamlData {

	final Plugin plugin;
	private final Supplier<FileConfiguration> loadSupplier;
	private final Consumer<FileConfiguration> saveConsumer;
	private FileConfiguration storage;
	private boolean dirty = false;
	private BukkitTask saveTask;

	public YamlData(@NotNull Plugin plugin, @NotNull Supplier<FileConfiguration> loadSupplier,
			@NotNull Consumer<FileConfiguration> saveConsumer) {
		this.plugin = plugin;
		this.loadSupplier = loadSupplier;
		this.saveConsumer = saveConsumer;
		this.storage = loadSupplier.get();
	}

	public void reload() {
		this.storage = loadSupplier.get();
	}

	void set(@NotNull String path, @Nullable Object value) {
		Object existing = this.storage.get(path);
		if (Objects.equals(value, existing)) {
			return;
		}
		this.storage.set(path, value);
		this.dirty = true;
		save();
	}

	@Nullable Object get(@NotNull String path) {
		return this.storage.get(path);
	}

	@Nullable String getString(@NotNull String path) {
		return this.storage.getString(path);
	}

	int getInt(@NotNull String path) {
		return this.storage.getInt(path);
	}

	boolean getBoolean(@NotNull String path) {
		return this.storage.getBoolean(path);
	}

	double getDouble(@NotNull String path) {
		return this.storage.getDouble(path);
	}

	long getLong(@NotNull String path) {
		return this.storage.getLong(path);
	}

	@NotNull List<String> getStringList(@NotNull String path) {
		return this.storage.getStringList(path);
	}

	<T> @Nullable T getObject(@NotNull String path, @NotNull Class<T> clazz) {
		return this.storage.getObject(path, clazz);
	}

	<T extends ConfigurationSerializable> @Nullable T getSerializable(@NotNull String path, @NotNull Class<T> clazz) {
		return this.storage.getSerializable(path, clazz);
	}

	@Nullable Vector getVector(@NotNull String path) {
		return this.storage.getVector(path);
	}

	@Nullable ItemStack getItemStack(@NotNull String path) {
		return this.storage.getItemStack(path);
	}

	@Nullable Location getLocation(@NotNull String path) {
		return this.storage.getLocation(path);
	}

	@NotNull FileConfiguration raw() {
		return this.storage;
	}

	void save() {
		if (saveTask != null || !dirty) {
			return;
		}
		try {
			saveTask = new BukkitRunnable() {
				@Override
				public void run() {

					saveNow();
				}

				@Override
				public synchronized void cancel() throws IllegalStateException {
					super.cancel();
					saveNow();
				}
			}.runTaskLater(plugin, 200L);
		} catch (IllegalStateException e) {
			// Plugin is being disabled, cannot schedule tasks
			saveNow();
		}
	}

	private void saveNow() {
		if (!this.dirty) {
			return;
		}

		try {
			this.saveConsumer.accept(this.storage);
			this.dirty = false;
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Error saving yaml data", e);
		}
	}

}
