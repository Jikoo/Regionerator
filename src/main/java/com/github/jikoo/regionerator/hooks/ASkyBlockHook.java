package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import com.wasteofplastic.askyblock.ASkyBlockAPI;

import com.github.jikoo.regionerator.CoordinateConversions;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/a-skyblock.1220/>ASkyBlock</a>.
 *
 * @author Jikoo
 */
public class ASkyBlockHook extends PluginHook implements Listener {

	public ASkyBlockHook() {
		super("ASkyBlock");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		Location chunkLoc = new Location(chunkWorld, CoordinateConversions.chunkToBlock(chunkX), 0,
				CoordinateConversions.chunkToBlock(chunkZ));
		return ASkyBlockAPI.getInstance().getIslandAt(chunkLoc) != null;
	}

	public boolean isReadyOnEnable() {
		return false;
	}

	@Override
	public void readyLater(Regionerator plugin) {

		plugin.getServer().getPluginManager().registerEvents(new ASkyBlockReadyListener(plugin, this), plugin);
	}

	public class ASkyBlockReadyListener implements Listener {
		private final Regionerator plugin;
		private final ASkyBlockHook hook;

		ASkyBlockReadyListener(Regionerator plugin, ASkyBlockHook hook) {
			this.plugin = plugin;
			this.hook = hook;
		}

		@EventHandler
		public void onASkyBlockReady(com.wasteofplastic.askyblock.events.ReadyEvent event) {

			if (!plugin.getConfig().getBoolean("hooks.ASkyBlock")) {
				return;
			}

			plugin.debug(DebugLevel.MEDIUM, () -> "ASkyBlock reports itself ready");

			if (!hook.isHookUsable()) {
				plugin.getLogger().severe("Hook for ASkyBlock failed usability check and could not be enabled!");
				return;
			}

			plugin.addHook(hook);

			plugin.debug(DebugLevel.LOW, () -> "Enabled protection hook for ASkyBlock");
		}
	}

}
