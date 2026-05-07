package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.planarwrappers.util.Coords;
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
			// This falls out as soon as world is not detected as BentoBox world.
			return false;
		}

		int distanceBetweenIslands = BentoBox.getInstance().getIWM().getWorldSettings(chunkWorld).getIslandDistance();
		
		if (distanceBetweenIslands <= 0)
		{
			// Failed BentoBox config file. This is impossible to reach.
			return false;
		}
		
		// Distance between islands is always a half of actual value.
		final int increment = Math.min(distanceBetweenIslands, 8) * 2 - 1;

		int locX = Coords.chunkToBlock(chunkX);
		int locZ = Coords.chunkToBlock(chunkZ);

		IslandsManager manager = BentoBox.getInstance().getIslandsManager();

		for (int x = 0; x < 16; x = x + increment)
		{
			for (int z = 0; z < 16; z = z + increment)
			{
				if (manager.getIslandAt(new Location(chunkWorld, locX + x, 0, locZ + z)).isPresent())
				{
					// Protected island area
					return true;
				}
			}
		}

		// Fallthrough.
		return false;
	}
}
