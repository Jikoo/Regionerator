package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.Hook;

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
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		int protectionRadius = Bukkit.getSpawnRadius();

		if (protectionRadius <= 0) {
			return false;
		}

		// Convert to chunk protection radius
		protectionRadius = CoordinateConversions.blockToChunk(protectionRadius);

		Location spawn = chunkWorld.getSpawnLocation();

		int spawnChunkX = CoordinateConversions.blockToChunk(spawn.getBlockX());
		if (chunkX > spawnChunkX + protectionRadius || chunkX < spawnChunkX - protectionRadius) {
			// Chunk x is outside of protection radius
			return false;
		}

		int spawnChunkZ = CoordinateConversions.blockToChunk(spawn.getBlockZ());

		return chunkZ <= spawnChunkZ + protectionRadius && chunkZ >= spawnChunkZ - protectionRadius;
	}

}
