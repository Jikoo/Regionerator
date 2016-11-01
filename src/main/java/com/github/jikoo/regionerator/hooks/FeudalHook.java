package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Hook;

import org.bukkit.World;

import us.forseth11.feudal.core.Feudal;
import us.forseth11.feudal.kingdoms.Land;

/**
 * Hook for the plugin <a href=https://www.spigotmc.org/resources/feudal.22873/>Feudal</a>.
 * 
 * @author Jikoo
 */
public class FeudalHook extends Hook {

	public FeudalHook() {
		super("Feudal");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return Feudal.getLandKingdom(new Land(chunkX, chunkZ, chunkWorld)) != null;
	}

}
