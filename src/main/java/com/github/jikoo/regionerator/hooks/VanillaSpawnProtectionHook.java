package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.planarwrappers.util.Coords;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Hook for respecting vanilla's spawn protection radius.
 * 
 * @author Jikoo
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
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
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
