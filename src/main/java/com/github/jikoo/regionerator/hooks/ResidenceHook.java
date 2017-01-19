package com.github.jikoo.regionerator.hooks;

import com.bekvon.bukkit.residence.Residence;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.PluginHook;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * PluginHook for the plugin <a href=https://www.spigotmc.org/resources/residence.11480/>Residence</a>.
 * 
 * @author Jikoo
 */
public class ResidenceHook extends PluginHook {

	public ResidenceHook() {
		super("Residence");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		Location chunkLocation = new Location(chunkWorld,
				CoordinateConversions.chunkToBlock(chunkX), 0,
				CoordinateConversions.chunkToBlock(chunkZ));
		return Residence.getInstance().getResidenceManager().getByLoc(chunkLocation) != null;
	}

}
