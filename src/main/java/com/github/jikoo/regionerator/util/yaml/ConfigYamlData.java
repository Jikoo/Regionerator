package com.github.jikoo.regionerator.util.yaml;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public abstract class ConfigYamlData extends YamlData {

	public ConfigYamlData(@NotNull Plugin plugin) {
		super(plugin, plugin::getConfig, yaml -> plugin.saveConfig());
	}

}
