/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.util;

import com.github.jikoo.regionerator.ChunkFlagger;
import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.VisitStatus;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.yaml.Config;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class VisitStatusCache extends SupplierCache<VisitStatus> {

	public VisitStatusCache(@NotNull Regionerator plugin, @NotNull ChunkInfo chunkInfo) {
		super(() -> {
			// If chunk is already orphaned on disk, don't check anything.
			if (chunkInfo.isOrphaned()) {
				return VisitStatus.ORPHANED;
			}

			long now = System.currentTimeMillis();
			final World bukkitWorld = chunkInfo.getWorld();
			ChunkFlagger.FlagData flagData = plugin.getFlagger()
					.getChunkFlag(bukkitWorld, chunkInfo.getChunkX(), chunkInfo.getChunkZ()).join();
			long lastVisit = flagData.getLastVisit();
			boolean isFresh = !plugin.config().isDeleteFreshChunks(bukkitWorld) && lastVisit == plugin.config().getFlagGenerated(bukkitWorld);

			// If chunk is visited, don't waste time processing hooks.
			if (!isFresh && now <= lastVisit) {
				plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s is visited until %s", flagData.getChunkId(), lastVisit));

				// Handle visit status magic values.
				if (lastVisit == Config.FLAG_ETERNAL) {
					return VisitStatus.PERMANENTLY_FLAGGED;
				} else if (lastVisit == Config.FLAG_OH_NO) {
					return VisitStatus.UNKNOWN;
				}

				return VisitStatus.VISITED;
			}

			// If chunk is recently modified, prioritize that over protections for the sake of speed/calculation load.
			if (!isFresh && now - plugin.config().getFlagDuration(bukkitWorld) <= chunkInfo.getLastModified()) {
				plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s is modified until %s", flagData.getChunkId(), lastVisit));
				return VisitStatus.VISITED;
			}

			Collection<Hook> syncHooks = Bukkit.isPrimaryThread() ? null : new ArrayList<>();
			WorldInfo world = chunkInfo.getRegionInfo().getWorldInfo();
			int chunkX = chunkInfo.getChunkX();
			int chunkZ = chunkInfo.getChunkZ();

			// Check available hooks.
			for (Hook hook : plugin.getProtectionHooks()) {
				// If hook must be queried on the main thread, add to the sync hook list.
				if (syncHooks != null && !hook.isAsyncCapable()) {
					syncHooks.add(hook);
					continue;
				}

				// Otherwise query the hook immediately.
				if (hook.isChunkProtected(world.getWorld(), chunkX, chunkZ)) {
					plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s contains protections by %s",
							flagData.getChunkId(), hook.getProtectionName()));
					return VisitStatus.PROTECTED;
				}
			}

			// If non-async-capable hooks are enabled, attempt to return to the main thread to query.
			if (syncHooks != null && !syncHooks.isEmpty()) {

				// Fall through to unknown status if we cannot query hooks.
				if (!plugin.isEnabled()) {
					return VisitStatus.UNKNOWN;
				}

				try {
					// Query remaining hooks on main thread.
					VisitStatus visitStatus = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
						for (Hook hook : syncHooks) {
							if (hook.isChunkProtected(world.getWorld(), chunkX, chunkZ)) {
								plugin.debug(DebugLevel.HIGH, () -> String.format("Chunk %s contains protections by %s",
										flagData.getChunkId(), hook.getProtectionName()));
								return VisitStatus.PROTECTED;
							}
						}
						return VisitStatus.UNKNOWN;
					}).get();
					if (visitStatus == VisitStatus.PROTECTED) {
						return VisitStatus.PROTECTED;
					}
				} catch (InterruptedException | ExecutionException e) {
					// Usually this only occurs on shutdown. Execution should stop instead of continuing with unknown status.
					throw new RuntimeException(e);
				}
			}

			// If chunk is fresh and nothing else overwrote status, fall through to generated status.
			if (isFresh) {
				plugin.debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " has not been visited since it was generated.");
				return VisitStatus.GENERATED;
			}

			return VisitStatus.UNVISITED;
		}, calcCacheDuration(plugin), TimeUnit.MINUTES);
	}

	/**
	 * Calculates the duration to cache VisitStatus values to prevent excess load.
	 *
	 * @return the value calculated
	 */
	private static int calcCacheDuration(@NotNull Regionerator plugin) {
		Config config = plugin.config();
		return (int) Math.ceil(1024D / config.getDeletionChunkCount() * config.getDeletionRecoveryMillis() / 60000);
	}

}
