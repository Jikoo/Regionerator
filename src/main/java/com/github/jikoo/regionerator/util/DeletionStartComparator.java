/*
 * Copyright (c) 2015-2025 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.util;

import com.github.jikoo.regionerator.util.yaml.MiscData;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class DeletionStartComparator implements Comparator<String> {

  private final MiscData miscData;

  public DeletionStartComparator(@NotNull MiscData miscData) {
    this.miscData = miscData;
  }

  @Override
  public int compare(String worldName1, String worldName2) {
    return Long.compare(miscData.getLastCycleStart(worldName1), miscData.getLastCycleStart(worldName2));
  }

}
