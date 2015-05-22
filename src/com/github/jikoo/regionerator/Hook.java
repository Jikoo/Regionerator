package com.github.jikoo.regionerator;

import org.bukkit.Bukkit;

/**
 * Framework for managing plugin hooks.
 * 
 * @author Jikoo
 */
public abstract class Hook {

	private final String pluginName;

	public Hook(String pluginName) {
		this.pluginName = pluginName;
	}

	public boolean isHookUsable() {
		return Bukkit.getPluginManager().isPluginEnabled(pluginName);
	}

	public abstract boolean isChunkProtected(int chunkX, int chunkZ);
}
