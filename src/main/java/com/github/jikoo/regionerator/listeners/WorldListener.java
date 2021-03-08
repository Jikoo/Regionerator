/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.Regionerator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldListener implements Listener {

	private final Regionerator plugin;

	public WorldListener(Regionerator plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		String name = event.getWorld().getName();
		for (String worldName : plugin.config().enabledWorlds()) {
			if (worldName.equals(name)) {
				// Name is correctly capitalized, do nothing.
				return;
			}
			if (!worldName.equalsIgnoreCase(name)) {
				// Names do not match, skip.
				continue;
			}

			// World name with incorrect casing loaded, reload config to correct values.
			plugin.reloadConfig();
			return;
		}
	}

}
