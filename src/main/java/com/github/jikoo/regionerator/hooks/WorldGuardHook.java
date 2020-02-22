package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.CoordinateConversions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

import org.bukkit.World;

/**
 * PluginHook for <a href=http://dev.bukkit.org/bukkit-plugins/worldguard/>WorldGuard</a>.
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

		BlockVector3 bottom = BlockVector3.at(chunkBlockX, 0, chunkBlockZ);
		BlockVector3 top = BlockVector3.at(chunkBlockX + 15, 255, chunkBlockZ + 15);

		RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(chunkWorld));

		if (regionManager == null) {
			return false;
		}

		return regionManager.getApplicableRegions(new ProtectedCuboidRegion("REGIONERATOR_TMP", bottom, top)).size() > 0;
	}

}
