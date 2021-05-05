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
import org.jetbrains.annotations.NotNull;

/**
 * Hook for respecting vanilla's spawn protection radius.
 */
public class VanillaSpawnProtectionHook extends Hook {

	public VanillaSpawnProtectionHook() {
		super("vanilla spawn protection");
	}

	@Override
	public boolean areDependenciesPresent() {
		return true;
	}

	@Override
	public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ) {
		int protectionRadius = Bukkit.getSpawnRadius();

		if (protectionRadius <= 0) {
			return false;
		}

		// Convert to chunk protection radius
		protectionRadius = Coords.blockToChunk(protectionRadius);

		Location spawn = chunkWorld.getSpawnLocation();

		int spawnChunkX = Coords.blockToChunk(spawn.getBlockX());
		if (chunkX > spawnChunkX + protectionRadius || chunkX < spawnChunkX - protectionRadius) {
			// Chunk x is outside of protection radius
			return false;
		}

		int spawnChunkZ = Coords.blockToChunk(spawn.getBlockZ());

		return chunkZ <= spawnChunkZ + protectionRadius && chunkZ >= spawnChunkZ - protectionRadius;
	}

	@Override
	public boolean isAsyncCapable() {
		return true;
	}

}
