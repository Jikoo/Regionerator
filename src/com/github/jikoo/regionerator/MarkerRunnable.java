package com.github.jikoo.regionerator;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runnable for flagging chunks as visited.
 * 
 * @author Jikoo
 */
public class MarkerRunnable extends BukkitRunnable {

	@Override
	public void run() {
		Regionerator plugin = Regionerator.getInstance();
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getGameMode() == GameMode.SPECTATOR) {
				// Skip spectators - if you can't touch it, you can't really visit it.
				continue;
			}
			if (plugin.getActiveWorlds().contains(player.getWorld().getName())) {
				Chunk chunk = player.getLocation().getChunk();
				plugin.getFlagger().flagChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
			}
		}
		if (plugin.activateRegen()) {
			// TODO
		}
	}
}
