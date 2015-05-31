package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

import me.ryanhamshire.GriefPrevention.CreateClaimResult;
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
		// TODO wait for BigScary to close http://dev.bukkit.org/bukkit-plugins/grief-prevention/tickets/910-
		// Till then, create an admin claim over the entire chunk
		int x = chunkX << 4;
		int z = chunkZ << 4;
		CreateClaimResult result = GriefPrevention.instance.dataStore.createClaim(world, x, x + 15, 0, 255, z, z + 15, null, null, null, null);
		if (result.succeeded) {
			GriefPrevention.instance.dataStore.deleteClaim(result.claim);
		}
		return !result.succeeded;
	}
}
