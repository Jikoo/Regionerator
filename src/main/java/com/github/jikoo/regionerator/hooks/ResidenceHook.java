package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Coords;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/residence.11480/>Residence</a>.
 *
 * @author Jikoo
 */
public class ResidenceHook extends PluginHook {

	private final Method pluginSingleton, pluginGetManager, managerGetResidences,
			residenceIsSubzone, residenceGetWorld, residenceGetAreaMap,
			cuboidGetHigh, cuboidGetLow;

	public ResidenceHook() throws ReflectiveOperationException {
		super("Residence");

		Class<?> pluginClass = Class.forName("com.bekvon.bukkit.residence.Residence");
		pluginSingleton = pluginClass.getDeclaredMethod("getInstance");
		pluginGetManager = pluginClass.getDeclaredMethod("getResidenceManager");
		Class<?> managerClass = Class.forName("com.bekvon.bukkit.residence.protection.ResidenceManager");
		managerGetResidences = managerClass.getDeclaredMethod("getResidences");
		Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.protection.ClaimedResidence");
		residenceIsSubzone = residenceClass.getDeclaredMethod("isSubzone");
		residenceGetWorld = residenceClass.getDeclaredMethod("getWorld");
		residenceGetAreaMap = residenceClass.getDeclaredMethod("getAreaMap");
		Class<?> cuboidClass = Class.forName("com.bekvon.bukkit.residence.protection.CuboidArea");
		cuboidGetHigh = cuboidClass.getDeclaredMethod("getHighLoc");
		cuboidGetLow = cuboidClass.getDeclaredMethod("getLowLoc");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		int minX = Coords.chunkToBlock(chunkX);
		int maxX = minX + 15;
		int minZ = Coords.chunkToBlock(chunkZ);
		int maxZ = minZ + 15;

		try {
			Map<?, ?> residences = (Map<?, ?>) managerGetResidences.invoke(pluginGetManager.invoke(pluginSingleton.invoke(null)));

			for (Object residence : residences.values()) {
				if ((boolean) residenceIsSubzone.invoke(residence)) {
					// Skip all subzones, hopefully will perform slightly better.
					continue;
				}

				if (!chunkWorld.getName().equals(residenceGetWorld.invoke(residence))) {
					continue;
				}

				for (Object cuboid : ((Map<?, ?>) residenceGetAreaMap.invoke(residence)).values()) {
					Location high = (Location) cuboidGetHigh.invoke(cuboid);
					Location low = (Location) cuboidGetLow.invoke(cuboid);

					if (minX <= high.getX() && maxX >= low.getX()
							&& minZ <= high.getZ() && maxZ >= low.getZ()) {
						return true;
					}
				}

			}

			return false;

		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

}
