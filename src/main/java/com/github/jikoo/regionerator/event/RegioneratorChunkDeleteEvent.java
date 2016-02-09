package com.github.jikoo.regionerator.event;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a Chunk is deleted by Regionerator. This event is not cancellable, because
 * plugins should not be preventing deletion here. Instead, they should be registering a Hook. This
 * event is purely available so data relevant to a chunk can be deleted safely.
 * <p>
 * Unfortunately, Regionerator cannot guarantee that the chunk has been completely deleted - it's
 * possible that the chunk has been unloaded and is queued to be saved, causing Regionerator's
 * changes to be overwritten.
 * 
 * @author Jikoo
 */
public class RegioneratorChunkDeleteEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final World world;
	private final int chunkX, chunkZ;

	public RegioneratorChunkDeleteEvent(World world, int chunkX, int chunkZ) {
		this.world = world;
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
	}

	public World getWorld() {
		return this.world;
	}

	/**
	 * Gets the X coordinate of the Chunk being deleted.
	 * <p>
	 * This is NOT a Location coordinate.
	 * Location x = Chunk x << 4
	 * 
	 * @return the Chunk X coordinate
	 */
	public int getChunkX() {
		return this.chunkX;
	}

	/**
	 * Gets the Z coordinate of the Chunk being deleted.
	 * <p>
	 * This is NOT a Location coordinate.
	 * Location Z = Chunk Z << 4
	 * 
	 * @return the Chunk Z coordinate
	 */
	public int getChunkZ() {
		return this.chunkZ;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
