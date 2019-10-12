package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

/**
 * PluginHook for <a href=http://dev.bukkit.org/bukkit-plugins/grief-prevention/>GriefPrevention</a>.
 *
 * @author Jikoo
 */
public class GriefPreventionHook extends PluginHook {

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
