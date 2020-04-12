package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Regionerator;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public abstract class WorldInfo {

	protected final Regionerator plugin;
	private final World world;
	protected final Map<String, RegionInfo> regions;

	public WorldInfo(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		regions = new ConcurrentHashMap<>();
	}

	public World getWorld() {
		return world;
	}

	/**
	 * Gets RegionInfo for the specified region coordinates. Note that the region may not exist.
	 *
	 * @param regionX the region's X coordinate
	 * @param regionZ the region's Z coordinate
	 * @return the RegionInfo
	 */
	@NotNull
	public abstract RegionInfo getRegion(int regionX, int regionZ) throws IOException;

	public abstract Stream<RegionInfo> getRegions();

}
