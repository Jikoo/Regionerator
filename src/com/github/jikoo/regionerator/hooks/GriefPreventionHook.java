package com.github.jikoo.regionerator.hooks;

import org.bukkit.Location;
import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

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
		// Till then, check corner as a placeholder implementation.
		return GriefPrevention.instance.dataStore.getClaimAt(new Location(world, chunkX << 4, 64, chunkZ << 4), true, null) != null;
	}
}
