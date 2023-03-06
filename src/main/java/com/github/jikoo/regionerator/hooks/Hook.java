/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.planarwrappers.util.Coords;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A framework for adapters allowing Regionerator to respect other systems.
 */
public abstract class Hook {

	private final String protectionName;

	/**
	 * Constructs a Hook using the specified name.
	 *
	 * @param protectionName the name of the protection system used by the hook
	 */
	public Hook(String protectionName) {
		this.protectionName = protectionName;
	}

	/**
	 * Gets the name of the protection system the Hook is designed for.
	 *
	 * @return the name
	 */
	public String getProtectionName() {
		return protectionName;
	}

	/**
	 * Gets whether the Hook's dependencies are present.
	 *
	 * @return true if the Hook's dependencies are present
	 */
	public abstract boolean areDependenciesPresent();

	/**
	 * Checks a Hook's usability.
	 *
	 * @return true if the hook can be used to check a chunk
	 */
	public boolean isHookUsable() {
		try {
			// Check every world - hooks may have a quick return condition for being disabled in a world.
			for (World world : Bukkit.getWorlds()) {
				Location spawn = world.getSpawnLocation();
				// Check 2 regions away from spawn. This ensures that we're not in loaded area.
				this.isChunkProtected(world, Coords.blockToChunk(spawn.getBlockX()) + 64, Coords.blockToChunk(spawn.getBlockZ()) + 64);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	/**
	 * Returns whether the Hook is capable of being used from other threads.
	 *
	 * @return true if the Hook is capable of asynchronous operations
	 */
	public boolean isAsyncCapable() {
		return false;
	}

	/**
	 * Checks whether the system the Hook interacts with is present in the specified chunk.
	 *
	 * @param chunkWorld the chunk {@link World}
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @return true if the chunk contains data from the hooked system
	 */
	public abstract boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ);

}
