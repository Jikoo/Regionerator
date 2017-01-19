package com.github.jikoo.regionerator.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.massivecraft.factions.entity.BoardColl;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.massivecore.ps.PS;

import com.github.jikoo.regionerator.PluginHook;

import org.bukkit.World;

/**
 * PluginHook for the plugins <a href=https://www.spigotmc.org/resources/factions.1900/>Factions</a> and
 * <a href=https://www.spigotmc.org/resources/factionsuuid.1035/>FactionsUUID</a>.
 * 
 * @author Jikoo
 */
public class FactionsHook extends PluginHook {

	private boolean isFactionsUUID;
	private Object board;
	private Constructor<?> factionLocationConstructor;
	private Method boardGetFaction, factionIsWilderness;

	public FactionsHook() {
		super("Factions");
		try {
			Class<?> boardClazz = Class.forName("com.massivecraft.factions.Board");
			Method boardSingleton = boardClazz.getMethod("getInstance");
			this.board = boardSingleton.invoke(null);
			Class<?> factionLocationClazz = Class.forName("com.massivecraft.factions.FLocation");
			this.boardGetFaction = boardClazz.getMethod("getFactionAt", factionLocationClazz);
			this.factionLocationConstructor = factionLocationClazz.getConstructor(String.class, int.class, int.class);
			Class<?> factionClazz = Class.forName("com.massivecraft.factions.Faction");
			this.factionIsWilderness = factionClazz.getMethod("isWilderness");
			this.isFactionsUUID = true;
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException
				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			this.isFactionsUUID = false;
		}
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		if (isFactionsUUID) {
			return isFactionsUUIDProtected(chunkWorld, chunkX, chunkZ);
		}
		return isFactionsProtected(chunkWorld, chunkX, chunkZ);
	}

	private boolean isFactionsProtected(World chunkWorld, int chunkX, int chunkZ) {
		Faction faction = BoardColl.get().getFactionAt(PS.valueOf(chunkWorld.getName(), chunkX, chunkZ));
		return faction != null && !faction.isNone();
	}

	private boolean isFactionsUUIDProtected(World chunkWorld, int chunkX, int chunkZ) {
		try {
			Object factionLocation = factionLocationConstructor.newInstance(chunkWorld.getName(), chunkX, chunkZ);
			Object faction = boardGetFaction.invoke(board, factionLocation);
			return faction != null && !(boolean) factionIsWilderness.invoke(faction);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			return false;
		}
	}

}
