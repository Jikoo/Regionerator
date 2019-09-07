package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.PluginHook;
import com.songoda.ultimateclaims.UltimateClaims;
import org.bukkit.World;

public class UltimateClaimsHook extends PluginHook {

	public UltimateClaimsHook() {
		super("UltimateClaims");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return UltimateClaims.getInstance().getClaimManager().getClaim(chunkWorld.getName(), chunkX, chunkZ) != null;
	}

}
