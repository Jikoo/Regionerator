/*
 * Copyright (c) 2015-2023 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.hooks;

import com.github.jikoo.planarwrappers.util.Coords;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.plot.PlotArea;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class PlotSquaredHook extends PluginHook {

  public PlotSquaredHook() {
    super("PlotSquared");
  }

  @Override
  public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ) {
    int chunkBlockX = Coords.chunkToBlock(chunkX);
    int chunkBlockZ = Coords.chunkToBlock(chunkZ);

    BlockVector3 bottom = BlockVector3.at(chunkBlockX, 0, chunkBlockZ);
    BlockVector3 top = BlockVector3.at(chunkBlockX + 15, 255, chunkBlockZ + 15);

    CuboidRegion region = new CuboidRegion(bottom, top);

    PlotArea[] plotAreas = PlotSquared.platform().plotAreaManager().getPlotAreas(chunkWorld.getName(), region);

    for (PlotArea plotArea : plotAreas) {
      if (!plotArea.getPlots().isEmpty()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isAsyncCapable() {
    return true;
  }

}
