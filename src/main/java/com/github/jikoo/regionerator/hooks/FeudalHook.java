package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Coords;
import com.github.jikoo.regionerator.world.LoadPreventingLocation;

import org.bukkit.World;

import us.forseth11.feudal.core.Feudal;
import us.forseth11.feudal.kingdoms.Land;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/feudal.22873/>Feudal</a>.
 *
 * @author Jikoo
 */
public class FeudalHook extends PluginHook {

	public FeudalHook() {
		super("Feudal");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return Feudal.getLandKingdom(new Land(
				new LoadPreventingLocation(chunkWorld, Coords.chunkToBlock(chunkX),
						0, Coords.chunkToBlock(chunkZ)))) != null;
	}

}
