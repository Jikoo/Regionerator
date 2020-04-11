package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Regionerator;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

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
	 * Gets RegionInfo if generated.
	 *
	 * @param regionX the region's X coordinate
	 * @param regionZ the region's Z coordinate
	 * @return the RegionInfo, or <pre>null</pre> if the region does not exist
	 */
	@Nullable
	public abstract RegionInfo getRegion(int regionX, int regionZ) throws IOException;

	public abstract Stream<RegionInfo> getRegions();

}
