package com.github.jikoo.regionerator.hooks;

import com.palmergames.bukkit.towny.object.WorldCoord;

import com.github.jikoo.regionerator.Hook;

import org.bukkit.World;

/**
 * Hook for the protection plugin <a href=https://github.com/LlmDl/Towny>Towny</a>.
 * 
 * @author Jikoo
 */
public class TownyHook extends Hook {

	public TownyHook() {
		super("Towny");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		try {
			return new WorldCoord(chunkWorld.getName(), chunkX, chunkZ).getTownBlock().hasTown();
		} catch (Exception e) {
			return false;
		}
	}

}
