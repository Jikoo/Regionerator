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

import com.github.jikoo.regionerator.Regionerator;
import me.angeschossen.lands.api.integration.LandsIntegration;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * PluginHook for <a href="https://www.spigotmc.org/resources/lands.53313/">Lands</a>.
 */
public class LandsHook extends PluginHook {

	private LandsIntegration landsAPI;

	public LandsHook() {
		super("Lands");
	}

	@Override
	public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ) {
		return getLandsAPI().isClaimed(chunkWorld, chunkX, chunkZ);
	}

	@Override
	public boolean isAsyncCapable() {
		return true;
	}

	private @NotNull LandsIntegration getLandsAPI() {
		if (landsAPI == null) {
			landsAPI = new LandsIntegration(Regionerator.getPlugin(Regionerator.class));
		}

		return landsAPI;
	}

}
