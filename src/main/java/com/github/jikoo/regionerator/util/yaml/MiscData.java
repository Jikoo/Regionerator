package com.github.jikoo.regionerator.util.yaml;

import java.io.File;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MiscData extends FileYamlData {

	public MiscData(@NotNull Plugin plugin, @NotNull File file) {
		super(plugin, file);
	}

	public void setNextCycle(@NotNull String worldName, long timestamp) {
		set("next-cycle." + worldName, timestamp);
	}

	public long getNextCycle(@NotNull String worldName) {
		return getLong("next-cycle." + worldName);
	}

}
