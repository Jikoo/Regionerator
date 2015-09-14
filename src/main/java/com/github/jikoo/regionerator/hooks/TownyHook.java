package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.WorldCoord;

/**
 * Hook for the protection plugin <a href=https://github.com/ElgarL/Towny>Towny</a>.
 * 
 * @author Jikoo
 */
public class TownyHook extends Hook {

	public TownyHook() {
		super("Towny");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		try {
			return new WorldCoord(chunkWorld.getName(), Coord.parseCoord(chunkX << 4, chunkZ << 4)).getTownBlock().hasTown();
		} catch (NotRegisteredException e) {
			return false;
		}
	}

}
