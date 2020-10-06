package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Listener used for additional debugging info.
 *
 * @author Jikoo
 */
public class DebugListener implements Listener {

	private final Regionerator plugin;
	private final Set<Long> badChunkHashes;

	public DebugListener(Regionerator plugin) {
		this.plugin = plugin;
		this.badChunkHashes = new HashSet<>();
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		if (badChunkHashes.contains(getHash(event.getChunk()))) {
			plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk loaded while being checked at %s, %s", event.getChunk().getX(), event.getChunk().getZ()));
			plugin.debug(DebugLevel.EXTREME, () -> plugin.getLogger().log(Level.INFO, "Chunk load trace", new Throwable()));
		}
	}

	public void monitorChunk(int chunkX, int chunkZ) {
		badChunkHashes.add(getHash(chunkX, chunkZ));
	}

	public void ignoreChunk(int chunkX, int chunkZ) {
		badChunkHashes.remove(getHash(chunkX, chunkZ));
	}

	private long getHash(Chunk chunk) {
		return getHash(chunk.getX(), chunk.getZ());
	}

	private long getHash(long x, long z) {
		return z ^ (x << 32);
	}

}
