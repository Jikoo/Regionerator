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

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.util.DistributedTask;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Listener used to flag chunks as visited.
 */
public class FlaggingListener implements Listener {

	private final @NotNull Regionerator plugin;
	private final @NotNull FlaggingRunnable flagger;

	public FlaggingListener(@NotNull Regionerator plugin) {
		this.plugin = plugin;
		this.flagger = new FlaggingRunnable(plugin);

		for (Player player : plugin.getServer().getOnlinePlayers()) {
			flagger.add(player);
		}

		flagger.schedule(plugin);
	}

	public void cancel() {
		this.flagger.cancel(plugin);
	}

	/**
	 * Regionerator periodically spends a sizable amount of time deleting untouched area, causing unnecessary load.
	 * To combat this a little, freshly generated chunks are automatically flagged.
	 * 
	 * @param event the ChunkPopulateEvent
	 */
	@EventHandler
	private void onChunkPopulate(@NotNull ChunkPopulateEvent event) {
		World world = event.getWorld();

		if (!plugin.config().isEnabled(world.getName())) {
			return;
		}

		plugin.getServer().getScheduler().runTaskAsynchronously(
				plugin,
				() ->
						plugin.getFlagger().flagChunk(
								world.getName(),
								event.getChunk().getX(),
								event.getChunk().getZ(),
								plugin.config().getFlagGenerated(world)));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerJoin(@NotNull PlayerJoinEvent event) {
		flagger.add(event.getPlayer());
	}

	@EventHandler
	private void onPlayerQuit(@NotNull PlayerQuitEvent event) {
		flagger.remove(event.getPlayer());
	}

	/**
	 * DistributedTask for periodically marking chunks near players as visited.
	 */
	private static class FlaggingRunnable extends DistributedTask<Player> {

		FlaggingRunnable(@NotNull Regionerator plugin) {
			super(plugin.config().getFlaggingInterval() * 50, TimeUnit.MILLISECONDS, players -> {
				List<ChunkId> flagged = new ArrayList<>();
				for (Player player : players) {
					if (player.getGameMode().name().equals("SPECTATOR")
							|| !plugin.config().isEnabled(player.getWorld().getName())) {
						continue;
					}

					flagged.add(new ChunkId(player.getWorld(), player.getLocation()));
				}

				if (!flagged.isEmpty()) {
					plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
						for (ChunkId chunk : flagged) {
							plugin.getFlagger().flagChunksInRadius(chunk.worldName, chunk.chunkX, chunk.chunkZ);
						}
					});
				}

			});
		}

	}

	private static class ChunkId {
		private final @NotNull String worldName;
		private final int chunkX;
		private final int chunkZ;

		private ChunkId(@NotNull World world, @NotNull Location location) {
			this.worldName = world.getName();
			this.chunkX = Coords.blockToChunk(location.getBlockX());
			this.chunkZ = Coords.blockToChunk(location.getBlockZ());
		}
	}

}
