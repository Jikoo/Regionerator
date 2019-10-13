package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Regionerator;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Basic framework for all hooks.
 *
 * @author Jikoo
 */
public abstract class Hook {

	private final String protectionName;

	public Hook(String protectionName) {
		this.protectionName = protectionName;
	}

	public String getProtectionName() {
		return protectionName;
	}

	public abstract boolean areDependenciesPresent();

	public boolean isHookUsable() {
		try {
			this.isChunkProtected(Bukkit.getWorlds().get(0), 0, 0);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public boolean isReadyOnEnable() {
		return true;
	}

	public void readyLater(Regionerator plugin) {

	}

	public abstract boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ);

}
