package com.github.jikoo.regionerator.spigot;

import com.github.jikoo.planarwrappers.event.Event;
import com.github.jikoo.regionerator.platform.RescueListener;
import com.github.jikoo.regionerator.util.yaml.Config;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@NullMarked
public class SyncRescueListener extends RescueListener {

  public SyncRescueListener(Plugin plugin, Config config) {
    super(plugin, config);
  }

  @Override
  public boolean isUsable() {
    return true;
  }

  @Override
  public void register() {
    Event.register(
        PlayerSpawnLocationEvent.class,
        event -> handlePlayerLogin(
            new PlayerLogin() {
              @Override
              public Optional<Player> player() {
                return Optional.of(event.getPlayer());
              }

              @Override
              public UUID getUuid() {
                return event.getPlayer().getUniqueId();
              }

              @Override
              public Location getLocation() {
                return event.getSpawnLocation();
              }

              @Override
              public void setLocation(Location location) {
                event.setSpawnLocation(location);
              }
            }
        ),
        plugin,
        EventPriority.LOWEST
    );
  }

  @Override
  protected CompletableFuture<Boolean> syncChunk(Location location, Consumer<Chunk> onLoaded) {
    Chunk chunk = location.getChunk();
    onLoaded.accept(chunk);
    return CompletableFuture.completedFuture(true);
  }

  @Override
  protected boolean rescueToRespawn(Player player, PlayerLogin login) {
    Location spawnLoc = player.getRespawnLocation();
    if (spawnLoc != null) {
      login.setLocation(spawnLoc);
      return true;
    }
    return false;
  }

}
