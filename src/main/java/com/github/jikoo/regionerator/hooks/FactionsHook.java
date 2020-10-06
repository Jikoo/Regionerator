package com.github.jikoo.regionerator.hooks;

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
	private Constructor<?> locationConstructor = null;
	private Method locationValueOf = null;

	public FactionsHook() throws ReflectiveOperationException {
		super("Factions");

		// Set up FactionsUUID.
		try {
			Class<?> boardClazz = Class.forName("com.massivecraft.factions.Board");
			boardSingleton = boardClazz.getMethod("getInstance");
			Class<?> factionLocationClazz = Class.forName("com.massivecraft.factions.FLocation");
			boardGetFaction = boardClazz.getMethod("getFactionAt", factionLocationClazz);
			locationConstructor = factionLocationClazz.getConstructor(String.class, int.class, int.class);
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
		locationValueOf = massivePS.getDeclaredMethod("valueOf", String.class, int.class, int.class);
		Class<?> massiveFaction = Class.forName("com.massivecraft.factions.entity.Faction");
		factionIsWilderness = massiveFaction.getDeclaredMethod("isNone");

	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		try {
			Object faction = boardGetFaction.invoke(boardSingleton.invoke(null),
					getFactionLocation(chunkWorld.getName(), chunkX, chunkZ));
			return faction != null && !(boolean) factionIsWilderness.invoke(faction);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private Object getFactionLocation(String worldName, int chunkX, int chunkZ) throws ReflectiveOperationException {
		if (locationConstructor != null) {
			return locationConstructor.newInstance(worldName, chunkX, chunkZ);
		}
		return locationValueOf.invoke(null, worldName, chunkX, chunkZ);
	}

}
