/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world;

import com.github.jikoo.planarwrappers.util.Coords;
import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A Location implementation that returns a DummyChunk instead of CraftBukkit's implementation,
 * preventing the chunk from being loaded by the server.
 */
public class LoadPreventingLocation extends Location {

	public LoadPreventingLocation(World world, double x, double y, double z) {
		super(world, x, y, z);
	}

	@Override
	public @NotNull Chunk getChunk() {
		return new DummyChunk(Objects.requireNonNull(getWorld()), Coords.blockToChunk((int) this.getX()),
				Coords.blockToChunk((int) this.getZ()));
	}

}
