package com.github.jikoo.regionerator.hooks;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.CuboidArea;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.PluginHook;

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
		int blockX = CoordinateConversions.chunkToBlock(chunkX);
		int blockZ = CoordinateConversions.chunkToBlock(chunkZ);
		for (ClaimedResidence residence : Residence.getInstance().getResidenceManager().getResidences().values()) {
			if (residence.isSubzone()) {
				// Skip all subzones, hopefully will perform slightly better.
				continue;
			}
			if (!chunkWorld.equals(residence.getWorld())) {
				continue;
			}
			for (CuboidArea area : residence.getAreaArray()) {
				if (area.getHighLoc().getX() >= blockX && area.getLowLoc().getX() <= blockX
						&& area.getHighLoc().getZ() >= blockZ && area.getLowLoc().getZ() <= blockZ) {
					return true;
				}
			}
		}
		return false;
	}

}
