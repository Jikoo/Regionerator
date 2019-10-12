package com.github.jikoo.regionerator.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.jikoo.regionerator.CoordinateConversions;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.util.Triple;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Class for handling logic related to flag management commands.
 * 
 * @author Jikoo
 */
public class CommandFlag {

	private final Regionerator plugin;

	public CommandFlag(Regionerator plugin) {
		this.plugin = plugin;
	}

	public void handleFlags(CommandSender sender, String[] args, boolean flag) {
		List<Triple<String, Integer, Integer>> chunks = getSelectedArea(sender, args);
		if (chunks == null) {
			// More descriptive errors are handled when selecting chunks
			return;
		}

		if (chunks.isEmpty()) {
			sender.sendMessage("No chunks selected for (un)flagging!");
			return;
		}

		String worldName = chunks.get(0).getLeft();
		boolean invalid = true;
		for (String world : plugin.getActiveWorlds()) {
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

		for (Triple<String, Integer, Integer> chunk : chunks) {
			if (flag) {
				plugin.getFlagger().flagChunk(worldName, chunk.getMiddle(), chunk.getRight(), 0, plugin.getEternalFlag());
			} else {
				plugin.getFlagger().unflagChunk(worldName, chunk.getMiddle(), chunk.getRight());
			}
		}

		sender.sendMessage("Edited flags successfully!");
	}

	private List<Triple<String, Integer, Integer>> getSelectedArea(CommandSender sender, String[] args) {
		if (args.length < 3 && !(sender instanceof Player)) {
			sender.sendMessage("Console usage: /regionerator (un)flag <world> <chunk X> <chunk Z>");
			sender.sendMessage("Chunk coordinates = regular coordinates / 16");
			return null;
		}

		// Flag chunks by chunk coordinates
		if (args.length > 2) {

			String worldName;
			if (args.length > 3) {
				worldName = args[1];
			} else if (sender instanceof Player) {
				worldName = ((Player) sender).getWorld().getName();
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
			// This looks silly, but it's necessary to make the compiler happy
			return Collections.singletonList(new Triple<>(worldName, chunkX, chunkZ));
		}

		// Safe cast: prior 2 blocks remove all non-players.
		Player player = (Player) sender;

		// Flag current chunk
		if (args.length < 2) {
			Location location = player.getLocation();
			return Collections.singletonList(new Triple<>(
					player.getWorld().getName(),
					CoordinateConversions.blockToChunk(location.getBlockX()),
					CoordinateConversions.blockToChunk(location.getBlockZ())));
		}

		// 2 args guaranteed, safe
		args[1] = args[1].toLowerCase();
		if (!args[1].equals("selection")) {
			sender.sendMessage("/regionerator (un)flag - (un)flag current chunk");
			sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
			sender.sendMessage("/regionerator (un)flag selection - unflag WorldEdit selection");
			return null;
		}

		try {
			Class.forName("com.sk89q.worldedit.bukkit.WorldEditPlugin");
		} catch (ClassNotFoundException e) {
			sender.sendMessage("WorldEdit must be enabled to (un)flag selection!");
			return null;
		}

		WorldEditPlugin worldedit = getWE();

		if (worldedit == null) {
			sender.sendMessage("WorldEdit must be enabled to (un)flag selection!");
			return null;
		}

		LocalSession session = worldedit.getSession(player);

		if (session == null || session.getSelectionWorld() == null) {
			sender.sendMessage("You must select an area with WorldEdit to (un)flag!");
			return null;
		}

		Region selection = null;
		try {
			selection = session.getSelection(session.getSelectionWorld());
		} catch (Exception ignored) {
			// Ignored - we return anyway.
		}

		if (selection == null) {
			sender.sendMessage("You must select an area with WorldEdit to (un)flag!");
			return null;
		}

		ArrayList<Triple<String, Integer, Integer>> chunks = new ArrayList<>();
		String worldName = session.getSelectionWorld().getName();
		int maxChunkX = CoordinateConversions.blockToChunk(selection.getMaximumPoint().getBlockX());
		int maxChunkZ = CoordinateConversions.blockToChunk(selection.getMaximumPoint().getBlockZ());

		for (int minChunkX = CoordinateConversions.blockToChunk(selection.getMinimumPoint().getBlockX());
		     minChunkX <= maxChunkX; minChunkX++) {
			for (int minChunkZ = CoordinateConversions.blockToChunk(selection.getMinimumPoint().getBlockZ());
			     minChunkZ <= maxChunkZ; minChunkZ++) {
				chunks.add(new Triple<>(worldName, minChunkX, minChunkZ));
			}
		}

		return chunks;
	}

	private WorldEditPlugin getWE() {
		if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
			return null;
		}

		try {
			return (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
		} catch (ClassCastException e) {
			// Not present, another plugin claims to be WE
			return null;
		}
	}
}
