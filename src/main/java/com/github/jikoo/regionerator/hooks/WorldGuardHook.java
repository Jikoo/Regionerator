package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.PluginHook;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

import org.bukkit.World;

/**
 * PluginHook for the protection plugin <a href=http://dev.bukkit.org/bukkit-plugins/worldguard/>WorldGuard</a>.
 * 
 * @author Jikoo
 */
public class WorldGuardHook extends PluginHook {

	public WorldGuardHook() {
		super("WorldGuard");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		int chunkBlockX = CoordinateConversions.chunkToBlock(chunkX);
		int chunkBlockZ = CoordinateConversions.chunkToBlock(chunkZ);
		BlockVector bottom = new BlockVector(chunkBlockX, 0, chunkBlockZ);
		BlockVector top = new BlockVector(chunkBlockX + 15, 255, chunkBlockZ + 15);
		return WorldGuardPlugin.inst().getRegionManager(chunkWorld)
				.getApplicableRegions(new ProtectedCuboidRegion("REGIONERATOR_TMP", bottom, top)).size() > 0;
	}
}
