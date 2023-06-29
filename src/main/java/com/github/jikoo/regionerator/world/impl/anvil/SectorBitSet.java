/*
 * Copyright (c) 2015-2023 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world.impl.anvil;

import java.util.BitSet;

class SectorBitSet {

  private final BitSet sectorStates = new BitSet();

  void set(int start, int length) {
    this.sectorStates.set(start, start + length);
  }

  void clear(int start, int length) {
    this.sectorStates.clear(start, start + length);
  }

  void clear() {
    if (sectorStates.length() > RegionFile.REGION_HEADER_SECTORS) {
      this.sectorStates.clear(RegionFile.REGION_HEADER_SECTORS, sectorStates.length());
    }
  }

  int getLastUsed() {
    return this.sectorStates.length() - 1;
  }

  int findContiguousRegion(int length) {
    int clearBit;
    int usedBit = RegionFile.REGION_HEADER_SECTORS - 1; // 0-index header sector count.

    do {
      clearBit = this.sectorStates.nextClearBit(usedBit);
      usedBit = this.sectorStates.nextSetBit(clearBit);
    } while (usedBit == -1 || usedBit - clearBit >= length);

    this.set(clearBit, length);

    return clearBit;
  }

}
