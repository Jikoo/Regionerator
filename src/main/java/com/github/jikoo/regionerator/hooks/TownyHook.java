package com.github.jikoo.regionerator.hooks;

import com.palmergames.bukkit.towny.object.WorldCoord;

import com.github.jikoo.regionerator.PluginHook;

import org.bukkit.World;

/**
 * PluginHook for the protection plugin <a href=https://github.com/LlmDl/Towny>Towny</a>.
 * 
 * @author Jikoo
 */
public class TownyHook extends PluginHook {

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
