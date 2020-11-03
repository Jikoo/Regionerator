package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.Regionerator;
import org.bukkit.World;
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
	 * Regionerator periodically spends a sizable amount of time deleting untouched area, causing unnecessary load.
	 * To combat this a little, freshly generated chunks are automatically flagged.
	 * 
	 * @param event the ChunkPopulateEvent
	 */
	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event) {
		World world = event.getWorld();

		if (!plugin.config().isEnabled(world.getName())) {
			return;
		}

		plugin.getFlagger().flagChunk(world.getName(), event.getChunk().getX(), event.getChunk().getZ(), plugin.config().getFlagGenerated(world));
	}

}
