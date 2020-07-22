package com.github.jikoo.regionerator.util.yaml;

import com.github.jikoo.regionerator.DebugLevel;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class Config extends ConfigYamlData {

	private DebugLevel debugLevel;
	private List<String> worlds;
	private long flagDuration, ticksPerFlag, millisBetweenCycles, deletionInterval;
	private int flaggingRadius, deletionChunkCount;

	public Config(Plugin plugin) {
		super(plugin);
	}

	@Override
	public void reload() {
		super.reload();

		List<String> worldList = getStringList("worlds");

		try {
			String debugConfigVal = getString("debug-level");
			if (debugConfigVal == null) {
				debugConfigVal = "OFF";
			}
			debugLevel = DebugLevel.valueOf(debugConfigVal.toUpperCase());
		} catch (IllegalArgumentException e) {
			debugLevel = DebugLevel.OFF;
			set("debug-level", "OFF");
		}

		boolean correctedWorldNames = false;
		worlds = new ArrayList<>();
		for (World world : Bukkit.getWorlds()) {
			if (worldList.contains(world.getName())) {
				worlds.add(world.getName());
				continue;
			}
			for (String name : worldList) {
				if (world.getName().equalsIgnoreCase(name)) {
					worlds.add(world.getName());
					correctedWorldNames = true;
					break;
				}
			}
		}

		// Immutable list, this should not be changed during run by myself or another plugin
		worlds = ImmutableList.copyOf(worlds);
		if (correctedWorldNames) {
			set("worlds", worlds);
		}

		// 86,400,000 = 24 hours * 60 minutes * 60 seconds * 1000 milliseconds
		flagDuration = 86400000L * getInt("days-till-flag-expires");

		if (flagDuration <= 0) {
			// Flagging will not be editing values, flagging untouched chunks is not an option
			set("delete-new-unvisited-chunks", true);
		}

		if (getInt("chunk-flag-radius") < 0) {
			set("chunk-flag-radius", 4);
		}

		flaggingRadius = Math.max(0, getInt("chunk-flag-radius"));

		if (getInt("seconds-per-flag") < 1) {
			set("seconds-per-flag", 10);
		}
		// 20 ticks per second
		ticksPerFlag = getInt("seconds-per-flag") * 20L;

		if (getLong("ticks-per-deletion") < 1) {
			set("ticks-per-deletion", 20L);
		}

		deletionInterval = getLong("ticks-per-deletion") * 50;

		if (getInt("chunks-per-deletion") < 1) {
			set("chunks-per-deletion", 20);
		}

		deletionChunkCount = getInt("chunks-per-deletion");

		if (getInt("hours-between-cycles") < 0) {
			set("hours-between-cycles", 0);
		}
		// 60 minutes per hour, 60 seconds per minute, 1000 milliseconds per second
		millisBetweenCycles = getInt("hours-between-cycles") * 3600000L;

	}

	public DebugLevel getDebugLevel() {
		return debugLevel;
	}

	public int getDeletionChunkCount() {
		return deletionChunkCount;
	}

	public long getDeletionRecoveryMillis() {
		return deletionInterval;
	}

	public long getMillisBetweenCycles() {
		return millisBetweenCycles;
	}

	public boolean isRememberCycleDelay() {
		return getBoolean("remember-next-cycle-time");
	}

	public boolean isDeleteFreshChunks() {
		return getBoolean("delete-new-unvisited-chunks");
	}

	public long getFlagDuration() {
		return flagDuration;
	}

	public long getFlagGenerated() {
		return isDeleteFreshChunks() ? getFlagVisit() : Long.MAX_VALUE;
	}

	public long getFlagVisit() {
		return System.currentTimeMillis() + getFlagDuration();
	}

	public static long getFlagEternal() {
		return Long.MAX_VALUE - 1;
	}

	public static long getFlagDefault() {
		return -1;
	}

	public long getFlaggingInterval() {
		return ticksPerFlag;
	}

	public int getFlaggingRadius() {
		return flaggingRadius;
	}

	public List<String> getWorlds() {
		return worlds;
	}

}
