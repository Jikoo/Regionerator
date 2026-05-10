package com.github.jikoo.regionerator.platform.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.jikoo.planarwrappers.event.Event;
import com.github.jikoo.regionerator.platform.RescueListener;
import com.github.jikoo.regionerator.util.yaml.Config;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

@NullMarked
public class AsyncRescueListener extends RescueListener {

  public AsyncRescueListener(Plugin plugin, Config config) {
    super(plugin, config);
  }

  @Override
  public boolean isUsable() {
    try {
      Class.forName("io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent");
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  @Override
  public void register() {
    Event.register(
        io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent.class,
        event -> {
          PlayerProfile profile = event.getConnection().getProfile();
          if (profile.getId() == null) {
            // This shouldn't be possible; player needs a UUID to connect.
            return;
          }
          handlePlayerLogin(new PlayerLogin() {
            @Override
            public Optional<Player> player() {
              return Optional.empty();
            }

            @Override
            public UUID getUuid() {
              return profile.getId();
            }

            @Override
            public Location getLocation() {
              return event.getSpawnLocation();
            }

            @Override
            public void setLocation(Location location) {
              event.setSpawnLocation(location);
            }
          });
        },
        plugin,
        EventPriority.LOWEST
    );
  }

  @Override
  protected CompletableFuture<Boolean> syncChunk(Location location, Consumer<Chunk> onLoaded) {
    World world = location.getWorld();

    // This shouldn't be possible - the player has to be joining a world.
    if (world == null) {
      return CompletableFuture.completedFuture(false);
    }

    CompletableFuture<Boolean> future = new CompletableFuture<>();
    world.getChunkAtAsyncUrgently(location).whenComplete((chunk, throwable) -> {
      if (throwable != null) {
        plugin.getLogger().log(Level.WARNING, "Failed to load chunk for rescue check", throwable);
        future.complete(false);
        return;
      }

      onLoaded.accept(chunk);
      future.complete(true);
    });

    return future;
  }

  @Override
  protected boolean rescueToRespawn(Player player, PlayerLogin login) {
    // TODO can dynamically register for PlayerJoinEvent or something here
    return false;
  }

}
