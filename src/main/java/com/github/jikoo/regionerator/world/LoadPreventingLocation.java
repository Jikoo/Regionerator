package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Coords;

import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A Location implementation that returns a DummyChunk instead of CraftBukkit's implementation,
 * preventing the chunk from being loaded by the server.
 * 
 * @author Jikoo
 */
public class LoadPreventingLocation extends Location {

	public LoadPreventingLocation(World world, double x, double y, double z) {
		super(world, x, y, z);
	}

	@NotNull
	@Override
	public Chunk getChunk() {
		return new DummyChunk(Objects.requireNonNull(getWorld()), Coords.blockToChunk((int) this.getX()),
				Coords.blockToChunk((int) this.getZ()));
	}

}
