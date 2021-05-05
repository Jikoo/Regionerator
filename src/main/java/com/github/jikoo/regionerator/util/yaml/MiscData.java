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

import com.github.jikoo.regionerator.Regionerator;
import java.io.File;
import java.util.Collection;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public class MiscData extends FileYamlData {

	private final @NotNull Regionerator plugin;

	public MiscData(@NotNull Regionerator plugin, @NotNull File file) {
		super(plugin, file);
		this.plugin = plugin;
	}

	@Override
	public void reload() {
		super.reload();
		checkWorldValidity();
	}

	public void checkWorldValidity() {
		if (plugin.config() == null) {
			return;
		}
		ConfigurationSection worlds = raw().getConfigurationSection("next-cycle");
		if (worlds == null) {
			return;
		}
		Collection<String> enabledWorlds = plugin.config().enabledWorlds();
		for (String worldName : worlds.getKeys(false)) {
			if (!enabledWorlds.contains(worldName) && enabledWorlds.stream().noneMatch(worldName::equalsIgnoreCase)) {
				set("next-cycle." + worldName, null);
			}
		}
	}

	public void setNextCycle(@NotNull String worldName, long timestamp) {
		set("next-cycle." + worldName, timestamp);
	}

	public long getNextCycle(@NotNull String worldName) {
		long nextCycle = getLong("next-cycle." + worldName);
		if (nextCycle == 0) {
			nextCycle = plugin.config().getFlagVisit(worldName);
			setNextCycle(worldName, nextCycle);
		}
		return nextCycle;
	}

}
