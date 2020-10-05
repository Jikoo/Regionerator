package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Regionerator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A container used to generate {@link RegionInfo} for a {@link World}.
 *
 * @author Jikoo
 */
public abstract class WorldInfo {

	private final Regionerator plugin;
	private final World world;
	protected final Map<String, RegionInfo> regions;

	public WorldInfo(Regionerator plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		regions = new ConcurrentHashMap<>();
	}

	/**
	 * Gets the {@link World} the WorldInfo represents.
	 *
	 * @return the Bukkit world
	 */
	public World getWorld() {
		return world;
	}

	/**
	 * Gets {@link RegionInfo} for the specified region coordinates. Note that the region may not exist - check {@link RegionInfo#exists()}.
	 *
	 * @param regionX the region's X coordinate
	 * @param regionZ the region's Z coordinate
	 * @return the {@link RegionInfo}
	 */
	@NotNull
	public abstract RegionInfo getRegion(int regionX, int regionZ);

	/**
	 * Gets a {@link Stream<RegionInfo>} requesting every {@link RegionInfo} contained by the WorldInfo.
	 *
	 * @return a {@link Stream<RegionInfo>}
	 */
	public abstract Stream<RegionInfo> getRegions();

	/**
	 * Gets the instance of Regionerator loading the WorldInfo.
	 *
	 * @return the Regionerator instance
	 */
	protected Regionerator getPlugin() {
		return plugin;
	}

}
