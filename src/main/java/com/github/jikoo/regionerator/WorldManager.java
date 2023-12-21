/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.world.impl.anvil.AnvilWorld;
import com.github.jikoo.regionerator.world.WorldInfo;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class WorldManager {

	private final @NotNull Regionerator plugin;
	private final @NotNull Map<String, WorldInfo> worlds;

	public WorldManager(@NotNull Regionerator plugin) {
		this.plugin = plugin;
		this.worlds = new HashMap<>();
	}

	public @NotNull WorldInfo getWorld(@NotNull World world) {
		return worlds.computeIfAbsent(world.getName(), (name) -> getWorldImpl(world));
	}

	private @NotNull WorldInfo getWorldImpl(@NotNull World world) {
		return new AnvilWorld(plugin, world);
	}

}
