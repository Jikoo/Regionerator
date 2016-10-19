package com.github.jikoo.regionerator.hooks;

import com.wasteofplastic.askyblock.ASkyBlockAPI;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.Hook;

import org.bukkit.Location;
import org.bukkit.World;

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
		Location chunkLoc = new Location(chunkWorld, CoordinateConversions.chunkToBlock(chunkX), 0,
				CoordinateConversions.chunkToBlock(chunkZ));
		return ASkyBlockAPI.getInstance().getIslandAt(chunkLoc) != null;
	}

}
