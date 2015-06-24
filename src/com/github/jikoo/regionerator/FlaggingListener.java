package com.github.jikoo.regionerator;

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
	 * Deleting freshly generated chunks is not worth it. During its live test, I've noticed that
	 * Regionerator periodically spends a sizeable amount of time deleting untouched area, causing
	 * unnecessary load. To combat this a little, freshly generated chunks are automatically
	 * flagged.
	 * <p>
	 * Rather than use the ChunkLoadEvent, to reduce server load we use the ChunkPopulateEvent,
	 * which is called when the decorations are added to a fresh chunk.
	 * 
	 * @param event the ChunkPopulateEvent
	 */
	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event) {
		// Flag only this chunk
		plugin.getFlagger().flagChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ(), 0);
	}
}
