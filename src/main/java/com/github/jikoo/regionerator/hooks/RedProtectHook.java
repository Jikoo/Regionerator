package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import br.net.fabiozumbi12.RedProtect.RedProtect;
import br.net.fabiozumbi12.RedProtect.Region;

import com.github.jikoo.regionerator.Hook;

/**
 * Hook for the plugin <a href=https://www.spigotmc.org/resources/redprotect.15841/>RedProtect</a>.
 * 
 * @author Jikoo
 */
public class RedProtectHook extends Hook {

	public RedProtectHook() {
		super("RedProtect");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		/*
		 * RedProtectAPI is somewhat lacking. Also, it contains a typo. While I'd bet it's pretty
		 * efficient and good, the code style is utterly horrifying.
		 * 
		 * API-only solution:
		 * return RedProtectAPI.getChunkRegions(new DummyChunk(chunkWorld, chunkX, chunkZ)).size() > 0;
		 * However, that method is new in 6.6.0, and I can perform the needed checks faster avoiding it.
		 */
		for (Region region : RedProtect.rm.getRegionsByWorld(chunkWorld)) {
			if (region.getMinMbrX() >> 4 > chunkX || region.getMaxMbrX() >> 4 < chunkX
					|| region.getMinMbrZ() >> 4 > chunkZ || region.getMaxMbrZ() >> 4 < chunkZ) {
				continue;
			}
			return true;
		}
		return false;
	}

}
