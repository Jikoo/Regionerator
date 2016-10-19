package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Hook;

import org.bukkit.World;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

/**
 * Hook for the protection plugin <a href=http://dev.bukkit.org/bukkit-plugins/grief-prevention/>GriefPrevention</a>.
 * 
 * @author Jikoo
 */
public class GriefPreventionHook extends Hook {

	public GriefPreventionHook() {
		super("GriefPrevention");
	}

	@Override
	public boolean isChunkProtected(World world, int chunkX, int chunkZ) {
		for (Claim claim : GriefPrevention.instance.dataStore.getClaims(chunkX, chunkZ)) {
			if (claim.getGreaterBoundaryCorner().getWorld().equals(world)) {
				return true;
			}
		}
		return false;
	}
}
