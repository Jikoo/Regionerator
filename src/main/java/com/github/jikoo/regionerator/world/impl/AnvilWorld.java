/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world.impl;

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AnvilWorld extends WorldInfo {

	// r.0.0.mca, r.-1.0.mca, etc.
	public static final Pattern ANVIL_REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	public AnvilWorld(@NotNull Regionerator plugin, @NotNull World world) {
		super(plugin, world);
	}

	@Override
	public @NotNull RegionInfo getRegion(int regionX, int regionZ) {
		File regionFolder = findRegionFolder();
		File regionFile = new File(regionFolder, "r." + regionX + "." + regionZ + ".mca");
		return new AnvilRegion(this, regionFile, Coords.regionToChunk(regionX), Coords.regionToChunk(regionZ));
	}

	@Override
	public @NotNull Stream<RegionInfo> getRegions() {
		File regionFolder = findRegionFolder();
		File[] regionFiles = regionFolder.listFiles((dir, name) -> ANVIL_REGION.matcher(name).matches());
		if (regionFiles == null) {
			return Stream.empty();
		}
		AtomicInteger index = new AtomicInteger();
		return Stream.generate(() -> parseRegion(regionFiles[index.getAndIncrement()])).limit(regionFiles.length).filter(Objects::nonNull);
	}

	private @NotNull File findRegionFolder() {
		World world = getWorld();
		World defaultWorld = Bukkit.getWorlds().get(0);

		if (world.equals(defaultWorld)) {
			// World is the default world.
			return getDimFolder(world.getEnvironment(), world.getWorldFolder());
		}

		String defaultWorldFolder = defaultWorld.getWorldFolder().getAbsolutePath();
		File worldFolder = world.getWorldFolder();
		if (defaultWorldFolder.equals(worldFolder.getAbsolutePath())) {
			// This is not a Craftbukkit-based Bukkit implementation.
			// The world is not the default world but the world folder is the default world's folder.
			// Determining which folder is actually the folder for this world's data would require us to parse
			// level.dat and determine which world matches, but this is really more of a platform bug.
			throw new IllegalStateException("Cannot determine world data directory! Platform has provided base world directory instead of dimension directory.");
		}

		return getDimFolder(world.getEnvironment(), worldFolder);
	}

	private static @NotNull File getDimFolder(@NotNull World.Environment environment, @NotNull File worldFolder) {
		File bukkitDimFolder = switch (environment) {
			case NETHER -> new File(worldFolder, "DIM-1");
			case THE_END -> new File(worldFolder, "DIM1");
			default -> worldFolder;
		};

		if (bukkitDimFolder.isDirectory()) {
			// Default Bukkit behavior.
			return new File(bukkitDimFolder, "region");
		}

		// Unknown but presumably good platform. We should already be inside the correct dimension folder.
		return new File(worldFolder, "region");
	}

	private @Nullable RegionInfo parseRegion(@NotNull File regionFile) {
		Matcher matcher = ANVIL_REGION.matcher(regionFile.getName());
		if (!matcher.matches() || !getPlugin().isEnabled()) {
			return null;
		}
		return new AnvilRegion(this, regionFile, Coords.regionToChunk(Integer.parseInt(matcher.group(1))),
				Coords.regionToChunk(Integer.parseInt(matcher.group(2))));
	}

}
