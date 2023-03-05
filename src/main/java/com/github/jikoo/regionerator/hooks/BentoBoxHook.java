package com.github.jikoo.regionerator.hooks;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.managers.IslandsManager;


/**
 * PluginHook for <a href="https://github.com/BentoBoxWorld/BentoBox">BentoBox</a> and its GameModes.
 */
public class BentoBoxHook extends PluginHook
{
	public BentoBoxHook()
	{
		super("BentoBox");
	}

	@Override
	public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ)
	{
		if (!BentoBox.getInstance().getIWM().inWorld(chunkWorld))
		{
			return false;
		}

		int locX = chunkX << 4;
		int locZ = chunkZ << 4;

		IslandsManager manager = BentoBox.getInstance().getIslandsManager();

		return manager.getIslandAt(new Location(chunkWorld, locX, 0, locZ)).isPresent() ||
			manager.getIslandAt(new Location(chunkWorld, locX + 15, 0, locZ)).isPresent() ||
			manager.getIslandAt(new Location(chunkWorld, locX + 15, 0, locZ + 15)).isPresent() ||
			manager.getIslandAt(new Location(chunkWorld, locX, 0, locZ + 15)).isPresent();
	}
}
