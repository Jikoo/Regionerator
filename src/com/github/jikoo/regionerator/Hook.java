package com.github.jikoo.regionerator;

import org.bukkit.Bukkit;
import org.bukkit.World;

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

	public abstract boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ);
}
