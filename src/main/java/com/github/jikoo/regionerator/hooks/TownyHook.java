package com.github.jikoo.regionerator.hooks;

import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.WorldCoord;

import com.github.jikoo.regionerator.CoordinateConversions;

import org.bukkit.World;

/**
 * PluginHook for <a href=https://github.com/LlmDl/Towny>Towny</a>.
 *
 * @author Jikoo
 */
public class TownyHook extends PluginHook {

	public TownyHook() {
		super("Towny");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		int minX = CoordinateConversions.chunkToBlock(chunkX);
		int maxX = minX + 15;
		int minZ = CoordinateConversions.chunkToBlock(chunkZ);
		int maxZ = minZ + 15;

		Coord lowCoord = Coord.parseCoord(minX, minZ);
		Coord highCoord = Coord.parseCoord(maxX, maxZ);
		int cellSize = TownySettings.getTownBlockSize();

		for (int x = lowCoord.getX(); x <= highCoord.getX(); x += cellSize) {
			for (int z = lowCoord.getZ(); z <= highCoord.getZ(); z += cellSize) {
				WorldCoord worldCoord = new WorldCoord(chunkWorld.getName(), x, z);
				try {
					if (worldCoord.getTownBlock().hasTown()) {
						return true;
					}
				} catch (Exception ignored) {}
			}
		}
		return false;
	}

}
