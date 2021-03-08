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

import org.bukkit.Bukkit;
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
	 * Gets whether or not the Hook's dependencies are present.
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
			this.isChunkProtected(Bukkit.getWorlds().get(0), 0, 0);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	/**
	 * Returns whether or not the Hook is capable of being used from other threads.
	 *
	 * @return true if the Hook is capable of asynchronous operations
	 */
	public boolean isAsyncCapable() {
		return false;
	}

	/**
	 * Checks whether or not the system the Hook interacts with is present in the specified chunk.
	 *
	 * @param chunkWorld the chunk {@link World}
	 * @param chunkX the chunk X coordinate
	 * @param chunkZ the chunk Z coordinate
	 * @return true if the chunk contains data from the hooked system
	 */
	public abstract boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ);

}
