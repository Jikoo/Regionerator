package com.github.jikoo.regionerator.hooks;

import org.bukkit.Location;
import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

import com.wasteofplastic.askyblock.ASkyBlockAPI;

/**
 * Hook for the plugin <a href=https://www.spigotmc.org/resources/a-skyblock.1220/>ASkyBlock</a>.
 * 
 * @author Jikoo
 */
public class ASkyBlockHook extends Hook {

	public ASkyBlockHook() {
		super("ASkyBlock");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return ASkyBlockAPI.getInstance().getIslandAt(new Location(chunkWorld, chunkX << 4, 0, chunkZ << 4)) != null;
	}

}
