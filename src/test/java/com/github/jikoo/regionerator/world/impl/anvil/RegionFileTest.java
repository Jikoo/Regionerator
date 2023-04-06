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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.DataFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RegionFileTest {

  private Path workDir;
  private Path workFile;
  private Path resultDir;

  @BeforeEach
  void beforeEach() throws IOException {
    Path regionDir = Path.of("src", "test", "resources", "region");
    String regionFileName = "r.0.0.mca";
    Path inFile = regionDir.resolve(Path.of("in", regionFileName));
    workDir = regionDir.resolve(Path.of("work", getClass().getSimpleName()));
    Files.createDirectories(workDir);
    workFile = workDir.resolve(regionFileName);
    Files.copy(inFile, workFile, StandardCopyOption.REPLACE_EXISTING);
    resultDir = regionDir.resolve(Path.of("out"));
  }

  @AfterEach
  void afterEach() throws IOException {
    Files.deleteIfExists(workFile);
    Files.deleteIfExists(workDir);
  }

  @ParameterizedTest
  @CsvSource({ "0,0,0", "10,10", "1,20"})
  void testDeleteChunk(int x, int z) throws IOException, DataFormatException {
    Path resultFile = resultDir.resolve(Path.of("deleteChunk", x + "," + z, workFile.getFileName().toString()));
    ByteBuffer chunkHeaderBuffer = ByteBuffer.allocateDirect(RegionFile.CHUNK_HEADER_LENGTH);

    ByteBuffer resultRegionHeaderBuffer = ByteBuffer.allocateDirect(RegionFile.REGION_HEADER_LENGTH);
    try (RegionFile resultRegion = regionFile(resultFile, resultRegionHeaderBuffer, chunkHeaderBuffer)) {
      resultRegion.open(true);
      resultRegion.readHeader();
    }

    ByteBuffer workRegionHeaderBuffer = ByteBuffer.allocateDirect(RegionFile.REGION_HEADER_LENGTH);
    IntBuffer resultPointers = resultRegionHeaderBuffer.slice(0, RegionFile.SECTOR_BYTES).asIntBuffer();
    IntBuffer workPointers = workRegionHeaderBuffer.slice(0, RegionFile.SECTOR_BYTES).asIntBuffer();
    try (RegionFile workRegion = regionFile(workFile, workRegionHeaderBuffer, chunkHeaderBuffer)) {
      workRegion.open(false);
      workRegion.readHeader();

      assertNotEquals(resultPointers, workPointers, "Pointers must not match before edit");

      workRegion.deleteChunk(x, z);

      assertEquals(resultPointers, workPointers, "Pointers must match after edit");

      workRegion.writeHeader();
      workRegion.readHeader();

      assertEquals(resultPointers, workPointers, "Pointers must match after write and re-read");
    }
  }

  @Contract("_, _, _ -> new")
  private @NotNull RegionFile regionFile(
          @NotNull Path regionPath,
          @NotNull ByteBuffer regionHeaderBuffer,
          @NotNull ByteBuffer chunkHeaderBuffer) {
    return new RegionFile(
            regionPath,
            regionHeaderBuffer,
            chunkHeaderBuffer,
            true,
            "I am John RegionFile; I understand that providing my own buffer may be unsafe.");
  }

}
