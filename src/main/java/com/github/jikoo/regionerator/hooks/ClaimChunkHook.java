package com.github.jikoo.regionerator.hooks;

import com.cjburkey.claimchunk.ClaimChunk;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://github.com/cjburkey01/ClaimChunk/>ClaimChunk</a>.
 *
 * @author Jikoo
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
