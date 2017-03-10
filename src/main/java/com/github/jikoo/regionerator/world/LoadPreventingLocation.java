package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.CoordinateConversions;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * 
 * 
 * @author Jikoo
 */
public class LoadPreventingLocation extends Location {

	public LoadPreventingLocation(World world, double x, double y, double z) {
		super(world, x, y, z);
	}

	@Override
	public Chunk getChunk() {
		return new DummyChunk(getWorld(), CoordinateConversions.blockToChunk((int) this.getX()),
				CoordinateConversions.blockToChunk((int) this.getZ()));
	}

}
