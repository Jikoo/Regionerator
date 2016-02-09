package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.Hook;

import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.WorldCoord;

/**
 * Hook for the protection plugin <a href=https://github.com/ElgarL/Towny>Towny</a>.
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
			Coord chunkCorner = Coord.parseCoord(CoordinateConversions.chunkToBlock(chunkX),
					CoordinateConversions.chunkToBlock(chunkX));
			return new WorldCoord(chunkWorld.getName(), chunkCorner).getTownBlock().hasTown();
		} catch (Exception e) {
			return false;
		}
	}

}
