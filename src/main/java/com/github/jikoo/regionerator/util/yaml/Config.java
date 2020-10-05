package com.github.jikoo.regionerator.util.yaml;

import com.github.jikoo.regionerator.DebugLevel;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class Config extends ConfigYamlData {

	/** Constant representing the default flag timestamp. */
	public static final long FLAG_DEFAULT = -1;
	/** Constant representing a flag that will never expire. */
	public static final long FLAG_ETERNAL = Long.MAX_VALUE - 1;
	/** Constant representing a failure to load data. */
	public static final long FLAG_OH_NO = Long.MAX_VALUE - 2;

	private final Object lock = new Object();
	private DebugLevel debugLevel;
	private List<String> worlds;
	private final AtomicLong flagDuration = new AtomicLong(), ticksPerFlag = new AtomicLong(),
			millisBetweenCycles = new AtomicLong(), deletionRecovery = new AtomicLong();
	private final AtomicInteger flaggingRadius = new AtomicInteger(), deletionChunkCount = new AtomicInteger();
	private final AtomicBoolean rememberCycleDelay = new AtomicBoolean(), deleteFreshChunks = new AtomicBoolean();

	public Config(Plugin plugin) {
		super(plugin);
		reload();
	}

	@Override
	public void reload() {
		super.reload();

		List<String> worldConfigList = getStringList("worlds");
		List<String> worldCorrectCaseList = new ArrayList<>();
		for (World world : Bukkit.getWorlds()) {
			if (worldConfigList.contains(world.getName())) {
				worldCorrectCaseList.add(world.getName());
				continue;
			}
			for (String name : worldConfigList) {
				if (world.getName().equalsIgnoreCase(name)) {
					worldCorrectCaseList.add(world.getName());
					break;
				}
			}
		}

		synchronized (lock) {
			// Immutable list, this should not be changed during run by myself or another plugin
			this.worlds = ImmutableList.copyOf(worldCorrectCaseList);

			try {
				String debugConfigVal = getString("debug-level");
				if (debugConfigVal == null) {
					debugConfigVal = "OFF";
				}
				debugLevel = DebugLevel.valueOf(debugConfigVal.toUpperCase());
			} catch (IllegalArgumentException e) {
				debugLevel = DebugLevel.OFF;
			}
		}

		long newMilliValue = TimeUnit.DAYS.toMillis(getInt("days-till-flag-expires"));
		flagDuration.set(Math.max(0, newMilliValue));

		// If flagging will not be editing values, flagging untouched chunks is not an option
		deleteFreshChunks.set(newMilliValue <= 0 || getBoolean("delete-new-unvisited-chunks"));

		flaggingRadius.set(Math.max(0, getInt("chunk-flag-radius")));

		int secondsPerFlag = getInt("seconds-per-flag");
		if (secondsPerFlag < 1) {
			ticksPerFlag.set(10);
		} else {
			ticksPerFlag.set(20 * secondsPerFlag);
		}

		long ticksPerDeletion = getLong("ticks-per-deletion");
		if (ticksPerDeletion < 1) {
			deletionRecovery.set(0);
		} else {
			deletionRecovery.set(ticksPerDeletion * 50);
		}

		int chunksPerDelete = getInt("chunks-per-deletion");
		if (chunksPerDelete < 1) {
			deletionChunkCount.set(32);
		} else {
			deletionChunkCount.set(chunksPerDelete);
		}

		millisBetweenCycles.set(TimeUnit.HOURS.toMillis(Math.max(0, getInt("hours-between-cycles"))));

		rememberCycleDelay.set(getBoolean("remember-next-cycle-time"));

	}

	public DebugLevel getDebugLevel() {
		synchronized (lock) {
			return debugLevel;
		}
	}

	public int getDeletionChunkCount() {
		return deletionChunkCount.get();
	}

	public long getDeletionRecoveryMillis() {
		return deletionRecovery.get();
	}

	public long getMillisBetweenCycles() {
		return millisBetweenCycles.get();
	}

	public boolean isRememberCycleDelay() {
		return rememberCycleDelay.get();
	}

	public boolean isDeleteFreshChunks() {
		return deleteFreshChunks.get();
	}

	public long getFlagDuration() {
		return flagDuration.get();
	}

	public long getFlagGenerated() {
		return isDeleteFreshChunks() ? getFlagVisit() : Long.MAX_VALUE;
	}

	public long getFlagVisit() {
		return System.currentTimeMillis() + getFlagDuration();
	}

	@Deprecated
	public static long getFlagEternal() {
		return FLAG_ETERNAL;
	}

	@Deprecated
	public static long getFlagDefault() {
		return FLAG_DEFAULT;
	}

	public long getFlaggingInterval() {
		return ticksPerFlag.get();
	}

	public int getFlaggingRadius() {
		return flaggingRadius.get();
	}

	public List<String> getWorlds() {
		synchronized (lock) {
			return worlds;
		}
	}

}
