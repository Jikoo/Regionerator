/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.regionerator.world.DummyChunk;
import net.sacredlabyrinth.Phaed.PreciousStones.PreciousStones;
import net.sacredlabyrinth.Phaed.PreciousStones.field.FieldFlag;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * PluginHook for <a href="https://github.com/elBukkit/PreciousStones/">PreciousStones</a>.
 */
public class PreciousStonesHook extends PluginHook {

	public PreciousStonesHook() {
		super("PreciousStones");
	}

	@Override
	public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ) {
		return PreciousStones.API().getChunkFields(new DummyChunk(chunkWorld, chunkX, chunkZ), FieldFlag.ALL).size() > 0;
	}

}
