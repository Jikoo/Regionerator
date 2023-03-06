/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.hooks;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * PluginHook for <a href="http://dev.bukkit.org/bukkit-plugins/grief-prevention/">GriefPrevention</a>.
 */
public class GriefPreventionHook extends PluginHook {

	public GriefPreventionHook() {
		super("GriefPrevention");
	}

	@Override
	public boolean isChunkProtected(@NotNull World world, int chunkX, int chunkZ) {
		if (!GriefPrevention.instance.claimsEnabledForWorld(world)) {
			return false;
		}

		for (Claim claim : GriefPrevention.instance.dataStore.getClaims(chunkX, chunkZ)) {
			if (world.equals(claim.getGreaterBoundaryCorner().getWorld())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isAsyncCapable() {
		return true;
	}

}
