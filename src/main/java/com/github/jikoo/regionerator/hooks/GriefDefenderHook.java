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

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.ClaimManager;
import java.util.Set;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/griefdefender.68900/>GriefDefender</a>.
 */
public class GriefDefenderHook extends PluginHook {

	public GriefDefenderHook() {
		super("GriefDefender");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		if (!GriefDefender.getCore().isEnabled(chunkWorld.getUID())) {
			return false;
		}
		ClaimManager claimManager = GriefDefender.getCore().getClaimManager(chunkWorld.getUID());
		Set<Claim> claims = claimManager.getChunksToClaimsMap().get(getChunkKey(chunkX, chunkZ));
		return claims != null && claims.size() > 0;
	}

	/**
	 * Calculate key from chunk coordinates.
	 * <p>
	 * See <a href=https://github.com/bloodmc/GriefDefender/blob/6f96c90d9d0baf29dfe167fe1f20b678adb6b72d/bukkit/src/main/java/com/griefdefender/claim/GDClaimManager.java#L649>GDClaimManager#L649</a>.
	 *
	 * @param chunkX the chunk X
	 * @param chunkZ the chunk Z
	 * @return the calculated chunk key
	 */
	private long getChunkKey(long chunkX, long chunkZ) {
		return chunkX & 0xffffffffL | (chunkZ & 0xffffffffL) << 32;
	}

}
