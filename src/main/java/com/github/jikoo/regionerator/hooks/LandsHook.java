package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.PluginHook;
import com.github.jikoo.regionerator.world.DummyChunk;
import me.angeschossen.lands.Lands;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://www.spigotmc.org/resources/lands.53313/>Lands</a>.
 *
 * @author Jikoo
 */
public class LandsHook extends PluginHook {

	public LandsHook() {
		super("Lands");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return Lands.getLandsAPI().isLandChunk(new DummyChunk(chunkWorld, chunkX, chunkZ));
	}
}
