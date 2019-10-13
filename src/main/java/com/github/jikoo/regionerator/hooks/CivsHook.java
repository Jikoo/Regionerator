package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.CoordinateConversions;
import org.bukkit.Location;
import org.bukkit.World;
import org.redcastlemedia.multitallented.civs.regions.RegionManager;

public class CivsHook extends PluginHook {

	public CivsHook() {
		super("Civs");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return RegionManager.getInstance().getRegionsXYZ(
				new Location(chunkWorld, CoordinateConversions.chunkToBlock(chunkX), 0, CoordinateConversions.chunkToBlock(chunkZ)),
				16, 0, 255, 0, 16, 0, false).size() > 0;
	}

}
