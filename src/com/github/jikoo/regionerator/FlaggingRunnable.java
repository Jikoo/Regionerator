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
public class FlaggingRunnable extends BukkitRunnable {

	private final Regionerator plugin;

	public FlaggingRunnable(Regionerator plugin) {
		this.plugin = plugin;
	}

	@Override
	public void run() {
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
		plugin.attemptDeletionActivation();
	}
}
