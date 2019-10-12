package com.github.jikoo.regionerator.hooks;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import br.net.fabiozumbi12.RedProtect.Bukkit.Region;

import com.github.jikoo.regionerator.CoordinateConversions;

import com.github.jikoo.regionerator.world.DummyChunk;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/redprotect.15841/>RedProtect</a>.
 *
 * @author Jikoo
 */
public class RedProtectHook extends PluginHook {

	private boolean needAPI = false;

	public RedProtectHook() {
		super("RedProtect");
	}

	@Override
	public boolean isHookUsable() {
		try {
			return super.isHookUsable();
		} catch (Exception | NoSuchMethodError | NoSuchFieldError e) {
			System.err.println("RedProtect fast hook failed usability! Falling back to slower API-based solution.");
			System.err.println("Please report this stack trace:");
			e.printStackTrace();
			needAPI = true;
		}

		return super.isHookUsable();
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		/*
		 * RedProtectAPI is somewhat lacking and contains several typos.
		 * Check is far more efficient to perform off the API.
		 */

		if (needAPI) {
			return RedProtect.get().getAPI().getChunkRegions(new DummyChunk(chunkWorld, chunkX, chunkZ)).size() > 0;
		}

		for (Region region : RedProtect.get().rm.getRegionsByWorld(chunkWorld.getName())) {
			Location min = region.getMinLocation();
			Location max = region.getMaxLocation();
			if (CoordinateConversions.blockToChunk(min.getBlockX()) > chunkX
					|| CoordinateConversions.blockToChunk(max.getBlockX()) < chunkX
					|| CoordinateConversions.blockToChunk(min.getBlockZ()) > chunkZ
					|| CoordinateConversions.blockToChunk(max.getBlockZ()) < chunkZ) {
				continue;
			}
			return true;
		}

		return false;
	}

}
