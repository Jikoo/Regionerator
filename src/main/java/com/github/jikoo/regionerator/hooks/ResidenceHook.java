package com.github.jikoo.regionerator.hooks;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.CuboidArea;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.PluginHook;

import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/residence.11480/>Residence</a>.
 *
 * @author Jikoo
 */
public class ResidenceHook extends PluginHook {

	public ResidenceHook() {
		super("Residence");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		int minX = CoordinateConversions.chunkToBlock(chunkX);
		int maxX = minX + 15;
		int minZ = CoordinateConversions.chunkToBlock(chunkZ);
		int maxZ = minZ + 15;
		for (ClaimedResidence residence : Residence.getInstance().getResidenceManager().getResidences().values()) {
			if (residence.isSubzone()) {
				// Skip all subzones, hopefully will perform slightly better.
				continue;
			}
			if (!chunkWorld.getName().equals(residence.getWorld())) {
				continue;
			}
			for (CuboidArea area : residence.getAreaMap().values()) {
				if (minX <= area.getHighLoc().getX() && maxX >= area.getLowLoc().getX()
						&& minZ <= area.getHighLoc().getZ() && maxZ >= area.getLowLoc().getZ()) {
					return true;
				}
			}
		}
		return false;
	}

}
