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
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnvilWorld extends WorldInfo {

	// r.0.0.mca, r.-1.0.mca, etc.
	public static final Pattern ANVIL_REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	public AnvilWorld(Regionerator plugin, World world) {
		super(plugin, world);
	}

	@NotNull
	@Override
	public RegionInfo getRegion(int regionX, int regionZ) {
		File regionFolder = findRegionFolder(getWorld());
		File regionFile = new File(regionFolder, "r." + regionX + "." + regionZ + ".mca");
		return new AnvilRegion(this, regionFile, Coords.regionToChunk(regionX), Coords.regionToChunk(regionZ));
	}

	@Nullable
	@Override
	public Stream<RegionInfo> getRegions() {
		AtomicInteger index = new AtomicInteger();
		File regionFolder = findRegionFolder(getWorld());
		File[] regionFiles = regionFolder.listFiles((dir, name) -> ANVIL_REGION.matcher(name).matches());
		if (regionFiles == null) {
			return null;
		}
		return Stream.generate(() -> parseRegion(regionFiles[index.getAndIncrement()])).limit(regionFiles.length).filter(Objects::nonNull);
	}

	private File findRegionFolder(@NotNull World world) {
		switch (world.getEnvironment()) {
			case NETHER:
				return new File(world.getWorldFolder(), "DIM-1" + File.separatorChar + "region");
			case THE_END:
				return new File(world.getWorldFolder(), "DIM1" + File.separatorChar + "region");
			case NORMAL:
			default:
				return new File(world.getWorldFolder(), "region");
		}
	}

	private @Nullable RegionInfo parseRegion(@NotNull File regionFile) {
		Matcher matcher = ANVIL_REGION.matcher(regionFile.getName());
		if (!matcher.matches()) {
			return null;
		}
		return new AnvilRegion(this, regionFile, Coords.regionToChunk(Integer.parseInt(matcher.group(1))),
				Coords.regionToChunk(Integer.parseInt(matcher.group(2))));
	}

}
