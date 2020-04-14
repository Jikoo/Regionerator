package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.Regionerator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

/**
 * Listener used to flag chunks.
 * 
 * @author Jikoo
 */
public class FlaggingListener implements Listener {

	private final Regionerator plugin;

	public FlaggingListener(Regionerator plugin) {
		this.plugin = plugin;
	}

	/**
	 * Deleting freshly generated chunks is not worth it. Regionerator periodically spends a sizable
	 * amount of time deleting untouched area, causing unnecessary load. To combat this a little,
	 * freshly generated chunks are automatically flagged.
	 * 
	 * @param event the ChunkPopulateEvent
	 */
	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event) {
		plugin.getFlagger().flagChunksInRadius(event.getWorld().getName(), event.getChunk().getX(),
				event.getChunk().getZ(), 0, plugin.config().getFlagGenerated());
	}

}
