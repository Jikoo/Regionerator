/*
 * Copyright (c) 2015-2024 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.Regionerator;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class RescueListener implements Listener {

  private final @NotNull Regionerator plugin;
  private final @NotNull NamespacedKey loggedInSinceFeature;

  public RescueListener(@NotNull Regionerator plugin) {
    this.plugin = plugin;
    this.loggedInSinceFeature = new NamespacedKey(plugin, "safe-logout");
  }

  /**
   * Paper fires {@code AsyncPlayerSpawnLocationEvent} instead of {@link PlayerSpawnLocationEvent}. We keep Spigot
   * compatibility by registering a reflective handler only if the Paper event class is present at runtime.
   */
  public void registerPaperAsyncSpawnIfPresent() {
    final Class<?> asyncEventClass;
    try {
      asyncEventClass = Class.forName("io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent");
    } catch (ClassNotFoundException ignored) {
      return; // Not running on Paper with this API.
    }

    Bukkit.getPluginManager().registerEvent(
        asyncEventClass.asSubclass(Event.class),
        this,
        EventPriority.LOWEST,
        (listener, event) -> handlePaperAsyncSpawn(event),
        plugin,
        true
    );

    plugin.getLogger().fine("Registered Paper AsyncPlayerSpawnLocationEvent handler (reflective).");
  }

  @EventHandler(priority = EventPriority.MONITOR) // Run late so we get final post-plugin-modification location
  private void onPlayerQuit(@NotNull PlayerQuitEvent event) {
    Chunk chunk = event.getPlayer().getLocation().getChunk();
    NamespacedKey key = getLogoutKey(event.getPlayer().getUniqueId());
    chunk.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
  }

  @SuppressWarnings("deprecation") // Paper warns about this; Paper handler is registered reflectively when available.
  @EventHandler(priority = EventPriority.LOWEST) // Run early so everyone overrides us
  private void onPlayerSpawn(@NotNull PlayerSpawnLocationEvent event) {
    Player player = event.getPlayer();
    // Check if the player has logged in since this feature was added.
    PersistentDataContainer playerPdc = player.getPersistentDataContainer();
    if (!playerPdc.has(loggedInSinceFeature, PersistentDataType.BYTE)) {
      playerPdc.set(loggedInSinceFeature, PersistentDataType.BYTE, (byte) 1);
      return;
    }

    applySpawnRescue(player.getUniqueId(), Optional.of(player), event.getSpawnLocation(), event::setSpawnLocation);
  }

  private void handlePaperAsyncSpawn(@NotNull Event event) throws EventException {
    // Runs on an async thread; we must compute the result on the main thread and block.
    final Object connection;
    final Object profile;
    final UUID uuid;
    final Location spawnLoc;
    try {
      connection = event.getClass().getMethod("getConnection").invoke(event);
      profile = connection.getClass().getMethod("getProfile").invoke(connection);
      uuid = (UUID) profile.getClass().getMethod("getId").invoke(profile);
      spawnLoc = (Location) event.getClass().getMethod("getSpawnLocation").invoke(event);
    } catch (ReflectiveOperationException ex) {
      throw new EventException(ex);
    }
    if (uuid == null || spawnLoc == null) {
      return;
    }

    try {
      Location result = Bukkit.getScheduler().callSyncMethod(plugin, () ->
          computeRescueSpawn(uuid, spawnLoc)
      ).get(5, TimeUnit.SECONDS);
      if (result != null) {
        event.getClass().getMethod("setSpawnLocation", Location.class).invoke(event, result);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      plugin.getLogger().log(Level.WARNING, "Failed to compute rescue spawn for async spawn event", e);
    } catch (ReflectiveOperationException e) {
      throw new EventException(e);
    }
  }

  private @NotNull Location computeRescueSpawn(@NotNull UUID uuid, @NotNull Location spawnLoc) {
    final Location[] out = new Location[] { spawnLoc };
    applySpawnRescue(uuid, Optional.empty(), spawnLoc, loc -> out[0] = loc);
    return out[0];
  }

  private void applySpawnRescue(
      @NotNull UUID uuid,
      @NotNull Optional<Player> player,
      @NotNull Location spawnLoc,
      @NotNull java.util.function.Consumer<Location> setSpawnLocation
  ) {
    Chunk chunk = spawnLoc.getChunk();
    PersistentDataContainer chunkPdc = chunk.getPersistentDataContainer();
    NamespacedKey logoutKey = getLogoutKey(uuid);
    // If the key is set, the chunk has not been deleted since the player last logged out.
    if (chunkPdc.has(logoutKey, PersistentDataType.BYTE)) {
      chunkPdc.remove(logoutKey);
      return;
    }

    // If rescue is not enabled, exit early. Note that we do still want to do the tagging in case enabled later.
    if (!plugin.config().rescueEnabled()) {
      return;
    }

    // Only rescue safe players if configured to do so.
    if (!plugin.config().rescueIfSafe() && !isUnsafe(spawnLoc)) {
      return;
    }

    // If rescuing up, check if top block can be stood on safely.
    if (plugin.config().rescueToTopBlock()) {
      World world = spawnLoc.getWorld();
      if (world != null) {
        Block topBlock = world.getHighestBlockAt(spawnLoc);
        if (!isUnsafe(topBlock.getType()) && !isNotStandable(topBlock)) {
          setSpawnLocation.accept(topBlock.getLocation().add(0.5, 1, 0.5));
          return;
        }
      }
    }

    // If rescuing to personal respawn location, do so if available.
    if (plugin.config().rescueToRespawn()) {
      Location respawn = player.map(Player::getBedSpawnLocation)
          .orElseGet(() -> Bukkit.getOfflinePlayer(uuid).getBedSpawnLocation());
      if (respawn != null) {
        setSpawnLocation.accept(respawn);
        return;
      }
    }

    // Otherwise, use respawn location of rescue world.
    World defaultWorld = spawnLoc.getWorld();
    if (defaultWorld == null) {
      defaultWorld = player.map(Player::getWorld).orElse(null);
    }
    if (defaultWorld == null) {
      return;
    }

    World spawnWorld = plugin.config().getRescueWorld(defaultWorld);
    setSpawnLocation.accept(spawnWorld.getSpawnLocation());
  }

  private boolean isUnsafe(@NotNull Location location) {
    World world = location.getWorld();
    if (world == null) {
      return true;
    }

    // Underground is probably unsafe, skip more expensive checks.
    if (location.getBlockY() < world.getSeaLevel()) {
      return true;
    }

    Block footBlock = location.getBlock();

    if (isUnsafe(footBlock.getType())) {
      return true;
    }

    Block headBlock = footBlock.getRelative(BlockFace.UP);
    if (isUnsafe(headBlock.getType())) {
      return true;
    }

    // If the player's head is in a block with a full collision box they'll suffocate.
    Collection<BoundingBox> boxes = headBlock.getCollisionShape().getBoundingBoxes();
    if (!boxes.isEmpty() && boxes.stream().allMatch(box -> box.getVolume() == 1.0)) {
      return true;
    }

    // If the player is not standing on anything, they'll fall.
    // Could get a more accurate representation by checking if any block underneath the player intersects with their
    // hitbox when expanding it, but that seems like overkill.
    Block underBlock = footBlock.getRelative(BlockFace.DOWN);
    return isUnsafe(underBlock.getType()) || isNotStandable(underBlock);
  }

  private boolean isNotStandable(@NotNull Block block) {
    return block.getCollisionShape().getBoundingBoxes().isEmpty();
  }

  private boolean isUnsafe(@NotNull Material material) {
    if (Tag.FIRE.isTagged(material)) {
      return true;
    }
    return switch (material) {
      case WATER, LAVA, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, POWDER_SNOW, BAMBOO -> true;
      default -> false;
    };
  }

  private @NotNull NamespacedKey getLogoutKey(@NotNull UUID uuid) {
    return new NamespacedKey(plugin, "safe-logout-" + uuid.toString().toLowerCase());
  }

}
