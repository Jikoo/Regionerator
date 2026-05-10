package com.github.jikoo.regionerator.platform;

import com.github.jikoo.regionerator.util.yaml.Config;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@NullMarked
public abstract class RescueListener {

  protected final Plugin plugin;
  private final Config config;
  private final NamespacedKey loggedInSinceFeature;

  protected RescueListener(Plugin plugin, Config config) {
    this.plugin = plugin;
    this.loggedInSinceFeature = new NamespacedKey(plugin, "safe-logout");
    this.config = config;
  }

  public abstract boolean isUsable();

  public abstract void register();

  protected void handlePlayerLogin(PlayerLogin login) {
    // Check if the player has logged in since this feature was added.
    if (!login.player().map(this::hasPlayerLoggedInSinceFeature).orElse(true)) {
      // If not, we can't tell if they should be rescued.
      return;
    }

    // Block until the location the player is logging in has been loaded and is checked for safety.
    syncChunk(login.getLocation(), chunk -> checkSafety(login, chunk)).join();
  }

  private boolean hasPlayerLoggedInSinceFeature(Player player) {
    PersistentDataContainer playerPdc = player.getPersistentDataContainer();

    if (playerPdc.has(loggedInSinceFeature, PersistentDataType.BYTE)) {
      return true;
    }

    playerPdc.set(loggedInSinceFeature, PersistentDataType.BYTE, (byte) 1);
    return false;
  }

  protected abstract CompletableFuture<Boolean> syncChunk(
      Location location,
      Consumer<Chunk> onLoaded
  );

  private void checkSafety(PlayerLogin login, Chunk chunk) {
    PersistentDataContainer chunkPdc = chunk.getPersistentDataContainer();
    NamespacedKey logoutKey = getLogoutKey(login.getUuid());
    // If the key is set, the chunk has not been deleted since the player last logged out.
    if (chunkPdc.has(logoutKey, PersistentDataType.BYTE)) {
      chunkPdc.remove(logoutKey);
      return;
    }

    // If rescue is not enabled, exit early. Note that we do still want to do the tagging in case enabled later.
    if (!config.rescueEnabled()) {
      return;
    }

    // Only rescue safe players if configured to do so.
    if (!config.rescueIfSafe() && !isUnsafe(chunk, login.getLocation())) {
      return;
    }

    // If rescuing up, check if top block can be stood on safely.
    if (config.rescueToTopBlock()) {
      World world = chunk.getWorld();
      Block topBlock = world.getHighestBlockAt(login.getLocation().getBlockX(), login.getLocation().getBlockZ());
      if (!isUnsafe(topBlock.getType()) && !isNotStandable(topBlock)) {
        login.setLocation(topBlock.getLocation().add(0.5, 1, 0.5));
        return;
      }
    }

    // If rescuing to personal respawn location, do so if available.
    if (config.rescueToRespawn() && login.player().map(player -> rescueToRespawn(player, login)).orElse(false)) {
      return;
    }

    // Otherwise, use respawn location of rescue world.
    World world = chunk.getWorld();
    world = config.getRescueWorld(world);
    login.setLocation(world.getSpawnLocation());
  }

  protected abstract boolean rescueToRespawn(Player player, PlayerLogin login);

  private boolean isUnsafe(Chunk chunk, Location location) {
    // Underground is probably unsafe, skip more expensive checks.
    if (location.getBlockY() < chunk.getWorld().getSeaLevel()) {
      return true;
    }

    Block footBlock = chunk.getBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());

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

  private NamespacedKey getLogoutKey(UUID uuid) {
    return new NamespacedKey(plugin, "safe-logout-" + uuid.toString().toLowerCase());
  }

  protected interface PlayerLogin {

    Optional<Player> player();

    UUID getUuid();

    Location getLocation();

    void setLocation(Location location);

  }

}
