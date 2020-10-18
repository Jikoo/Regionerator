package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.util.function.ThrowingTriFunction;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/factions.1900/>Factions</a> and
 * <a href=https://www.spigotmc.org/resources/factionsuuid.1035/>FactionsUUID</a>.
 *
 * @author Jikoo
 */
public class FactionsHook extends PluginHook {

	private Method boardSingleton, boardGetFaction, factionIsWilderness;
	private ThrowingTriFunction<Object, String, Integer, Integer> getLocation;

	public FactionsHook() throws ReflectiveOperationException {
		super("Factions");

		// Set up FactionsUUID.
		try {
			Class<?> boardClazz = Class.forName("com.massivecraft.factions.Board");
			boardSingleton = boardClazz.getMethod("getInstance");
			Class<?> factionLocationClazz = Class.forName("com.massivecraft.factions.FLocation");
			boardGetFaction = boardClazz.getMethod("getFactionAt", factionLocationClazz);
			Constructor<?> locationConstructor = factionLocationClazz.getConstructor(String.class, int.class, int.class);
			getLocation = (worldName, chunkX, chunkZ) -> locationConstructor.newInstance(worldName, chunkX, chunkZ);
			Class<?> factionClazz = Class.forName("com.massivecraft.factions.Faction");
			factionIsWilderness = factionClazz.getMethod("isWilderness");

			// FactionsUUID set up successfully, we're done.
			return;
		} catch (ReflectiveOperationException e) {
			// Eat first reflection error - throw on second.
		}

		// MassiveCraft's (discontinued) Factions
		Class<?> massiveBoard = Class.forName("com.massivecraft.factions.entity.BoardColl");
		boardSingleton = massiveBoard.getDeclaredMethod("get");
		Class<?> massivePS = Class.forName("com.massivecraft.massivecore.ps.PS");
		boardGetFaction = massiveBoard.getDeclaredMethod("getFactionAt", massivePS);
		Method locationValueOf = massivePS.getDeclaredMethod("valueOf", String.class, int.class, int.class);
		getLocation = (worldName, chunkX, chunkZ) -> locationValueOf.invoke(null, worldName, chunkX, chunkZ);
		Class<?> massiveFaction = Class.forName("com.massivecraft.factions.entity.Faction");
		factionIsWilderness = massiveFaction.getDeclaredMethod("isNone");

	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		try {
			Object faction = boardGetFaction.invoke(boardSingleton.invoke(null),
					getLocation.apply(chunkWorld.getName(), chunkX, chunkZ));
			return faction != null && !(boolean) factionIsWilderness.invoke(faction);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
