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

import com.cjburkey.claimchunk.ClaimChunk;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://github.com/cjburkey01/ClaimChunk/>ClaimChunk</a>.
 */
public class ClaimChunkHook extends PluginHook {

	public ClaimChunkHook() {
		super("ClaimChunk");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return ClaimChunk.getPlugin(ClaimChunk.class).getChunkHandler().isClaimed(chunkWorld, chunkX, chunkZ);
	}

}
