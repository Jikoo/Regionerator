package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.PluginHook;
import com.github.jikoo.regionerator.world.DummyChunk;
import net.sacredlabyrinth.Phaed.PreciousStones.PreciousStones;
import net.sacredlabyrinth.Phaed.PreciousStones.field.FieldFlag;
import org.bukkit.World;

/**
 * PluginHook for <a href=https://github.com/marcelo-mason/PreciousStones/>PreciousStones</a>.
 *
 * @author Jikoo
 */
public class PreciousStonesHook extends PluginHook {

	public PreciousStonesHook() {
		super("PreciousStones");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return PreciousStones.API().getChunkFields(new DummyChunk(chunkWorld, chunkX, chunkZ), FieldFlag.ALL).size() > 0;
	}
}
