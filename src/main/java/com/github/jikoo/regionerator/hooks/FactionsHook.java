package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

import com.massivecraft.factions.entity.BoardColl;
import com.massivecraft.massivecore.ps.PS;

/**
 * Hook for the plugin <a href=http://dev.bukkit.org/bukkit-plugins/factions/>Factions</a>.
 * 
 * @author Jikoo
 */
public class FactionsHook extends Hook {

	public FactionsHook() {
		super("Factions");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return !BoardColl.get().getFactionAt(PS.valueOf(chunkWorld.getName(), chunkX, chunkZ)).isNone();
	}

}
