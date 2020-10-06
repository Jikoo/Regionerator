package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Coords;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/civs.67350/>Civs</a>.
 *
 * @author Jikoo
 */
public class CivsHook extends PluginHook {

	private final Method regionManagerSingleton, regionManagerGetRegionsXYZ;
	private final Constructor<?> regionPointsConstructor;

	public CivsHook() throws ReflectiveOperationException {
		super("Civs");

		Class<?> regionManagerClass = Class.forName("org.redcastlemedia.multitallented.civs.regions.RegionManager");
		regionManagerSingleton = regionManagerClass.getDeclaredMethod("getInstance");
		Class<?> regionPointsClass = Class.forName("org.redcastlemedia.multitallented.civs.regions.RegionPoints");
		regionManagerGetRegionsXYZ = regionManagerClass.getDeclaredMethod("getRegionsXYZ", Location.class, regionPointsClass, boolean.class);
		regionPointsConstructor = regionPointsClass.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class);
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		try {
			return ((Collection<?>) regionManagerGetRegionsXYZ.invoke(regionManagerSingleton.invoke(null),
					new Location(chunkWorld, Coords.chunkToBlock(chunkX), 0, Coords.chunkToBlock(chunkZ)),
					regionPointsConstructor.newInstance(16, 0, 255, 0, 16, 0), false)).size() > 0;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

}
