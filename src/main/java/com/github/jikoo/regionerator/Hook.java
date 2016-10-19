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

	public String getPluginName() {
		return pluginName;
	}

	public boolean isHookUsable() {
		if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
			return false;
		}

		try {
			this.isChunkProtected(Bukkit.getWorlds().get(0), 0, 0);
		} catch (Exception e) {
			return false;
		}

		return true;
	}

	public abstract boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ);
}
