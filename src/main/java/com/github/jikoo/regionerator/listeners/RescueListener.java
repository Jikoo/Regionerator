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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.Collection;
import java.util.UUID;

public class RescueListener implements Listener {

  private final @NotNull Regionerator plugin;
  private final @NotNull NamespacedKey loggedInSinceFeature;

  public RescueListener(@NotNull Regionerator plugin) {
    this.plugin = plugin;
    this.loggedInSinceFeature = new NamespacedKey(plugin, "safe-logout");
  }

  @EventHandler(priority = EventPriority.MONITOR) // Run late so we get final post-plugin-modification location
  private void onPlayerQuit(@NotNull PlayerQuitEvent event) {
    Chunk chunk = event.getPlayer().getLocation().getChunk();
    NamespacedKey key = getLogoutKey(event.getPlayer().getUniqueId());
    chunk.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
  }

  @EventHandler(priority = EventPriority.LOWEST) // Run early so everyone overrides us
  private void onPlayerSpawn(@NotNull PlayerSpawnLocationEvent event) {
    Player player = event.getPlayer();
    // Check if the player has logged in since this feature was added.
    PersistentDataContainer playerPdc = player.getPersistentDataContainer();
    if (!playerPdc.has(loggedInSinceFeature, PersistentDataType.BYTE)) {
      playerPdc.set(loggedInSinceFeature, PersistentDataType.BYTE, (byte) 1);
      return;
    }

    Chunk chunk = event.getSpawnLocation().getChunk();
    PersistentDataContainer chunkPdc = chunk.getPersistentDataContainer();
    NamespacedKey logoutKey = getLogoutKey(player.getUniqueId());
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
    if (!plugin.config().rescueIfSafe() && !isUnsafe(player)) {
      return;
    }

    // If rescuing up, check if top block can be stood on safely.
    if (plugin.config().rescueToTopBlock()) {
      Block topBlock = player.getWorld().getHighestBlockAt(player.getLocation());
      if (!isUnsafe(topBlock.getType()) && !isNotStandable(topBlock)) {
        event.setSpawnLocation(topBlock.getLocation().add(0.5, 1, 0.5));
        return;
      }
    }

    // If rescuing to personal respawn location, do so if available.
    if (plugin.config().rescueToRespawn()) {
      Location spawnLoc = player.getBedSpawnLocation();
      if (spawnLoc != null) {
        event.setSpawnLocation(spawnLoc);
        return;
      }
    }

    // Otherwise, use respawn location of rescue world.
    World spawnWorld = plugin.config().getRescueWorld(player.getWorld());
    event.setSpawnLocation(spawnWorld.getSpawnLocation());
  }

  private boolean isUnsafe(Player player) {
    // Underground is probably unsafe, skip more expensive checks.
    if (player.getLocation().getBlockY() < player.getWorld().getSeaLevel()) {
      return true;
    }

    Block headBlock = player.getEyeLocation().getBlock();
    if (isUnsafe(headBlock.getType())) {
      return true;
    }

    // If the player's head is in a block with a full collision box they'll suffocate.
    Collection<BoundingBox> boxes = headBlock.getCollisionShape().getBoundingBoxes();
    if (!boxes.isEmpty() && boxes.stream().allMatch(box -> box.getVolume() == 1.0)) {
      return true;
    }

    Block footBlock = player.getLocation().getBlock();
    if (isUnsafe(footBlock.getType())) {
      return true;
    }

    // If the player is not standing on anything, they'll fall.
    // Could get a more accurate representation by checking if any block underneath the player intersects with their
    // hitbox when expanding it, but that seems like overkill.
    return isNotStandable(player.getLocation().subtract(0, 1, 0).getBlock());
  }

  private boolean isNotStandable(Block block) {
    return block.getCollisionShape().getBoundingBoxes().isEmpty();
  }

  private boolean isUnsafe(Material material) {
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
