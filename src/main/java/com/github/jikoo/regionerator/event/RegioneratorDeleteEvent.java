package com.github.jikoo.regionerator.event;

import com.github.jikoo.regionerator.world.ChunkInfo;
import java.util.Collection;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RegioneratorDeleteEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final World world;
	private final Collection<ChunkInfo> chunks;

	public RegioneratorDeleteEvent(@NotNull World world,
			Collection<ChunkInfo> chunks) {
		this.world = world;
		this.chunks = chunks;
	}

	@NotNull
	public World getWorld() {
		return world;
	}

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
