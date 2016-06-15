package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import org.kingdoms.constants.land.Land;
import org.kingdoms.constants.land.SimpleChunkLocation;
import org.kingdoms.manager.game.GameManagement;

import com.github.jikoo.regionerator.Hook;

/**
 * Hook for the plugin <a href=https://www.spigotmc.org/resources/kingdoms.11833/>Kingdoms</a>.
 * 
 * @author Jikoo
 */
public class KingdomsHook extends Hook {

	public KingdomsHook() {
		super("Kingdoms");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		SimpleChunkLocation chunkLocation = new SimpleChunkLocation(chunkWorld.getName(), chunkX, chunkZ);
		Land land = GameManagement.getLandManager().getOrLoadLand(chunkLocation);
		return land.getOwner() != null;
	}

}
