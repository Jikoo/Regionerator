package com.github.jikoo.regionerator.commands;

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.DeletionRunnable;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.yaml.Config;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RegioneratorExecutor implements TabExecutor {

	private final Regionerator plugin;
	private final Map<String, DeletionRunnable> deletionRunnables;
	private final FlagHandler flagHandler;

	public RegioneratorExecutor(Regionerator plugin,
			Map<String, DeletionRunnable> deletionRunnables) {
		this.plugin = plugin;
		this.deletionRunnables = deletionRunnables;
		flagHandler = new FlagHandler(plugin);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
			@NotNull String label, String[] args) {
		plugin.attemptDeletionActivation();

		if (args.length < 1) {
			if (plugin.config().enabledWorlds().isEmpty()) {
				sender.sendMessage("No worlds are configured. Edit your config and use /regionerator reload.");
				return true;
			}

			SimpleDateFormat format = new SimpleDateFormat("HH:mm 'on' d MMM");
			long millisBetweenCycles = plugin.config().getCycleDelayMillis();

			for (String worldName : plugin.config().enabledWorlds()) {
				long activeAt = plugin.getMiscData().getNextCycle(worldName);
				if (activeAt > System.currentTimeMillis()) {
					// Not time yet.
					String message;
					if (plugin.config().isRememberCycleDelay() && activeAt >= System.currentTimeMillis() - millisBetweenCycles) {
						message = worldName + ": Next run at " + format.format(new Date(activeAt));
					} else {
						message = worldName + ": Gathering data, deletion starts " + format.format(new Date(activeAt));
					}
					sender.sendMessage(message);
					continue;
				}

				if (deletionRunnables.containsKey(worldName)) {
					DeletionRunnable runnable = deletionRunnables.get(worldName);
					sender.sendMessage(runnable.getRunStats());
					if (runnable.getNextRun() < Long.MAX_VALUE) {
						sender.sendMessage(" - Next run: " + format.format(runnable.getNextRun()));
					}
				} else {
					sender.sendMessage("Cycle for " + worldName + " is ready to start.");
				}
			}

			if (plugin.isPaused()) {
				sender.sendMessage("Regionerator is paused. Use \"/regionerator resume\" to continue.");
			}
			return true;
		}

		args[0] = args[0].toLowerCase();
		if (args[0].equals("reload")) {
			plugin.reloadConfig();
			plugin.reloadFeatures();
			sender.sendMessage("Regionerator configuration reloaded!");
			return true;
		}

		if (args[0].equals("pause") || args[0].equals("stop") ) {
			plugin.setPaused(true);
			sender.sendMessage("Paused Regionerator. Use /regionerator resume to resume.");
			return true;
		}
		if (args[0].equals("resume") || args[0].equals("unpause") || args[0].equals("start")) {
			plugin.setPaused(false);
			sender.sendMessage("Resumed Regionerator. Use /regionerator pause to pause.");
			return true;
		}

		if (args[0].equals("flag")) {
			flagHandler.handleFlags(sender, args, true);
			return true;
		}
		if (args[0].equals("unflag")) {
			flagHandler.handleFlags(sender, args, false);
			return true;
		}

		if (args[0].equals("cache")) {
			sender.sendMessage("Cached chunk values: " + plugin.getFlagger().getCached());
			sender.sendMessage("Queued saves: " + plugin.getFlagger().getQueued());
			return true;
		}

		if (sender instanceof Player && args[0].equals("check")) {
			Player player = (Player) sender;

			if (!plugin.config().isEnabled(player.getWorld().getName())) {
				player.sendMessage("World is not configured for deletion.");
			}

			Chunk chunk = player.getLocation().getChunk();
			World world = chunk.getWorld();

			for (Hook hook : plugin.getProtectionHooks()) {
				player.sendMessage("Chunk is " + (hook.isChunkProtected(world, chunk.getX(), chunk.getZ()) ? "" : "not ") + "protected by " + hook.getProtectionName());
			}

			SimpleDateFormat format = new SimpleDateFormat("HH:mm 'on' d MMM yyyy");
			RegionInfo regionInfo = plugin.getWorldManager().getWorld(player.getWorld()).getRegion(Coords.chunkToRegion(chunk.getX()), Coords.chunkToRegion(chunk.getZ()));
			try {
				regionInfo.read();
			} catch (IOException e) {
				player.sendMessage("Caught IOException reading region data. Please check console!");
				plugin.getLogger().log(Level.WARNING, "Unable to read region!", e);
			}

			// Region not yet saved, cannot obtain chunk detail data
			if (!regionInfo.exists()) {
				long visit = plugin.getFlagger().getChunkFlag(world, chunk.getX(), chunk.getZ()).join().getLastVisit();
				if (visit == Config.FLAG_DEFAULT) {
					player.sendMessage("Chunk has not been visited.");
				} else if (!plugin.config().isDeleteFreshChunks(world) && visit == plugin.config().getFlagGenerated(world)) {
					player.sendMessage("Chunk has not been visited since generation.");
				} else if (visit == Config.FLAG_ETERNAL) {
					player.sendMessage("Chunk is eternally flagged.");
				} else {
					player.sendMessage("Chunk visited until: " + format.format(new Date(visit)));
				}
				visit = plugin.getFlagger().getChunkFlagOnDelete(world, chunk.getX(), chunk.getZ()).join().getLastVisit();
				if (visit != Config.FLAG_DEFAULT) {
					player.sendMessage("Visited (last delete): " + format.format(new Date(visit)));
				}
				player.sendMessage("Region not available from disk! Cannot check details.");
				return true;
			}

			ChunkInfo chunkInfo = regionInfo.getChunk(chunk.getX(), chunk.getZ());
			player.sendMessage("Chunk visited until: " + format.format(new Date(chunkInfo.getLastVisit())));
			player.sendMessage("Chunk last modified: " + format.format(new Date(chunkInfo.getLastModified())));
			player.sendMessage("Chunk VisitStatus: " + chunkInfo.getVisitStatus().name());
			long visit = plugin.getFlagger().getChunkFlagOnDelete(world, chunk.getX(), chunk.getZ()).join().getLastVisit();
			if (visit != Config.FLAG_DEFAULT) {
				player.sendMessage("Visited (last delete): " + format.format(new Date(visit)));
			}
			if (chunkInfo.isOrphaned()) {
				player.sendMessage("Chunk is marked as orphaned. VisitStatus should be GENERATED or UNKNOWN.");
			}
			return true;
		}

		return false;
	}

	@Nullable
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
			@NotNull String label, @NotNull String[] args) {
		if (!sender.hasPermission("regionerator.command") || args.length < 1) {
			return Collections.emptyList();
		}

		if (args.length == 1) {
			String[] completions = sender instanceof Player
					? new String[]{"pause", "resume", "reload", "flag", "unflag", "cache", "check"}
					: new String[]{"pause", "resume", "reload", "flag", "unflag", "cache"};
			return TabCompleter.completeString(args[0], completions);
		}

		args[0] = args[0].toLowerCase(Locale.ENGLISH);

		if ("flag".equals(args[0]) || "unflag".equals(args[0])) {

			if (args.length == 2) {
				return TabCompleter.completeString(args[1], Stream.concat(Stream.of("selection"), plugin.config().enabledWorlds().stream()).toArray(String[]::new));
			}

			if (!"selection".equalsIgnoreCase(args[1]) && args.length <= 4) {
				return TabCompleter.completeInteger(args[args.length - 1]);
			}
		}

		return Collections.emptyList();
	}

}
