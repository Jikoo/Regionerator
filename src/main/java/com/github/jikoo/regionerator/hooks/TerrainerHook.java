package com.github.jikoo.regionerator.hooks;

import com.epicnicity322.terrainer.core.terrain.Terrain;
import com.epicnicity322.terrainer.core.terrain.TerrainManager;
import com.epicnicity322.terrainer.core.terrain.WorldTerrain;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * PluginHook for <a href="https://github.com/chrisrnj/Terrainer">Terrainer</a>.
 */
public class TerrainerHook extends PluginHook {

    public TerrainerHook() {
        super("Terrainer");
    }

    @Override
    public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ) {
        for (Terrain terrain : TerrainManager.terrainsAtChunk(chunkWorld.getUID(), chunkX, chunkZ)) {
            if (!(terrain instanceof WorldTerrain)) return true; // Global world terrain is ignored.
        }
        return false;
    }

    @Override
    public boolean isAsyncCapable() {
        return true;
    }

}
