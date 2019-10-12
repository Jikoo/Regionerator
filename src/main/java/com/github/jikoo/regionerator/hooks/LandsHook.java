package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Regionerator;
import java.util.concurrent.ExecutionException;
import me.angeschossen.lands.api.landsaddons.LandsAddon;
import me.angeschossen.lands.api.objects.LandWorld;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/lands.53313/>Lands</a>.
 *
 * @author Jikoo
 */
public class LandsHook extends PluginHook {

	private final LandsAddon landsAPI;

	public LandsHook() {
		super("Lands");
		landsAPI = new LandsAddon(Regionerator.getPlugin(Regionerator.class), false);
		landsAPI.initialize();
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		LandWorld landWorld = landsAPI.getLandWorld(chunkWorld.getName());

		if (landWorld == null) {
			return false;
		}

		try {
			return (boolean) landsAPI.isClaimed(chunkWorld.getName(), chunkX, chunkZ).get();
		} catch (InterruptedException | ExecutionException | ClassCastException e) {
			e.printStackTrace();
			return true;
		}
	}
}
