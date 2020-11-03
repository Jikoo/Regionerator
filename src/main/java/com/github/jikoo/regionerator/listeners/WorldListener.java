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
