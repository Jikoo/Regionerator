package com.github.jikoo.regionerator.event;

import com.github.jikoo.regionerator.world.ChunkInfo;
import java.util.Collection;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a collection of chunks is deleted by Regionerator. This event is not cancellable because
 * plugins should not be preventing deletion here - a PluginHook should be used to mark chunks as in use.
 * This event is purely available so data relevant to a chunk can be deleted safely.
 * <p>
 * Unfortunately, Regionerator cannot guarantee that the chunk has been completely deleted - it's
 * possible that the chunk has been unloaded and is queued to be saved, causing Regionerator's
 * changes to be overwritten. It is probably advisable to instead clean up data during the ChunkPopulateEvent.
 *
 * @author Jikoo
 */
public class RegioneratorDeleteEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final World world;
	private final Collection<ChunkInfo> chunks;

	public RegioneratorDeleteEvent(@NotNull World world, Collection<ChunkInfo> chunks) {
		super(true);
		this.world = world;
		this.chunks = chunks;
	}

	/**
	 * Gets the {@link World} the chunks are being deleted from.
	 *
	 * @return the {@link World}
	 */
	@NotNull
	public World getWorld() {
		return world;
	}

	/**
	 * Gets a {@link Collection} of the {@link ChunkInfo} being wiped.
	 *
	 * @return the {@link Collection<ChunkInfo>}
	 */
	@NotNull
	public Collection<ChunkInfo> getChunks() {
		return chunks;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
