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

import com.github.jikoo.planarwrappers.util.Coords;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

public class RegionFile implements AutoCloseable {

  public static final Pattern FILE_NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)(\\.mc[ar])$");

  // Useful constants for region file parsing.
  /** Each sector is made up of a fixed number of bytes. */
  private static final int SECTOR_BYTES = 4096;
  /** Integers comprise certain entire sectors. */
  private static final int SECTOR_INTS = SECTOR_BYTES / Integer.BYTES;
  /** The header consumes a number of sectors at the start of the file. */
  static final int REGION_HEADER_SECTORS = 2;
  /** The total header size in bytes. */
  private static final int REGION_HEADER_LENGTH = SECTOR_BYTES * REGION_HEADER_SECTORS;
  private static final int CHUNK_NOT_PRESENT = 0;
  /** Chunks start with a header declaring their size and compression type. */
  private static final int CHUNK_HEADER_LENGTH = Integer.BYTES + 1;
  /** Internally-saved chunk data may not exceed a certain number of sectors. */
  private static final int CHUNK_MAXIMUM_SECTORS = 256;
  /** Internally-saved chunk data may not exceed a certain length. */
  private static final int CHUNK_MAXIMUM_LENGTH = CHUNK_MAXIMUM_SECTORS * SECTOR_BYTES - CHUNK_HEADER_LENGTH;
  /** Chunks exceeding {@link #CHUNK_MAXIMUM_LENGTH} are flagged and saved externally. */
  private static final int FLAG_CHUNK_TOO_LARGE = 0b10000000;
  /** Bitmask for a local chunk coordinate. */
  private static final int BITMASK_LOCAL_CHUNK = 0x1F;
  /** Bitmask for the number of sectors used to store a chunk's data. */
  private static final int BITMASK_OFFSET_SECTOR_COUNT = 0xFF;
  /** Bitmask for the start sector of a chunk's data. */
  private static final int BITMASK_OFFSET_START_SECTOR = 0xFFFFFF;
  // TODO may want a util to construct these automatically from bitmasks
  /** Number of bits in {@link #BITMASK_OFFSET_SECTOR_COUNT} */
  private static final int BIT_COUNT_LOCAL_CHUNK = 5;
  /** Number of bits in {@link #BITMASK_OFFSET_SECTOR_COUNT} */
  private static final int BIT_COUNT_OFFSET_SECTOR_COUNT = 8;

  private final Path regionPath;
  private final boolean sync;
  private final int regionX;
  private final int regionZ;
  private final ByteBuffer regionHeader;
  private final IntBuffer chunkOffsets;
  private final IntBuffer chunkTimestamps;
  private boolean regionHeaderRead = false;

  private final ByteBuffer chunkHeader;

  private @Nullable FileChannel file;

  public RegionFile(@NotNull Path regionPath, boolean sync) {
    if (Files.isDirectory(regionPath)) {
      throw new IllegalArgumentException("Provided region file is a directory " + regionPath.toAbsolutePath());
    }
    this.regionPath = regionPath.normalize();
    Matcher matcher = FILE_NAME_PATTERN.matcher(this.regionPath.getFileName().toString());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Provided region file does not match name format " + regionPath.getFileName());
    }
    this.regionX = Integer.parseInt(matcher.group(1));
    this.regionZ = Integer.parseInt(matcher.group(2));
    this.sync = sync;

    // A direct ByteBuffer is much slower to allocate but more performant when reading/writing.
    // As we care most about minimizing the time we spend doing I/O, a direct buffer makes sense.
    regionHeader = ByteBuffer.allocateDirect(SECTOR_BYTES * REGION_HEADER_SECTORS);
    chunkOffsets = regionHeader.slice(0, SECTOR_BYTES).asIntBuffer();
    chunkTimestamps = regionHeader.position(SECTOR_BYTES).slice().asIntBuffer();
    regionHeader.position(0);

    chunkHeader = ByteBuffer.allocateDirect(CHUNK_HEADER_LENGTH);
  }

  public void open(boolean readOnly) throws IOException {
    close();

    OpenOption[] options;
    if (readOnly) {
      // This is the only case where a RandomAccessFile outperforms a FileChannel, but the gain is so marginal that it
      // isn't worth retooling the rest of the class around accepting two completely different input methods.
      options = new OpenOption[] { StandardOpenOption.READ };
    } else if (sync) {
      options = new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DSYNC };
    } else {
      options = new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE };
    }

    file = FileChannel.open(regionPath, options);
  }

  public void readHeader() throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }

    regionHeader.rewind();
    int bytesRead = file.read(regionHeader);
    regionHeader.flip();

    if (bytesRead != -1 && bytesRead < REGION_HEADER_LENGTH) {
      throw new DataFormatException(String.format(
              "Invalid header for %s; read %s bytes of expected %s",
              regionPath.getFileName(),
              bytesRead,
              REGION_HEADER_LENGTH));
    }

    regionHeaderRead = true;
  }

  public void writeHeader() throws IOException {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }
    if (file == null) {
      throw new ClosedChannelException();
    }
    regionHeader.rewind();
    file.write(regionHeader, 0);
  }

  public boolean isPresent(int x, int z) {
    checkLegalChunks(x, z);
    return isPresent(packIndex(x, z));
  }

  public boolean isPresent(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    return chunkOffsets.get(index) != CHUNK_NOT_PRESENT;
  }

  public long getLastModified(int x, int z) {
    checkLegalChunks(x, z);
    return getLastModified(packIndex(x, z));
  }

  public long getLastModified(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    return TimeUnit.MILLISECONDS.convert(chunkTimestamps.get(index), TimeUnit.SECONDS);
  }

  public void deleteChunk(int x, int z) {
    deleteChunk(packIndex(x, z));
  }

  public void deleteChunk(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    chunkOffsets.put(index, 0);
    chunkTimestamps.put(index, (int) Instant.now().getEpochSecond());
    // FUTURE need to fetch old value and free sectors (and track sectors to start with)
  }

  private @NotNull Path getXlChunkPath(int index) {
    int localX = unpackLocalX(index);
    int localZ = unpackLocalZ(index);
    String fileName = String.format(
            "c.%s.%s.mcc",
            Coords.regionToChunk(regionX) + localX,
            Coords.regionToChunk(regionZ) + localZ);
    return regionPath.resolveSibling(fileName);
  }

  // FUTURE publicize readChunk
  private @Nullable InputStream readChunk(int x, int z) throws IOException, DataFormatException {
    return readChunk(x, z, true);
  }

  private @Nullable InputStream readChunk(int x, int z, boolean decode) throws IOException, DataFormatException {
    return readChunk(packIndex(x, z), decode);
  }

  private @Nullable InputStream readChunk(int index) throws IOException, DataFormatException {
    return readChunk(index, true);
  }

  // FUTURE add chunk identifiers to exceptions
  private @Nullable InputStream readChunk(int index, boolean decode) throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    int packedOffsetData = chunkOffsets.get(index);

    if (packedOffsetData == CHUNK_NOT_PRESENT) {
      return null;
    }

    int startSector = packedOffsetData >> BIT_COUNT_OFFSET_SECTOR_COUNT & BITMASK_OFFSET_START_SECTOR;
    if (startSector < REGION_HEADER_SECTORS) {
      throw new DataFormatException("start sector in region header");
    }

    chunkHeader.clear();
    file.read(chunkHeader, (long) startSector * SECTOR_BYTES);
    chunkHeader.flip();

    if (chunkHeader.remaining() < CHUNK_HEADER_LENGTH) {
      throw new DataFormatException("chunk header too short");
    }

    int declaredLength = (packedOffsetData & BITMASK_OFFSET_SECTOR_COUNT) * SECTOR_BYTES;
    int realLength = chunkHeader.getInt();

    if (realLength > declaredLength || realLength < 0) {
      // FUTURE Should external chunks skip this check? Need to actually read how mcc is handled
      throw new DataFormatException("invalid chunk data length");
    }

    byte encoding = chunkHeader.get();
    RegionCompression compression;
    if (decode) {
      compression = RegionCompression.byCompressionId(encoding);
      if (compression == null) {
        throw new DataFormatException("Unknown compression ID " + encoding);
      }
    } else {
      compression = RegionCompression.NONE;
    }

    if ((encoding & FLAG_CHUNK_TOO_LARGE) != 0) {
      return compression.decode(getXlChunk(index));
    }

    // FUTURE
    throw new IOException("Lazy person negates compiler warnings for " + index);
  }

  private @NotNull InputStream getXlChunk(int index) throws IOException {
    Path xlFile = getXlChunkPath(index);
    if (!Files.isRegularFile(xlFile)) {
      throw new NoSuchFileException(xlFile.toAbsolutePath().toString());
    }

    return Files.newInputStream(xlFile, StandardOpenOption.READ);
  }

  // FUTURE
  //  fragment -> all decompressed .mcc for readability
  //  defragment(RegionCompression) -> parse and recompress, re-assign sectors and fully rewrite file

  @Override
  public void close() throws IOException {
    if (file != null) {
      file.close();
    }
  }

  private void checkLegalChunks(int chunkX, int chunkZ) {
    boolean regionalX = checkLocalOrRegionalChunk(chunkX, regionX);
    boolean regionalZ = checkLocalOrRegionalChunk(chunkZ, regionZ);
    /*
     * If the raw regional state matches, the pair is either local-local or regional-regional and is good. However,
     * region 0 on one axis will result in valid coordinates that are both regional and local. As a result of assuming
     * locality (because ideally everyone will use local coordinates and just pass the first condition outright),
     * valid chunk coordinates may be deemed a regional/local mismatch due to the ambiguity of one axis. Therefore,
     * if a coordinate is regional and the other coordinate is in region 0, the other coordinate is also regional.
     */
    if (regionalX != regionalZ && (regionalZ && regionX != 0 || regionalX && regionZ != 0)) {
      // Mixed regional and local chunks, i.e. probably trying to query the wrong region.
      throw new IllegalArgumentException("Mismatched local and regional coordinates " + chunkX + " and " + chunkZ);
    }
  }

  private boolean checkLocalOrRegionalChunk(int chunk, int region) {
    if (chunk >= 0 && chunk <= BITMASK_LOCAL_CHUNK) {
      // Note: Can't tell whether individual axes are regional or local for region 0.
      return false;
    }
    int regionChunk = region << BIT_COUNT_LOCAL_CHUNK;
    if (chunk < regionChunk || chunk > regionChunk + BITMASK_LOCAL_CHUNK) {
      throw new IllegalArgumentException("Chunk " + chunk + " is out of range for region " + region);
    }
    return true;
  }

  public static int packIndex(int x, int z) {
    return (z & BITMASK_LOCAL_CHUNK) << BIT_COUNT_LOCAL_CHUNK | (x & BITMASK_LOCAL_CHUNK);
  }

  public static int unpackLocalX(int index) {
    return index & BITMASK_LOCAL_CHUNK;
  }

  public static int unpackLocalZ(int index) {
    return index >> BIT_COUNT_LOCAL_CHUNK & BITMASK_LOCAL_CHUNK;
  }

  private static @NotNull String friendlyIndex(int index) {
    return "[" + unpackLocalX(index) + "," + unpackLocalZ(index) + "]";
  }

}
