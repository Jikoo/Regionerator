/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.util.yaml;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

final class ConfigUpdater {

	private static final int CURRENT_CONFIG_VERSION = 1;

	static void doUpdates(Config config) {
		switch (config.raw().getInt("config-version", 0)) {
			case 0:
				updateConfig0To1(config);
			case CURRENT_CONFIG_VERSION:
				return;
			default:
				config.plugin.getLogger().warning("You appear to have messed with your configuration version.");
				config.plugin.getLogger().warning("Please ensure that your configuration contains the correct nodes.");
				config.set("config-version", CURRENT_CONFIG_VERSION);
				break;
		}
	}

	static void updateConfig0To1(Config config) {
		// Flagging section
		config.set("flagging.seconds-per-flag", config.getInt("seconds-per-flag"));
		config.set("seconds-per-flag", null);
		config.set("flagging.chunk-flag-radius", config.getInt("chunk-flag-radius"));
		config.set("chunk-flag-radius", null);
		config.set("flagging.flag-generated-chunks-until-visited", !config.getBoolean("delete-new-unvisited-chunks"));
		config.set("delete-new-unvisited-chunks", null);

		// Cache section
		config.set("cache.minimum-expiration-frequency", 10);
		config.set("cache.retention", 10);
		config.set("cache.maximum-batch-size", 1_024);
		config.set("cache.batch-delay", 500);
		config.set("cache.max-cache-size", 640_000);

		// Deletion section
		config.set("deletion.recovery-time", config.getInt("ticks-per-deletion") * 50);
		config.set("ticks-per-deletion", null);
		config.set("deletion.expensive-checks-between-recovery", config.getInt("chunks-per-deletion"));
		config.set("chunks-per-deletion", null);
		config.set("deletion.hours-between-cycles", config.getInt("hours-between-cycles"));
		config.set("hours-between-cycles", null);
		config.set("deletion.remember-next-cycle-time", config.getInt("remember-next-cycle-time"));
		config.set("remember-next-cycle-time", null);

		// World settings
		int oldDaysTillFlagExpires = Math.max(0, config.getInt("days-till-flag-expires"));
		config.set("days-till-flag-expires", null);

		List<String> worldConfigList = config.getStringList("worlds");
		ConfigurationSection worldSection = config.raw().createSection("worlds");
		worldSection.set("default.days-till-flag-expires", -1);
		for (String world : worldConfigList) {
			final ConfigurationSection section = worldSection.createSection(world);
			section.set("days-till-flag-expires", oldDaysTillFlagExpires);
		}

		config.set("config-version", 1);
	}

	private ConfigUpdater() {}

}
