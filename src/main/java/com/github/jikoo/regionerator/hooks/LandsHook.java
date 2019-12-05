package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Regionerator;
import java.util.concurrent.ExecutionException;
import me.angeschossen.lands.api.integration.LandsIntegration;
import me.angeschossen.lands.api.land.LandWorld;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/lands.53313/>Lands</a>.
 *
 * @author Jikoo
 */
public class LandsHook extends PluginHook {

	private final LandsIntegration landsAPI;

	public LandsHook() {
		super("Lands");
		landsAPI = new LandsIntegration(Regionerator.getPlugin(Regionerator.class), false);
		landsAPI.initialize();
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		LandWorld landWorld = landsAPI.getLandWorld(chunkWorld.getName());

		if (landWorld == null) {
			return false;
		}

		try {
			return landsAPI.isClaimed(chunkWorld.getName(), chunkX, chunkZ).get();
		} catch (InterruptedException | ExecutionException | ClassCastException e) {
			e.printStackTrace();
			return true;
		}
	}
}
