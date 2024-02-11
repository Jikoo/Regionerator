/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.commands;

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.util.yaml.Config;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class for handling logic related to flag management commands.
 */
public class FlagHandler {

	private final @NotNull Regionerator plugin;

	public FlagHandler(@NotNull Regionerator plugin) {
		this.plugin = plugin;
	}

	public void handleFlags(@NotNull CommandSender sender, String @NotNull [] args, boolean flag) {
		Set<ChunkPosition> chunks = getSelectedArea(sender, args);
		if (chunks == null) {
			// More descriptive errors are handled when selecting chunks
			return;
		}

		if (chunks.isEmpty()) {
			sender.sendMessage("No chunks selected for (un)flagging!");
			return;
		}

		String worldName = chunks.stream().findFirst().get().name();
		boolean invalid = true;
		for (String world : plugin.config().enabledWorlds()) {
			if (world.equalsIgnoreCase(worldName)) {
				invalid = false;
				// Re-assign so case will match when editing values
				worldName = world;
				break;
			}
		}
		if (invalid) {
			sender.sendMessage("No world \"" + worldName + "\" is enabled for regeneration.");
			return;
		}

		for (ChunkPosition chunk : chunks) {
			if (flag) {
				plugin.getFlagger().flagChunk(worldName, chunk.chunkX(), chunk.chunkZ(), Config.FLAG_ETERNAL);
			} else {
				plugin.getFlagger().unflagChunk(worldName, chunk.chunkX(), chunk.chunkZ());
			}
		}

		sender.sendMessage("Edited flags successfully!");
	}

	private @Nullable Set<ChunkPosition> getSelectedArea(CommandSender sender, String @NotNull [] args) {
		if (args.length < 3 && !(sender instanceof Player)) {
			sender.sendMessage("Console usage: /regionerator (un)flag <world> <chunk X> <chunk Z>");
			sender.sendMessage("Chunk coordinates = regular coordinates / 16");
			return null;
		}

		// Flag chunks by chunk coordinates
		if (args.length > 2) {
			return getExplicitChunk(sender, args);
		}

		// Safe cast: prior 2 blocks remove all non-players.
		Player player = (Player) sender;

		// Flag current chunk
		if (args.length < 2) {
			Location location = player.getLocation();
			return Set.of(new ChunkPosition(
					player.getWorld().getName(),
					Coords.blockToChunk(location.getBlockX()),
					Coords.blockToChunk(location.getBlockZ())));
		}

		// 2 args guaranteed, safe
		args[1] = args[1].toLowerCase();
		if (!args[1].equals("selection")) {
			sender.sendMessage("/regionerator (un)flag - (un)flag current chunk");
			sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
			sender.sendMessage("/regionerator (un)flag selection - unflag WorldEdit selection");
			return null;
		}

		return getWorldEditSelection(player);
	}

	private @Nullable Set<ChunkPosition> getExplicitChunk(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
		String worldName;
		if (args.length > 3) {
			worldName = args[1];
		} else if (sender instanceof Player player) {
			worldName = player.getWorld().getName();
		} else {
			sender.sendMessage("Unable to parse world.");
			sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
			return null;
		}

		int chunkX;
		int chunkZ;
		try {
			chunkX = Integer.parseInt(args[args.length - 2]);
			chunkZ = Integer.parseInt(args[args.length - 1]);
		} catch (NumberFormatException e) {
			sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
			return null;
		}
		return Set.of(new ChunkPosition(worldName, chunkX, chunkZ));
	}

	private @Nullable Set<ChunkPosition> getWorldEditSelection(@NotNull Player player) {
		try {
			Class.forName("com.sk89q.worldedit.WorldEdit");
		} catch (ClassNotFoundException e) {
			player.sendMessage("WorldEdit must be enabled to (un)flag selection!");
			return null;
		}

		LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(player));

		if (session == null || session.getSelectionWorld() == null) {
			player.sendMessage("You must select an area with WorldEdit to (un)flag!");
			return null;
		}

		Region selection = null;
		try {
			selection = session.getSelection(session.getSelectionWorld());
		} catch (Exception ignored) {
			// If there was an exception getting their selection, they probably don't have one.
		}

		if (selection == null) {
			player.sendMessage("You must select an area with WorldEdit to (un)flag!");
			return null;
		}

		String worldName = session.getSelectionWorld().getName();
		return selection.getChunks().stream()
				.map(vector -> new ChunkPosition(worldName, vector.getX(), vector.getZ()))
				.collect(Collectors.toUnmodifiableSet());
	}

	private record ChunkPosition(String name, int chunkX, int chunkZ) {}

}
