package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.PluginHook;

import org.bukkit.World;

import us.forseth11.feudal.core.Feudal;
import us.forseth11.feudal.kingdoms.Land;

/**
 * PluginHook for the plugin <a href=https://www.spigotmc.org/resources/feudal.22873/>Feudal</a>.
 * 
 * @author Jikoo
 */
public class FeudalHook extends PluginHook {

	public FeudalHook() {
		super("Feudal");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return Feudal.getLandKingdom(new Land(chunkX, chunkZ, chunkWorld)) != null;
	}

}
