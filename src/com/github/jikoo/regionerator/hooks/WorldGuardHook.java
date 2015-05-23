package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

/**
 * Hook for the protection plugin <a href=http://dev.bukkit.org/bukkit-plugins/worldguard/>WorldGuard</a>.
 * 
 * @author Jikoo
 */
public class WorldGuardHook extends Hook {

	public WorldGuardHook() {
		super("WorldGuard");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		BlockVector bottom = new BlockVector(chunkX << 4, 0, chunkZ << 4);
		BlockVector top = new BlockVector(chunkX << 4 + 15, 255, chunkZ << 4 + 15);
		return WorldGuardPlugin.inst().getRegionManager(chunkWorld)
				.getApplicableRegions(new ProtectedCuboidRegion("REGIONERATOR_TMP", bottom, top))
				.getRegions().size() > 0;
	}
}
