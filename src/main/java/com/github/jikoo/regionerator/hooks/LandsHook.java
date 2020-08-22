package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.Regionerator;
import me.angeschossen.lands.api.integration.LandsIntegration;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/lands.53313/>Lands</a>.
 *
 * @author Jikoo
 */
public class LandsHook extends PluginHook {

	private LandsIntegration landsAPI;

	public LandsHook() {
		super("Lands");
	}

	@Override
	public boolean isHookUsable() {
		return super.isHookUsable();
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return getLandsAPI().isClaimed(chunkWorld, chunkX, chunkZ);
	}

	@Override
	public boolean isAsyncCapable() {
		return true;
	}

	@NotNull
	private LandsIntegration getLandsAPI() {
		if (landsAPI == null) {
			landsAPI = new LandsIntegration(Regionerator.getPlugin(Regionerator.class));
		}

		return landsAPI;
	}

}
