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

import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AnvilWorld extends WorldInfo {

	// r.0.0.mca, r.-1.0.mca, etc.
	public static final Pattern ANVIL_REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)(\\.mc[ar])");

	public AnvilWorld(@NotNull Regionerator plugin, @NotNull World world) {
		super(plugin, world);
	}

	@Override
	public @NotNull RegionInfo getRegion(int regionX, int regionZ) {
		Path dataFolder = findWorldDataFolder();
		return new AnvilRegion(this, dataFolder, regionX, regionZ, "r.%s.%s.mca");
	}

	@Override
	public @NotNull Stream<RegionInfo> getRegions() {
		Path dataFolder = findWorldDataFolder();

		Stream<File> fileStream = null;
		for (String folder : AnvilRegion.DATA_SUBDIRS) {
			File file = dataFolder.resolve(folder).toFile();
			File[] files = file.listFiles();
			if (files == null) {
				continue;
			}
			Stream<File> localFileStream = Arrays.stream(files);
			fileStream = fileStream == null ? localFileStream : Stream.concat(fileStream, localFileStream);
		}

		if (fileStream == null) {
			return Stream.empty();
		}

		return fileStream
						.map(File::getName)
						.distinct()
						.map(fileName -> parseRegion(dataFolder, fileName))
						.filter(Objects::nonNull);
	}

	private @NotNull Path findWorldDataFolder() {
		World world = getWorld();
		Path worldFolder = world.getWorldFolder().toPath();
		return switch (world.getEnvironment()) {
			case NETHER -> worldFolder.resolve("DIM-1");
			case THE_END -> worldFolder.resolve("DIM1");
			default -> worldFolder;
		};
	}

	private @Nullable RegionInfo parseRegion(Path dataFolder, String fileName) {
		Matcher matcher = ANVIL_REGION.matcher(fileName);
		if (!matcher.matches() || !getPlugin().isEnabled()) {
			return null;
		}
		return new AnvilRegion(this, dataFolder, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), "r.%s.%s" + matcher.group(3));
	}

}
